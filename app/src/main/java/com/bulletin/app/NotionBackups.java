package com.bulletin.app;

import android.app.*;
import android.os.*;
import android.content.*;
import org.json.*;
import java.util.*;
import java.io.IOException;
import java.time.*;
import java.time.format.DateTimeFormatter;

/** Runs only while foregrounded. Network work shares the serialized Notes executor. */
final class NotionBackups {
    static final long INTERVAL=4*60*60*1000L;
    static final int KEEP=20;
    static final String PAGE="BulletinBackupCodes", PREFIX="Bulletin backup | ";
    final BulletinApplication app;
    final Handler main=new Handler(Looper.getMainLooper());
    boolean running;
    int started;
    String status="";
    NotionBackups(BulletinApplication app){this.app=app;}
    static boolean due(long now,long last){return last==0||now-last>=INTERVAL;}
    void start(){started++;main.removeCallbacks(tick);main.post(tick);}
    void stop(){started=Math.max(0,started-1);if(started==0)main.removeCallbacks(tick);}
    private final Runnable tick=new Runnable(){public void run(){if(started==0)return;check();main.postDelayed(this,60000);}};
    void check(){NotesStore notes=NotesStore.get(app);long last=app.getSharedPreferences("notion-backups",0).getLong("last-"+notes.model.parent,0);if(notes.connected()&&!notes.model.parent.isEmpty()&&due(System.currentTimeMillis(),last)&&!running)backup("",null);}
    interface Result {void done(Exception error);}
    void backup(String protect,Result callback){
        if(running){if(callback!=null)callback.done(new IOException("A backup is already running. Try again shortly."));return;}
        String snapshot;try{snapshot=app.store().exportSave();}catch(Exception e){if(callback!=null)callback.done(e);return;}
        running=true;status="Backing up…";
        NotesStore notes=NotesStore.get(app);
        String root; synchronized(notes){root=notes.model.parent;}
        notes.worker.execute(()->{Exception error=null;try{NotionClient api=new NotionClient(notes.token());String page=page(api,root);long now=System.currentTimeMillis();String title=PREFIX+now+" | "+UUID.randomUUID();
            JSONArray shell=new JSONArray().put(block("toggle",new JSONObject().put("rich_text",rich("Uploading | "+title))));
            String id=api.request("PATCH","blocks/"+page+"/children",new JSONObject().put("children",shell)).getJSONArray("results").getJSONObject(0).getString("id");
            for(int offset=0;offset<snapshot.length();){JSONArray batch=new JSONArray();for(int n=0;n<50&&offset<snapshot.length();n++){int end=Math.min(snapshot.length(),offset+1800);if(end<snapshot.length()&&Character.isHighSurrogate(snapshot.charAt(end-1)))end--;batch.put(block("code",new JSONObject().put("language","plain text").put("rich_text",rich(snapshot.substring(offset,end)))));offset=end;}api.request("PATCH","blocks/"+id+"/children",new JSONObject().put("children",batch));}
            String verified=read(api,id);if(!snapshot.equals(verified))throw new IOException("Backup verification failed; previous backups were kept.");
            api.request("PATCH","blocks/"+id,new JSONObject().put("toggle",new JSONObject().put("rich_text",rich(title))));
            // Prune only after a complete, read-back-verified backup exists.
            List<Entry> all=list(api,page);for(Entry old:prune(all,protect))api.request("DELETE","blocks/"+old.id,null);
            if(!app.getSharedPreferences("notion-backups",0).edit().putLong("last-"+root,now).commit())throw new IOException("Could not save backup timestamp");status="Backed up";
        }catch(Exception e){error=e;status="Backup failed: "+e.getMessage();}Exception result=error;main.post(()->{running=false;if(callback!=null)callback.done(result);});});
    }
    static JSONObject block(String type,JSONObject value)throws Exception{return new JSONObject().put("object","block").put("type",type).put(type,value);}
    static JSONArray rich(String value)throws Exception{return new JSONArray().put(new JSONObject().put("type","text").put("text",new JSONObject().put("content",value)));}
    static String plain(JSONArray a){StringBuilder s=new StringBuilder();if(a!=null)for(int i=0;i<a.length();i++){JSONObject r=a.optJSONObject(i);JSONObject t=r.optJSONObject("text");s.append(t==null?r.optString("plain_text"):t.optString("content"));}return s.toString();}
    static final class Entry {final String id;final long date;Entry(String id,long date){this.id=id;this.date=date;}String label(){return Instant.ofEpochMilli(date).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));}}
    static List<Entry> prune(List<Entry> entries,String protectedId){List<Entry> sorted=new ArrayList<>(entries);sorted.sort(Comparator.comparingLong(e->e.date));List<Entry> remove=new ArrayList<>();int left=sorted.size();for(Entry e:sorted)if(left>KEEP&&!e.id.equals(protectedId)){remove.add(e);left--;}return remove;}
    static List<Entry> list(NotionClient api,String page)throws Exception{List<Entry> list=new ArrayList<>();for(JSONObject b:api.blocks(page)){JSONObject toggle=b.optJSONObject("toggle");if(toggle==null)continue;String title=plain(toggle.optJSONArray("rich_text"));if(!title.startsWith(PREFIX))continue;try{long date=Long.parseLong(title.substring(PREFIX.length()).split(" \\| ")[0]);list.add(new Entry(b.getString("id"),date));}catch(NumberFormatException ignored){}}list.sort((a,b)->Long.compare(b.date,a.date));return list;}
    static String read(NotionClient api,String id)throws Exception{StringBuilder s=new StringBuilder();for(JSONObject b:api.blocks(id)){JSONObject code=b.optJSONObject("code");if(code!=null)s.append(plain(code.optJSONArray("rich_text")));}if(s.length()==0)throw new IOException("The backup is empty");return s.toString();}
    static String page(NotionClient api,NotesStore notes)throws Exception{String parent;synchronized(notes){parent=notes.model.parent;}return page(api,parent);}
    static String page(NotionClient api,String parent)throws Exception{if(parent.isEmpty())throw new IOException("Choose the Bulletin document first.");for(JSONObject b:api.blocks(parent))if("child_page".equals(b.optString("type"))&&PAGE.equals(b.getJSONObject("child_page").optString("title")))return b.getString("id");return api.create(parent,PAGE);}
}
