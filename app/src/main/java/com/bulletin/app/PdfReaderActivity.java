package com.bulletin.app;
import android.os.*;
import android.view.*;
import android.widget.*;
import android.graphics.drawable.GradientDrawable;
import org.json.*;
import java.io.File;
public final class PdfReaderActivity extends ToolScreen {
    private PdfCanvas document;private Button back,invertButton,sepiaButton;private LinearLayout controls;private String documentKey;private boolean backVisible,invert,sepia;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable hide=this::hideBack;
    private final Runnable save=()->savePosition(false);
    private TextView controlsHint;
    @Override public void onCreate(Bundle state){super.onCreate(state);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        if(Build.VERSION.SDK_INT>=30){getWindow().setDecorFitsSystemWindows(false);getWindow().getInsetsController().hide(WindowInsets.Type.systemBars());getWindow().getInsetsController().setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);}
        FrameLayout frame=new FrameLayout(this);frame.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);frame.setBackgroundColor(ui.background);setContentView(frame);
        String path=getIntent().getStringExtra("path");if(path==null||!new File(path).getName().equals(path)){finish();return;}documentKey=path;
        Bundle position=state==null?null:state.getBundle("position");if(position==null)position=loadPosition();
        document=new PdfCanvas(this,new File(getFilesDir(),"pdf-library/"+path),position,this::showBack,this::hideBack,this::positionChanged);frame.addView(document,new FrameLayout.LayoutParams(-1,-1));
        controls=new LinearLayout(this);controls.setOrientation(LinearLayout.HORIZONTAL);controls.setVisibility(View.GONE);controls.setTranslationY(-ui.dp(100));
        FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(-1,-2,Gravity.TOP|Gravity.START);p.setMargins(ui.dp(8),ui.dp(8),ui.dp(8),0);frame.addView(controls,p);
        back=readerButton("Back");controls.addView(new View(this),new LinearLayout.LayoutParams(0,1,1));invertButton=readerButton("Invert");sepiaButton=readerButton("Sepia");((LinearLayout.LayoutParams)sepiaButton.getLayoutParams()).rightMargin=0;
        android.content.SharedPreferences preferences=getSharedPreferences("pdf-colors",0);
        invert=preferences.getBoolean(documentKey+":invert",false);sepia=preferences.getBoolean(documentKey+":sepia",false);updateColors();
        back.setOnClickListener(v->finish());invertButton.setOnClickListener(v->{invert=!invert;updateColors();});sepiaButton.setOnClickListener(v->{sepia=!sepia;updateColors();});
        android.content.SharedPreferences hints=getSharedPreferences("pdf-controls-hints",0);
        if(!hints.getBoolean(documentKey,false)){
            hints.edit().putBoolean(documentKey,true).apply();controlsHint=new TextView(this);controlsHint.setText("Double tap to bring out controls.");controlsHint.setTextColor(ui.text);controlsHint.setTextSize(16);controlsHint.setPadding(ui.dp(16),ui.dp(12),ui.dp(16),ui.dp(12));controlsHint.setGravity(Gravity.CENTER);controlsHint.setBackground(ui.panel(ui.border));controlsHint.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
            FrameLayout.LayoutParams hint=new FrameLayout.LayoutParams(-2,-2,Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL);hint.setMargins(ui.dp(16),0,ui.dp(16),ui.dp(24));frame.addView(controlsHint,hint);handler.postDelayed(()->controlsHint.setVisibility(View.GONE),5000);
        }
    }
    private Button readerButton(String title){Button button=new Button(this);button.setText(title);button.setTextSize(12);button.setAllCaps(false);button.setPadding(ui.dp(10),0,ui.dp(10),0);button.setMinimumWidth(0);button.setMinWidth(0);button.setMinimumHeight(0);button.setMinHeight(0);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(ui.dp(64),ui.dp(36));p.rightMargin=ui.dp(6);controls.addView(button,p);styleButton(button,false);return button;}
    private void styleButton(Button button,boolean selected){button.setTextColor(selected?ui.background:ui.text);GradientDrawable outline=new GradientDrawable();outline.setColor(selected?ui.border:ui.background);outline.setStroke(1,ui.border);button.setBackground(outline);button.setSelected(selected);}
    private void updateColors(){document.setColors(invert,sepia);styleButton(invertButton,invert);styleButton(sepiaButton,sepia);invertButton.setContentDescription("Invert PDF colors, "+(invert?"on":"off"));sepiaButton.setContentDescription("Sepia PDF colors, "+(sepia?"on":"off"));getSharedPreferences("pdf-colors",0).edit().putBoolean(documentKey+":invert",invert).putBoolean(documentKey+":sepia",sepia).apply();if(backVisible){handler.removeCallbacks(hide);handler.postDelayed(hide,2000);}}
    private void showBack(){if(controls==null)return;handler.removeCallbacks(hide);if(controlsHint!=null)controlsHint.setVisibility(View.GONE);backVisible=true;controls.animate().cancel();controls.setVisibility(View.VISIBLE);controls.animate().translationY(0).setDuration(150).start();handler.postDelayed(hide,2000);}
    private void hideBack(){handler.removeCallbacks(hide);if(controls==null||!backVisible)return;backVisible=false;controls.animate().cancel();controls.animate().translationY(-ui.dp(100)).setDuration(180).withEndAction(()->{if(!backVisible)controls.setVisibility(View.GONE);}).start();}
    private void positionChanged(){handler.removeCallbacks(save);handler.postDelayed(save,350);}
    private Bundle loadPosition(){try{JSONObject json=new JSONObject(getSharedPreferences("pdf-positions",0).getString(documentKey,"{}"));if(!json.has("page"))return null;Bundle p=new Bundle();p.putInt("page",json.optInt("page"));p.putDouble("fraction",json.optDouble("fraction",0));p.putDouble("gap",json.optDouble("gap",0));p.putFloat("zoom",(float)json.optDouble("zoom",1));p.putDouble("xFraction",json.optDouble("xFraction",0));return p;}catch(JSONException e){return null;}}
    @android.annotation.SuppressLint("ApplySharedPref") // Commit the exact top anchor before closing the document.
    private void savePosition(boolean immediate){if(document==null||documentKey==null)return;Bundle p=document.snapshot();try{JSONObject json=new JSONObject().put("page",p.getInt("page")).put("fraction",p.getDouble("fraction")).put("gap",p.getDouble("gap")).put("zoom",p.getFloat("zoom",1)).put("xFraction",p.getDouble("xFraction",0));android.content.SharedPreferences.Editor editor=getSharedPreferences("pdf-positions",0).edit().putString(documentKey,json.toString());if(immediate)editor.commit();else editor.apply();}catch(JSONException ignored){}}
    @Override protected void onPause(){handler.removeCallbacks(save);if(document!=null)document.stopMotion();savePosition(true);super.onPause();}
    @Override protected void onSaveInstanceState(Bundle out){if(document!=null)out.putBundle("position",document.snapshot());savePosition(true);super.onSaveInstanceState(out);}
    @Override protected void onDestroy(){handler.removeCallbacksAndMessages(null);if(document!=null)document.close();super.onDestroy();}
}
