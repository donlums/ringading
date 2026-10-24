package uk.co.lumsdon.ringading;

import android.content.Context;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;
import org.json.JSONObject;

/** Proof-of-concept Blink v60 OAuth/FCM association. Unofficial and may change. */
public final class BlinkCloud {
    private BlinkCloud() { }
    private static final String TOKEN_URL = "https://api.oauth.blink.com/oauth/token";
    private static final Pattern SIMPLE = Pattern.compile("[a-zA-Z0-9-]{1,45}");
    private static final String USER_AGENT = "Blink/60.0 (Ringading; Android TV)";

    private static class Response {
        final int code;
        final JSONObject obj;
        final String clientId;
        Response(int code, JSONObject obj, String clientId) {
            this.code = code; this.obj = obj; this.clientId = clientId;
        }
    }
    private static Response request(String method, String url, JSONObject body,
                             String form, String token, String clientId) throws Exception {
        URL u = new URL(url);
        if (!"https".equals(u.getProtocol())) throw new SecurityException("HTTPS required");
        HttpURLConnection con = (HttpURLConnection) u.openConnection();
        con.setRequestMethod(method);
        con.setConnectTimeout(15000);
        con.setReadTimeout(15000);
        con.setRequestProperty("Accept", "application/json");
        con.setRequestProperty("User-Agent", USER_AGENT);
        if (token != null) con.setRequestProperty("Authorization", "Bearer " + token);
        if (clientId != null) con.setRequestProperty("Client-Id", clientId);
        if (body != null || form != null) {
            con.setDoOutput(true);
            con.setRequestProperty("Content-Type", form == null ? "application/json" : "application/x-www-form-urlencoded");
            byte[] bytes = form != null ? form.getBytes(StandardCharsets.UTF_8)
                : body.toString().getBytes(StandardCharsets.UTF_8);
            try (var stream = con.getOutputStream()) { stream.write(bytes); }
        }
        try {
            int status = con.getResponseCode();
            if (status < 200 || status >= 300) throw new IllegalStateException("HTTP " + status + " from Blink");
            JSONObject json;
            try (InputStream stream = con.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int read, total = 0;
                while ((read = stream.read(buffer)) != -1) {
                    total += read;
                    if (total > 262144) throw new IllegalStateException("Unexpectedly large Blink response");
                    out.write(buffer, 0, read);
                }
                byte[] bytes = out.toByteArray();
                String text = new String(bytes, StandardCharsets.UTF_8).trim();
                json = text.isEmpty() ? new JSONObject() : new JSONObject(text);
            }
            return new Response(status, json, con.getHeaderField("Client-Id"));
        } finally { con.disconnect(); }
    }
    private static String escape(String value) {
        try { return URLEncoder.encode(value, "UTF-8"); }
        catch (java.io.UnsupportedEncodingException e) { throw new IllegalStateException(e); }
    }
    private static void saveTokens(SecureStore store, JSONObject obj) {
        String access = obj.optString("access_token", "");
        String refresh = obj.optString("refresh_token", "");
        if (access.isEmpty() || refresh.isEmpty()) throw new IllegalStateException("Incomplete OAuth tokens");
        store.put("access_token", access);
        store.put("refresh_token", refresh);
        long expiration = System.currentTimeMillis() + Math.max(60, obj.optLong("expires_in", 3600) - 60) * 1000L;
        store.put("expires_at", Long.toString(expiration));
    }
    static void exchange(Context context, String code, String verifier, String hardwareId) throws Exception {
        String form = "grant_type=authorization_code&redirect_uri=" + escape("https://applinks.blink.com/signin/callback")
            + "&code=" + escape(code) + "&code_verifier=" + escape(verifier) + "&client_id=android";
        Response r = request("POST", TOKEN_URL, null, form, null, null);
        SecureStore store = new SecureStore(context);
        saveTokens(store, r.obj);
        store.put("hardware_id", hardwareId);
        link(context);
    }
    private static String valid(String val, String name) {
        if (!SIMPLE.matcher(val).matches()) throw new IllegalArgumentException("Invalid " + name);
        return val;
    }
    static String access(Context context) throws Exception {
        SecureStore store = new SecureStore(context);
        String token = store.get("access_token");
        long exp;
        try { exp = Long.parseLong(store.get("expires_at")); }
        catch (NumberFormatException e) { exp = 0; }
        if (!token.isEmpty() && exp > System.currentTimeMillis()) return token;
        String refresh = store.get("refresh_token");
        if (refresh.isEmpty()) throw new IllegalStateException("Not signed in");
        String form = "grant_type=refresh_token&refresh_token=" + escape(refresh) + "&client_id=android&scope=client";
        Response r = request("POST", TOKEN_URL, null, form, null, null);
        saveTokens(store, r.obj);
        return store.get("access_token");
    }
    /** Link the independent TV's FCM token to the Blink client. Must run off UI thread. */
    static void link(Context context) throws Exception {
        SecureStore store = new SecureStore(context);
        String fcmToken = store.get("fcm_token");
        if (fcmToken.isEmpty()) throw new IllegalStateException("No TV FCM token yet. Check Google Play services.");
        String access = access(context);
        String tier = store.get("tier");
        String accountId = store.get("account_id");
        if (tier.isEmpty() || accountId.isEmpty()) {
            JSONObject data = request("GET", "https://rest-prod.immedia-semi.com/api/v1/users/tier_info",
                null, null, access, null).obj;
            tier = valid(data.optString("tier", ""), "tier");
            accountId = valid(data.optString("account_id", ""), "account ID");
            store.put("tier", tier); store.put("account_id", accountId);
        }
        tier = valid(tier, "tier");
        accountId = valid(accountId, "account ID");
        String root = "https://rest-" + tier + ".immedia-semi.com";
        String clientId = store.get("client_id");
        if (clientId.isEmpty()) {
            Response screen = request("GET", root + "/api/v3/accounts/" + accountId + "/homescreen",
                null, null, access, null);
            clientId = valid(screen.clientId == null ? "" : screen.clientId, "client ID");
            store.put("client_id", clientId);
        }
        JSONObject body = new JSONObject();
        body.put("app_version", "60.0 (29834864)");
        body.put("os_version", android.os.Build.VERSION.RELEASE);
        body.put("client_name", "Ringading TV");
        body.put("notification_key", fcmToken);
        request("POST", root + "/clients/" + valid(clientId, "client ID") + "/update",
            body, null, access, clientId);
        store.put("linked", "true");
        Status.write(context, "Blink association submitted; press the doorbell to verify push reception");
    }
    static void acknowledge(Context context, java.util.Map<String,String> payload) throws Exception {
        String flag = payload.getOrDefault("return_receipt", "").toLowerCase(java.util.Locale.ROOT);
        if (!flag.equals("1") && !flag.equals("yes") && !flag.equals("true")) return;
        SecureStore store = new SecureStore(context);
        String tier = valid(store.get("tier"), "tier");
        String accountId = store.get("account_id");
        String xid = payload.getOrDefault("unique_id", "");
        if (xid.isEmpty() || payload.getOrDefault("tier", "").isEmpty()) return;
        JSONObject body = new JSONObject();
        body.put("transaction_id", xid);
        boolean match = tier.equals(payload.get("tier")) && accountId.equals(payload.get("account_id"));
        body.put("status", match ? "RECEIVED" : "UNWANTED");
        request("POST", "https://rest-" + tier + ".immedia-semi.com/api/v2/notification",
            body, null, access(context), store.get("client_id"));
    }
}