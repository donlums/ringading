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
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Ringading: remote-first TV dashboard with separated Home, Chime, Blink and About screens. */
public final class MainActivity extends Activity {
    private static final int BG = 0xff0b121e;
    private static final int PANEL = 0xff172336;
    private static final int PANEL_LIGHT = 0xff22334b;
    private static final int LINE = 0xff36495e;
    private static final int AMBER = 0xffffc777;
    private static final int MINT = 0xffa4ebbf;
    private static final int WHITE = 0xfff5f7fb;
    private static final int MUTED = 0xffa9b8c9;
    private int page = 0; // Home, Chime & alerts, Blink, About
    private LinearLayout host;
    private TextView eventText, headerConnection;
    private final BroadcastReceiver changes = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (eventText != null) eventText.setText(Status.read(context));
            if (headerConnection != null) headerConnection.setText(connectionLabel());
        }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if (state != null) page = state.getInt("page", 0);
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 101);
        render();
    }
    @Override public void onSaveInstanceState(Bundle state) {
        state.putInt("page", page);
        super.onSaveInstanceState(state);
    }
    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
    private LinearLayout vertical() {
        LinearLayout view = new LinearLayout(this);
        view.setOrientation(LinearLayout.VERTICAL);
        return view;
    }
    private LinearLayout horizontal() {
        LinearLayout view = new LinearLayout(this);
        view.setOrientation(LinearLayout.HORIZONTAL);
        view.setGravity(Gravity.CENTER_VERTICAL);
        return view;
    }
    private static GradientDrawable bg(int color, int border, float radius) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(radius);
        if (border != 0) shape.setStroke(2, border);
        return shape;
    }
    private LinearLayout.LayoutParams margins(int width, int height, int bottom) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(width, height);
        p.bottomMargin = dp(bottom);
        return p;
    }
    private TextView text(String value, float sp, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextColor(color);
        view.setTextSize(sp);
        view.setGravity(Gravity.CENTER_VERTICAL);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }
    private void addText(LinearLayout parent, String value, int size, int color, boolean bold, int bottom) {
        parent.addView(text(value, size, color, bold), margins(-1, -2, bottom));
    }
    private LinearLayout card() {
        LinearLayout panel = vertical();
        panel.setPadding(dp(22), dp(18), dp(22), dp(20));
        panel.setBackground(bg(PANEL, LINE, dp(17)));
        return panel;
    }
    private Button button(String title, boolean primary, Runnable click) {
        Button b = new Button(this);
        b.setAllCaps(false);
        b.setText(title);
        b.setTextSize(15);
        b.setSingleLine(true);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setTextColor(primary ? BG : WHITE);
        int base = primary ? AMBER : PANEL_LIGHT;
        b.setBackground(bg(base, primary ? 0 : LINE, dp(12)));
        b.setMinHeight(dp(52));
        b.setPadding(dp(12), dp(5), dp(12), dp(5));
        b.setFocusable(true);
        b.setOnFocusChangeListener((v, focus) -> {
            b.setBackground(bg(focus ? (primary ? 0xffffd89d : 0xff2c4661) : base,
                    focus ? WHITE : (primary ? 0 : LINE), dp(12)));
            b.animate().scaleX(focus ? 1.035f : 1f).scaleY(focus ? 1.035f : 1f).setDuration(120).start();
        });
        b.setOnClickListener(v -> click.run());
        return b;
    }
    private void go(int destination) {
        page = destination;
        render();
    }
    private void action(LinearLayout box, String title, boolean primary, Runnable handler, int bottom) {
        box.addView(button(title, primary, handler), margins(-1, dp(54), bottom));
    }
    private String connectionLabel() {
        SecureStore store = new SecureStore(this);
        if ("true".equals(store.get("linked"))) {
            return lastRingMs() > 0 ? "CONNECTED · PRESS VERIFIED" : "LINKED · AWAITING FIRST PRESS";
        }
        return store.get("refresh_token").isEmpty() ? "NOT CONNECTED" : "SIGN-IN SAVED · RECONNECT";
    }
    private boolean connected() { return "true".equals(new SecureStore(this).get("linked")); }
    private long lastRingMs() {
        return getSharedPreferences("visible_settings", MODE_PRIVATE).getLong("last_ring_ms", 0L);
    }
    private String lastRingLabel() {
        long at = lastRingMs();
        return at == 0 ? "No doorbell presses recorded yet" :
                "Last ring · " + new SimpleDateFormat("EEE d MMM, HH:mm", Locale.UK).format(new Date(at));
    }
    private void render() {
        LinearLayout root = vertical();
        root.setBackgroundColor(BG);

        // TV-safe horizontal and vertical margins, avoids clipping at the screen edges.
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout body = vertical();
        body.setPadding(dp(40), dp(27), dp(40), dp(20));
        scroll.addView(body);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
        host = body;

        LinearLayout brand = horizontal();
        ImageView bell = new ImageView(this);
        bell.setImageResource(R.drawable.ic_ringading);
        bell.setContentDescription("Ringading bell logo");
        brand.addView(bell, new LinearLayout.LayoutParams(dp(46), dp(46)));
        LinearLayout titles = vertical();
        titles.setPadding(dp(12), 0, 0, 0);
        addText(titles, "Ringading", 26, WHITE, true, 0);
        addText(titles, "YOUR TV DOORBELL", 11, MUTED, true, 0);
        brand.addView(titles, new LinearLayout.LayoutParams(0, -2, 1));
        headerConnection = text(connectionLabel(), 11, connected() ? MINT : AMBER, true);
        headerConnection.setPadding(dp(12), dp(9), dp(12), dp(9));
        headerConnection.setBackground(bg(PANEL_LIGHT, LINE, dp(30)));
        brand.addView(headerConnection);
        host.addView(brand, margins(-1, dp(56), 16));

        LinearLayout menu = horizontal();
        final String[] labels = {"Home", "Chime & alerts", "Blink connection", "About"};
        for (int i = 0; i < labels.length; i++) {
            final int selected = i;
            Button nav = button(labels[i], i == page, () -> go(selected));
            LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(0, dp(48), 1);
            if (i < labels.length - 1) np.rightMargin = dp(11);
            menu.addView(nav, np);
        }
        host.addView(menu, margins(-1, -2, 22));

        if (page == 0) home();
        else if (page == 1) soundSettings();
        else if (page == 2) account();
        else about();

        LinearLayout foot = horizontal();
        TextView credit = text("Developed by Lumsdon", 12, MUTED, true);
        foot.addView(credit, new LinearLayout.LayoutParams(0, -2, 1));
        foot.addView(text("RINGADING  ·  v0.4  ·  ANDROID TV", 11, MUTED, false));
        host.addView(foot, margins(-1, dp(34), 0));
    }
    private void home() {
        LinearLayout hero = card();
        hero.setBackground(bg(0xff1c2a3d, AMBER, dp(18)));
        LinearLayout row = horizontal();
        LinearLayout copy = vertical();
        addText(copy, "Every ring, heard.", 29, WHITE, true, 8);
        addText(copy, "Your Blink doorbell, through your TV speakers.", 16, MUTED, false, 15);
        addText(copy, lastRingLabel(), 15, MINT, true, 0);
        row.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.ic_ringading);
        icon.setContentDescription("Doorbell");
        row.addView(icon, new LinearLayout.LayoutParams(dp(92), dp(92)));
        hero.addView(row, margins(-1, -2, 16));
        LinearLayout actions = horizontal();
        Button ring = button("▶  Play test chime", true, () -> {
            BellPlayer.ring(this);
            Status.write(this, "Manual chime test");
        });
        LinearLayout.LayoutParams half = new LinearLayout.LayoutParams(0, dp(55), 1);
        half.rightMargin = dp(13);
        actions.addView(ring, half);
        actions.addView(button("Watch live camera", false, () -> startActivity(new Intent(this, LiveViewActivity.class))),
                new LinearLayout.LayoutParams(0, dp(55), 1));
        LinearLayout.LayoutParams previewParams=new LinearLayout.LayoutParams(0, dp(55), 1);
        previewParams.leftMargin=dp(13);
        actions.addView(button("Preview door alert", false, this::previewAlert), previewParams);
        hero.addView(actions);
        host.addView(hero, margins(-1, -2, 16));

        LinearLayout cols = horizontal();
        LinearLayout connection = card();
        addText(connection, "DOORBELL CONNECTION", 12, AMBER, true, 11);
        addText(connection, connected() ? "Blink is connected" : "Connect your Blink", 21, WHITE, true, 8);
        addText(connection, connected() ? "Doorbell button presses can trigger your chime."
                : "Sign in to Blink to enable real doorbell alerts.", 13, MUTED, false, 14);
        action(connection, connected() ? "Connection details" : "Connect Blink", false, () -> go(2), 0);
        LinearLayout.LayoutParams left = new LinearLayout.LayoutParams(0, -2, 1);
        left.rightMargin = dp(14);
        cols.addView(connection, left);

        LinearLayout sound = card();
        addText(sound, "CHIME & ALERTS", 12, AMBER, true, 11);
        int seconds = getSharedPreferences("visible_settings", MODE_PRIVATE)
                .getInt("ring_seconds", ChimeSettings.DEFAULT_DURATION);
        addText(sound, ChimeSettings.duration(seconds) + " seconds · Classic ding-dong", 21, WHITE, true, 8);
        addText(sound, "A louder chime with an optional on-screen visitor alert.", 13, MUTED, false, 14);
        action(sound, "Adjust sound & alerts", false, () -> go(1), 0);
        cols.addView(sound, new LinearLayout.LayoutParams(0, -2, 1));
        host.addView(cols, margins(-1, -2, 15));

        LinearLayout latest = card();
        addText(latest, "LATEST ACTIVITY", 12, AMBER, true, 7);
        eventText = text(Status.read(this), 14, WHITE, false);
        latest.addView(eventText);
        host.addView(latest, margins(-1, -2, 14));
    }
    private void soundSettings() {
        addText(host, "Chime & alerts", 27, WHITE, true, 5);
        addText(host, "Make your doorbell impossible to miss — without harsh distortion.", 14, MUTED, false, 18);

        LinearLayout row = horizontal();
        LinearLayout audio = card();
        addText(audio, "CHIME VOLUME", 12, AMBER, true, 12);
        int v = ChimeSettings.volume(getSharedPreferences("visible_settings", MODE_PRIVATE)
                .getInt("volume", ChimeSettings.DEFAULT_VOLUME));
        addText(audio, v + "% of TV media volume", 21, WHITE, true, 11);
        LinearLayout adjust = horizontal();
        Button down = button("− 10%", false, () -> changeVolume(-10));
        LinearLayout.LayoutParams half = new LinearLayout.LayoutParams(0, dp(50), 1);
        half.rightMargin = dp(10);
        adjust.addView(down, half);
        adjust.addView(button("+ 10%", false, () -> changeVolume(10)),
                new LinearLayout.LayoutParams(0, dp(50), 1));
        audio.addView(adjust, margins(-1, -2, 18));
        addText(audio, "RING DURATION", 12, AMBER, true, 11);
        int current = ChimeSettings.duration(getSharedPreferences("visible_settings", MODE_PRIVATE)
                .getInt("ring_seconds", ChimeSettings.DEFAULT_DURATION));
        LinearLayout durations = horizontal();
        for (int sec : new int[]{4, 8, 12}) {
            Button choice = button(sec + " seconds", current == sec, () -> {
                getSharedPreferences("visible_settings", MODE_PRIVATE).edit().putInt("ring_seconds", sec).apply();
                render();
            });
            LinearLayout.LayoutParams cell = new LinearLayout.LayoutParams(0, dp(54), 1);
            cell.rightMargin = dp(8);
            durations.addView(choice, cell);
        }
        audio.addView(durations, margins(-1, -2, 15));
        action(audio, "▶  Play chime", true, () -> BellPlayer.ring(this), 8);
        action(audio, "■  Stop chime", false, BellPlayer::stop, 0);
        LinearLayout.LayoutParams left = new LinearLayout.LayoutParams(0, -2, 1);
        left.rightMargin = dp(15);
        row.addView(audio, left);

        LinearLayout pop = card();
        addText(pop, "ON-SCREEN NOTIFICATION", 12, AMBER, true, 13);
        addText(pop, "Someone is at your door", 21, WHITE, true, 8);
        addText(pop, "Show a small banner above the TV picture for 9 seconds when the bell rings.", 13, MUTED, false, 12);
        boolean enabled = getSharedPreferences("visible_settings", MODE_PRIVATE)
                .getBoolean("show_popup", true);
        addText(pop, enabled ? "Door alert: ON" : "Door alert: OFF", 16,
                enabled ? MINT : MUTED, true, 11);
        action(pop, enabled ? "Disable pop-up" : "Enable pop-up", false, () -> {
            getSharedPreferences("visible_settings", MODE_PRIVATE).edit()
                    .putBoolean("show_popup", !enabled).apply();
            render();
        }, 10);
        if (!Settings.canDrawOverlays(this)) {
            addText(pop, "One-time TV permission needed", 13, AMBER, true, 7);
            action(pop, "Allow pop-up permission", false, this::requestOverlayPermission, 10);
        } else {
            addText(pop, "Pop-up permission granted", 13, MINT, true, 12);
        }
        action(pop, "Preview on-screen alert", true, this::previewAlert, 0);
        row.addView(pop, new LinearLayout.LayoutParams(0, -2, 1));
        host.addView(row, margins(-1, -2, 15));
        addText(host, "The TV's physical volume still controls the maximum loudness. "
                + "Some streaming apps or TV firmware may restrict overlays.", 12, MUTED, false, 20);
    }
    private void account() {
        addText(host, "Blink connection", 27, WHITE, true, 6);
        addText(host, "Manage the doorbell linked to this TV.", 14, MUTED, false, 18);
        LinearLayout panel = card();
        addText(panel, "CONNECTION STATUS", 12, AMBER, true, 11);
        addText(panel, connectionLabel(), 22, connected() ? MINT : WHITE, true, 8);
        addText(panel, lastRingLabel(), 14, MUTED, false, 18);
        SecureStore store = new SecureStore(this);
        if (store.get("refresh_token").isEmpty())
            action(panel, "Connect Blink account", true,
                    () -> startActivity(new Intent(this, SignInActivity.class)), 9);
        else
            action(panel, "Reauthenticate Blink account", false,
                    () -> startActivity(new Intent(this, SignInActivity.class)), 9);
        action(panel, "Reconnect doorbell notifications", false, () -> {
            Status.write(this, "Checking Blink association…");
            ChimeApp.IO.execute(() -> {
                try { BlinkCloud.link(this); }
                catch (Exception e) { Status.write(this, "Link error: " + e.getMessage()); }
            });
        }, 10);
        if (!store.get("refresh_token").isEmpty()) {
            action(panel, "Disconnect Blink…", false, () -> new AlertDialog.Builder(this)
                    .setTitle("Disconnect Blink?")
                    .setMessage("Your saved Blink connection will be cleared on this TV. You will need to sign in again.")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Disconnect", (dialog, which) -> {
                        new SecureStore(this).clearAccount();
                        Status.write(this, "Disconnected locally");
                        render();
                    }).show(), 0);
        }
        host.addView(panel, margins(-1, -2, 16));
        LinearLayout recent = card();
        addText(recent, "CONNECTION ACTIVITY", 12, AMBER, true, 10);
        eventText = text(Status.read(this), 14, WHITE, false);
        recent.addView(eventText);
        host.addView(recent, margins(-1, -2, 14));
    }
    private void about() {
        LinearLayout panel = card();
        addText(panel, "Ringading", 30, WHITE, true, 9);
        addText(panel, "Developed by Lumsdon", 18, MINT, true, 18);
        addText(panel, "Version 0.4 live-view preview · Android TV", 15, WHITE, false, 13);
        addText(panel, "A phone-free chime for your Blink video doorbell. "
                + "It rings when Blink sends a doorbell button-press event to your TV. "
                + "Not affiliated with Blink, Amazon or Google.", 15, MUTED, false, 15);
        addText(panel, "Blink cloud notifications are unofficial and could change. "
                + "Do not rely on Ringading as your only doorbell alert until tested on your device.", 13, MUTED, false, 0);
        host.addView(panel, margins(-1, -2, 15));
    }
    private void changeVolume(int delta) {
        var prefs = getSharedPreferences("visible_settings", MODE_PRIVATE);
        int next = ChimeSettings.volume(prefs.getInt("volume", ChimeSettings.DEFAULT_VOLUME) + delta);
        prefs.edit().putInt("volume", next).apply();
        render();
    }
    private void previewAlert() {
        if (DoorbellOverlay.canShow(this)) {
            DoorbellOverlay.show(this);
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Ringading visitor alert")
                .setMessage("Someone is at your door\n\nTo show this over TV apps, enable the pop-up "
                        + "and allow Ringading to display over other apps in Android TV settings.")
                .setPositiveButton("OK", null).show();
    }
    private void requestOverlayPermission() {
        if (Settings.canDrawOverlays(this)) { previewAlert(); return; }
        try {
            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName())));
        } catch (Exception first) {
            try { startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)); }
            catch (Exception second) {
                new AlertDialog.Builder(this).setTitle("Permission unavailable")
                        .setMessage("Your TV doesn't expose this setting directly. "
                                + "Try Settings > Apps > Special app access > Display over other apps. "
                                + "Ordinary notifications can still appear.")
                        .setPositiveButton("OK", null).show();
            }
        }
    }
    @Override protected void onStart() {
        super.onStart();
        IntentFilter filter = new IntentFilter(Status.CHANGED);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(changes, filter, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(changes, filter);
    }
    @Override protected void onResume() { super.onResume(); if (host != null) render(); }
    @Override protected void onStop() { unregisterReceiver(changes); super.onStop(); }
}
