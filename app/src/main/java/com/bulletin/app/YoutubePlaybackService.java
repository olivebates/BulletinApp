package com.bulletin.app;

import android.annotation.SuppressLint;
import android.app.*;
import android.content.*;
import android.media.*;
import android.media.session.*;
import android.os.*;
import android.view.*;
import android.webkit.*;
import android.widget.FrameLayout;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;
import org.json.*;
import java.util.*;

/** Owns one browser player independently of any screen. No Java bridge touches remote content. */
public final class YoutubePlaybackService extends Service {
    static YoutubePlaybackService active;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final LocalBinder binder=new LocalBinder();
    final class LocalBinder extends Binder {YoutubePlaybackService service(){return YoutubePlaybackService.this;}}
    private WebView web;
    private MutableContextWrapper webContext;
    private FrameLayout surface;
    private Activity owner;
    private boolean viewing;
    private MediaSession session;


    private boolean playing,foreground,destroying,unsaved;
    private String id="",title="YouTube",error="";
    private double seconds,duration;
    private long lastSave,lastCheckpoint,loadedAt;
    private int lastState=-1;
    private PowerManager.WakeLock wake;
    private final Runnable tick=new Runnable(){public void run(){capture();}};
    private GameStore store(){return ((BulletinApplication)getApplication()).store();}
    static boolean validId(String value){return value!=null&&value.matches("[A-Za-z0-9_-]{11}");}
    String videoId(){return id;}
    String playbackError(){return error;}
    boolean isPlaying(){return playing;}
    static void restoreCheckpoint(Context context,Game game){
        android.content.SharedPreferences checkpoint=context.getSharedPreferences("youtube-checkpoints",0);
        String id=checkpoint.getString("id","");long at=checkpoint.getLong("at",0);float seconds=checkpoint.getFloat("seconds",0);
        if(!validId(id)||!Float.isFinite(seconds)||seconds<0)return;
        for(int i=0;i<game.youtubeBookmarks.length();i++){JSONObject video=game.youtubeBookmarks.optJSONObject(i);if(video!=null&&id.equals(video.optString("id"))&&at>video.optLong("checkpointAt",0))try{video.put("seconds",(double)seconds);video.put("checkpointAt",at);}catch(JSONException ignored){}}
    }

    @Override public void onCreate(){
        super.onCreate();active=this;
        NotificationChannel channel=new NotificationChannel("youtube-playback","YouTube playback",NotificationManager.IMPORTANCE_LOW);channel.setSound(null,null);
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
        wake=getSystemService(PowerManager.class).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"Bulletin:Video");wake.setReferenceCounted(false);
        // Chromium owns audio focus; a second request here pauses the WebView itself.
        session=new MediaSession(this,"Bulletin video");session.setCallback(new MediaSession.Callback(){
            @Override public void onPlay(){command("playVideo()");}
            @Override public void onPause(){command("pauseVideo()");}
            @Override public void onStop(){stopPlayback();}
            @Override public void onSeekTo(long position){command("seekTo("+Math.max(0,position/1000d)+",true)");}
        });
        registerReceiver(noisy,new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY));
    }
    private final BroadcastReceiver noisy=new BroadcastReceiver(){@Override public void onReceive(Context c,Intent i){command("pauseVideo()");}};
    @Override public IBinder onBind(Intent intent){return binder;}
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(intent!=null){String action=intent.getAction();if("stop".equals(action))stopPlayback();else if("pause".equals(action)){command("pauseVideo()");}else if("play".equals(action))command("playVideo()");}
        return START_NOT_STICKY;
    }

    @SuppressLint("SetJavaScriptEnabled")
    void open(String requested){
        if(!validId(requested)||destroying)return;
        if(requested.equals(id)&&web!=null){handler.removeCallbacks(tick);handler.post(tick);return;}
        persist(true);releaseBrowser();setPlaying(false);id=requested;title="YouTube";seconds=duration=0;error="";lastState=-1;loadedAt=SystemClock.elapsedRealtime();
        for(int i=0;i<store().game.youtubeBookmarks.length();i++){JSONObject video=store().game.youtubeBookmarks.optJSONObject(i);if(video!=null&&id.equals(video.optString("id"))){title=video.optString("name","YouTube");seconds=Math.max(0,video.optDouble("seconds",0));}}
        webContext=new MutableContextWrapper(this);
        web=new WebView(webContext){@Override protected void onWindowVisibilityChanged(int visibility){super.onWindowVisibilityChanged(destroying?visibility:View.VISIBLE);}};
        web.setBackgroundColor(android.graphics.Color.BLACK);
        WebSettings settings=web.getSettings();settings.setJavaScriptEnabled(true);settings.setDomStorageEnabled(true);settings.setAllowFileAccess(false);settings.setAllowContentAccess(false);settings.setMediaPlaybackRequiresUserGesture(false);settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        web.setWebChromeClient(new WebChromeClient());
        web.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request){return request.isForMainFrame()||!allowed(request.getUrl());}
            @Override public WebResourceResponse shouldInterceptRequest(WebView view,WebResourceRequest request){String host=request.getUrl().getHost(),path=request.getUrl().getPath();if(blocked(host,path))return new WebResourceResponse("text/plain","UTF-8",new java.io.ByteArrayInputStream(new byte[0]));return null;}
            @Override public void onPageFinished(WebView view,String url){handler.removeCallbacks(tick);handler.post(tick);}
            @Override public boolean onRenderProcessGone(WebView view,RenderProcessGoneDetail detail){error="Video player closed. Reopen the video to try again.";persist(true);setPlaying(false);releaseBrowser();return true;}
        });
        // Run before YouTube installs its visibility listeners. This is best-effort and may
        // need adapting if YouTube changes its web player; older WebViews retain normal playback.
        if(WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)){
            String keepVisible="(()=>{for(const key of ['hidden','webkitHidden'])try{Object.defineProperty(document,key,{configurable:true,get:()=>false});}catch(e){}for(const key of ['visibilityState','webkitVisibilityState'])try{Object.defineProperty(document,key,{configurable:true,get:()=> 'visible'});}catch(e){}for(const event of ['visibilitychange','webkitvisibilitychange'])document.addEventListener(event,e=>e.stopImmediatePropagation(),true);})();";
            WebViewCompat.addDocumentStartJavaScript(web,keepVisible,new HashSet<>(Arrays.asList("https://com.bulletin.app","https://www.youtube.com","https://www.youtube-nocookie.com","https://m.youtube.com")));
        }else error="Update Android System WebView for background playback support.";
        String html="<!doctype html><html><head><meta name='viewport' content='width=device-width,initial-scale=1'><meta name='referrer' content='strict-origin-when-cross-origin'><style>html,body{margin:0;width:100%;height:100%;background:#000}#player{width:100%;height:100%}#error{position:absolute;bottom:0;color:white;background:#0009;font:16px sans-serif;padding:12px}</style></head><body><div id='player'></div><div id='error' hidden></div><script>let player,lastError='';function onYouTubeIframeAPIReady(){player=new YT.Player('player',{videoId:'"+id+"',playerVars:{playsinline:1,start:"+(int)seconds+",origin:'https://com.bulletin.app'},events:{onError:e=>{lastError='YouTube cannot play this video (error '+e.data+').';let box=document.getElementById('error');box.hidden=false;box.textContent=lastError;}}});}function checkpoint(){if(!player||typeof player.getCurrentTime!=='function')return null;return {seconds:player.getCurrentTime(),duration:player.getDuration(),state:player.getPlayerState(),error:lastError};}</script><script src='https://www.youtube.com/iframe_api'></script></body></html>";
        web.loadDataWithBaseURL("https://com.bulletin.app/",html,"text/html","UTF-8",null);
        handler.removeCallbacks(tick);handler.post(tick);
    }
    private static boolean allowed(android.net.Uri uri){String host=uri.getHost();return "https".equals(uri.getScheme())&&host!=null&&(host.equals("youtube.com")||host.endsWith(".youtube.com")||host.equals("youtube-nocookie.com")||host.endsWith(".youtube-nocookie.com"));}
    static boolean blocked(String host,String path){return host!=null&&(host.equals("doubleclick.net")||host.endsWith(".doubleclick.net")||host.equals("googleadservices.com")||host.endsWith(".googleadservices.com")||host.equals("googlesyndication.com")||host.endsWith(".googlesyndication.com")||((host.equals("youtube.com")||host.endsWith(".youtube.com"))&&path!=null&&(path.startsWith("/pagead/")||path.startsWith("/api/stats/ads"))));}
    void attach(Activity activity,FrameLayout target){
        mount(activity,target,true);
    }
    private void mount(Activity activity,FrameLayout target,boolean visible){
        if(web==null)return;if(surface==target){viewing=visible;web.setKeepScreenOn(visible&&playing);handler.removeCallbacks(tick);handler.post(tick);return;}boolean resume=playing;detach();owner=activity;surface=target;viewing=visible;webContext.setBaseContext(activity);target.addView(web,new FrameLayout.LayoutParams(-1,-1));web.onResume();web.setKeepScreenOn(visible&&playing);if(resume)command("playVideo()");handler.removeCallbacks(tick);handler.postDelayed(tick,300);
    }
    void park(Activity activity){
        if(web==null||activity instanceof YoutubePlayerActivity)return;
        android.view.ViewGroup decor=(android.view.ViewGroup)activity.getWindow().getDecorView();FrameLayout host=decor.findViewWithTag("youtube-background-host");
        if(host==null){host=new FrameLayout(activity);host.setTag("youtube-background-host");host.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);decor.addView(host,0,new android.view.ViewGroup.LayoutParams(640,360));}
        mount(activity,host,false);
    }
    void activityDestroyed(Activity activity){if(owner==activity)detach();}
    void activityPaused(Activity activity){if(owner==activity){viewing=false;if(web!=null)web.setKeepScreenOn(false);}}
    void detach(){if(web!=null&&web.getParent() instanceof android.view.ViewGroup)((android.view.ViewGroup)web.getParent()).removeView(web);surface=null;owner=null;viewing=false;if(webContext!=null)webContext.setBaseContext(this);}
    void checkpoint(){handler.removeCallbacks(tick);capture();}
    private void capture(){
        if(web==null||destroying)return;final WebView current=web;final String currentId=id;
        current.evaluateJavascript("typeof checkpoint==='function'?checkpoint():null",value->{
            if(current!=web||!id.equals(currentId)||destroying)return;
            try{if(value!=null&&!value.equals("null")){
                JSONObject data=new JSONObject(value);int state=data.optInt("state",-1);double position=data.optDouble("seconds",Double.NaN);
                if(state!=-1&&state!=5&&Double.isFinite(position)&&position>=0){seconds=position;duration=Math.max(0,data.optDouble("duration",0));persist(false);}
                String failure=data.optString("error");if(!failure.isEmpty())error=failure;
                boolean running=state==1||(state==3&&playing);setPlaying(running);
                // Renew only while the browser reports active playback. A stalled or dead
                // renderer must not leave the CPU locked awake for hours.
                if(state==1&&playing)wake.acquire(30000);
                if(state!=lastState){lastState=state;if(state==2||state==0)persist(true);updateSession();if(foreground)getSystemService(NotificationManager.class).notify(73,notification());}
            }}catch(JSONException ignored){}
            handler.removeCallbacks(tick);
            if(viewing||playing||SystemClock.elapsedRealtime()-loadedAt<60000)handler.postDelayed(tick,viewing?1000:5000);
        });
    }
    private void command(String action){if(web==null)return;if(action.equals("playVideo()")&&!foreground){try{startForeground(73,notification());foreground=true;}catch(RuntimeException e){error="Open the video to resume playback.";return;}}web.evaluateJavascript("if(typeof player!=='undefined'&&player&&typeof player.getPlayerState==='function')player."+action+";",null);handler.removeCallbacks(tick);handler.postDelayed(tick,200);}
    private void setPlaying(boolean value){
        if(playing==value)return;playing=value;
        if(value){
            try{startForeground(73,notification());foreground=true;}catch(RuntimeException unavailable){playing=false;error="Open the video to resume playback.";command("pauseVideo()");return;}

            if(!wake.isHeld())wake.acquire(30000);
            if(web!=null)web.setKeepScreenOn(viewing);
        }else{
            if(wake.isHeld())wake.release();if(web!=null)web.setKeepScreenOn(false);

            if(foreground){stopForeground(STOP_FOREGROUND_DETACH);foreground=false;getSystemService(NotificationManager.class).notify(73,notification());}
        }
        updateSession();
    }
    private void updateSession(){if(session==null)return;session.setActive(!id.isEmpty());session.setMetadata(new MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_TITLE,title).putLong(MediaMetadata.METADATA_KEY_DURATION,(long)(duration*1000)).build());session.setPlaybackState(new PlaybackState.Builder().setActions(PlaybackState.ACTION_PLAY|PlaybackState.ACTION_PAUSE|PlaybackState.ACTION_PLAY_PAUSE|PlaybackState.ACTION_STOP|PlaybackState.ACTION_SEEK_TO).setState(playing?PlaybackState.STATE_PLAYING:PlaybackState.STATE_PAUSED,(long)(seconds*1000),playing?1:0).build());}
    private PendingIntent control(String action){return PendingIntent.getService(this,action.hashCode(),new Intent(this,YoutubePlaybackService.class).setAction(action),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);}
    private Notification notification(){
        PendingIntent open=PendingIntent.getActivity(this,73,new Intent(this,YoutubePlayerActivity.class).putExtra("id",id).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this,"youtube-playback").setSmallIcon(R.drawable.ic_notification).setContentTitle(title).setContentText(playing?"Playing":"Paused").setContentIntent(open).setDeleteIntent(control("stop")).setOnlyAlertOnce(true).setOngoing(playing).setVisibility(Notification.VISIBILITY_PUBLIC).setCategory(Notification.CATEGORY_TRANSPORT)
            .addAction(new Notification.Action.Builder(null,playing?"Pause":"Play",control(playing?"pause":"play")).build()).addAction(new Notification.Action.Builder(null,"Stop",control("stop")).build())
            .setStyle(new Notification.MediaStyle().setMediaSession(session.getSessionToken()).setShowActionsInCompactView(0,1)).build();
    }
    private void persist(boolean force){
        if(id.isEmpty())return;long now=SystemClock.elapsedRealtime();boolean changed=false;
        long at=System.currentTimeMillis();
        for(int i=0;i<store().game.youtubeBookmarks.length();i++){JSONObject video=store().game.youtubeBookmarks.optJSONObject(i);if(video!=null&&id.equals(video.optString("id"))){try{if(Math.abs(video.optDouble("seconds",0)-seconds)>=.25){video.put("seconds",seconds);video.put("checkpointAt",at);changed=true;unsaved=true;}}catch(JSONException ignored){}break;}}
        // Small checkpoints survive process death; the main save receives the same position.
        if((changed||force&&unsaved)&&(force||now-lastCheckpoint>=5000)){getSharedPreferences("youtube-checkpoints",0).edit().putString("id",id).putFloat("seconds",(float)seconds).putLong("at",at).apply();lastCheckpoint=now;}
        if(unsaved&&(force||now-lastSave>=30000)){if(store().save())unsaved=false;lastSave=now;}
    }
    void stopPlayback(){if(destroying)return;persist(true);if(web!=null)web.evaluateJavascript("if(typeof player!=='undefined'&&player&&player.pauseVideo)player.pauseVideo()",null);releaseBrowser();setPlaying(false);id="";session.setActive(false);stopForeground(STOP_FOREGROUND_REMOVE);getSystemService(NotificationManager.class).cancel(73);stopSelf();}
    private void releaseBrowser(){handler.removeCallbacks(tick);detach();if(web!=null){web.stopLoading();web.destroy();web=null;}webContext=null;}
    @Override public void onTaskRemoved(Intent rootIntent){stopPlayback();super.onTaskRemoved(rootIntent);}
    @Override public void onDestroy(){destroying=true;persist(true);releaseBrowser();if(wake.isHeld())wake.release();session.release();unregisterReceiver(noisy);getSystemService(NotificationManager.class).cancel(73);if(active==this)active=null;super.onDestroy();}
}
