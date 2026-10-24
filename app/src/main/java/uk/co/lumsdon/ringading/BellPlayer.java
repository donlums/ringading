package uk.co.lumsdon.ringading;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;

/** Synthesize a classic two-note doorbell chime; no binary WAV required. */
public final class BellPlayer {
    private static final int RATE = 22050;
    private static volatile AudioTrack playing;
    private BellPlayer() {}

    public static synchronized void ring(Context context) {
        final Context app = context.getApplicationContext();
        final float volume = Math.max(0, Math.min(1,
            app.getSharedPreferences("visible_settings", Context.MODE_PRIVATE).getInt("volume", 80) / 100f));
        new Thread(() -> {
            AudioTrack track = null;
            AudioManager manager = null;
            AudioFocusRequest focus = null;
            try {
                AudioAttributes attrs = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build();
                manager = (AudioManager) app.getSystemService(Context.AUDIO_SERVICE);
                if (manager != null) {
                    focus = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                        .setAudioAttributes(attrs).setOnAudioFocusChangeListener(change -> {}).build();
                    manager.requestAudioFocus(focus);
                }
                int samples = RATE * 9 / 10;
                short[] sound = new short[samples];
                for (int i = 0; i < sound.length; i++) {
                    double t = i / (double) RATE;
                    double noteTime = t < 0.44 ? t : t - 0.44;
                    double hz = t < 0.44 ? 659.25 : 523.25;
                    double envelope = (1 - Math.exp(-noteTime * 80)) * Math.exp(-noteTime * 7);
                    double tone = Math.sin(2 * Math.PI * hz * noteTime)
                        + 0.22 * Math.sin(2 * Math.PI * hz * 2 * noteTime);
                    sound[i] = (short) (Math.max(-1, Math.min(1, envelope * tone * volume * 0.7)) * 32767);
                }
                track = new AudioTrack.Builder().setAudioAttributes(attrs)
                    .setAudioFormat(new AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(RATE).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                    .setTransferMode(AudioTrack.MODE_STATIC).setBufferSizeInBytes(sound.length * 2).build();
                playing = track;
                track.write(sound, 0, sound.length);
                track.play();
                try { Thread.sleep(1000); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
            } catch (Exception e) {
                Status.write(app, "Chime playback failed: " + e.getClass().getSimpleName());
            } finally {
                if (track != null) {
                    try { track.stop(); } catch (Exception ignored) { }
                    track.release();
                    if (track == playing) playing = null;
                }
                if (manager != null && focus != null) manager.abandonAudioFocusRequest(focus);
            }
        }, "ringading-chime").start();
    }
}
