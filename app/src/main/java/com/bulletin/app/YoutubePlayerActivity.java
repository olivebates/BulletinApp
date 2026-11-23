package com.bulletin.app;

import android.annotation.SuppressLint;
import android.os.*;
import android.webkit.*;
import android.widget.*;
import org.json.*;

/** Embedded player with no native bridge exposed to internet content. */
public final class YoutubePlayerActivity extends ToolScreen {
    private WebView web;
    private String id;
    private boolean closing;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable checkpoint=new Runnable(){public void run(){capture(false);handler.postDelayed(this,1000);}};
    private GameStore store(){return ((BulletinApplication)getApplication()).store();}
    @SuppressLint("SetJavaScriptEnabled")
    @Override public void onCreate(Bundle state){super.onCreate(state);id=getIntent().getStringExtra("id");if(id==null||!id.matches("[A-Za-z0-9_-]{11}")){finish();return;}double seconds=0;for(int i=0;i<store().game.youtubeBookmarks.length();i++){JSONObject video=store().game.youtubeBookmarks.optJSONObject(i);if(id.equals(video.optString("id")))seconds=video.optDouble("seconds",0);}
        web=new WebView(this);web.setBackgroundColor(ui.background);web.getSettings().setJavaScriptEnabled(true);web.getSettings().setDomStorageEnabled(true);web.getSettings().setAllowFileAccess(false);web.getSettings().setAllowContentAccess(false);web.getSettings().setMediaPlaybackRequiresUserGesture(true);web.getSettings().setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        web.setWebViewClient(new WebViewClient(){@Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request){String host=request.getUrl().getHost();return request.isForMainFrame()||!"https".equals(request.getUrl().getScheme())||host==null||!(host.equals("youtube.com")||host.endsWith(".youtube.com")||host.equals("youtube-nocookie.com")||host.endsWith(".youtube-nocookie.com"));}@Override public WebResourceResponse shouldInterceptRequest(WebView view,WebResourceRequest request){String host=request.getUrl().getHost(),path=request.getUrl().getPath();if(host!=null&&(host.equals("doubleclick.net")||host.endsWith(".doubleclick.net")||host.equals("googleadservices.com")||host.endsWith(".googleadservices.com")||host.equals("googlesyndication.com")||host.endsWith(".googlesyndication.com")||(host.endsWith("youtube.com")&&path!=null&&(path.startsWith("/pagead/")||path.startsWith("/api/stats/ads")))))return new WebResourceResponse("text/plain","UTF-8",new java.io.ByteArrayInputStream(new byte[0]));return null;}});
        root.addView(web,new LinearLayout.LayoutParams(-1,0,1));LinearLayout actions=row();Button back=button("Back");action(actions,back);back.setOnClickListener(v->finish());root.addView(actions);addToolNavigation();
        String html="<!doctype html><html><head><meta name='viewport' content='width=device-width,initial-scale=1'><meta name='referrer' content='strict-origin-when-cross-origin'><style>html,body{margin:0;height:100%;background:#000;display:flex;align-items:center;justify-content:center;width:100%}#player{width:100%;height:100%}#error{color:white;position:absolute;bottom:20px;font:16px sans-serif;padding:16px;pointer-events:none}</style></head><body><div id='player'></div><div id='error'></div><script>let player;function onYouTubeIframeAPIReady(){player=new YT.Player('player',{videoId:'"+id+"',playerVars:{playsinline:1,start:"+(int)Math.max(0,seconds)+",origin:'https://com.bulletin.app'},events:{onError:e=>{document.getElementById('error').textContent='This video cannot play here (error '+e.data+').';}}});}function checkpoint(pause){if(!player||typeof player.getCurrentTime!=='function')return null;const state=player.getPlayerState(),seconds=player.getCurrentTime();if(pause)player.pauseVideo();return state===-1||state===5?null:seconds;}</script><script src='https://www.youtube.com/iframe_api'></script></body></html>";
        web.loadDataWithBaseURL("https://com.bulletin.app/",html,"text/html","UTF-8",null);
    }
    private void capture(boolean pause){capture(pause,null);}
    private void capture(boolean pause,Runnable done){if(web==null){if(done!=null)done.run();return;}web.evaluateJavascript("checkpoint("+pause+")",value->{try{if(value==null||value.equals("null"))return;double seconds=Double.parseDouble(value);if(!Double.isFinite(seconds)||seconds<0)return;for(int i=0;i<store().game.youtubeBookmarks.length();i++){JSONObject video=store().game.youtubeBookmarks.optJSONObject(i);if(id.equals(video.optString("id"))&&Math.abs(video.optDouble("seconds")-seconds)>=0.5){video.put("seconds",seconds);store().save();break;}}}catch(Exception ignored){}finally{if(done!=null)done.run();}});}
    @Override public void finish(){if(closing)return;if(web==null){super.finish();return;}closing=true;handler.removeCallbacks(checkpoint);Runnable close=()->{if(!isFinishing())super.finish();};handler.postDelayed(close,250);capture(true,close);}
    @Override protected void onResume(){super.onResume();if(web!=null){web.onResume();handler.post(checkpoint);}}
    @Override protected void onPause(){handler.removeCallbacks(checkpoint);capture(true);if(web!=null)web.onPause();super.onPause();}
    @Override protected void onDestroy(){handler.removeCallbacksAndMessages(null);if(web!=null){root.removeView(web);web.destroy();web=null;}super.onDestroy();}
}
