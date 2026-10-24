package uk.co.lumsdon.ringading;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Short passive TV overlay. Requires an explicit Android "Display over other apps" permission.
 * Does not steal remote focus, intercept touch or open another app. Notification remains fallback.
 */
public final class DoorbellOverlay {
    private static final Handler UI = new Handler(Looper.getMainLooper());
    private static WindowManager windowManager;
    private static View current;
    private static Runnable dismissal;
    private DoorbellOverlay() {}

    public static boolean canShow(Context context) {
        return context.getSharedPreferences("visible_settings", Context.MODE_PRIVATE)
                .getBoolean("show_popup", true) && Settings.canDrawOverlays(context);
    }

    public static void show(Context context) {
        Context app = context.getApplicationContext();
        if (!canShow(app)) return;
        UI.post(() -> {
            if (!canShow(app)) return;
            dismiss();
            try {
                WindowManager wm = (WindowManager) app.getSystemService(Context.WINDOW_SERVICE);
                if (wm == null) return;
                int width = dp(app, 540);
                GradientDrawable bg = new GradientDrawable();
                bg.setColor(Color.rgb(25, 39, 56));
                bg.setCornerRadius(dp(app, 24));
                bg.setStroke(dp(app, 2), Color.rgb(117, 221, 179));

                LinearLayout frame = new LinearLayout(app);
                frame.setOrientation(LinearLayout.VERTICAL);
                frame.setPadding(dp(app, 28), dp(app, 18), dp(app, 28), dp(app, 20));
                frame.setBackground(bg);
                frame.setElevation(dp(app, 16));
                frame.setMinimumWidth(width);

                TextView appName = new TextView(app);
                appName.setText("RINGADING  •  DOORBELL");
                appName.setTextColor(Color.rgb(117, 221, 179));
                appName.setTextSize(15);
                appName.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
                frame.addView(appName);

                TextView headline = new TextView(app);
                headline.setText("Someone is at your door");
                headline.setTextColor(Color.WHITE);
                headline.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
                headline.setTextSize(27);
                LinearLayout.LayoutParams headlineParams = new LinearLayout.LayoutParams(-1, -2);
                headlineParams.topMargin = dp(app, 8);
                frame.addView(headline, headlineParams);

                TextView detail = new TextView(app);
                detail.setText("Your Blink doorbell has been pressed");
                detail.setTextColor(Color.rgb(198, 213, 224));
                detail.setTextSize(17);
                frame.addView(detail);

                WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                        WindowManager.LayoutParams.WRAP_CONTENT,
                        WindowManager.LayoutParams.WRAP_CONTENT,
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                                | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                        PixelFormat.TRANSLUCENT);
                params.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
                params.y = dp(app, 35);
                wm.addView(frame, params);
                windowManager = wm;
                current = frame;
                frame.setAlpha(0);
                frame.animate().alpha(1).setDuration(240).start();
                dismissal = DoorbellOverlay::dismiss;
                UI.postDelayed(dismissal, 9000);
            } catch (RuntimeException e) {
                // Some Android TV builds block overlays even after user permission.
                // Keep the standard high-priority notification as a fallback.
                Status.write(app, "TV pop-up unavailable (" + e.getClass().getSimpleName() + ")");
                dismiss();
            }
        });
    }

    private static void dismiss() {
        if (dismissal != null) UI.removeCallbacks(dismissal);
        dismissal = null;
        if (windowManager != null && current != null) {
            try { windowManager.removeViewImmediate(current); } catch (RuntimeException ignored) { }
        }
        windowManager = null;
        current = null;
    }

    private static int dp(Context ctx, int value) {
        return (int)(value * ctx.getResources().getDisplayMetrics().density + 0.5f);
    }
}
