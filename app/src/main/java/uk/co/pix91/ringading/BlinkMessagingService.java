package uk.co.pix91.ringading;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import android.app.Notification;
import java.util.Map;

/** Receives only pushes routed to this TV's own independent FCM token. */
public final class BlinkMessagingService extends FirebaseMessagingService {
    @Override public void onNewToken(String token) {
        super.onNewToken(token);
        ((ChimeApp)getApplication()).onToken(token);
    }
    @Override public void onMessageReceived(RemoteMessage message) {
        Map<String,String> data = BlinkEvent.flatten(message.getData());
        String eventType = data.getOrDefault("event_type", "unknown");
        if (BlinkEvent.isDoorbell(data) && "true".equals(new SecureStore(this).get("linked"))) {
            long now = System.currentTimeMillis();
            var settings = getSharedPreferences("visible_settings", MODE_PRIVATE);
            long last = settings.getLong("last_ring_ms", 0);
            // Avoid a double ding if FCM retries the same push within a few seconds.
            if (now - last > 5000L) {
                settings.edit().putLong("last_ring_ms", now).apply();
                Status.write(this, "Doorbell pressed — " + new java.text.SimpleDateFormat("HH:mm:ss",
                    java.util.Locale.UK).format(new java.util.Date(now)));
                BellPlayer.ring(this);
                DoorbellOverlay.show(this);
                notifyBell();
            }
        } else if (DoorbellHealthMonitor.isHealthPush(data)) {
            // Push is only a hint. Verify battery/offline status through Blink homescreen;
            // never infer an offline camera from a missing push or a failed HTTP request.
            ChimeApp.IO.execute(() -> {
                try { DoorbellHealthMonitor.check(this,false); }
                catch (Exception ignored) { }
            });
        } else {
            Status.write(this, "Blink push received (ignored: " + eventType.substring(0, Math.min(45, eventType.length())) + ")");
        }
        ChimeApp.IO.execute(() -> {
            try { BlinkCloud.acknowledge(this, data); }
            catch (Exception e) { Status.write(this, "Blink receipt not confirmed: " + e.getClass().getSimpleName()); }
        });
    }
    private void notifyBell() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        String channelId = "blink_press_silent_v1";
        NotificationChannel channel = new NotificationChannel(channelId, "Ringading doorbell alerts", NotificationManager.IMPORTANCE_HIGH);
        channel.setSound(null, null); // BellPlayer is the only sound source.
        nm.createNotificationChannel(channel);
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 1, open,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification note = new Notification.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Ringading · Someone is at your door")
            .setContentText("Blink doorbell button pressed")
            .setAutoCancel(true)
            .setContentIntent(pi).build();
        try { nm.notify(1002, note); } catch (SecurityException ignored) { }
    }
}