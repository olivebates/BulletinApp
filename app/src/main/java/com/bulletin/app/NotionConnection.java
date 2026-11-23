package com.bulletin.app;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.widget.*;
import java.util.*;
import org.json.*;

/** Shared connection flow for Notes and Settings. */
final class NotionConnection {
    static final String INSTRUCTIONS="1) Create a Notion account\n2) Create a document called Bulletin\n3) Create an API Token\nEnable permissions: Read content, Update content, Insert content\n4) Allow access to the Bulletin document\n5) Paste API Token here.";
    static void show(Activity activity,Runnable done){
        NotesStore notes=NotesStore.get(activity);
        Ui ui=new Ui(activity,(((BulletinApplication)activity.getApplication()).store().game.hue+55)%360);
        LinearLayout content=NotesDialogs.column(activity);content.addView(NotesDialogs.label(activity,ui,"Connect Notion"));
        step(activity,ui,content,"1) Create a Notion account","https://www.notion.so/signup");
        content.addView(NotesDialogs.label(activity,ui,"2) Create a document called Bulletin"));
        step(activity,ui,content,"3) Create an API Token","https://www.notion.so/profile/integrations");
        content.addView(NotesDialogs.label(activity,ui,"Enable permissions:\nRead content\nUpdate content\nInsert content"));
        content.addView(NotesDialogs.label(activity,ui,"4) Allow access to the Bulletin document"));
        content.addView(NotesDialogs.label(activity,ui,"5) Paste API Token here"));
        EditText token=NotesDialogs.input(activity,ui,"API Token");token.setInputType(129);content.addView(token,NotesDialogs.field(ui));
        try{if(notes.connected()){token.setText(notes.token());token.selectAll();}}catch(Exception ignored){}
        LinearLayout actions=new LinearLayout(activity);Button cancel=NotesDialogs.button(activity,ui,"Cancel"),connect=NotesDialogs.button(activity,ui,"Connect");
        for(Button button:new Button[]{cancel,connect}){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,ui.dp(48),1);p.setMargins(ui.dp(4),ui.dp(8),ui.dp(4),0);actions.addView(button,p);}content.addView(actions);
        Dialog dialog=NotesDialogs.show(activity,ui,content);cancel.setOnClickListener(v->dialog.dismiss());
        connect.setOnClickListener(v->{String value=token.getText().toString().trim();if(value.isEmpty()){token.setError("Enter an API Token");return;}connect.setEnabled(false);notes.worker.execute(()->{try{
            NotionClient api=new NotionClient(value);api.request("GET","users/me",null);
            String parent;synchronized(notes){parent=notes.model.parent;}boolean accessible=false;
            if(!parent.isEmpty())try{JSONObject page=api.request("GET","pages/"+parent,null);accessible=!page.optBoolean("in_trash")&&!page.optBoolean("archived");}catch(Exception e){if(e.getMessage()==null||(!e.getMessage().contains("Notion 403")&&!e.getMessage().contains("Notion 404")))throw e;}
            notes.token(value);final boolean keep=accessible;
            if(!keep)synchronized(notes){notes.model.parent="";notes.save();}
            activity.runOnUiThread(()->{if(activity.isDestroyed())return;dialog.dismiss();if(keep){notes.sync(null);((BulletinApplication)activity.getApplication()).backups().check();done.run();}else choose(activity,notes,done);});
        }catch(Exception e){activity.runOnUiThread(()->{if(activity.isDestroyed())return;connect.setEnabled(true);token.setError(e.getMessage());});}});});
    }
    private static void step(Activity activity,Ui ui,LinearLayout content,String title,String url){Button link=NotesDialogs.button(activity,ui,title);link.setContentDescription(title+". Open setup link");link.setMinHeight(ui.dp(48));LinearLayout.LayoutParams p=NotesDialogs.field(ui);p.height=-2;content.addView(link,p);link.setOnClickListener(v->{try{activity.startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)));}catch(ActivityNotFoundException e){Toast.makeText(activity,"No browser is available.",Toast.LENGTH_LONG).show();}});}
    private static void choose(Activity activity,NotesStore notes,Runnable done){
        Toast.makeText(activity,"Loading shared Notion documents…",Toast.LENGTH_SHORT).show();notes.worker.execute(()->{try{List<JSONObject> pages=new NotionClient(notes.token()).search();pages.removeIf(p->NotionBackups.PAGE.equals(NotionClient.title(p)));pages.sort(Comparator.comparing(p->!"Bulletin".equals(NotionClient.title(p))));String[] titles=new String[pages.size()];int current=0;for(int i=0;i<pages.size();i++){titles[i]=NotionClient.title(pages.get(i));if(pages.get(i).getString("id").equals(notes.model.parent))current=i;}final int initial=current;
            activity.runOnUiThread(()->{if(activity.isDestroyed())return;if(pages.isEmpty()){Toast.makeText(activity,"Share your Bulletin document with the connection, then try again.",Toast.LENGTH_LONG).show();return;}int[] chosen={initial};new AlertDialog.Builder(activity).setTitle("Select the shared Bulletin document").setSingleChoiceItems(titles,initial,(d,w)->chosen[0]=w).setNegativeButton("Cancel",null).setPositiveButton("Use document",(d,w)->{synchronized(notes){String id=pages.get(chosen[0]).optString("id");notes.model.parent=id;NotesModel.Page p=notes.model.pages.get(id);if(p==null){p=new NotesModel.Page();p.id=id;p.title=NotionClient.title(pages.get(chosen[0]));notes.model.pages.put(id,p);}p.selected=true;p.excluded=false;notes.save();}notes.sync(null);((BulletinApplication)activity.getApplication()).backups().check();done.run();}).show();});
        }catch(Exception e){activity.runOnUiThread(()->{if(!activity.isDestroyed())Toast.makeText(activity,e.getMessage(),Toast.LENGTH_LONG).show();});}});
    }
}

