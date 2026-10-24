package uk.co.lumsdon.ringading;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.net.Uri;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Fully D-pad navigable, no touchscreen or external companion required. */
public final class MainActivity extends Activity {
    private static final int BG = Color.rgb(16, 23, 37);
    private static final int PANEL = Color.rgb(27, 39, 57);
    private static final int ACCENT = Color.rgb(117, 221, 179);
    private static final int MUTED = Color.rgb(155, 170, 186);
    private TextView connection, status, volume, lastRing, duration, popupStatus;
    private Button connect, popupToggle;
    private final BroadcastReceiver changes = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) { refresh(); }
    };
    private static GradientDrawable background(int color, int stroke) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color); g.setCornerRadius(25);
        if (stroke != 0) g.setStroke(2, stroke);
        return g;
    }
    private static TextView label(Context context, String text, int sp, int color, boolean heavy) {
        TextView v = new TextView(context);
        v.setText(text); v.setTextSize(sp); v.setTextColor(color);
        if (heavy) v.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return v;
    }
    private static LinearLayout.LayoutParams lp(int w, int h, int marginBottom) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w,h);
        p.bottomMargin = marginBottom;
        return p;
    }
    private static void addText(LinearLayout parent, TextView value, int marginBottom) {
        parent.addView(value, lp(-1, -2, marginBottom));
    }
    private Button button(String text, boolean primary, Runnable action) {
        Button b = new Button(this);
        b.setText(text); b.setAllCaps(false); b.setTextSize(16); b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setTextColor(primary ? BG : Color.WHITE);
        b.setBackground(background(primary ? ACCENT : Color.rgb(51, 67, 89), 0));
        b.setPadding(18, 12, 18, 12); b.setMinHeight(68);
        b.setFocusable(true);
        b.setOnFocusChangeListener((v, focused) -> {
            b.setBackground(background(primary ? ACCENT : (focused ? Color.rgb(81, 107, 137) : Color.rgb(51, 67, 89)),
                focused ? Color.WHITE : 0));
            b.setScaleX(focused ? 1.03f : 1f); b.setScaleY(focused ? 1.03f : 1f);
        });
        b.setOnClickListener(v -> action.run());
        return b;
    }
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED) requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 20);
        FrameLayout stage = new FrameLayout(this);
        stage.setBackgroundColor(BG);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(55, 40, 55, 35);
        addText(content, label(this, "RINGADING  /  ANDROID TV", 14, ACCENT, true), 10);
        addText(content, label(this, "Ringading", 42, Color.WHITE, true), 4);
        addText(content, label(this, "Hear your Blink doorbell through the TV.", 23, Color.WHITE, false), 12);
        addText(content, label(this, "TV-only • No phone companion • No Home Assistant • Free to use", 16, MUTED, false), 25);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout left = panel();
        LinearLayout right = panel();
        LinearLayout.LayoutParams col1 = new LinearLayout.LayoutParams(0, -2, 1.15f);
        col1.rightMargin = 16;
        row.addView(left, col1);
        row.addView(right, new LinearLayout.LayoutParams(0, -2, 1));
        content.addView(row, lp(-1, -2, 20));
        addText(left, label(this, "DOORBELL", 13, ACCENT, true), 15);
        connection = label(this, "Not connected", 24, Color.WHITE, true);
        addText(left, connection, 14);
        addText(left, label(this, "Blink cloud integration is experimental. An actual button press is required to verify it.", 15, MUTED, false), 25);
        connect = button("Connect Blink account", true, () -> startActivity(new Intent(this, SignInActivity.class)));
        left.addView(connect, lp(-1, 68, 10));
        left.addView(button("Retry push registration", false, () -> {
            Status.write(this, "Checking Blink association…");
            ChimeApp.IO.execute(() -> {
                try { BlinkCloud.link(this); } catch (Exception e) { Status.write(this, "Link error: " + e.getMessage()); }
            });
        }), lp(-1, 65, 8));
        left.addView(button("Sign out and forget Blink credentials", false, () -> {
            new SecureStore(this).clearAccount();
            Status.write(this, "Signed out locally; previous Blink push association may remain server-side");
            refresh();
        }), lp(-1, 65, 0));

        addText(right, label(this, "CHIME SETTINGS", 13, ACCENT, true), 15);
        addText(right, label(this, "Classic ding-dong", 25, Color.WHITE, true), 12);
        addText(right, label(this, "Sound through the TV speakers", 15, MUTED, false), 15);
        right.addView(button("Play test chime", true, () -> {
            BellPlayer.ring(this);
            Status.write(this, "Manual test sound played (not a Blink event)");
        }), lp(-1, 68, 18));
        volume = label(this, "Volume: 100%", 18, Color.WHITE, true);
        addText(right, volume, 12);
        LinearLayout volRow = new LinearLayout(this);
        volRow.setOrientation(LinearLayout.HORIZONTAL);
        Button minus = button("− Quieter", false, () -> changeVolume(-10));
        Button plus = button("+ Louder", false, () -> changeVolume(10));
        LinearLayout.LayoutParams half = new LinearLayout.LayoutParams(0, 65, 1);
        half.rightMargin = 12;
        volRow.addView(minus, half);
        volRow.addView(plus, new LinearLayout.LayoutParams(0,65,1));
        right.addView(volRow, lp(-1, -2, 15));
        duration = label(this, "Ring duration: 8 seconds", 18, Color.WHITE, true);
        addText(right, duration, 9);
        right.addView(button("Change duration (4 / 8 / 12 sec)", false, () -> {
            var prefs = getSharedPreferences("visible_settings", MODE_PRIVATE);
            int old = prefs.getInt("ring_seconds", ChimeSettings.DEFAULT_DURATION);
            prefs.edit().putInt("ring_seconds", ChimeSettings.nextDuration(old)).apply();
            refresh();
        }), lp(-1, 65, 18));
        addText(right, label(this, "ON-SCREEN DOORBELL ALERT", 13, ACCENT, true), 8);
        popupStatus = label(this, "Pop-up requires permission", 15, MUTED, false);
        addText(right, popupStatus, 12);
        popupToggle = button("TV pop-up: ON", false, () -> {
            var prefs = getSharedPreferences("visible_settings", MODE_PRIVATE);
            prefs.edit().putBoolean("show_popup", !prefs.getBoolean("show_popup", true)).apply();
            refresh();
        });
        right.addView(popupToggle, lp(-1, 65, 9));
        right.addView(button("Allow display over other apps", false, this::requestOverlayPermission),
            lp(-1, 65, 9));
        right.addView(button("Preview: Someone is at your door", false, () -> {
            if (DoorbellOverlay.canShow(this)) {
                DoorbellOverlay.show(this);
            } else {
                new AlertDialog.Builder(this)
                    .setTitle("Ringading TV pop-up")
                    .setMessage("Switch TV pop-up ON, then allow Ringading Beta to display over other apps in Android TV settings. The regular notification is still available without this permission.")
                    .setPositiveButton("OK", null).show();
            }
        }), lp(-1, 65, 0));

        LinearLayout activity = panel();
        content.addView(activity, lp(-1, -2, 12));
        addText(activity, label(this, "LIVE STATUS", 13, ACCENT, true), 12);
        status = label(this, "Ready to set up", 18, Color.WHITE, false);
        addText(activity, status, 10);
        lastRing = label(this, "No Blink button presses detected yet", 15, MUTED, false);
        addText(activity, lastRing, 10);
        addText(activity, label(this,
            "TV-only beta: requires Google Play services, a functioning WebView, and compatible Blink cloud registration. "
            + "A TV notification may not appear over every streaming app.",
            14, MUTED, false), 0);
        LinearLayout footer = new LinearLayout(this);
        footer.setOrientation(LinearLayout.HORIZONTAL);
        footer.setGravity(Gravity.CENTER_VERTICAL);
        TextView credit = label(this, "Developed by Lumsdon", 15, MUTED, true);
        footer.addView(credit, new LinearLayout.LayoutParams(0, -2, 1));
        footer.addView(button("About Ringading", false, () -> new AlertDialog.Builder(this)
            .setTitle("Ringading")
            .setMessage("Developed by Lumsdon\n\nVersion 0.2.0 beta\n\nIndependent Android TV chime prototype for Blink doorbells. Not affiliated with or endorsed by Blink or Amazon. The Blink cloud connection must be tested on your TV before relying on it.")
            .setPositiveButton("Close", null).show()), new LinearLayout.LayoutParams(220, 65));
        content.addView(footer, lp(-1, -2, 0));
        scroll.addView(content);
        stage.addView(scroll);
        setContentView(stage);
        connect.requestFocus();
        refresh();
    }
    private LinearLayout panel() {
        LinearLayout v = new LinearLayout(this);
        v.setOrientation(LinearLayout.VERTICAL);
        v.setPadding(25, 22, 25, 25);
        v.setBackground(background(PANEL, Color.rgb(47, 64, 85)));
        return v;
    }
    private void requestOverlayPermission() {
        if (Settings.canDrawOverlays(this)) {
            new AlertDialog.Builder(this).setTitle("Already allowed")
                .setMessage("Ringading already has permission to show a TV pop-up above other apps. Select Preview to test it.")
                .setPositiveButton("OK", null).show();
            return;
        }
        try {
            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + getPackageName())));
        } catch (Exception first) {
            try {
                startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION));
            } catch (Exception second) {
                new AlertDialog.Builder(this).setTitle("Permission settings unavailable")
                    .setMessage("This Google TV version does not provide an in-app route to the display-over-other-apps permission. Check Settings > Apps > Special app access > Display over other apps, or use the standard Ringading notification.")
                    .setPositiveButton("OK", null).show();
            }
        }
    }

    private void changeVolume(int amount) {
        var prefs = getSharedPreferences("visible_settings", MODE_PRIVATE);
        int level = Math.min(100, Math.max(0, prefs.getInt("volume", ChimeSettings.DEFAULT_VOLUME) + amount));
        prefs.edit().putInt("volume", level).apply();
        refresh();
    }
    private void refresh() {
        if (connection == null) return;
        SecureStore store = new SecureStore(this);
        boolean signedIn = !store.get("refresh_token").isEmpty();
        connection.setText("true".equals(store.get("linked")) ? "Blink associated · not yet verified"
            : signedIn ? "Blink login saved — link pending" : "Not connected");
        connect.setText(signedIn ? "Reauthenticate Blink" : "Connect Blink account");
        status.setText(Status.read(this));
        int level = getSharedPreferences("visible_settings", MODE_PRIVATE).getInt("volume", ChimeSettings.DEFAULT_VOLUME);
        volume.setText("Chime volume: " + level + "% of TV media volume");
        if (duration != null) duration.setText("Ring duration: " + ChimeSettings.duration(getSharedPreferences("visible_settings", MODE_PRIVATE).getInt("ring_seconds", ChimeSettings.DEFAULT_DURATION)) + " seconds");
        if (popupToggle != null) popupToggle.setText(getSharedPreferences("visible_settings", MODE_PRIVATE).getBoolean("show_popup", true) ? "TV pop-up: ON" : "TV pop-up: OFF");
        if (popupStatus != null) popupStatus.setText(Settings.canDrawOverlays(this) ? "Permission granted · Pop-up appears for 9 seconds" : "Permission needed · Allow display over other apps");
        long timestamp = getSharedPreferences("visible_settings", MODE_PRIVATE).getLong("last_ring_ms", 0);
        lastRing.setText(timestamp == 0 ? "No Blink button presses detected yet"
            : "Last real press: " + new SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.UK).format(new Date(timestamp)));
    }
    @Override protected void onStart() {
        super.onStart();
        IntentFilter filter = new IntentFilter(Status.CHANGED);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(changes, filter, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(changes, filter);
        refresh();
    }
    @Override protected void onStop() {
        unregisterReceiver(changes);
        super.onStop();
    }
    @Override protected void onResume() { super.onResume(); refresh(); }
}
