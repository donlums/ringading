package uk.co.pix91.ringading;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import java.util.concurrent.TimeUnit;

/** Scheduled OAuth health check, subject to Android TV power management and Blink MFA rules. */
public final class SessionMaintenance extends Worker {
    private static final String JOB = "ringading_blink_session_renewal";
    private static final long CHECK_EVERY_MS = 30L * 60 * 1000;

    public SessionMaintenance(@NonNull Context context, @NonNull WorkerParameters parameters) {
        super(context, parameters);
    }

    static void schedule(Context context) {
        Constraints constraints = new Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED).build();
        PeriodicWorkRequest work = new PeriodicWorkRequest.Builder(
            SessionMaintenance.class, 12, TimeUnit.HOURS)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
            .build();
        WorkManager.getInstance(context.getApplicationContext())
            .enqueueUniquePeriodicWork(JOB, ExistingPeriodicWorkPolicy.KEEP, work);
    }

    /** Opportunistic foreground check. No HTTP call from the main thread. */
    static void checkWhenOpened(Context context) {
        Context app = context.getApplicationContext();
        ChimeApp.IO.execute(() -> {
            SecureStore store = new SecureStore(app);
            if (store.get("refresh_token").isEmpty()) return;
            long last;
            try { last = Long.parseLong(store.get("last_auth_checked_ms")); }
            catch (NumberFormatException ignored) { last = 0; }
            if (last > 0 && System.currentTimeMillis() - last < CHECK_EVERY_MS) return;
            try { BlinkCloud.maintainSession(app); }
            catch (Exception ignored) {
                // Preserve credentials. A denied refresh is surfaced on Blink connection screen.
            }
        });
    }

    @NonNull @Override public Result doWork() {
        SecureStore store = new SecureStore(getApplicationContext());
        if (store.get("refresh_token").isEmpty()) return Result.success();
        try {
            BlinkCloud.maintainSession(getApplicationContext());
            return Result.success();
        } catch (BlinkCloud.BlinkHttpException e) {
            // An authentication challenge cannot be solved with repeated silent retries.
            if (e.status == 400 || e.status == 401) return Result.success();
            return Result.retry();
        } catch (Exception e) {
            // Network outages never erase the linked Blink account.
            return Result.retry();
        }
    }
}
