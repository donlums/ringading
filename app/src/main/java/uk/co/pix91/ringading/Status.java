package uk.co.pix91.ringading;

import android.content.Context;
import android.content.Intent;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class Status {
    public static final String CHANGED = "uk.co.pix91.ringading.STATUS_CHANGED";
    private Status() { }
    public static void write(Context context, String status) {
        String time = new SimpleDateFormat("HH:mm:ss", Locale.UK).format(new Date());
        context.getSharedPreferences("visible_settings", Context.MODE_PRIVATE)
            .edit().putString("status", time + "  " + status).apply();
        Intent event = new Intent(CHANGED).setPackage(context.getPackageName());
        context.sendBroadcast(event);
    }
    public static String read(Context context) {
        return context.getSharedPreferences("visible_settings", Context.MODE_PRIVATE)
            .getString("status", "Ready to set up");
    }
}