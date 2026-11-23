package com.bulletin.app;

import android.app.*;
import android.content.*;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.net.*;
import java.util.*;

/** PDF-style bookmark library. All entries and playback positions live in the app save. */
public final class YoutubeLibraryActivity extends ToolScreen {
    private boolean arranging;
    private ScrollView scroll;
    private int scrollY;
    private ToolListReorder drag;
    private final List<Game.ChoiceList> entries=new ArrayList<>();
    private GameStore store(){return ((BulletinApplication)getApplication()).store();}
    @android.annotation.SuppressLint("ClickableViewAccessibility")
    @Override public void onCreate(Bundle state){super.onCreate(state);scrollY=getSharedPreferences("tool-navigation",0).getInt("youtube-scroll",0);if(state!=null)scrollY=state.getInt("scroll");build();if(android.os.Build.VERSION.SDK_INT>=33)getOnBackInvokedDispatcher().registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,this::back);}
    static String videoId(String link){
        try{URI uri=new URI(link.trim());String host=uri.getHost();if(host==null||!"https".equalsIgnoreCase(uri.getScheme()))return "";String id="";if(host.equalsIgnoreCase("youtu.be"))id=uri.getPath().substring(1);else if(host.equalsIgnoreCase("youtube.com")||host.equalsIgnoreCase("www.youtube.com")||host.equalsIgnoreCase("m.youtube.com")){String path=uri.getPath();if(path.startsWith("/shorts/")||path.startsWith("/embed/")||path.startsWith("/live/"))id=path.substring(path.lastIndexOf('/')+1);else if("/watch".equals(path)&&uri.getRawQuery()!=null)for(String pair:uri.getRawQuery().split("&"))if(pair.startsWith("v="))id=pair.substring(2);}return id.matches("[A-Za-z0-9_-]{11}")?id:"";}catch(Exception e){return "";}
    }
    private void build(){
        if(drag!=null){drag.cancel();drag=null;}if(scroll!=null)scrollY=scroll.getScrollY();root.removeAllViews();title("Youtube Bookmark");scroll=new ScrollView(this);scroll.setVerticalScrollBarEnabled(true);if(arranging){scroll.setScrollbarFadingEnabled(false);scroll.setScrollBarSize(ui.dp(30));}LinearLayout rows=column();rows.setPadding(ui.dp(8),ui.dp(8),ui.dp(8),0);scroll.addView(rows);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));entries.clear();
        JSONArray videos=store().game.youtubeBookmarks;for(int i=0;i<videos.length();i++){JSONObject video=videos.optJSONObject(i);if(video==null)continue;String id=video.optString("id");View entry;
            if(arranging){Game.ChoiceList item=new Game.ChoiceList();item.id=id;item.name=video.optString("name");entries.add(item);LinearLayout card=row();card.setGravity(Gravity.CENTER_VERTICAL);card.setPadding(ui.dp(6),0,ui.dp(6),0);card.setBackground(ui.button(ui.border));card.setTag(item);TextView grip=text("⠿",18);grip.setGravity(Gravity.CENTER);grip.setContentDescription("Drag video: "+item.name);card.addView(grip,new LinearLayout.LayoutParams(ui.dp(28),-1));TextView name=text(item.name,14);name.setSingleLine(true);name.setEllipsize(android.text.TextUtils.TruncateAt.END);card.addView(name,new LinearLayout.LayoutParams(0,-2,1));Button edit=icon("edit","Rename video"),remove=icon("trash","Remove video");edit.setContentDescription("Rename video");remove.setContentDescription("Remove video");card.addView(edit,new LinearLayout.LayoutParams(ui.dp(40),ui.dp(40)));card.addView(remove,new LinearLayout.LayoutParams(ui.dp(40),ui.dp(40)));edit.setOnClickListener(v->rename(video));remove.setOnClickListener(v->new AlertDialog.Builder(this).setMessage("Remove this bookmark?").setNegativeButton("Cancel",null).setPositiveButton("Remove",(d,w)->remove(id)).show());View.OnLongClickListener begin=v->{boolean started=card.startDragAndDrop(ClipData.newPlainText("youtube",""),new View.DragShadowBuilder(card),card,0);if(started)card.setVisibility(View.INVISIBLE);return started;};card.setOnLongClickListener(begin);grip.setOnClickListener(v->begin.onLongClick(v));grip.setOnTouchListener((v,event)->{if(event.getActionMasked()==MotionEvent.ACTION_DOWN)v.performClick();return true;});entry=card;
            }else{Button open=button(video.optString("name"));open.setOnClickListener(v->startActivity(new Intent(this,YoutubePlayerActivity.class).putExtra("id",id)));open.setOnLongClickListener(v->{arranging=true;build();return true;});entry=open;}
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,ui.dp(56));lp.bottomMargin=ui.dp(8);rows.addView(entry,lp);
        }
        if(arranging)drag=new ToolListReorder(rows,scroll,root,entries,()->{JSONArray sorted=new JSONArray();for(Game.ChoiceList item:entries)for(int i=0;i<videos.length();i++)if(item.id.equals(videos.optJSONObject(i).optString("id")))sorted.put(videos.optJSONObject(i));store().game.youtubeBookmarks=sorted;store().save();build();},ui);
        LinearLayout actions=row();Button back=button("Back");action(actions,back);back.setOnClickListener(v->back());if(!arranging){Button add=button("Add Video");action(actions,add);add.setOnClickListener(v->add());}root.addView(actions);addToolNavigation();scroll.post(()->scroll.scrollTo(0,scrollY));
    }
    private Button icon(String symbol,String description){Button b=button("");Ui.Icon icon=new Ui.Icon(symbol,ui.border);icon.setBounds(0,0,ui.dp(22),ui.dp(22));b.setCompoundDrawables(icon,null,null,null);b.setContentDescription(description);b.setPadding(ui.dp(8),0,ui.dp(8),0);b.setMinHeight(0);b.setMinimumHeight(0);return b;}
    private void remove(String id){if(YoutubePlaybackService.active!=null&&id.equals(YoutubePlaybackService.active.videoId()))YoutubePlaybackService.active.stopPlayback();JSONArray old=store().game.youtubeBookmarks,next=new JSONArray();for(int i=0;i<old.length();i++)if(!old.optJSONObject(i).optString("id").equals(id))next.put(old.optJSONObject(i));store().game.youtubeBookmarks=next;store().save();build();}
    private void rename(JSONObject video){LinearLayout content=column();content.addView(text("Edit Video",18));EditText input=NotesDialogs.input(this,ui,"Video name");input.setText(video.optString("name"));content.addView(input,NotesDialogs.field(ui));LinearLayout actions=row();Button cancel=button("Cancel"),save=button("Save");action(actions,cancel);action(actions,save);content.addView(actions);Dialog dialog=NotesDialogs.show(this,ui,content);cancel.setOnClickListener(v->dialog.dismiss());save.setOnClickListener(v->{String name=input.getText().toString().trim();if(name.isEmpty()){input.setError("Enter a name");return;}try{video.put("name",name);store().save();dialog.dismiss();build();}catch(JSONException e){message("Could not rename video");}});}
    private void add(){LinearLayout content=column();content.addView(text("Add YouTube Video",18));EditText input=NotesDialogs.input(this,ui,"YouTube link");input.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_URI);content.addView(input,NotesDialogs.field(ui));LinearLayout actions=row();Button cancel=button("Cancel"),save=button("Add");action(actions,cancel);action(actions,save);content.addView(actions);Dialog dialog=NotesDialogs.show(this,ui,content);cancel.setOnClickListener(v->dialog.dismiss());save.setOnClickListener(v->{String id=videoId(input.getText().toString());if(id.isEmpty()){input.setError("Enter a valid HTTPS YouTube video link");return;}for(int i=0;i<store().game.youtubeBookmarks.length();i++)if(id.equals(store().game.youtubeBookmarks.optJSONObject(i).optString("id"))){input.setError("Already bookmarked");return;}save.setEnabled(false);new Thread(()->{String title="YouTube "+id;try{HttpURLConnection connection=(HttpURLConnection)new URL("https://www.youtube.com/oembed?url=https://www.youtube.com/watch?v="+id+"&format=json").openConnection();connection.setConnectTimeout(10000);connection.setReadTimeout(10000);try(java.io.InputStream in=connection.getInputStream();java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream()){byte[] bytes=new byte[4096];int n;while((n=in.read(bytes))!=-1)out.write(bytes,0,n);title=new JSONObject(out.toString("UTF-8")).getString("title");}finally{connection.disconnect();}}catch(Exception ignored){}final String name=title;runOnUiThread(()->{try{store().game.youtubeBookmarks.put(new JSONObject().put("id",id).put("name",name).put("seconds",0));store().save();dialog.dismiss();if(!isDestroyed())build();}catch(JSONException e){save.setEnabled(true);message("Could not save bookmark");}});},"YouTube-title").start();});}
    private void back(){if(arranging){arranging=false;build();}else {getSharedPreferences("tool-navigation",0).edit().putBoolean("youtube-open",false).apply();finish();}}
    @SuppressWarnings("deprecation") @android.annotation.SuppressLint("GestureBackNavigation")
    @Override public void onBackPressed(){back();}
    @Override protected void onResume(){super.onResume();getSharedPreferences("tool-navigation",0).edit().putBoolean("youtube-open",true).putBoolean("notes-open",false).putString("youtube-video","").apply();}
    @Override protected void onPause(){if(scroll!=null)getSharedPreferences("tool-navigation",0).edit().putInt("youtube-scroll",scroll.getScrollY()).apply();super.onPause();}
    @Override protected void onSaveInstanceState(Bundle out){out.putInt("scroll",scroll.getScrollY());super.onSaveInstanceState(out);}
    @Override public void onConfigurationChanged(android.content.res.Configuration config){super.onConfigurationChanged(config);build();}
    @Override protected void onDestroy(){if(drag!=null)drag.cancel();super.onDestroy();}
}


