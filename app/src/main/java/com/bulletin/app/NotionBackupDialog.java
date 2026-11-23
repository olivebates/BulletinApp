package com.bulletin.app;

import android.app.*;
import android.widget.*;
import java.util.*;
import java.util.function.Consumer;

final class NotionBackupDialog {
    static void show(Activity activity,Consumer<String> restore){
        NotesStore notes=NotesStore.get(activity);if(!notes.connected()||notes.model.parent.isEmpty()){NotionConnection.show(activity,()->show(activity,restore));return;}
        Toast.makeText(activity,"Loading backups…",Toast.LENGTH_SHORT).show();notes.worker.execute(()->{try{NotionClient api=new NotionClient(notes.token());List<NotionBackups.Entry> entries=NotionBackups.list(api,NotionBackups.page(api,notes));String[] titles=new String[entries.size()];for(int i=0;i<entries.size();i++)titles[i]=entries.get(i).label();activity.runOnUiThread(()->{if(activity.isDestroyed())return;if(entries.isEmpty()){new AlertDialog.Builder(activity).setTitle("Restore backup").setMessage("No backups are available yet.").setPositiveButton("Close",null).show();return;}
            LinearLayout buttons=new LinearLayout(activity);buttons.setOrientation(LinearLayout.VERTICAL);ScrollView scroll=new ScrollView(activity);scroll.addView(buttons);AlertDialog list=new AlertDialog.Builder(activity).setTitle("Restore backup").setView(scroll).setNegativeButton("Close",null).create();for(int i=0;i<entries.size();i++){NotionBackups.Entry entry=entries.get(i);Button button=new Button(activity);button.setAllCaps(false);button.setText(titles[i]);buttons.addView(button);button.setOnClickListener(v->new AlertDialog.Builder(activity).setTitle("Restore "+entry.label()+"?").setMessage("A new backup of your current progress will be saved first. Then this backup will replace your current app save. Previous backups remain available, up to the latest 20.").setNegativeButton("Cancel",null).setPositiveButton("Restore",(d,w)->{list.dismiss();load(activity,entry,restore);}).show());}list.show();});
        }catch(Exception e){error(activity,e);}});
    }
    private static void load(Activity activity,NotionBackups.Entry entry,Consumer<String> restore){
        NotesStore notes=NotesStore.get(activity);
        AlertDialog progress=new AlertDialog.Builder(activity).setTitle("Preparing restore").setMessage("Validating the selected save and uploading a safety backup…").setCancelable(false).show();
        notes.worker.execute(()->{try{String saved=NotionBackups.read(new NotionClient(notes.token()),entry.id);SaveImports.decode(saved,System.currentTimeMillis(),java.time.ZoneId.systemDefault());activity.runOnUiThread(()->{if(activity.isDestroyed())return;((BulletinApplication)activity.getApplication()).backups().backup(entry.id,error->{if(activity.isDestroyed())return;progress.dismiss();if(error!=null){error(activity,error);return;}restore.accept(saved);});});}catch(Exception e){activity.runOnUiThread(()->{if(!activity.isDestroyed()){progress.dismiss();error(activity,e);}});}});
    }
    private static void error(Activity activity,Exception e){activity.runOnUiThread(()->{if(!activity.isDestroyed())new AlertDialog.Builder(activity).setTitle("Notion backup").setMessage(e.getMessage()).setPositiveButton("Close",null).show();});}
}

