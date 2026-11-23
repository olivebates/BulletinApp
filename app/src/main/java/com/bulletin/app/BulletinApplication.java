package com.bulletin.app;

import android.app.Application;

public final class BulletinApplication extends Application {
    private GameStore store;
    private NotionBackups backups;
    private int startedActivities;
    private boolean changingConfiguration;
    @Override public void onCreate() {
        super.onCreate();
        store = new GameStore(this);
        MustDoReminder.schedule(this);
        backups = new NotionBackups(this);
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks(){
            public void onActivityStarted(android.app.Activity a){if(startedActivities++==0&&!changingConfiguration)NotesStore.resetReadOnly();changingConfiguration=false;backups.start();}
            public void onActivityStopped(android.app.Activity a){startedActivities=Math.max(0,startedActivities-1);if(startedActivities==0)changingConfiguration=a.isChangingConfigurations();backups.stop();}
            public void onActivityCreated(android.app.Activity a,android.os.Bundle b){}
            public void onActivityResumed(android.app.Activity a){}
            public void onActivityPaused(android.app.Activity a){}
            public void onActivitySaveInstanceState(android.app.Activity a,android.os.Bundle b){}
            public void onActivityDestroyed(android.app.Activity a){}
        });
    }
    public GameStore store() { return store; }
    NotionBackups backups(){return backups;}
}
