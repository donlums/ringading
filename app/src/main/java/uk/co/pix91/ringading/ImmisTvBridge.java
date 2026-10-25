package uk.co.pix91.ringading;

import android.content.Context;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

/**
 * TV-local, experimental IMMIS-to-MPEG-TS bridge.
 * No recording, external player server, certificate bypass or raw stream logging.
 * Protocol research: fronzbot/blinkpy and Teethree89/ha-blink-live-view-proxy (MIT).
 */
final class ImmisTvBridge implements AutoCloseable {
    interface Listener {
        void update(String status);
        void error(String message);
    }
    private static final int MAX_IMMI_PAYLOAD = 1024 * 1024;
    private static final int MAX_SNIFF_BUFFER = 64 * 1024;
    private static final int SESSION_SECONDS = 35;
    private final LiveViewApi.Session session;
    private final Listener listener;
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final String secret = java.util.UUID.randomUUID().toString();
    private final Object writeLock = new Object();
    private ServerSocket local;
    private Socket playerClient;
    private SSLSocket remote;
    private Thread worker;
    private Thread heartbeat;
    private volatile String stage = "Waiting for player";
    private volatile String failure = "";
    private volatile boolean tlsEstablished;
    private volatile boolean streamVerified;
    private volatile long immiFrames;
    private volatile long nonVideoFrames;
    private volatile long ignoredMediaFrames;
    private volatile long tsPackets;
    private volatile long videoBytes;

    ImmisTvBridge(Context ctx, LiveViewApi.Session liveSession, Listener events) {
        session=liveSession;
        listener=events;
    }

    String start() throws IOException {
        local = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"));
        local.setSoTimeout(15000);
        worker=new Thread(this::receiveAndServe, "ringading-immis-bridge");
        worker.setDaemon(true);
        worker.start();
        return "http://127.0.0.1:" + local.getLocalPort() + "/live/" + secret + ".ts";
    }

    String diagnostics() {
        return "Transport IMMIS · TLS " + (tlsEstablished?"OK":"not verified")
            + " · Video sync " + (streamVerified?"OK":"not yet")
            + "\nIMMI frames " + immiFrames + " · Non-video " + nonVideoFrames
            + " · Invalid media " + ignoredMediaFrames
            + "\nTS packets " + tsPackets + " · Video bytes " + videoBytes
            + "\nStage: " + stage
            + (failure.isEmpty()?"":"\nBridge error: "+failure);
    }

    String failure() { return failure; }

    private void report(String message) {
        stage=message;
        if(listener!=null) listener.update(message);
    }
    private void fail(String message) {
        failure=message;
        stage="Failed";
        if(listener!=null) listener.error(message);
    }

    private void receiveAndServe() {
        String operation="Opening TV video socket";
        OutputStream output=null;
        boolean httpStarted=false;
        try {
            report("Waiting for local TV player");
            playerClient=local.accept();
            playerClient.setSoTimeout(16000);
            DataInputStream browser=new DataInputStream(new BufferedInputStream(playerClient.getInputStream()));
            String request=readLine(browser,512);
            String expected="/live/" + secret + ".ts";
            if(!request.equals("GET "+expected+" HTTP/1.1")
                && !request.equals("GET "+expected+" HTTP/1.0"))
                throw new IOException("Unexpected player path");
            for(int i=0;i<32;i++) {
                String header=readLine(browser,2048);
                if(header.isEmpty())break;
                if(i==31)throw new IOException("Oversized video player headers");
            }
            output=new BufferedOutputStream(playerClient.getOutputStream(), 32768);

            operation="Establishing Blink TLS";
            report("Connecting securely to Blink");
            URI uri=URI.create(session.server);
            String host=uri.getHost();
            if(host==null || uri.getPort()<1 || uri.getPort()>65535)
                throw new IOException("Invalid Blink camera endpoint");
            if(session.camera.serial==null || session.camera.serial.isEmpty())
                throw new IOException("Camera serial absent in Blink discovery");
            remote=(SSLSocket)SSLSocketFactory.getDefault().createSocket(host,uri.getPort());
            remote.setSoTimeout(13000);
            SSLParameters tls=remote.getSSLParameters();
            tls.setEndpointIdentificationAlgorithm("HTTPS");
            remote.setSSLParameters(tls);
            remote.startHandshake();
            tlsEstablished=true;

            DataInputStream source=new DataInputStream(new BufferedInputStream(remote.getInputStream()));
            DataOutputStream commands=new DataOutputStream(remote.getOutputStream());
            synchronized(writeLock) { commands.write(authHeader(uri)); commands.flush(); }
            startHeartbeat(commands);

            operation="Receiving Blink IMMI";
            report("Awaiting video packets from Blink");
            long deadline=System.currentTimeMillis()+SESSION_SECONDS*1000L;
            ByteArrayOutputStream firstPackets=new ByteArrayOutputStream(8192);
            while(running.get() && System.currentTimeMillis()<deadline) {
                int type;
                try { type=source.readUnsignedByte(); }
                catch(SocketTimeoutException timeout) {
                    if(!streamVerified) throw new IOException("No video packets from Blink (13-second timeout)");
                    continue;
                }
                source.readInt(); // Sequence is not needed for MPEG-TS delivery.
                int size=source.readInt();
                if(size<0 || size>MAX_IMMI_PAYLOAD)
                    throw new IOException("Malformed Blink IMMI frame size");
                byte[] payload=new byte[size];
                source.readFully(payload);
                immiFrames++;
                if(type!=0){nonVideoFrames++;continue;}
                if(size<TsProbe.TS_BYTES || (payload[0]&0xff)!=0x47) {
                    ignoredMediaFrames++;
                    continue;
                }
                if(!streamVerified) {
                    firstPackets.write(payload,0,payload.length);
                    if(firstPackets.size()>MAX_SNIFF_BUFFER) {
                        firstPackets.reset();
                        ignoredMediaFrames++;
                        report("Received Blink media, but no validated MPEG-TS packets");
                        continue;
                    }
                    if(firstPackets.size()<TsProbe.SNIFF_BYTES)continue;
                    byte[] data=firstPackets.toByteArray();
                    int offset=TsProbe.syncOffset(data,data.length);
                    if(offset<0) {
                        // Ignore non-TS framing without feeding it to Media3.
                        if(firstPackets.size()>8192) {
                            firstPackets.reset();
                            ignoredMediaFrames++;
                        }
                        continue;
                    }
                    httpStarted=true;
                    byte[] header=("HTTP/1.1 200 OK\r\n"
                        +"Content-Type: video/mp2t\r\n"
                        +"Cache-Control: no-store\r\n"
                        +"Connection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII);
                    output.write(header);
                    output.write(data,offset,data.length-offset);
                    output.flush();
                    videoBytes+=data.length-offset;
                    tsPackets+=(data.length-offset)/TsProbe.TS_BYTES;
                    streamVerified=true;
                    firstPackets.reset();
                    report("Verified MPEG-TS packets · TV decoder starting");
                    continue;
                }
                output.write(payload);
                output.flush();
                videoBytes+=payload.length;
                tsPackets+=payload.length/TsProbe.TS_BYTES;
                if(immiFrames%30==0) report("Live media received · "+tsPackets+" TS packets");
            }
            if(streamVerified)report("Blink live session finished · "+tsPackets+" TS packets delivered");
            else fail("Blink connected, but returned no MPEG-TS video packets");
        } catch(Exception error) {
            if(running.get()) {
                String description;
                if(error instanceof javax.net.ssl.SSLException)
                    description="Blink TLS verification failed";
                else if(error instanceof SocketTimeoutException)
                    description=operation+" timed out";
                else if(error instanceof IOException
                    && error.getMessage()!=null
                    && (error.getMessage().startsWith("No video")
                    || error.getMessage().startsWith("Camera serial")
                    || error.getMessage().startsWith("Malformed Blink")))
                    description=error.getMessage();
                else description=operation+" failed ("+error.getClass().getSimpleName()+")";
                fail(description);
            }
        } finally {
            if(!httpStarted && output!=null) {
                try {
                    String body="Blink stream unavailable; check Ringading's on-screen diagnostics.";
                    byte[] bytes=body.getBytes(StandardCharsets.US_ASCII);
                    output.write(("HTTP/1.1 503 Service Unavailable\r\n"
                        +"Content-Type: text/plain\r\n"
                        +"Cache-Control: no-store\r\n"
                        +"Content-Length: "+bytes.length+"\r\n"
                        +"Connection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
                    output.write(bytes);
                    output.flush();
                } catch(Exception ignored) {}
            }
            close();
        }
    }

    private void startHeartbeat(DataOutputStream commands) {
        heartbeat=new Thread(()->{
            int tick=0;
            while(running.get()) {
                try {
                    synchronized(writeLock) {
                        if(tick%10==0) {
                            commands.writeByte(0x0a);
                            commands.writeInt(tick/10+1);
                            commands.writeInt(0);
                        }
                        commands.writeByte(0x12);
                        commands.writeInt(1000);
                        commands.writeInt(24);
                        for(int i=0;i<24;i++)commands.writeByte(0);
                        commands.flush();
                    }
                    tick++;
                    Thread.sleep(1000);
                } catch(Exception e) { break; }
            }
        },"ringading-immis-heartbeat");
        heartbeat.setDaemon(true);
        heartbeat.start();
    }

    private static String readLine(DataInputStream input,int max) throws IOException {
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        for(int i=0;i<max;i++) {
            int b=input.read();
            if(b<0)throw new IOException("Local player disconnected");
            if(b=='\n')return bytes.toString("US-ASCII").trim();
            if(b=='\r')continue;
            bytes.write(b);
        }
        throw new IOException("Local player request too large");
    }

    private static void fixedField(DataOutputStream stream,String value,int width) throws IOException {
        byte[] raw=value==null?new byte[0]:value.getBytes(StandardCharsets.UTF_8);
        byte[] padded=new byte[width];
        System.arraycopy(raw,0,padded,0,Math.min(width,raw.length));
        stream.writeInt(width);
        stream.write(padded);
    }

    private byte[] authHeader(URI uri) throws IOException {
        String clientId="0";
        String query=uri.getRawQuery();
        if(query!=null) for(String item:query.split("&")) {
            String[] pair=item.split("=",2);
            if(pair.length==2 && "client_id".equals(pair[0]))clientId=pair[1];
        }
        int id;
        try { id=Integer.parseInt(clientId); }
        catch(NumberFormatException e) { throw new IOException("Invalid Blink stream client"); }
        String path=uri.getPath();
        if(path==null||path.isEmpty()) throw new IOException("Blink stream has no connection ID");
        String connection=path.substring(path.lastIndexOf('/')+1).split("__",2)[0];
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        DataOutputStream out=new DataOutputStream(bytes);
        out.writeInt(40);
        fixedField(out,session.camera.serial,16);
        out.writeInt(id);
        out.writeByte(0x01);
        out.writeByte(0x08);
        // Existing BlinkPy-compatible default, not an OAuth token.
        fixedField(out,"",64);
        fixedField(out,connection,16);
        out.writeInt(1);
        out.flush();
        return bytes.toByteArray();
    }

    @Override public void close() {
        if(!running.getAndSet(false))return;
        try { if(remote!=null)remote.close(); }catch(Exception ignored){}
        try { if(playerClient!=null)playerClient.close(); }catch(Exception ignored){}
        try { if(local!=null)local.close(); }catch(Exception ignored){}
        if(heartbeat!=null)heartbeat.interrupt();
    }
}
