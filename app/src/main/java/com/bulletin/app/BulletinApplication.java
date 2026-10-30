package com.bulletin.app;

import android.app.Application;

public final class BulletinApplication extends Application {
    private GameStore store;
    @Override public void onCreate() {
        super.onCreate();
        store = new GameStore(this);
    }
    public GameStore store() { return store; }
}
