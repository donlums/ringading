package uk.co.pix91.ringading;

import android.content.Context;
import java.net.URI;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

/** Discover owned Blink doorbells and request a one-off live view session. */
final class LiveViewApi {
    private LiveViewApi() {}

    static final class Camera {
        final String id, networkId, serial, name;
        Camera(String id, String networkId, String serial, String name) {
            this.id=id; this.networkId=networkId; this.serial=serial; this.name=name;
        }
    }

    static final class Session {
        final Camera camera;
        final String server;
        final String token;
        final long commandId;
        Session(Camera camera, String server, String token, long commandId) {
            this.camera=camera; this.server=server; this.token=token; this.commandId=commandId;
        }
    }

    private static String string(JSONObject object, String... keys) {
        for (String key: keys) {
            String value=object.optString(key, "").trim();
            if (!value.isEmpty() && !"null".equalsIgnoreCase(value)) return value;
        }
        return "";
    }
    private static String numberId(String value) {
        if (!value.matches("[0-9]{1,18}")) throw new IllegalArgumentException("Invalid Blink camera/network ID");
        return value;
    }

    static List<Camera> discover(Context context) throws Exception {
        JSONObject root=BlinkCloud.liveHomescreen(context);
        JSONArray list=root.optJSONArray("doorbells");
        ArrayList<Camera> found=new ArrayList<>();
        if (list == null) return found;
        for (int i=0; i<list.length(); i++) {
            JSONObject d=list.optJSONObject(i);
            if (d == null) continue;
            String id=string(d, "id", "doorbell_id", "device_id");
            String network=string(d, "network_id");
            if (!id.matches("[0-9]{1,18}") || !network.matches("[0-9]{1,18}")) continue;
            String name=string(d, "name");
            String serial=string(d, "serial", "serial_number", "serial_no");
            found.add(new Camera(id, network, serial, name.isEmpty()?"Blink doorbell":name));
        }
        return found;
    }

    static Session start(Context context, Camera camera) throws Exception {
        String account=numberId(new SecureStore(context).get("account_id"));
        String url=BlinkCloud.restRoot(context)
            + "/api/v2/accounts/" + account
            + "/networks/" + numberId(camera.networkId)
            + "/doorbells/" + numberId(camera.id) + "/liveview";
        JSONObject data=new JSONObject();
        data.put("intent", "liveview");
        data.put("motion_event_start_time", JSONObject.NULL);
        JSONObject result=BlinkCloud.liveRequest(context, "POST", url, data);
        String server=result.optString("server", "");
        if (server.isEmpty()) throw new IllegalStateException("Blink did not provide a live stream (camera asleep, busy or Event Response mode)");
        URI target=URI.create(server);
        if (!"immis".equalsIgnoreCase(target.getScheme())) {
            throw new UnsupportedOperationException("Blink selected " + target.getScheme() + " transport; this preview supports IMMIS only");
        }
        if (target.getHost() == null || target.getPort() < 1)
            throw new IllegalStateException("Invalid Blink live-view endpoint");
        return new Session(camera, server, result.optString("liveview_token", ""), result.optLong("command_id", 0L));
    }
}
