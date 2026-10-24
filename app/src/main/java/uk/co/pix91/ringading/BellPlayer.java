package uk.co.pix91.ringading;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * TV-volume media-stream chime. Repeated richer two-tone sequence.
 * Works from the Firebase background message service without opening an activity.
 */
public final class BellPlayer {
    private static final int SAMPLE_RATE = 22050;
    private static final AtomicInteger SEQUENCE = new AtomicInteger();
    private BellPlayer() {}

    public static void ring(Context context) {
        final Context app = context.getApplicationContext();
        final int seq = SEQUENCE.incrementAndGet();
        final int seconds = ChimeSettings.duration(app.getSharedPreferences("visible_settings", Context.MODE_PRIVATE)
                .getInt("ring_seconds", ChimeSettings.DEFAULT_DURATION));
        final double gain = ChimeSettings.volume(app.getSharedPreferences("visible_settings", Context.MODE_PRIVATE)
                .getInt("volume", ChimeSettings.DEFAULT_VOLUME)) / 100.0;
        Thread player = new Thread(() -> play(app, seq, seconds, gain), "ringading-longer-chime");
        player.setDaemon(true);
        player.start();
    }

    public static void stop() { SEQUENCE.incrementAndGet(); }

    private static void play(Context app, int seq, int seconds, double gain) {
        AudioManager manager = null;
        AudioFocusRequest focus = null;
        AudioTrack track = null;
        try {
            // Uses the TV's media volume; sound quality is louder than the original
            // low-amplitude notification stream but cannot exceed physical TV volume.
            AudioAttributes attributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build();
            manager = (AudioManager) app.getSystemService(Context.AUDIO_SERVICE);
            if (manager != null) {
                focus = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                        .setAudioAttributes(attributes)
                        .setOnAudioFocusChangeListener(value -> {}).build();
                manager.requestAudioFocus(focus);
            }
            AudioFormat format = new AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build();
            int minimum = AudioTrack.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT);
            int bufferBytes = Math.max(16384, minimum > 0 ? minimum : 0);
            track = new AudioTrack.Builder().setAudioAttributes(attributes).setAudioFormat(format)
                    .setTransferMode(AudioTrack.MODE_STREAM).setBufferSizeInBytes(bufferBytes).build();
            if (track.getState() != AudioTrack.STATE_INITIALIZED) throw new IllegalStateException("Audio output unavailable");
            track.play();
            int total = seconds * SAMPLE_RATE;
            short[] samples = new short[1024];
            for (int start = 0; start < total && SEQUENCE.get() == seq; start += samples.length) {
                int count = Math.min(samples.length, total - start);
                for (int i = 0; i < count; i++) {
                    samples[i] = sample((start + i) / (double) SAMPLE_RATE, gain);
                }
                int offset = 0;
                while (offset < count && SEQUENCE.get() == seq) {
                    int wrote = track.write(samples, offset, count - offset, AudioTrack.WRITE_BLOCKING);
                    if (wrote <= 0) throw new IllegalStateException("Audio write error " + wrote);
                    offset += wrote;
                }
            }
            // Allow the final 250 ms audio buffer to finish before releasing.
            if (SEQUENCE.get() == seq) {
                try { Thread.sleep(220); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }
        } catch (Exception e) {
            Status.write(app, "Chime failed: " + e.getClass().getSimpleName());
        } finally {
            if (track != null) {
                try { track.stop(); } catch (Exception ignored) { }
                track.release();
            }
            if (manager != null && focus != null) manager.abandonAudioFocusRequest(focus);
        }
    }

    private static short sample(double t, double gain) {
        if (gain <= 0) return 0;
        // A two-note pair every 2 seconds. Gentle attack, slower bell decay and
        // short pauses yield a clear ding-dong rather than a continuous harsh tone.
        double beat = t % 2.0;
        double noteTime;
        double frequency;
        if (beat < 0.72) {
            noteTime = beat;
            frequency = 659.25;
        } else if (beat >= 0.86 && beat < 1.58) {
            noteTime = beat - 0.86;
            frequency = 523.25;
        } else {
            return 0;
        }
        double attack = 1.0 - Math.exp(-noteTime * 130.0);
        double decay = Math.exp(-noteTime * 2.1);
        double tail = Math.min(1.0, Math.max(0.0, (0.72 - noteTime) / 0.18));
        double wave = Math.sin(2.0 * Math.PI * frequency * noteTime)
                + 0.24 * Math.sin(2.0 * Math.PI * frequency * 2 * noteTime)
                + 0.09 * Math.sin(2.0 * Math.PI * frequency * 3 * noteTime);
        // Soft-limit instead of hard clipping, with stronger apparent loudness.
        double normalized = Math.tanh(wave * attack * decay * tail * 1.7 * gain) * 0.94;
        return (short)(normalized * 32767.0);
    }
}
