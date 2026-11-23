package com.bulletin.app;

import android.app.*;
import android.content.*;
import android.os.Build;
import java.time.*;

/** One local evening reminder; rescheduled after boot and clock changes. */
public final class MustDoReminder extends BroadcastReceiver {
    static void schedule(Context context){
        AlarmManager alarms=context.getSystemService(AlarmManager.class);
        ZonedDateTime now=ZonedDateTime.now(),next=now.withHour(20).withMinute(0).withSecond(0).withNano(0);
        if(!next.isAfter(now))next=next.plusDays(1);
        PendingIntent intent=PendingIntent.getBroadcast(context,2000,new Intent(context,MustDoReminder.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        // Inexact alarms work without asking for special exact-alarm access.
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,next.toInstant().toEpochMilli(),intent);
    }
    @Override public void onReceive(Context context,Intent intent){
        schedule(context);GameStore store=((BulletinApplication)context.getApplicationContext()).store();store.sync();Game game=store.game;
        if(game.requiredPenalty().days==0)return;
        long day=LocalDate.now().toEpochDay();android.content.SharedPreferences prefs=context.getSharedPreferences("must-do-reminder",0);if(prefs.getLong("sent",Long.MIN_VALUE)==day)return;
        StringBuilder tasks=new StringBuilder();for(Game.Quest q:game.quests)if(game.standaloneRequired(q)&&q.due(game.day)&&!q.done(game.day)){if(tasks.length()>0)tasks.append('\n');tasks.append(q.name);}
        for(Game.Category c:game.categories)if(game.categoryRemaining(c,game.day)>0){if(tasks.length()>0)tasks.append('\n');tasks.append(game.categoryTitle(c));}
        NotificationManager manager=context.getSystemService(NotificationManager.class);manager.createNotificationChannel(new NotificationChannel("must-do-evening","Must-do reminders",NotificationManager.IMPORTANCE_DEFAULT));
        if(Build.VERSION.SDK_INT>=33&&context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED)return;
        PendingIntent open=PendingIntent.getActivity(context,2000,new Intent(context,MainActivity.class).putExtra("navigateTab",0).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        manager.notify(2000,new Notification.Builder(context,"must-do-evening").setSmallIcon(com.bulletin.app.R.drawable.ic_notification).setContentTitle("Finish your must-do tasks").setContentText(tasks.toString()).setStyle(new Notification.BigTextStyle().bigText(tasks.toString())).setContentIntent(open).setAutoCancel(true).build());prefs.edit().putLong("sent",day).apply();
    }
}
