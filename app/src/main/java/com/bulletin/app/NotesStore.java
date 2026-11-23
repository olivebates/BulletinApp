package com.bulletin.app;

import android.content.*;
import android.security.keystore.*;
import android.util.Base64;
import android.util.AtomicFile;
import org.json.*;
import java.io.*;
import java.security.KeyStore;
import java.util.*;
import java.util.concurrent.*;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;

final class NotesStore {
    // The singleton holds only getApplicationContext(), never an Activity.
    @android.annotation.SuppressLint("StaticFieldLeak")
    private static NotesStore instance;
    static synchronized NotesStore get(Context c){if(instance==null)instance=new NotesStore(c.getApplicationContext());return instance;}
    static synchronized void resetReadOnly(){if(instance!=null)instance.readOnly=false;}
    final Context context;
    final ExecutorService worker=Executors.newSingleThreadExecutor();
    NotesModel model=new NotesModel();
    volatile String status="";
    volatile String schedulingError="";
    volatile boolean busy;
    volatile boolean invalidNotes;
    boolean readOnly;
    private final List<Runnable> syncCallbacks=new ArrayList<>();
    private final AtomicFile file;
    private boolean damaged;
    private NotesStore(Context c){context=c;file=new AtomicFile(new File(c.getFilesDir(),"notes.json"));if(file.getBaseFile().exists())try{model=NotesModel.read(new String(file.readFully(),java.nio.charset.StandardCharsets.UTF_8));}catch(Exception e){damaged=true;status="Notes storage could not be read. Original data has been preserved.";}}
    synchronized void save(){
        if(damaged)throw new IllegalStateException(status);
        FileOutputStream out=null;try{byte[] bytes=model.json().toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);out=file.startWrite();out.write(bytes);file.finishWrite(out);}catch(Exception e){if(out!=null)file.failWrite(out);throw new IllegalStateException("Could not save Notes locally",e);}
    }
    private SecretKey key()throws Exception {KeyStore ks=KeyStore.getInstance("AndroidKeyStore");ks.load(null);if(!ks.containsAlias("notes-notion")){KeyGenerator g=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");g.init(new KeyGenParameterSpec.Builder("notes-notion",KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());g.generateKey();}return (SecretKey)ks.getKey("notes-notion",null);}
    void token(String value)throws Exception {Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.ENCRYPT_MODE,key());String encrypted=Base64.encodeToString(c.getIV(),Base64.NO_WRAP)+":"+Base64.encodeToString(c.doFinal(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)),Base64.NO_WRAP);if(!context.getSharedPreferences("notes-auth",0).edit().putString("token",encrypted).commit())throw new IOException("Could not store connection");}
    String token()throws Exception {String value=context.getSharedPreferences("notes-auth",0).getString("token","");if(value.isEmpty())return "";String[] a=value.split(":");Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,Base64.decode(a[0],Base64.NO_WRAP)));return new String(c.doFinal(Base64.decode(a[1],Base64.NO_WRAP)),java.nio.charset.StandardCharsets.UTF_8);}
    boolean connected(){return context.getSharedPreferences("notes-auth",0).contains("token");}
    synchronized boolean canUpload() {
        return !damaged && connected() && !model.parent.isEmpty() && model.hasPending();
    }

    synchronized String displayStatus() {
        if (damaged) return status;
        if (!connected()) return "Connect to Notion using Sync";
        if (model.parent.isEmpty()) return "Choose a Notion destination in Settings.";
        if (busy) return "Uploading notes to Notion...";
        if (!schedulingError.isEmpty() && model.hasPending()) return schedulingError;
        if (!status.isEmpty() && !status.equals("Synced")) return status;
        if (!model.hasPending()) return "";
        if (NotesSyncJob.isQueued(context)) {
            return NotesSyncJob.isOnline(context)
                ? "Notes upload queued." : "Notes upload waiting for internet.";
        }
        return "Notes changes saved. Upload when you leave Tools.";
    }
    synchronized void edit(String id,String body){NotesModel.Page p=model.pages.get(id);p.body=body;save();}
    void sync(Runnable done){
        synchronized(this){if(damaged||model.parent.isEmpty()||!model.hasPending()){if(done!=null)done.run();return;}if(done!=null)syncCallbacks.add(done);if(busy)return;busy=true;invalidNotes=false;}
        worker.execute(()->{try{String token=token();if(token.isEmpty())return;status="Syncing…";NotionClient api=new NotionClient(token);syncWith(api);status="Synced";}
            catch(NotesSync.InvalidNotes e){invalidNotes=true;status=e.getMessage();}
            catch(Exception e){status=e.getMessage()==null?"Sync failed; local changes are saved":e.getMessage();}
            finally{List<Runnable> callbacks;synchronized(this){busy=false;callbacks=new ArrayList<>(syncCallbacks);syncCallbacks.clear();}for(Runnable callback:callbacks)callback.run();}});
    }
    private void syncWith(NotionClient api)throws Exception {new NotesSync(model,this,this::save).run(api);}
    synchronized NotesModel.Page create(String parent,String title,boolean category){NotesModel.Page p=model.create(parent,title,category);save();return p;}
    synchronized void move(String id,String parent){if(model.descendant(parent,id))throw new IllegalArgumentException("A Category cannot be moved inside itself.");NotesModel.Page p=model.pages.get(id);p.parent=parent;p.move=parent.isEmpty()?model.parent:parent;p.selected=true;save();}
    synchronized void trash(String id){model.trash(id);save();status="Move to Trash pending sync";}
    synchronized void update(NotesModel.Page p,String title,int hue,String emoji){model.update(p,title,hue,emoji);save();}
    synchronized void setCollapsed(String id,boolean collapsed){NotesModel.Page p=model.pages.get(id);if(p!=null&&p.category){p.collapsed=collapsed;save();}}
    synchronized void rename(NotesModel.Page p,String title){if(!p.title.equals(title)){model.update(p,title,p.hue,p.emoji);save();}}
}

