package com.bulletin.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class TimerReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (!Intent.ACTION_BOOT_COMPLETED.equals(action) && !Intent.ACTION_TIME_CHANGED.equals(action)
                && !Intent.ACTION_TIMEZONE_CHANGED.equals(action)) return;
        GameStore store = ((BulletinApplication) context.getApplicationContext()).store();
        store.sync();
        if (store.game.hasRunningTimers()) {
            try { context.startForegroundService(new Intent(context, TimerService.class)); }
            catch (IllegalStateException ignored) { /* Resume the saved timer when the app next opens. */ }
        }
    }
}
