package uk.co.lumsdon.ringading;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class ChimeSettingsTest {
    @Test public void defaultsAndDurations() {
        assertEquals(8, ChimeSettings.duration(0));
        assertEquals(4, ChimeSettings.duration(4));
        assertEquals(8, ChimeSettings.duration(8));
        assertEquals(12, ChimeSettings.duration(12));
        assertEquals(8, ChimeSettings.nextDuration(4));
        assertEquals(12, ChimeSettings.nextDuration(8));
        assertEquals(4, ChimeSettings.nextDuration(12));
    }
    @Test public void safeVolume() {
        assertEquals(0, ChimeSettings.volume(-20));
        assertEquals(65, ChimeSettings.volume(65));
        assertEquals(100, ChimeSettings.volume(180));
    }
}
