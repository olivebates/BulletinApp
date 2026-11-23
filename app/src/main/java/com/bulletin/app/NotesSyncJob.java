package com.bulletin.app;

import android.app.job.*;
import android.content.*;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;

/** One durable upload request on leaving Tools, with network-aware backoff on failure. */
public final class NotesSyncJob extends JobService {
    private static final int ID=7301;
    private volatile int generation;
    static void migrate(Context c){JobScheduler s=c.getSystemService(JobScheduler.class);JobInfo old=s.getPendingJob(ID);if(old!=null&&old.isPeriodic())s.cancel(ID);}
    static boolean isOnline(Context context) {
        ConnectivityManager manager = context.getSystemService(ConnectivityManager.class);
        NetworkCapabilities capabilities = manager.getNetworkCapabilities(manager.getActiveNetwork());
        return capabilities != null && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
    }

    static boolean isQueued(Context context) {
        return context.getSystemService(JobScheduler.class).getPendingJob(ID) != null;
    }

    static void schedule(Context c){
        c = c.getApplicationContext();
        NotesStore notes=NotesStore.get(c);
        if (!notes.canUpload() || notes.busy) return;
        JobScheduler scheduler=c.getSystemService(JobScheduler.class);
        JobInfo old=scheduler.getPendingJob(ID);
        notes.schedulingError = "";
        if (old == null || old.isPeriodic()) {
            try {
                int result = scheduler.schedule(new JobInfo.Builder(ID,new ComponentName(c,NotesSyncJob.class))
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setPersisted(true)
                    .setMinimumLatency(1000).setBackoffCriteria(30000,JobInfo.BACKOFF_POLICY_EXPONENTIAL).build());
                if (result != JobScheduler.RESULT_SUCCESS) {
                    notes.schedulingError = "Android could not queue the Notes upload. Your changes are saved.";
                }
            } catch (IllegalStateException | IllegalArgumentException | SecurityException error) {
                notes.schedulingError = "Android could not queue the Notes upload. Your changes are saved.";
            }
        }
        // An explicit departure can upload now. The durable job remains available if the
        // process stops or the upload needs a network retry.
        if (isOnline(c)) {
            notes.sync(() -> {
                if (!notes.canUpload() || notes.invalidNotes) scheduler.cancel(ID);
            });
        }
    }
    @Override public boolean onStartJob(JobParameters p){final int run=++generation;NotesStore notes=NotesStore.get(this);notes.sync(()->{
        boolean retry=!notes.invalidNotes&&notes.canUpload();
        if(run==generation)jobFinished(p,retry);
    });return true;}
    @Override public boolean onStopJob(JobParameters p){generation++;return true;}
}
