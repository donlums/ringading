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

/** Inexact 30-minute polling; never equates TV internet failure with doorbell offline. */
public final class HealthCheckWorker extends Worker {
    private static final String JOB="ringading_doorbell_health_v1";
    private static final long STARTUP_INTERVAL=15L*60_000L;

    public HealthCheckWorker(@NonNull Context context,@NonNull WorkerParameters params){
        super(context,params);
    }

    static void schedule(Context context) {
        PeriodicWorkRequest request=new PeriodicWorkRequest.Builder(
            HealthCheckWorker.class,30,TimeUnit.MINUTES)
            .setConstraints(new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL,30,TimeUnit.MINUTES)
            .build();
        WorkManager.getInstance(context.getApplicationContext())
            .enqueueUniquePeriodicWork(JOB,ExistingPeriodicWorkPolicy.KEEP,request);
    }

    static void checkWhenOpened(Context context) {
        Context app=context.getApplicationContext();
        ChimeApp.IO.execute(()->{
            if(new SecureStore(app).get("refresh_token").isEmpty())return;
            long last=app.getSharedPreferences(DoorbellHealthMonitor.PREFS,Context.MODE_PRIVATE)
                .getLong("last_success_ms",0);
            if(last>0 && System.currentTimeMillis()-last<STARTUP_INTERVAL)return;
            try{DoorbellHealthMonitor.check(app,false);}catch(Exception ignored){}
        });
    }

    @NonNull @Override public Result doWork(){
        try {
            DoorbellHealthMonitor.check(getApplicationContext(),false);
            return Result.success();
        } catch(BlinkCloud.BlinkHttpException error){
            return (error.status==400 || error.status==401)
                ? Result.success():Result.retry();
        } catch(Exception error){
            return Result.retry();
        }
    }
}
