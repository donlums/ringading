package uk.co.pix91.ringading;

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

/** Passive, silent status banner; never plays the visitor chime. */
final class HealthOverlay {
    private static final Handler UI=new Handler(Looper.getMainLooper());
    private static WindowManager windows;
    private static View current;
    private static Runnable hide;
    private HealthOverlay(){}

    static void show(Context ctx,String title,String detail) {
        Context app=ctx.getApplicationContext();
        if(!app.getSharedPreferences("visible_settings",Context.MODE_PRIVATE)
            .getBoolean("health_tv_popups",true) || !Settings.canDrawOverlays(app))return;
        UI.post(()->{
            dismiss();
            try {
                WindowManager wm=(WindowManager)app.getSystemService(Context.WINDOW_SERVICE);
                if(wm==null)return;
                LinearLayout card=new LinearLayout(app);
                card.setOrientation(LinearLayout.VERTICAL);
                int d=(int)app.getResources().getDisplayMetrics().density;
                card.setPadding(24*d,17*d,24*d,19*d);
                GradientDrawable bg=new GradientDrawable();
                bg.setColor(Color.rgb(30,42,60));
                bg.setCornerRadius(18*d);
                bg.setStroke(2*d,Color.rgb(255,199,119));
                card.setBackground(bg);
                TextView label=new TextView(app);
                label.setText("RINGADING  ·  DOORBELL HEALTH");
                label.setTextColor(Color.rgb(255,199,119));
                label.setTextSize(14);
                label.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
                card.addView(label);
                TextView heading=new TextView(app);
                heading.setText(title);
                heading.setTextSize(25);
                heading.setTextColor(Color.WHITE);
                heading.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
                card.addView(heading);
                TextView info=new TextView(app);
                info.setText(detail);
                info.setTextSize(16);
                info.setTextColor(Color.rgb(198,213,224));
                card.addView(info);
                WindowManager.LayoutParams p=new WindowManager.LayoutParams(
                    560*d,WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        |WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                    PixelFormat.TRANSLUCENT);
                p.gravity=Gravity.TOP|Gravity.CENTER_HORIZONTAL;
                p.y=30*d;
                wm.addView(card,p);
                windows=wm;
                current=card;
                hide=HealthOverlay::dismiss;
                UI.postDelayed(hide,12000);
            } catch(RuntimeException ignored) {
                dismiss(); // System notification is the fallback.
            }
        });
    }

    private static void dismiss() {
        if(hide!=null)UI.removeCallbacks(hide);
        hide=null;
        if(windows!=null && current!=null){
            try{windows.removeViewImmediate(current);}catch(RuntimeException ignored){}
        }
        windows=null;
        current=null;
    }
}
