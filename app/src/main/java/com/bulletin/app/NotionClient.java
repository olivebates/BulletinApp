package com.bulletin.app;

import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Small, paginated REST client. No credentials are logged or bundled in the APK. */
class NotionClient {
    final String token;
    private long lastRequest;
    NotionClient(String token){this.token=token;}
    JSONObject request(String method,String path,JSONObject body)throws Exception {
        long delay=350-(System.currentTimeMillis()-lastRequest);if(delay>0)Thread.sleep(delay);lastRequest=System.currentTimeMillis();
        HttpURLConnection c=(HttpURLConnection)new URL("https://api.notion.com/v1/"+path).openConnection();
        c.setConnectTimeout(15000);c.setReadTimeout(45000);c.setInstanceFollowRedirects(false);
        c.setRequestMethod(method);c.setRequestProperty("Authorization","Bearer "+token);
        c.setRequestProperty("Notion-Version","2026-03-11");c.setRequestProperty("Content-Type","application/json");
        try {
            if(body!=null){c.setDoOutput(true);try(OutputStream out=c.getOutputStream()){out.write(body.toString().getBytes(StandardCharsets.UTF_8));}}
            int status=c.getResponseCode();InputStream stream=status<400?c.getInputStream():c.getErrorStream();
            String value="";if(stream!=null)try(InputStream in=stream;ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);value=out.toString("UTF-8");}
            if(status>=400)throw new IOException("Notion "+status+": "+(status==401?"Check your connection token.":status==403?"Share the page with your connection and allow read, insert and update content.":status==429?"Rate limit reached; changes will retry later.":"Request failed; local changes are saved."));
            return new JSONObject(value);
        } finally {c.disconnect();}
    }
    List<JSONObject> search()throws Exception {
        List<JSONObject> result=new ArrayList<>();String cursor="";
        do {JSONObject q=new JSONObject().put("page_size",100).put("filter",new JSONObject().put("value","page").put("property","object"));if(!cursor.isEmpty())q.put("start_cursor",cursor);
            JSONObject r=request("POST","search",q);JSONArray a=r.getJSONArray("results");for(int i=0;i<a.length();i++)result.add(a.getJSONObject(i));cursor=r.optBoolean("has_more")?r.getString("next_cursor"):"";
        }while(!cursor.isEmpty());return result;
    }
    List<String> children(String id)throws Exception {
        List<String> ids=new ArrayList<>();String cursor="";
        do {JSONObject r=request("GET","blocks/"+id+"/children?page_size=100"+(cursor.isEmpty()?"":"&start_cursor="+cursor),null);
            JSONArray a=r.getJSONArray("results");for(int i=0;i<a.length();i++){JSONObject b=a.getJSONObject(i);if("child_page".equals(b.optString("type")))ids.add(b.getString("id"));else if(b.optBoolean("has_children")&&!"child_database".equals(b.optString("type")))ids.addAll(children(b.getString("id")));}
            cursor=r.optBoolean("has_more")?r.getString("next_cursor"):"";
        }while(!cursor.isEmpty());return ids;
    }
    JSONObject markdown(String id)throws Exception{return request("GET","pages/"+id+"/markdown",null);}
    JSONObject replace(String id,String markdown)throws Exception{return request("PATCH","pages/"+id+"/markdown",new JSONObject().put("type","replace_content").put("replace_content",new JSONObject().put("new_str",markdown.replace("<span color=\"beige\">","<span color=\"brown\">"))));}
    List<JSONObject> blocks(String id)throws Exception {
        List<JSONObject> result=new ArrayList<>();String cursor="";
        do{JSONObject r=request("GET","blocks/"+id+"/children?page_size=100"+(cursor.isEmpty()?"":"&start_cursor="+cursor),null);JSONArray a=r.getJSONArray("results");for(int i=0;i<a.length();i++)result.add(a.getJSONObject(i));cursor=r.optBoolean("has_more")?r.getString("next_cursor"):"";}while(!cursor.isEmpty());return result;
    }
    JSONObject write(String id,String before,String after)throws Exception {
        JSONObject q=new JSONObject();
        if(before.isEmpty())q.put("type","insert_content").put("insert_content",new JSONObject().put("content",after));
        else q.put("type","update_content").put("update_content",new JSONObject().put("content_updates",new JSONArray().put(new JSONObject().put("old_str",before).put("new_str",after))));
        return request("PATCH","pages/"+id+"/markdown",q);
    }
    String create(String parent,String title)throws Exception {
        JSONObject q=new JSONObject().put("parent",new JSONObject().put("page_id",parent)).put("properties",new JSONObject().put("title",new JSONObject().put("type","title").put("title",new JSONArray().put(new JSONObject().put("type","text").put("text",new JSONObject().put("content",title))))));
        return request("POST","pages",q).getString("id");
    }
    void move(String id,String parent)throws Exception{request("POST","pages/"+id+"/move",new JSONObject().put("parent",new JSONObject().put("type","page_id").put("page_id",parent)));}
    void rename(String id,String title)throws Exception{request("PATCH","pages/"+id,new JSONObject().put("properties",new JSONObject().put("title",new JSONObject().put("title",new JSONArray().put(new JSONObject().put("text",new JSONObject().put("content",title)))))));}
    static String title(JSONObject p){JSONObject props=p.optJSONObject("properties");if(props!=null)for(Iterator<String> it=props.keys();it.hasNext();){JSONObject v=props.optJSONObject(it.next());if(v!=null&&"title".equals(v.optString("type"))){JSONArray a=v.optJSONArray("title");StringBuilder s=new StringBuilder();if(a!=null)for(int i=0;i<a.length();i++)s.append(a.optJSONObject(i).optString("plain_text"));return s.length()==0?"Untitled":s.toString();}}return "Untitled";}
}
