package uk.co.pix91.ringading;

import java.util.Locale;
import java.util.Map;

/** Pure-Java classification of an actual button press; never ring on PIR motion. */
public final class EventClassifier {
    private EventClassifier() {}
    public static boolean isButtonPress(Map<String, String> data) {
        return "media".equals(normalize(data.get("category")))
            && "button_press".equals(normalize(data.get("event_type")));
    }
    private static String normalize(String s) {
        return s == null ? "" : s.trim().toLowerCase(Locale.ROOT);
    }
}