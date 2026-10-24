package uk.co.lumsdon.ringading;

/** Pure Java settings validated for use from the Android TV interface and audio thread. */
public final class ChimeSettings {
    public static final int DEFAULT_DURATION = 8;
    public static final int DEFAULT_VOLUME = 100;
    private ChimeSettings() { }
    public static int duration(int value) {
        return value == 4 || value == 8 || value == 12 ? value : DEFAULT_DURATION;
    }
    public static int nextDuration(int current) {
        return duration(current) == 4 ? 8 : duration(current) == 8 ? 12 : 4;
    }
    public static int volume(int value) { return Math.min(100, Math.max(0, value)); }
}
