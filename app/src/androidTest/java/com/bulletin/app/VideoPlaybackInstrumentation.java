package com.bulletin.app;

import android.app.*;
import android.content.*;
import android.os.*;
import android.webkit.WebView;
import android.view.*;
import java.util.concurrent.*;
import org.json.*;

/** Real WebView audio checks, plus an explicitly reported live YouTube probe. */
public final class VideoPlaybackInstrumentation extends Instrumentation {
    private Activity activity,mainActivity;
    private WebView web;
    private YoutubePlaybackService service;
    private int assertions;
    private String original;
    private boolean live;
    @Override public void onCreate(Bundle args){super.onCreate(args);live=args!=null&&args.getBoolean("live",false);if(args!=null)live|="true".equals(args.getString("live"));start();}
    @Override public void onStart(){Bundle result=new Bundle();String probe="";int code=Activity.RESULT_CANCELED;
        try{
            activity=startActivitySync(new Intent(getTargetContext(),MainActivity.class).putExtra("navigateTab",0).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            mainActivity=activity;GameStore store=((BulletinApplication)getTargetContext().getApplicationContext()).store();
            ui(()->{JSONArray clean=new JSONArray();for(int i=0;i<store.game.youtubeBookmarks.length();i++){JSONObject v=store.game.youtubeBookmarks.optJSONObject(i);if(v!=null&&!"Playback test".equals(v.optString("name")))clean.put(v);}store.game.youtubeBookmarks=clean;original=clean.toString();try{store.game.youtubeBookmarks.put(new JSONObject().put("id","M7lc1UVf-VE").put("name","Playback test").put("seconds",0));}catch(JSONException e){throw new AssertionError(e);}getTargetContext().getSharedPreferences("tool-navigation",0).edit().putBoolean("notes-open",false).apply();});
            activity=startActivitySync(new Intent(getTargetContext(),YoutubePlayerActivity.class).putExtra("id","M7lc1UVf-VE").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            for(int n=0;n<100&&service==null;n++){ui(()->{service=YoutubePlaybackService.active;if(service!=null)try{web=(WebView)field(service,"web");}catch(Exception e){throw new AssertionError(e);}});if(web==null){service=null;Thread.sleep(100);}}
            if(web==null)throw new AssertionError("Service player not attached");assertions++;
            if(live){
                for(int n=0;n<30;n++){if("true".equals(js("typeof player!=='undefined'&&player&&typeof player.playVideo==='function'")))break;Thread.sleep(1000);}
                js("(typeof player!=='undefined'&&player&&player.playVideo)?(player.playVideo(),true):false");
                for(int n=0;n<20;n++){Thread.sleep(1000);if(service.isPlaying()||!service.playbackError().isEmpty())break;}
                probe="Live YouTube probe: "+js("typeof checkpoint==='function'?checkpoint():null")+"; "+service.playbackError()+"\n";
                if(service.isPlaying()){Thread.sleep(12000);
                    double start=new JSONObject(js("checkpoint()")).optDouble("seconds");
                    ui(()->activity.setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE));Thread.sleep(2500);
                    JSONObject rotated=new JSONObject(js("checkpoint()"));boolean rotation=rotated.optInt("state")==1&&rotated.optDouble("seconds")>start+1;
                    shell("input keyevent 3");Thread.sleep(15000);JSONObject background=new JSONObject(js("checkpoint()"));
                    boolean backgroundOk=background.optInt("state")==1&&background.optDouble("seconds")>rotated.optDouble("seconds")+3;
                    shell("input keyevent 223");Thread.sleep(15000);JSONObject locked=new JSONObject(js("checkpoint()"));
                    boolean lockOk=locked.optInt("state")==1&&locked.optDouble("seconds")>background.optDouble("seconds")+3;
                    probe+="Live playback: rotation="+rotation+", another app="+backgroundOk+", screen off="+lockOk+"; background "+background+"; final "+locked+"\n";
                    shell("input keyevent 224");shell("wm dismiss-keyguard");ui(()->activity.startActivity(new Intent(activity,YoutubePlayerActivity.class).putExtra("id","M7lc1UVf-VE").addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)));waitForIdleSync();
                }
            }
            Bundle progress=new Bundle();progress.putString("stream",probe);sendStatus(0,progress);
            // A local 120-second PCM track isolates our lifecycle from YouTube availability.
            byte[] wave=new byte[44+8000*120];java.util.Arrays.fill(wave,44,wave.length,(byte)128);java.nio.ByteBuffer b=java.nio.ByteBuffer.wrap(wave).order(java.nio.ByteOrder.LITTLE_ENDIAN);
            b.put(new byte[]{82,73,70,70}).putInt(wave.length-8).put(new byte[]{87,65,86,69,102,109,116,32}).putInt(16).putShort((short)1).putShort((short)1).putInt(8000).putInt(8000).putShort((short)1).putShort((short)8).put(new byte[]{100,97,116,97}).putInt(wave.length-44);
            String html="<audio id='media' controls src='data:audio/wav;base64,"+android.util.Base64.encodeToString(wave,android.util.Base64.NO_WRAP)+"'></audio><script>const media=document.getElementById('media');const player={getPlayerState:()=>media.paused?2:1,playVideo:()=>media.play(),pauseVideo:()=>media.pause(),seekTo:t=>media.currentTime=t};function checkpoint(){return {seconds:media.currentTime,duration:120,state:media.paused?2:1,error:''};}</script>";
            ui(()->web.loadDataWithBaseURL("https://com.bulletin.app/",html,"text/html","UTF-8",null));
            for(int n=0;n<100;n++){if("true".equals(js("typeof media!=='undefined'&&media.readyState>=2")))break;Thread.sleep(100);}
            js("(media.play(),true)");awaitPlaying(true);double before=position();Thread.sleep(1500);assertTrue(position()>before,"Real audio advances");
            WebView retained=web;ui(()->activity.setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE));Thread.sleep(1500);assertTrue(web==field(service,"web")&&position()>before,"Rotation retains advancing player");
            ActivityMonitor libraryMonitor=addMonitor(YoutubeLibraryActivity.class.getName(),null,false);ui(()->find(activity.getWindow().getDecorView(),"Back").performClick());Activity library=libraryMonitor.waitForActivityWithTimeout(10000);removeMonitor(libraryMonitor);assertTrue(library!=null,"Back opens library");activity=library;Thread.sleep(2000);assertTrue(service.isPlaying()&&retained==field(service,"web"),"Back keeps same playing browser");assertTrue(getTargetContext().getSharedPreferences("tool-navigation",0).getString("youtube-video","missing").isEmpty(),"Library location is remembered");
            ui(()->find(activity.getWindow().getDecorView(),"Bulletin").performClick());waitForIdleSync();activity=mainActivity;assertTrue(!activity.isFinishing(),"Bulletin navigation works");
            before=position();Thread.sleep(5500);assertTrue(position()>before+2&&service.isPlaying(),"Audio continues on Bulletin tab");
            libraryMonitor=addMonitor(YoutubeLibraryActivity.class.getName(),null,false);ui(()->find(activity.getWindow().getDecorView(),"Tools").performClick());library=libraryMonitor.waitForActivityWithTimeout(10000);removeMonitor(libraryMonitor);assertTrue(library!=null,"Tools restores video library");activity=library;
            before=position();shell("input keyevent 3");Thread.sleep(5500);assertTrue(position()>before+2&&service.isPlaying(),"Audio continues in another app");
            before=position();shell("input keyevent 223");Thread.sleep(5500);assertTrue(position()>before+2&&service.isPlaying(),"Audio continues with screen off");shell("input keyevent 224");shell("wm dismiss-keyguard");
            ui(()->getTargetContext().startService(new Intent(getTargetContext(),YoutubePlaybackService.class).setAction("pause")));awaitPlaying(false);before=position();Thread.sleep(1500);assertTrue(Math.abs(position()-before)<.2,"Notification pause stops audio");
            // A visible activity supplies the user gesture context for playback resumption.
            activity=startActivitySync(new Intent(getTargetContext(),YoutubePlayerActivity.class).putExtra("id","M7lc1UVf-VE").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            ui(()->getTargetContext().startService(new Intent(getTargetContext(),YoutubePlaybackService.class).setAction("play")));awaitPlaying(true);assertTrue(retained==field(service,"web"),"Reopening reuses playback");
            ui(()->service.checkpoint());Thread.sleep(500);assertTrue(store.game.youtubeBookmarks.optJSONObject(store.game.youtubeBookmarks.length()-1).optDouble("seconds")>0,"Position enters main save data");
            ui(()->{for(ActivityManager.AppTask task:getTargetContext().getSystemService(ActivityManager.class).getAppTasks())task.finishAndRemoveTask();});
            for(int n=0;n<50&&YoutubePlaybackService.active!=null;n++)Thread.sleep(100);assertTrue(YoutubePlaybackService.active==null,"Removing app task destroys playback service");
            result.putString("stream",probe+"Passed "+assertions+" video lifecycle assertions\n");code=Activity.RESULT_OK;
        }catch(Throwable e){result.putString("stream",probe+"FAILED: "+e+"\n");}
        finally{try{shell("input keyevent 224");shell("wm dismiss-keyguard");ui(()->{if(YoutubePlaybackService.active!=null)YoutubePlaybackService.active.stopPlayback();if(original!=null)try{GameStore s=((BulletinApplication)getTargetContext().getApplicationContext()).store();s.game.youtubeBookmarks=new JSONArray(original);s.save();}catch(Exception ignored){}getTargetContext().getSharedPreferences("tool-navigation",0).edit().putBoolean("youtube-open",false).putString("youtube-video","").apply();getTargetContext().getSharedPreferences("youtube-checkpoints",0).edit().clear().apply();});}catch(Exception ignored){}}
        finish(code,result);
    }
    private void assertTrue(boolean value,String label){if(!value)throw new AssertionError(label);assertions++;}
    private void awaitPlaying(boolean value)throws Exception{for(int i=0;i<60;i++){if(service.isPlaying()==value)return;Thread.sleep(100);}throw new AssertionError("Playback state expected "+value+": "+service.playbackError());}
    private double position()throws Exception{return Double.parseDouble(js("media.currentTime"));}
    private Object field(Object o,String name)throws Exception{java.lang.reflect.Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}
    private void ui(Runnable r){Throwable[] failure={null};runOnMainSync(()->{try{r.run();}catch(Throwable e){failure[0]=e;}});if(failure[0]!=null)throw new AssertionError(failure[0]);}
    private String js(String script)throws Exception{CountDownLatch done=new CountDownLatch(1);String[] result={null};ui(()->web.evaluateJavascript(script,value->{result[0]=value;done.countDown();}));if(!done.await(10,TimeUnit.SECONDS))throw new AssertionError("Browser response timed out");return result[0];}
    private void shell(String command)throws Exception{try(ParcelFileDescriptor descriptor=getUiAutomation().executeShellCommand(command);java.io.InputStream in=new ParcelFileDescriptor.AutoCloseInputStream(descriptor)){byte[] bytes=new byte[1024];while(in.read(bytes)!=-1){}}}
    private View find(View view,String label){if(view.getContentDescription()!=null&&label.contentEquals(view.getContentDescription()))return view;if(view instanceof android.widget.TextView&&label.contentEquals(((android.widget.TextView)view).getText()))return view;if(view instanceof android.view.ViewGroup){android.view.ViewGroup group=(android.view.ViewGroup)view;for(int i=0;i<group.getChildCount();i++){View found=find(group.getChildAt(i),label);if(found!=null)return found;}}return null;}
}
