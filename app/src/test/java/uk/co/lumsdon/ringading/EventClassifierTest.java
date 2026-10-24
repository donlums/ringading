package uk.co.lumsdon.ringading;

import static org.junit.Assert.*;
import org.junit.Test;
import java.util.Map;

public class EventClassifierTest {
    @Test public void ringOnlyOnActualBlinkDoorbellButton() {
        assertTrue(EventClassifier.isButtonPress(Map.of("category","media", "event_type","button_press")));
        assertTrue(EventClassifier.isButtonPress(Map.of("category"," MEDIA ", "event_type"," BUTTON_PRESS ")));
        assertFalse(EventClassifier.isButtonPress(Map.of("category","media", "event_type","pir")));
        assertFalse(EventClassifier.isButtonPress(Map.of("category","motion", "event_type","cam2sm_motion")));
        assertFalse(EventClassifier.isButtonPress(Map.of("category","motion", "event_type","button_press")));
        assertFalse(EventClassifier.isButtonPress(Map.of("category","media", "event_type","")));
        assertFalse(EventClassifier.isButtonPress(Map.of("category","media")));
    }
}