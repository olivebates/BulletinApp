package com.bulletin.app;

import org.json.*;
import java.util.*;
import java.util.regex.*;

/** Persistent local state; category identity survives removal of its last child. */
final class NotesModel {
    static final class Page {
        String id="", parent="", title="Untitled", body="", base="", remote="", move="", remoteId="", creationParent="", emoji="";
        int hue=-1;
        boolean category, selected, excluded, hidden, conflict, loaded, unsupported, creating, uploadPending, named=true, collapsed=true;
        String notionId(){return id.startsWith("local-")?remoteId:id;}
        JSONObject json() throws JSONException {
            return new JSONObject().put("id",id).put("parent",parent).put("title",title)
                .put("body",body).put("base",base).put("remote",remote).put("move",move).put("uploadPending",uploadPending)
                .put("category",category).put("selected",selected).put("excluded",excluded)
                .put("hidden",hidden).put("conflict",conflict).put("loaded",loaded).put("unsupported",unsupported)
                .put("remoteId",remoteId).put("creating",creating).put("named",named).put("creationParent",creationParent).put("hue",hue).put("emoji",emoji).put("collapsed",collapsed);
        }
        static Page read(JSONObject j) {
            Page p=new Page(); p.id=j.optString("id");p.parent=j.optString("parent");p.title=j.optString("title","Untitled");
            p.body=j.optString("body");p.base=j.optString("base");p.remote=j.optString("remote");p.move=j.optString("move");p.uploadPending=j.optBoolean("uploadPending");
            p.category=j.optBoolean("category");p.selected=j.optBoolean("selected");p.excluded=j.optBoolean("excluded");
            p.hidden=j.optBoolean("hidden");p.conflict=j.optBoolean("conflict");p.loaded=j.optBoolean("loaded");p.unsupported=j.optBoolean("unsupported");p.remoteId=j.optString("remoteId");p.creating=j.optBoolean("creating");p.named=j.optBoolean("named",true);p.creationParent=j.optString("creationParent");p.hue=j.optInt("hue",-1);p.emoji=j.optString("emoji");p.collapsed=j.optBoolean("collapsed",true);return p;
        }
    }
    final LinkedHashMap<String,Page> pages=new LinkedHashMap<>();
    String parent="", trash="";
    boolean pending(Page p){return (parent.isEmpty()||descendant(p.id,parent)||unbound(p))&&(visible(p)||pendingTrash(p))&&(p.notionId().isEmpty()||!p.named||!p.move.isEmpty()||p.uploadPending||(!p.category&&!p.body.equals(p.base)));}
    boolean hasPending(){for(Page p:pages.values())if(pending(p))return true;return false;}
    boolean visible(Page p) {
        if(p.id.equals(parent)||reserved(p)||(!parent.isEmpty()&&!descendant(p.id,parent)&&!unbound(p)))return false;
        Set<String> seen=new HashSet<>(); boolean chosen=false;
        while(p!=null && seen.add(p.id)) {
            if(p.hidden||p.excluded||p.id.equals(trash)||reserved(p))return false;
            chosen|=p.selected||p.id.equals(parent);p=pages.get(p.parent);
        }
        return chosen;
    }
    private boolean unbound(Page p){Set<String> seen=new HashSet<>();while(p!=null&&seen.add(p.id)){if(p.id.startsWith("local-")&&p.parent.isEmpty())return true;p=pages.get(p.parent);}return false;}
    Page create(String parentId,String title,boolean category){Page p=new Page();p.id="local-"+UUID.randomUUID();p.parent=parentId;p.title=title;p.category=category;p.selected=p.loaded=true;p.named=false;pages.put(p.id,p);Page par=pages.get(parentId);if(par!=null)par.category=true;return p;}
    Page byNotion(String id){for(Page p:pages.values())if(p.notionId().equals(id))return p;return null;}
    String notionParent(Page p){Page par=pages.get(p.parent);return par==null?p.parent:par.notionId();}
    boolean pendingTrash(Page p){Set<String> seen=new HashSet<>();while(p!=null&&seen.add(p.id)){if("TRASH".equals(p.move))return true;p=pages.get(p.parent);}return false;}
    void trash(String id){Page p=pages.get(id);p.move="TRASH";p.hidden=true;}
    void update(Page p,String title,int hue,String emoji){if(!p.title.equals(title)){p.title=title;p.named=false;}if(p.category)p.hue=hue;else p.emoji=emoji;}
    int effectiveHue(Page p,int fallback){Set<String> seen=new HashSet<>();while(p!=null&&seen.add(p.id)){if(p.category&&p.hue>=0)return p.hue;p=pages.get(p.parent);}return fallback;}
    boolean reserved(Page p){return p.title.equals("BulletinBackupCodes")&&p.parent.equals(parent);}
    boolean descendant(String id,String ancestor) {
        Set<String> seen=new HashSet<>();
        while(seen.add(id)){if(id.equals(ancestor))return true;Page p=pages.get(id);if(p==null)return false;id=p.parent;}return false;
    }
    int words(Page p){return words(p,new HashSet<>());}
    private int words(Page p,Set<String> seen){
        if(!seen.add(p.id))return 0;if(!p.category)return wordCount(p.body);
        int n=0;for(Page c:pages.values())if(c.parent.equals(p.id)&&visible(c))n+=words(c,seen);return n;
    }
    static int wordCount(String markdown){
        String plain=markdown.replaceAll("!?\\[([^]]*)\\]\\([^)]*\\)","$1").replaceAll("<[^>]*>"," ")
            .replaceAll("(?m)^\\s*(?:- \\[[ xX]\\] |[-*+] |\\d+\\. |#{1,6} |>)", " ");
        Matcher m=Pattern.compile("[\\p{L}\\p{N}]+(?:['’][\\p{L}\\p{N}]+)*").matcher(plain);int n=0;while(m.find())n++;return n;
    }
    static void reconcile(Page p,String fetched){
        if(!p.loaded||p.body.equals(p.base)||p.body.equals(fetched)){p.body=fetched;p.base=fetched;p.conflict=false;}
        else if(!fetched.equals(p.base)){p.remote=fetched;p.conflict=true;}
        else p.conflict=false;
        p.loaded=true;
    }
    JSONObject json() throws JSONException {JSONArray a=new JSONArray();for(Page p:pages.values())a.put(p.json());return new JSONObject().put("pages",a).put("parent",parent).put("trash",trash);}
    static NotesModel read(String value)throws JSONException {JSONObject j=new JSONObject(value);NotesModel m=new NotesModel();m.parent=j.optString("parent");m.trash=j.optString("trash");JSONArray a=j.optJSONArray("pages");if(a!=null)for(int i=0;i<a.length();i++){Page p=Page.read(a.getJSONObject(i));m.pages.put(p.id,p);}return m;}
}
