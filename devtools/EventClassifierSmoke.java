import java.util.Map;
import uk.co.pix91.ringading.EventClassifier;

/** Run this with JDK 17, no Android SDK required. */
public final class EventClassifierSmoke {
    public static void main(String[] args) {
        if (!EventClassifier.isButtonPress(Map.of("category", "media", "event_type", "button_press")))
            throw new AssertionError("button_press missed");
        for (String motion : new String[]{"pir", "cam2sm_motion", "cloud_motion", "cv_motion"})
            if (EventClassifier.isButtonPress(Map.of("category", "media", "event_type", motion)))
                throw new AssertionError("motion misclassified: " + motion);
        if (EventClassifier.isButtonPress(Map.of("category", "motion", "event_type", "button_press")))
            throw new AssertionError("category not checked");
        System.out.println("PASS: Blink button press and motion classification smoke tests");
    }
}
