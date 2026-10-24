package uk.co.lumsdon.ringading;

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
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

/**
 * Prototype direct Blink IMMIS -> localhost MPEG-TS transport bridge.
 * No phone, HA, remote proxy, remote web server, or recording.
 * Protocol research: fronzbot/blinkpy and Teethree89/ha-blink-live-view-proxy (MIT).
 */
final class ImmisTvBridge implements AutoCloseable {
    interface Listener {
        void update(String status);
        void error(String stage);
    }
    private static final int MAX_PAYLOAD = 1024 * 1024;
    private final Context app;
    private final LiveViewApi.Session session;
    private final Listener listener;
    private final AtomicBoolean running=new AtomicBoolean(true);
    private ServerSocket local;
    private Socket playerClient;
    private SSLSocket remote;
    private Thread worker;
    private Thread heartbeat;
    private final Object writeLock=new Object();
    private long lastVideoMs=0;
    private volatile String stage="Awaiting player";
    private static final int SESSION_SECONDS=35;
    private final String secret;

    ImmisTvBridge(Context ctx, LiveViewApi.Session session, Listener listener) {
        this.app=ctx.getApplicationContext();
        this.session=session;
        this.listener=listener;
        secret=java.util.UUID.randomUUID().toString();
    }

    String start() throws IOException {
        local=new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"));
        local.setSoTimeout(15000);
        worker=new Thread(this::receiveAndServe, "ringading-immis-bridge");
        worker.setDaemon(true);
        worker.start();
        return "http://127.0.0.1:" + local.getLocalPort() + "/live/" + secret + ".ts";
    }
    private void report(String status) {
        stage=status;
        if(listener!=null) listener.update(status);
    }
    private void fail(String error) {
        if(listener!=null) listener.error(error);
    }
    private void receiveAndServe() {
        String current="Local player";
        try {
            report("Waiting for TV video player");
            playerClient=local.accept();
            playerClient.setSoTimeout(12000);
            DataInputStream clientIn=new DataInputStream(new BufferedInputStream(playerClient.getInputStream()));
            String firstLine=readLine(clientIn, 512);
            if(!("GET /live/" + secret + ".ts HTTP/1.1").equals(firstLine)
                    && !("GET /live/" + secret + ".ts HTTP/1.0").equals(firstLine))
                throw new IOException("Unexpected local player request");
            for (int i=0;i<32;i++) {
                String line=readLine(clientIn, 2048);
                if(line.isEmpty()) break;
                if(i==31) throw new IOException("Too many HTTP headers");
            }
            OutputStream output=new BufferedOutputStream(playerClient.getOutputStream(), 32768);
            byte[] headers=("HTTP/1.1 200 OK\r\n"
                + "Content-Type: video/mp2t\r\n"
                + "Cache-Control: no-store\r\n"
                + "Connection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII);
            output.write(headers);
            output.flush();

            current="Blink TLS";
            report("Connecting to Blink camera");
            URI uri=URI.create(session.server);
            String hostname=uri.getHost();
            if(hostname==null || uri.getPort()<1 || uri.getPort()>65535)
                throw new IOException("Invalid Blink server endpoint");
            remote=(SSLSocket)SSLSocketFactory.getDefault().createSocket(hostname, uri.getPort());
            remote.setSoTimeout(11000);
            javax.net.ssl.SSLParameters secureParams=remote.getSSLParameters();
            secureParams.setEndpointIdentificationAlgorithm("HTTPS");
            remote.setSSLParameters(secureParams);
            remote.startHandshake(); // Trust anchor and DNS-name verification are both mandatory.
            DataInputStream stream=new DataInputStream(new BufferedInputStream(remote.getInputStream()));
            DataOutputStream commands=new DataOutputStream(remote.getOutputStream());
            byte[] authentication=authHeader(uri);
            synchronized(writeLock) { commands.write(authentication); commands.flush(); }
            startHeartbeat(commands);

            current="Blink media";
            report("Waiting for live video");
            long cutoff=System.currentTimeMillis()+SESSION_SECONDS*1000L;
            int frames=0;
            while(running.get() && System.currentTimeMillis()<cutoff) {
                int type;
                try { type=stream.readUnsignedByte(); }
                catch(SocketTimeoutException timeout) {
                    if(frames==0) throw new IOException("No video received from Blink within timeout");
                    continue;
                }
                int sequence=stream.readInt();
                int size=stream.readInt();
                if(size<0 || size>MAX_PAYLOAD) throw new IOException("Blink frame exceeded safe size");
                if(size==0)continue;
                byte[] packet=new byte[size];
                stream.readFully(packet);
                if(type!=0 || packet.length<188 || (packet[0]&0xff)!=0x47)continue;
                // IMMI type 0 is an MPEG-TS payload with 188-byte sync packets.
                output.write(packet);
                frames++;
                if (frames==1) report("Playing Blink live camera");
                if(frames%8==0)output.flush();
                lastVideoMs=System.currentTimeMillis();
            }
            output.flush();
            if(frames==0) fail("Blink session opened, but no decodable MPEG-TS packets arrived");
            else report("Live view ended · Blink session time limit");
        } catch(Exception e) {
            if(running.get()) {
                String explanation=e instanceof javax.net.ssl.SSLException
                    ? "Camera TLS handshake rejected: " + e.getClass().getSimpleName()
                    : e instanceof SocketTimeoutException
                        ? current + ": timed out"
                        : current + ": " + e.getClass().getSimpleName();
                fail(explanation);
            }
        } finally { close(); }
    }
    private void startHeartbeat(DataOutputStream commands) {
        heartbeat=new Thread(()->{
            int tick=0;
            while(running.get()) {
                try {
                    synchronized(writeLock) {
                        // Keepalive every 10s and latency marker every second.
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
        }, "ringading-immis-heartbeat");
        heartbeat.setDaemon(true);
        heartbeat.start();
    }
    private static String readLine(DataInputStream input, int max) throws IOException {
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        for(int i=0;i<max;i++) {
            int b=input.read();
            if(b<0)throw new IOException("Unexpected EOF");
            if(b=='\n') return bytes.toString("US-ASCII").trim();
            if(b=='\r')continue;
            bytes.write(b);
        }
        throw new IOException("HTTP request too large");
    }
    private static void fixedField(DataOutputStream stream,String text,int width) throws IOException {
        byte[] raw=text==null?new byte[0]:text.getBytes(StandardCharsets.UTF_8);
        byte[] fixed=new byte[width];
        System.arraycopy(raw,0,fixed,0,Math.min(width,raw.length));
        stream.writeInt(width);
        stream.write(fixed);
    }
    private byte[] authHeader(URI uri) throws IOException {
        String clientId="0";
        String query=uri.getRawQuery();
        if(query!=null)for(String item:query.split("&")) {
            String[] pair=item.split("=",2);
            if(pair.length==2 && "client_id".equals(pair[0]))clientId=pair[1];
        }
        int id;
        try { id=Integer.parseInt(clientId); }
        catch(NumberFormatException e) { throw new IOException("Invalid Blink stream client"); }
        String path=uri.getPath();
        if(path==null || path.isEmpty())throw new IOException("Blink stream missing connection path");
        String conn=path.substring(path.lastIndexOf('/')+1).split("__",2)[0];
        ByteArrayOutputStream memory=new ByteArrayOutputStream();
        DataOutputStream out=new DataOutputStream(memory);
        out.writeInt(40);
        fixedField(out, session.camera.serial, 16);
        out.writeInt(id);
        out.writeByte(0x01);
        out.writeByte(0x08);
        // Blink Live Events community implementation uses 64 blank bytes for default auth.
        fixedField(out,"",64);
        fixedField(out,conn,16);
        out.writeInt(1);
        out.flush();
        return memory.toByteArray();
    }
    @Override public void close() {
        if(!running.getAndSet(false))return;
        try { if(remote!=null)remote.close(); }catch(Exception ignored){}
        try { if(playerClient!=null)playerClient.close(); }catch(Exception ignored){}
        try { if(local!=null)local.close(); }catch(Exception ignored){}
        if(heartbeat!=null)heartbeat.interrupt();
    }
}
