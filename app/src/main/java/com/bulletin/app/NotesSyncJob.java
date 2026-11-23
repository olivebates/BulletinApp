package com.bulletin.app;

import android.app.job.*;
import android.content.*;

/** Android retries durable changes when connectivity returns, including after process death. */
public final class NotesSyncJob extends JobService {
    static void schedule(Context c){JobScheduler scheduler=c.getSystemService(JobScheduler.class);scheduler.schedule(new JobInfo.Builder(7301,new ComponentName(c,NotesSyncJob.class)).setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setPersisted(true).setPeriodic(15*60*1000L).build());}
    @Override public boolean onStartJob(JobParameters p){NotesStore.get(this).sync(()->jobFinished(p,false));return true;}
    @Override public boolean onStopJob(JobParameters p){return true;}
}
