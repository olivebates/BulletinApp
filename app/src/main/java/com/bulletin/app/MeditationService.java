package com.bulletin.app;
import android.app.*;
import android.content.Intent;
import android.os.*;

public final class MeditationService extends Service {
    static MeditationService active;
    static long pausedRemaining,previousInterval;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private BellTones tones;
    private PowerManager.WakeLock wake;
    private long interval,next;
    private final Runnable ring=new Runnable(){public void run(){if(tones==null)return;tones.ring();wake.acquire(interval+5000);next=SystemClock.elapsedRealtime()+interval;handler.postDelayed(this,interval);}};
    static long remaining(){return active==null?pausedRemaining:Math.max(0,active.next-SystemClock.elapsedRealtime());}
    @Override public void onCreate(){super.onCreate();active=this;NotificationChannel c=new NotificationChannel("meditation","Meditation Bell",NotificationManager.IMPORTANCE_LOW);c.setSound(null,null);getSystemService(NotificationManager.class).createNotificationChannel(c);wake=getSystemService(PowerManager.class).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"Bulletin:Meditation");wake.setReferenceCounted(false);}
    @Override public int onStartCommand(Intent intent,int flags,int id){
        if(intent!=null&&"pause".equals(intent.getAction())){pause();return START_NOT_STICKY;}
        if(intent==null){stopSelf();return START_NOT_STICKY;}
        boolean update="interval".equals(intent.getAction());
        if(update&&tones==null){stopSelf();return START_NOT_STICKY;}
        interval=Math.max(1000,Math.min(604859000,intent.getLongExtra("interval",60000)));
        Intent stop=new Intent(this,MeditationService.class).setAction("pause");
        PendingIntent pause=PendingIntent.getService(this,24,stop,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        PendingIntent open=PendingIntent.getActivity(this,25,new Intent(this,MeditationActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        startForeground(24,new Notification.Builder(this,"meditation").setSmallIcon(R.drawable.ic_notification).setContentTitle("Meditation Bell").setContentText("Bell every "+TimerService.format(interval)).setContentIntent(open).setOngoing(true).addAction(new Notification.Action.Builder(null,"Pause",pause).build()).build());
        try{if(tones==null){tones=new BellTones(this);setBellVolume(getSharedPreferences("meditation",0).getInt("volume",55));}}catch(java.io.IOException e){stopSelf();return START_NOT_STICKY;}
        handler.removeCallbacksAndMessages(null);if(!update)tones.ring();
        long delay=!update&&previousInterval==interval&&pausedRemaining>0?pausedRemaining:interval;previousInterval=interval;pausedRemaining=0;
        next=SystemClock.elapsedRealtime()+delay;wake.acquire(delay+5000);handler.postDelayed(ring,delay);return START_NOT_STICKY;
    }
    void setBellVolume(int percent){if(tones!=null)tones.setVolume(Math.max(0,Math.min(100,percent))/100f);}
    private void pause(){pausedRemaining=remaining();stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();}
    @Override public void onDestroy(){handler.removeCallbacksAndMessages(null);if(wake!=null&&wake.isHeld())wake.release();if(tones!=null)tones.close();if(active==this)active=null;super.onDestroy();}
    @Override public IBinder onBind(Intent intent){return null;}
}