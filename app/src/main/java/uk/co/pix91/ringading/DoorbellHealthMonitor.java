package uk.co.pix91.ringading;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** One-time transition alerts, rather than repeated alerts on every health poll. */
final class DoorbellHealthMonitor {
    static final String PREFS = "doorbell_health_v1";
    static final long OFFLINE_CONFIRM_MS = 5L * 60_000L;
    private static final long MIN_POLL_MS = 5L * 60_000L;
    private static final String CHANNEL = "ringading_health_silent_v1";

    private DoorbellHealthMonitor(){}

    static boolean isHealthPush(Map<String,String> push) {
        String event=push.getOrDefault("event_type","").trim().toLowerCase(Locale.ROOT);
        switch(event){
            case "battery_low":case "low_battery":case "camera_battery_low":
            case "doorbell_battery_low":case "device_battery_low":
            case "doorbell_offline":case "camera_offline":case "device_offline":
            case "doorbell_disconnected":case "camera_disconnected":return true;
            default:return false;
        }
    }

    static void check(Context context,boolean force) throws Exception {
        Context app=context.getApplicationContext();
        SecureStore secured=new SecureStore(app);
        if(secured.get("refresh_token").isEmpty() || !"true".equals(secured.get("linked")))return;
        SharedPreferences prefs=app.getSharedPreferences(PREFS,Context.MODE_PRIVATE);
        long now=System.currentTimeMillis();
        if(!force && now-prefs.getLong("last_attempt_ms",0)<MIN_POLL_MS)return;
        // Rate limit applies even on network errors. Do NOT treat API failure as doorbell offline.
        prefs.edit().putLong("last_attempt_ms",now).apply();
        List<DoorbellHealth.Reading> readings=DoorbellHealth.fromHomescreen(
            BlinkCloud.liveHomescreen(app));
        process(app,readings,System.currentTimeMillis());
    }

    static synchronized void process(Context context,List<DoorbellHealth.Reading> readings,long now){
        SharedPreferences prefs=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE);
        SharedPreferences.Editor editor=prefs.edit();
        editor.putLong("last_success_ms",now);
        editor.putInt("device_count",readings.size());
        StringBuilder summary=new StringBuilder();
        for(DoorbellHealth.Reading r:readings) {
            String key=r.id;
            if(r.battery==DoorbellHealth.State.LOW) {
                if(!"low".equals(prefs.getString("battery_"+key,""))) {
                    alert(context,r,true);
                }
                editor.putString("battery_"+key,"low");
            } else if(r.battery==DoorbellHealth.State.OK) {
                editor.putString("battery_"+key,"ok");
                cancel(context,key,true);
            }

            if(r.connection==DoorbellHealth.State.OFFLINE){
                long first=prefs.getLong("offline_first_"+key,0L);
                if(first<=0 || first>now) {
                    editor.putLong("offline_first_"+key,now);
                } else if(now-first>=OFFLINE_CONFIRM_MS
                    && !"offline".equals(prefs.getString("connection_"+key,""))) {
                    alert(context,r,false);
                    editor.putString("connection_"+key,"offline");
                }
            } else if(r.connection==DoorbellHealth.State.ONLINE){
                editor.remove("offline_first_"+key);
                editor.putString("connection_"+key,"online");
                cancel(context,key,false);
            }
            if(summary.length()>0)summary.append("\n");
            summary.append(r.name).append("  ·  Battery: ")
                .append(r.battery==DoorbellHealth.State.LOW?"LOW":
                    r.battery==DoorbellHealth.State.OK?"OK":"not reported")
                .append("  ·  Connection: ")
                .append(r.connection==DoorbellHealth.State.OFFLINE?"reported offline":
                    r.connection==DoorbellHealth.State.ONLINE?"online":"not reported");
        }
        editor.putString("summary",summary.length()==0
            ?"No doorbell health details were reported by Blink":summary.toString());
        editor.apply();
    }

    private static int alertId(String key,boolean battery) {
        return 20000 + ((key + (battery?"battery":"connection")).hashCode() & 0x7fffffff)%40000;
    }
    private static void cancel(Context ctx,String key,boolean battery){
        NotificationManager manager=(NotificationManager)ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if(manager!=null)manager.cancel(alertId(key,battery));
    }

    private static void alert(Context ctx,DoorbellHealth.Reading reading,boolean battery) {
        SharedPreferences settings=ctx.getSharedPreferences("visible_settings",Context.MODE_PRIVATE);
        if(!settings.getBoolean(battery?"health_battery_alerts":"health_offline_alerts",true))return;

        String title=battery?"Doorbell battery low":"Doorbell disconnected";
        String detail=battery
            ?reading.name+" needs a battery check. Open the Blink app to confirm."
            :reading.name+" has been reported offline twice. Check power and Wi-Fi in the Blink app.";
        NotificationManager manager=(NotificationManager)ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if(manager!=null){
            NotificationChannel channel=new NotificationChannel(CHANNEL,
                "Ringading battery and connection alerts", NotificationManager.IMPORTANCE_HIGH);
            channel.setSound(null,null); // No ding-dong: health alerts are silent.
            manager.createNotificationChannel(channel);
            Intent open=new Intent(ctx,MainActivity.class);
            open.putExtra("open_blink_connection",true);
            open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent pending=PendingIntent.getActivity(ctx,105,open,
                PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
            Notification notice=new Notification.Builder(ctx,CHANNEL)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle("Ringading · "+title)
                .setContentText(detail)
                .setStyle(new Notification.BigTextStyle().bigText(detail))
                .setContentIntent(pending).setAutoCancel(true).build();
            try{manager.notify(alertId(reading.id,battery),notice);}
            catch(SecurityException ignored){}
        }
        HealthOverlay.show(ctx,title,detail);
        Status.write(ctx,title+" · "+reading.name);
    }
}
