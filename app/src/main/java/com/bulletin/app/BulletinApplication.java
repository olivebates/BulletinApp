package com.bulletin.app;

import android.app.Application;

public final class BulletinApplication extends Application {
    private GameStore store;
    private NotionBackups backups;
    private int startedActivities;
    private boolean changingConfiguration;
    private final android.os.Handler lifecycle=new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable background=()->{if(startedActivities==0&&!changingConfiguration){NotesSyncJob.schedule(this);store.appClosed();}};
    @Override public void onCreate() {
        super.onCreate();
        store = new GameStore(this);
        NotesSyncJob.migrate(this);
        MustDoReminder.schedule(this);
        backups = new NotionBackups(this);
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks(){
            public void onActivityStarted(android.app.Activity a){lifecycle.removeCallbacks(background);if(startedActivities++==0&&!changingConfiguration){NotesStore.resetReadOnly();store.appOpened();}changingConfiguration=false;backups.start();}
            public void onActivityStopped(android.app.Activity a){startedActivities=Math.max(0,startedActivities-1);if(startedActivities==0){changingConfiguration=a.isChangingConfigurations();if(!changingConfiguration)lifecycle.postDelayed(background,500);}backups.stop();}
            public void onActivityCreated(android.app.Activity a,android.os.Bundle b){}
            public void onActivityResumed(android.app.Activity a){if(YoutubePlaybackService.active!=null)YoutubePlaybackService.active.park(a);}
            public void onActivityPaused(android.app.Activity a){if(YoutubePlaybackService.active!=null)YoutubePlaybackService.active.activityPaused(a);}
            public void onActivitySaveInstanceState(android.app.Activity a,android.os.Bundle b){}
            public void onActivityDestroyed(android.app.Activity a){if(YoutubePlaybackService.active!=null)YoutubePlaybackService.active.activityDestroyed(a);}
        });
    }
    public GameStore store() { return store; }
    NotionBackups backups(){return backups;}
}
