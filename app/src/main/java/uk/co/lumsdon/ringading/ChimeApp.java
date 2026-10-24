package uk.co.lumsdon.ringading;

import android.app.Application;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class ChimeApp extends Application {
    public static final ExecutorService IO = Executors.newSingleThreadExecutor();
    // Public Firebase client identifiers used by Blink Android v60.0 as of Oct 2026.
    // Source: CoreyHayward/ha-blink-live-events (MIT). NOT a secret or Blink credential.
    private static final String FIREBASE_APP_ID = "1:351117235468:android:14fb3ef63df2a7c2";
    private static final String FIREBASE_KEY = "AIzaSyBcgwrpglzCZJPUNNeQQnpBbJAFlas_XAI";
    private static final String FIREBASE_PROJECT = "blink-6c1ea";
    private static final String FIREBASE_SENDER = "351117235468";

    @Override public void onCreate() {
        super.onCreate();
        try {
            if (FirebaseApp.getApps(this).isEmpty()) {
                FirebaseOptions opts = new FirebaseOptions.Builder()
                    .setApplicationId(FIREBASE_APP_ID)
                    .setApiKey(FIREBASE_KEY)
                    .setProjectId(FIREBASE_PROJECT)
                    .setGcmSenderId(FIREBASE_SENDER)
                    .build();
                FirebaseApp.initializeApp(this, opts);
            }
            FirebaseMessaging.getInstance().getToken()
                .addOnSuccessListener(token -> onToken(token))
                .addOnFailureListener(error -> Status.write(this, "FCM token error: " + error.getClass().getSimpleName()));
        } catch (Exception error) { Status.write(this, "FCM setup failed: " + error.getClass().getSimpleName()); }
    }
    public void onToken(String token) {
        IO.execute(() -> {
            try {
                SecureStore store = new SecureStore(this);
                String prior = store.get("fcm_token");
                store.put("fcm_token", token);
                if (!token.equals(prior)) store.put("linked", "false");
                Status.write(this, "Firebase token issued; " + (store.get("refresh_token").isEmpty() ? "connect Blink next" : "linking Blink"));
                if (!store.get("refresh_token").isEmpty() && !token.equals(prior)) {
                    BlinkCloud.link(this);
                    Status.write(this, "Push token re-linked to Blink");
                }
            } catch (Exception e) { Status.write(this, "Token association: " + e.getMessage()); }
        });
    }
}