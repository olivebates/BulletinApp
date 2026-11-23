package com.bulletin.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import java.util.Locale;

public final class TimerService extends Service {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private PowerManager.WakeLock wakeLock;
    private GameStore store;
    private long wakeUntil;
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            store.sync();
            if (!store.game.hasRunningTimers()) {
                if(wakeLock.isHeld())wakeLock.release();
                handler.postDelayed(() -> { stopForeground(STOP_FOREGROUND_REMOVE); stopSelf(); }, 2200);
                return;
            }
            keepAwake();
            getSystemService(NotificationManager.class).notify(1, notification());
            handler.postDelayed(this, nextTickDelay());
        }
    };
    private long nextTickDelay(){
        if(getSystemService(PowerManager.class).isInteractive())return 1000;
        long delay=60000,now=System.currentTimeMillis();
        Game.Session s=store.game.session;if(s!=null&&(s.timed||s.phase==Game.REST))delay=Math.min(delay,s.endsAt-now);
        Game.Fine law=store.game.nextRunningLaw();if(law!=null)delay=Math.min(delay,law.remaining(now));
        return Math.max(1000,delay);
    }
    @Override public void onCreate() {
        super.onCreate();
        store = ((BulletinApplication) getApplication()).store();
        NotificationChannel channel = new NotificationChannel("countdown", "Countdowns", NotificationManager.IMPORTANCE_LOW);
        channel.setSound(null, null);
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
        wakeLock = getSystemService(PowerManager.class).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Bulletin:QuestTimer");
        wakeLock.setReferenceCounted(false);
    }
    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(1, notification());
        handler.removeCallbacksAndMessages(null);
        store.sync();
        keepAwake();
        handler.post(tick);
        return START_STICKY;
    }
    private void keepAwake() {
        long until = store.game.lastTimerDeadline();
        if (until > 0 && (!wakeLock.isHeld() || until != wakeUntil)) {
            wakeLock.acquire(Math.max(1000, until - System.currentTimeMillis()) + 5000); wakeUntil = until;
        }
    }
    private Notification notification() {
        Game.Session s = store.game.session;
        String title = s == null ? "Bulletin is open" : s.phase == Game.QUEST ? s.questName : "Take a rest";
        String text = s == null ? "Your next quest awaits." : (s.phase == Game.REST ? "Rest for: " : "Time remaining: ") + format(s.endsAt - System.currentTimeMillis());
        Game.Fine law = store.game.nextRunningLaw();
        if (law != null) {
            String lawText = "Law allowance: " + law.name + " · " + format(law.remaining(System.currentTimeMillis()));
            if (s == null) { title = law.name; text = lawText; } else text += " · " + lawText;
        }
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        return new Notification.Builder(this, "countdown").setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title).setContentText(text).setContentIntent(open).setOnlyAlertOnce(true)
            .setStyle(new Notification.BigTextStyle().bigText(text))
            .setOngoing(store.game.hasRunningTimers()).setCategory(Notification.CATEGORY_PROGRESS).build();
    }
    public static String format(long milliseconds) {
        long seconds = milliseconds <= 0 ? 0 : 1 + (milliseconds - 1) / 1000;
        if (seconds >= 3600) return String.format(Locale.ROOT, "%d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60);
        return String.format(Locale.ROOT, "%02d:%02d", seconds / 60, seconds % 60);
    }
    @Override public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (wakeLock.isHeld()) wakeLock.release();
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent) { return null; }
}
