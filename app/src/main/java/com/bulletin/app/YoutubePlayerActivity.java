package com.bulletin.app;

import android.content.*;
import android.os.*;
import android.widget.*;

/** Reattaches the persistent browser without restarting playback. */
public final class YoutubePlayerActivity extends ToolScreen {
    private YoutubePlaybackService playback;
    private FrameLayout surface;
    private String id;
    private boolean bound,leaving;
    private final ServiceConnection connection=new ServiceConnection(){
        public void onServiceConnected(ComponentName name,IBinder binder){playback=((YoutubePlaybackService.LocalBinder)binder).service();playback.open(id);playback.attach(YoutubePlayerActivity.this,surface);}
        public void onServiceDisconnected(ComponentName name){playback=null;}
    };
    @Override public void onCreate(Bundle state){
        super.onCreate(state);id=getIntent().getStringExtra("id");if(!YoutubePlaybackService.validId(id)){finish();return;}
        remember();surface=new FrameLayout(this);root.addView(surface,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout actions=row();Button back=button("Back");action(actions,back);back.setOnClickListener(v->back());root.addView(actions);addToolNavigation();
        if(Build.VERSION.SDK_INT>=33)getOnBackInvokedDispatcher().registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,this::back);
    }
    private void remember(){getSharedPreferences("tool-navigation",0).edit().putBoolean("youtube-open",true).putBoolean("notes-open",false).putString("youtube-video",id).apply();}
    @Override protected void onResume(){super.onResume();if(!YoutubePlaybackService.validId(id))return;remember();if(playback!=null){startService(new Intent(this,YoutubePlaybackService.class));playback.open(id);playback.attach(this,surface);}else if(!bound){Intent intent=new Intent(this,YoutubePlaybackService.class);startService(intent);bound=bindService(intent,connection,BIND_AUTO_CREATE);}}
    @Override protected void onPause(){if(playback!=null)playback.checkpoint();super.onPause();}
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);String next=intent.getStringExtra("id");if(YoutubePlaybackService.validId(next)){id=next;setIntent(intent);remember();if(playback!=null){startService(new Intent(this,YoutubePlaybackService.class));playback.open(id);playback.attach(this,surface);}}}
    private void back(){if(leaving)return;leaving=true;getSharedPreferences("tool-navigation",0).edit().putString("youtube-video","").apply();startActivity(new Intent(this,YoutubeLibraryActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP));finish();}
    @SuppressWarnings("deprecation") @android.annotation.SuppressLint("GestureBackNavigation")
    @Override public void onBackPressed(){back();}
    @Override protected void onDestroy(){if(playback!=null)playback.activityDestroyed(this);if(bound){unbindService(connection);bound=false;}playback=null;super.onDestroy();}
}
