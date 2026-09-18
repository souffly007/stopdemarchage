// SPDX-License-Identifier: GPL-3.0-only
package fr.bonobo.stopdemarchage;

import android.app.job.*;
import android.content.ComponentName;
import android.content.Context;
import java.util.concurrent.*;

public final class CommunityJob extends JobService {
    private static final int ID = 24010;
    private ExecutorService executor;
    private Future<?> work;
    static void schedule(Context context) {
        JobScheduler scheduler = context.getSystemService(JobScheduler.class);
        if (scheduler == null) return;
        if (!Community.configured() || !new Prefs(context).data.getBoolean("community_enabled", false)) {
            scheduler.cancel(ID); return;
        }
        if (scheduler.getPendingJob(ID) != null) return;
        scheduler.schedule(new JobInfo.Builder(ID, new ComponentName(context, CommunityJob.class))
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setPersisted(true)
            .setPeriodic(24 * 60 * 60 * 1000L).build());
    }
    @Override public boolean onStartJob(JobParameters params) {
        executor = Executors.newSingleThreadExecutor();
        work = executor.submit(() -> {
            Community.sync(getApplicationContext());
            if (!Thread.currentThread().isInterrupted()) jobFinished(params, false);
        });
        executor.shutdown();
        return true;
    }
    @Override public boolean onStopJob(JobParameters params) {
        if (work != null) work.cancel(true);
        if (executor != null) executor.shutdownNow();
        return true;
    }
}
