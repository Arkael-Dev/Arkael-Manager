package com.arkael.app;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.util.concurrent.TimeUnit;

/** Every hour: new-release check + donation reminder. Survives app restarts and reboots (WorkManager). */
public class HourlyWorker extends Worker {
    public HourlyWorker(@NonNull Context c, @NonNull WorkerParameters p) { super(c, p); }

    @NonNull @Override
    public Result doWork() {
        Context c = getApplicationContext();
        try { UpdateNotifier.checkAndNotify(c); } catch (Throwable ignored) {}
        try { Notifier.donate(c); } catch (Throwable ignored) {}
        return Result.success();
    }

    static void schedule(Context c) {
        PeriodicWorkRequest w = new PeriodicWorkRequest.Builder(HourlyWorker.class, 1, TimeUnit.HOURS)
                .setInitialDelay(1, TimeUnit.HOURS)    // not right at first launch
                .build();
        WorkManager.getInstance(c.getApplicationContext())
                .enqueueUniquePeriodicWork("ax_hourly", ExistingPeriodicWorkPolicy.KEEP, w);
    }
}
