package uk.co.lumsdon.ringading;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.json.JSONObject;

/** Strictly separate button pushes from PIR/motion events. */
public final class BlinkEvent {
    private BlinkEvent() { }
    public static boolean isDoorbell(Map<String, String> input) {
        Map<String, String> data = flatten(input);
        return EventClassifier.isButtonPress(data);
    }
    public static Map<String, String> flatten(Map<String, String> input) {
        Map<String, String> result = new HashMap<>();
        // Blink messages may wrap the fields in JSON; explicit top-level values win.
        for (String name : new String[]{"data", "payload", "message", "notification"}) {
            String json = input.get(name);
            if (json == null) continue;
            try {
                JSONObject obj = new JSONObject(json);
                for (java.util.Iterator<String> keys = obj.keys(); keys.hasNext();) {
                    String key = keys.next();
                    result.put(key, obj.optString(key, ""));
                }
            } catch (Exception ignored) { }
        }
        result.putAll(input);
        return result;
    }
    private static String norm(String s) {
        return s == null ? "" : s.trim().toLowerCase(Locale.ROOT);
    }
}