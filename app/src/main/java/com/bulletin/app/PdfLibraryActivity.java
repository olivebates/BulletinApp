package com.bulletin.app;

import android.app.*;
import android.content.*;
import android.content.res.Configuration;
import android.database.Cursor;
import android.net.Uri;
import android.os.*;
import android.provider.*;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

public final class PdfLibraryActivity extends ToolScreen {
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final List<Document> library=new ArrayList<>();
    private final List<Game.ChoiceList> dragEntries=new ArrayList<>();
    private ToolListReorder drag;
    private boolean arranging,importing;
    private ScrollView scroll;
    private int scrollY;
    private Dialog editor;
    private static final int FILE=31;
    static final class Document {String uri,name,path;long modified;Document(String uri,String name,long modified){this.uri=uri;this.name=name;this.modified=modified;}}
    @Override public void onCreate(Bundle state){super.onCreate(state);if(state!=null){arranging=state.getBoolean("arranging");scrollY=state.getInt("scroll");}readLibrary();build();if(Build.VERSION.SDK_INT>=33)getOnBackInvokedDispatcher().registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,this::back);}
    private void readLibrary(){library.clear();try{JSONArray a=new JSONArray(getSharedPreferences("pdf-library",0).getString("documents","[]"));for(int i=0;i<a.length();i++){JSONObject v=a.getJSONObject(i);Document d=new Document(v.optString("uri"),v.getString("name"),v.optLong("modified"));d.path=v.getString("path");library.add(d);}}catch(JSONException e){message("The PDF library could not be loaded.");}}
    private void saveLibrary(){JSONArray a=new JSONArray();try{for(Document d:library)a.put(new JSONObject().put("uri",d.uri).put("name",d.name).put("path",d.path).put("modified",d.modified));getSharedPreferences("pdf-library",0).edit().putString("documents",a.toString()).apply();}catch(JSONException e){message("Could not save PDF library.");}}
    private void build(){
        if(drag!=null){drag.cancel();drag=null;}
        if(scroll!=null)scrollY=scroll.getScrollY();
        root.removeAllViews();title("PDF Reader");scroll=new ScrollView(this);scroll.setFillViewport(false);scroll.setVerticalScrollBarEnabled(true);if(arranging){scroll.setScrollbarFadingEnabled(false);scroll.setScrollBarSize(ui.dp(30));}
        LinearLayout rows=column();rows.setPadding(ui.dp(8),ui.dp(8),ui.dp(8),0);scroll.addView(rows);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        dragEntries.clear();
        for(Document d:library){
            View entry;
            if(arranging){Game.ChoiceList item=new Game.ChoiceList();item.id=d.path;item.name=d.name;dragEntries.add(item);entry=arrangeCard(d,item);}
            else{Button open=button(d.name);open.setOnClickListener(v->startActivity(new Intent(this,PdfReaderActivity.class).putExtra("path",d.path).putExtra("name",d.name)));open.setOnLongClickListener(v->{arranging=true;build();return true;});entry=open;}
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,ui.dp(56));p.bottomMargin=ui.dp(8);rows.addView(entry,p);
        }
        if(arranging)drag=new ToolListReorder(rows,scroll,root,dragEntries,()->{
            Map<String,Document> byId=new HashMap<>();for(Document d:library)byId.put(d.path,d);library.clear();for(Game.ChoiceList entry:dragEntries){Document d=byId.get(entry.id);if(d!=null)library.add(d);}saveLibrary();build();
        },ui);
        LinearLayout actions=row();Button back=button("Back");action(actions,back);back.setOnClickListener(v->back());
        if(!arranging){Button add=button("Add PDF");action(actions,add);add.setOnClickListener(v->browseFile());}root.addView(actions);addToolNavigation();
        final ScrollView current=scroll;current.post(()->current.scrollTo(0,scrollY));
    }
    @android.annotation.SuppressLint("ClickableViewAccessibility") // The grip routes touch through performClick before dragging.
    private View arrangeCard(Document d,Game.ChoiceList entry){
        LinearLayout card=row();card.setGravity(Gravity.CENTER_VERTICAL);card.setPadding(ui.dp(6),0,ui.dp(6),0);card.setBackground(ui.button(ui.border));card.setTag(entry);
        TextView grip=text("⠿",18);grip.setGravity(Gravity.CENTER);grip.setPadding(0,0,0,0);grip.setTextColor(ui.border);grip.setBackground(ui.panel(ui.border));grip.setContentDescription("Drag PDF: "+d.name);LinearLayout.LayoutParams gripParams=new LinearLayout.LayoutParams(ui.dp(20),ui.dp(40));gripParams.rightMargin=ui.dp(8);card.addView(grip,gripParams);
        TextView name=text(d.name,14);name.setPadding(ui.dp(4),0,ui.dp(4),0);name.setSingleLine(true);name.setEllipsize(android.text.TextUtils.TruncateAt.END);card.addView(name,new LinearLayout.LayoutParams(0,-2,1));
        Button edit=icon("edit","Edit PDF: "+d.name),remove=icon("trash","Delete PDF: "+d.name);card.addView(edit,new LinearLayout.LayoutParams(ui.dp(40),ui.dp(40)));card.addView(remove,new LinearLayout.LayoutParams(ui.dp(40),ui.dp(40)));edit.setOnClickListener(v->rename(d));remove.setOnClickListener(v->confirmRemove(d));
        View.OnLongClickListener begin=v->{boolean started=card.startDragAndDrop(ClipData.newPlainText("pdf",""),new View.DragShadowBuilder(card),card,0);if(started)card.setVisibility(View.INVISIBLE);return started;};card.setOnLongClickListener(begin);grip.setOnClickListener(v->begin.onLongClick(v));
        grip.setOnTouchListener((v,event)->{if(event.getActionMasked()==android.view.MotionEvent.ACTION_DOWN){v.performClick();return true;}return true;});return card;
    }
    private Button icon(String symbol,String description){Button b=button("");Ui.Icon icon=new Ui.Icon(symbol,ui.border);icon.setBounds(0,0,ui.dp(22),ui.dp(22));b.setCompoundDrawables(icon,null,null,null);b.setContentDescription(description);b.setPadding(ui.dp(8),0,ui.dp(8),0);b.setMinHeight(0);b.setMinimumHeight(0);return b;}
    private void rename(Document d){LinearLayout content=column();content.addView(text("Edit PDF",18));EditText name=new EditText(this);name.setSingleLine(true);name.setTextColor(ui.text);name.setHintTextColor(ui.muted);name.setHint("PDF name");name.setContentDescription("PDF name");name.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(200)});name.setText(d.name);name.setSelectAllOnFocus(true);name.setPadding(ui.dp(12),0,ui.dp(12),0);name.setBackground(ui.panel(ui.border));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,ui.dp(56));p.setMargins(ui.dp(8),ui.dp(8),ui.dp(8),ui.dp(8));content.addView(name,p);
        LinearLayout actions=row();Button cancel=button("Cancel"),save=button("Save");action(actions,cancel);action(actions,save);content.addView(actions);Dialog window=dialog(content);cancel.setOnClickListener(v->window.dismiss());save.setOnClickListener(v->{String value=name.getText().toString().trim();if(value.isEmpty()){name.setError("Enter a name");return;}d.name=value;saveLibrary();getSystemService(InputMethodManager.class).hideSoftInputFromWindow(name.getWindowToken(),0);window.dismiss();build();});}
    private void confirmRemove(Document d){LinearLayout content=column();content.addView(text("Remove this PDF from your library?",18));LinearLayout actions=row();Button no=button("Cancel"),yes=button("Remove");action(actions,no);action(actions,yes);content.addView(actions);Dialog window=dialog(content);no.setOnClickListener(v->window.dismiss());yes.setOnClickListener(v->{File file=new File(getFilesDir(),"pdf-library/"+d.path);if(file.exists()&&!file.delete()){message("Could not remove this PDF.");return;}library.remove(d);getSharedPreferences("pdf-positions",0).edit().remove(d.path).apply();saveLibrary();window.dismiss();build();});}
    private Dialog dialog(LinearLayout content){Dialog d=new Dialog(this);editor=d;content.setPadding(ui.dp(12),ui.dp(12),ui.dp(12),ui.dp(12));content.setBackground(ui.panel(ui.border));d.setContentView(content);d.getWindow().setBackgroundDrawableResource(android.R.color.transparent);d.setOnDismissListener(v->{if(editor==d)editor=null;});d.show();fit(d);return d;}
    private void fit(Dialog d){d.getWindow().setLayout(Math.max(ui.dp(200),getResources().getDisplayMetrics().widthPixels-ui.dp(32)),ViewGroup.LayoutParams.WRAP_CONTENT);}
    private void browseFile(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("application/pdf").addCategory(Intent.CATEGORY_OPENABLE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);try{startActivityForResult(i,FILE);}catch(ActivityNotFoundException e){message("No file picker is installed.");}}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request!=FILE||result!=RESULT_OK||data==null||data.getData()==null)return;Uri uri=data.getData();try{getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(SecurityException ignored){}
        String name="Document.pdf";long date=0;try(Cursor c=getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME,DocumentsContract.Document.COLUMN_LAST_MODIFIED},null,null,null)){if(c!=null&&c.moveToFirst()){name=c.getString(0);date=c.getLong(1);}}catch(Exception ignored){}importPdf(new Document(uri.toString(),name,date));}
    private void importPdf(Document d){if(importing)return;for(Document existing:library)if(existing.uri.equals(d.uri))return;importing=true;message("Adding PDF…");worker.execute(()->{File folder=new File(getFilesDir(),"pdf-library");folder.mkdirs();File file=new File(folder,UUID.randomUUID()+".pdf");try(InputStream input=getContentResolver().openInputStream(Uri.parse(d.uri));OutputStream output=new FileOutputStream(file)){if(input==null)throw new IOException();byte[] buffer=new byte[32768];int n;while((n=input.read(buffer))!=-1)output.write(buffer,0,n);}catch(Exception e){file.delete();runOnUiThread(()->{importing=false;message("Could not read this PDF.");});return;}
        try(ParcelFileDescriptor descriptor=ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY);android.graphics.pdf.PdfRenderer renderer=new android.graphics.pdf.PdfRenderer(descriptor)){if(renderer.getPageCount()==0)throw new IOException();}catch(Exception e){file.delete();runOnUiThread(()->{importing=false;message("This PDF is damaged, encrypted, or unsupported.");});return;}
        d.path=file.getName();runOnUiThread(()->{importing=false;library.add(d);saveLibrary();if(!isFinishing()&&!isDestroyed())build();});});}
    private void back(){if(arranging){if(drag!=null)drag.cancel();arranging=false;build();}else finish();}
    @SuppressWarnings("deprecation") @android.annotation.SuppressLint("GestureBackNavigation")
    @Override public void onBackPressed(){back();}
    @Override public void onConfigurationChanged(Configuration config){super.onConfigurationChanged(config);build();if(editor!=null&&editor.isShowing())fit(editor);}
    @Override protected void onSaveInstanceState(Bundle out){out.putBoolean("arranging",arranging);out.putInt("scroll",scroll==null?scrollY:scroll.getScrollY());super.onSaveInstanceState(out);}
    @Override protected void onDestroy(){if(drag!=null)drag.cancel();if(editor!=null)editor.dismiss();worker.shutdown();super.onDestroy();}
}