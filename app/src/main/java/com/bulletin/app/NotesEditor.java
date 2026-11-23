package com.bulletin.app;

import android.annotation.SuppressLint;
import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.webkit.*;
import android.widget.EditText;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;

/** Local-only rich text surface. The bridge is never exposed to a remote page. */
@SuppressLint("ViewConstructor")
final class NotesEditor extends WebView {
    interface Listener {void changed(String markdown);}
    private final Activity activity;
    private final Listener listener;
    private final String id;
    private final Ui ui;
    private String markdown;
    private int position,scroll;
    private boolean ready,enabled;
    private int pendingBeige=-1;
    void toggleFind(){if(ready)evaluateJavascript("toggleFind()",null);}
    void jumpBeige(int index){pendingBeige=index;if(ready)evaluateJavascript("jumpBeige("+index+")",null);}
    @SuppressLint({"SetJavaScriptEnabled","AddJavascriptInterface"})
    NotesEditor(Activity activity,Ui ui,String id,String markdown,boolean enabled,Listener listener){
        super(activity);this.activity=activity;this.ui=ui;this.id=id;this.markdown=markdown;this.enabled=enabled;this.listener=listener;
        position=activity.getSharedPreferences("notes-editor",0).getInt(id+"-cursor",0);scroll=activity.getSharedPreferences("notes-editor",0).getInt(id+"-scroll",0);
        setBackgroundColor(Color.TRANSPARENT);setContentDescription("Document editor");
        setOnScrollChangeListener((v,x,y,ox,oy)->savePosition(position,y));
        getSettings().setJavaScriptEnabled(true);getSettings().setAllowFileAccess(false);getSettings().setAllowContentAccess(false);getSettings().setBlockNetworkLoads(true);getSettings().setCacheMode(android.webkit.WebSettings.LOAD_NO_CACHE);
        setWebViewClient(new WebViewClient(){@Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request){return true;}});
        setWebChromeClient(new WebChromeClient(){@Override public boolean onJsPrompt(WebView view,String url,String message,String initial,JsPromptResult result){EditText input=new EditText(activity);input.setSingleLine(true);input.setText(initial);input.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_URI);new AlertDialog.Builder(activity).setTitle(message).setView(input).setNegativeButton("Cancel",(d,w)->result.cancel()).setPositiveButton("Apply",(d,w)->result.confirm(input.getText().toString())).setOnCancelListener(d->result.cancel()).show();return true;}});
        addJavascriptInterface(new Bridge(),"Native");
        try(java.io.InputStream in=activity.getAssets().open("notes-editor.html");java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream()){byte[] bytes=new byte[8192];int count;while((count=in.read(bytes))!=-1)out.write(bytes,0,count);loadDataWithBaseURL("https://bulletin.local/",out.toString("UTF-8"),"text/html","UTF-8",null);}catch(Exception e){throw new IllegalStateException("Could not open the Notes editor",e);}
    }
    private String color(int c){return String.format(java.util.Locale.ROOT,"#%06x",c&0xffffff);}
    private void load(){evaluateJavascript("loadDocument("+JSONObject.quote(markdown)+","+position+","+scroll+","+enabled+","+JSONObject.quote(color(ui.text))+","+JSONObject.quote(color(ui.background))+","+JSONObject.quote(color(ui.border))+")",null);}
    private void applyDisplaySize(){int size=activity.getSharedPreferences("notes-editor",0).getInt("font-size",18);evaluateJavascript("setDisplayFontSize("+Math.max(14,Math.min(30,size))+",false)",null);}
    void remote(String value,boolean editable){
        enabled=editable;
        if(!markdown.equals(value)){markdown=value;if(ready)evaluateJavascript("replaceDocument("+JSONObject.quote(value)+")",null);}
        if(ready)evaluateJavascript("setEditable("+editable+")",null);
    }
    void flush(){if(ready)evaluateJavascript("flush()",null);}
    private void savePosition(int cursor,int y){position=cursor;scroll=y;activity.getSharedPreferences("notes-editor",0).edit().putInt(id+"-cursor",cursor).putInt(id+"-scroll",y).apply();}
    private final class Bridge {
        @JavascriptInterface public void ready(){activity.runOnUiThread(()->{ready=true;load();applyDisplaySize();if(pendingBeige>=0)evaluateJavascript("setTimeout(()=>jumpBeige("+pendingBeige+"),150)",null);});}
        @JavascriptInterface public void changed(String value,int cursor,int y){activity.runOnUiThread(()->{savePosition(cursor,y);if(!markdown.equals(value)){markdown=value;listener.changed(value);}});}
        @JavascriptInterface public void fontSizeChanged(int size){activity.runOnUiThread(()->activity.getSharedPreferences("notes-editor",0).edit().putInt("font-size",Math.max(14,Math.min(30,size))).apply());}
        @JavascriptInterface public void position(int cursor,int y){activity.runOnUiThread(()->savePosition(cursor,y));}
    }
}
