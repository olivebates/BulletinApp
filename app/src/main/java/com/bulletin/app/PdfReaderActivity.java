package com.bulletin.app;
import android.os.*;
import android.view.*;
import android.widget.*;
import android.graphics.drawable.GradientDrawable;
import org.json.*;
import java.io.File;
public final class PdfReaderActivity extends ToolScreen {
    private PdfCanvas document;private Button back;private String documentKey;private boolean backVisible;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable hide=this::hideBack;
    private final Runnable save=()->savePosition(false);
    @Override public void onCreate(Bundle state){super.onCreate(state);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        if(Build.VERSION.SDK_INT>=30){getWindow().setDecorFitsSystemWindows(false);getWindow().getInsetsController().hide(WindowInsets.Type.systemBars());getWindow().getInsetsController().setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);}
        FrameLayout frame=new FrameLayout(this);frame.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);frame.setBackgroundColor(ui.background);setContentView(frame);
        String path=getIntent().getStringExtra("path");if(path==null||!new File(path).getName().equals(path)){finish();return;}documentKey=path;
        Bundle position=state==null?null:state.getBundle("position");if(position==null)position=loadPosition();
        document=new PdfCanvas(this,new File(getFilesDir(),"pdf-library/"+path),position,this::showBack,this::hideBack,this::positionChanged);frame.addView(document,new FrameLayout.LayoutParams(-1,-1));
        back=new Button(this);back.setText("Back");back.setTextSize(12);back.setAllCaps(false);back.setTextColor(ui.text);back.setPadding(ui.dp(10),0,ui.dp(10),0);back.setMinimumWidth(0);back.setMinWidth(0);back.setMinimumHeight(0);back.setMinHeight(0);GradientDrawable outline=new GradientDrawable();outline.setColor(ui.background);outline.setStroke(1,ui.border);back.setBackground(outline);FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(ui.dp(64),ui.dp(36),Gravity.TOP|Gravity.START);p.setMargins(ui.dp(8),ui.dp(8),0,0);frame.addView(back,p);back.setOnClickListener(v->finish());showBack();
    }
    private void showBack(){if(back==null)return;handler.removeCallbacks(hide);if(!backVisible){backVisible=true;back.animate().cancel();back.animate().translationY(0).setDuration(150).start();}handler.postDelayed(hide,2000);}
    private void hideBack(){handler.removeCallbacks(hide);if(back==null||!backVisible)return;backVisible=false;back.animate().cancel();back.animate().translationY(-ui.dp(100)).setDuration(180).start();}
    private void positionChanged(){handler.removeCallbacks(save);handler.postDelayed(save,350);}
    private Bundle loadPosition(){try{JSONObject json=new JSONObject(getSharedPreferences("pdf-positions",0).getString(documentKey,"{}"));if(!json.has("page"))return null;Bundle p=new Bundle();p.putInt("page",json.optInt("page"));p.putDouble("fraction",json.optDouble("fraction",0));p.putDouble("gap",json.optDouble("gap",0));p.putFloat("zoom",(float)json.optDouble("zoom",1));p.putDouble("xFraction",json.optDouble("xFraction",0));return p;}catch(JSONException e){return null;}}
    @android.annotation.SuppressLint("ApplySharedPref") // Commit the exact top anchor before closing the document.
    private void savePosition(boolean immediate){if(document==null||documentKey==null)return;Bundle p=document.snapshot();try{JSONObject json=new JSONObject().put("page",p.getInt("page")).put("fraction",p.getDouble("fraction")).put("gap",p.getDouble("gap")).put("zoom",p.getFloat("zoom",1)).put("xFraction",p.getDouble("xFraction",0));android.content.SharedPreferences.Editor editor=getSharedPreferences("pdf-positions",0).edit().putString(documentKey,json.toString());if(immediate)editor.commit();else editor.apply();}catch(JSONException ignored){}}
    @Override protected void onPause(){handler.removeCallbacks(save);if(document!=null)document.stopMotion();savePosition(true);super.onPause();}
    @Override protected void onSaveInstanceState(Bundle out){if(document!=null)out.putBundle("position",document.snapshot());savePosition(true);super.onSaveInstanceState(out);}
    @Override protected void onDestroy(){handler.removeCallbacksAndMessages(null);if(document!=null)document.close();super.onDestroy();}
}