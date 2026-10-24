package uk.co.lumsdon.ringading;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;

/** Short doorbell sound; does not need the app's UI to be visible. */
public final class BellPlayer {
    private static MediaPlayer current;
    private static AudioFocusRequest focus;
    private BellPlayer() { }

    public static synchronized void ring(Context context) {
        Context app = context.getApplicationContext();
        if (current != null) { try { current.release(); } catch (Exception ignored) { } current = null; }
        AudioManager manager = (AudioManager) app.getSystemService(Context.AUDIO_SERVICE);
        AudioAttributes attributes = new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build();
        try {
            if (manager != null) {
                focus = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                    .setAudioAttributes(attributes)
                    .setOnAudioFocusChangeListener(state -> { }).build();
                manager.requestAudioFocus(focus);
            }
            MediaPlayer player = MediaPlayer.create(app, R.raw.dingdong);
            if (player == null) throw new IllegalStateException("Could not open chime audio");
            current = player;
            float level = app.getSharedPreferences("visible_settings", 0).getInt("volume", 80) / 100f;
            player.setVolume(level, level);
            player.setOnCompletionListener(mp -> release(manager, mp));
            player.setOnErrorListener((mp, what, extra) -> { release(manager, mp); return true; });
            player.start();
        } catch (Exception e) { Status.write(app, "Chime playback failed: " + e.getClass().getSimpleName()); }
    }
    private static synchronized void release(AudioManager manager, MediaPlayer p) {
        try { p.release(); } catch (Exception ignored) { }
        if (p == current) current = null;
        if (manager != null && focus != null) manager.abandonAudioFocusRequest(focus);
        focus = null;
    }
}