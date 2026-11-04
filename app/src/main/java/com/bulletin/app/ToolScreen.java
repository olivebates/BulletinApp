package com.bulletin.app;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Typeface;
import android.view.*;
import android.widget.*;

abstract class ToolScreen extends Activity {
    Ui ui;
    LinearLayout root;
    private TextView navDays,navCoins;
    private final android.os.Handler navigationHandler=new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable navigationTick=new Runnable(){public void run(){if(navDays==null)return;GameStore store=((BulletinApplication)getApplication()).store();store.sync();Game g=store.game;long days=g.days,coins=g.coins-g.pendingCoins;for(Game.MissedReview review:g.missedReviews){days=Game.add(days,review.daysLost);coins=Game.add(coins,review.coinsLost);}navDays.setText(Long.toString(days));navCoins.setText(Money.format(coins));navCoins.setTextColor(coins<0?Ui.RED:Ui.GOLD);navigationHandler.postDelayed(this,1000);}};
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if(android.os.Build.VERSION.SDK_INT>=34){overrideActivityTransition(OVERRIDE_TRANSITION_OPEN,0,0);overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE,0,0);}else disableLegacyTransition();
        if(android.os.Build.VERSION.SDK_INT>=33)getOnBackInvokedDispatcher().registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,this::finish);
        Game game = ((BulletinApplication)getApplication()).store().game;
        ui = new Ui(this, (game.hue + 55) % 360);
        getWindow().setStatusBarColor(ui.background); getWindow().setNavigationBarColor(ui.background);
        if (android.os.Build.VERSION.SDK_INT >= 30) getWindow().setDecorFitsSystemWindows(false);
        root = column(); root.setBackgroundColor(ui.background); setContentView(root);
        root.setOnApplyWindowInsetsListener((v,insets)->{if(android.os.Build.VERSION.SDK_INT>=30){android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout()|WindowInsets.Type.ime());v.setPadding(bars.left,bars.top,bars.right,bars.bottom);}else v.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());return insets;});root.requestApplyInsets();
    }
    @Override public void startActivity(android.content.Intent intent){super.startActivity(intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NO_ANIMATION),android.app.ActivityOptions.makeCustomAnimation(this,0,0).toBundle());}
    @Override public void finish(){super.finish();disableLegacyTransition();}
    @Override public void finishAfterTransition(){finish();}
    @SuppressWarnings("deprecation")
    private void disableLegacyTransition(){overridePendingTransition(0,0);}
    LinearLayout column() { LinearLayout v = new LinearLayout(this); v.setOrientation(LinearLayout.VERTICAL); return v; }
    LinearLayout row() { LinearLayout v = new LinearLayout(this); v.setOrientation(LinearLayout.HORIZONTAL); return v; }
    TextView text(String value, int size) { TextView v = new TextView(this); v.setText(value); v.setTextSize(size); v.setTextColor(ui.text); v.setPadding(ui.dp(12),ui.dp(8),ui.dp(12),ui.dp(8)); return v; }
    void title(String value) { TextView v=text(value,20); v.setTypeface(null,Typeface.BOLD); v.setGravity(Gravity.CENTER); v.setBackground(ui.panel(ui.border)); root.addView(v,new LinearLayout.LayoutParams(-1,ui.dp(56))); }
    Button button(String value) { Button v = new BorderedButton(this,ui.border); v.setText(value); v.setTextColor(ui.text); v.setTextSize(14); v.setAllCaps(false); v.setBackground(ui.button(ui.border)); return v; }
    void action(LinearLayout row, View view) { LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,ui.dp(48),1);p.setMargins(ui.dp(8),ui.dp(8),ui.dp(8),ui.dp(8));row.addView(view,p); }
    void addToolNavigation(){
        View divider=new View(this);divider.setBackgroundColor(ui.border);root.addView(divider,new LinearLayout.LayoutParams(-1,1));
        LinearLayout bar=row(),counters=row();counters.setGravity(Gravity.CENTER);counters.setBackground(ui.panel(ui.border));
        LinearLayout dayGroup=row();dayGroup.setGravity(Gravity.CENTER);dayGroup.addView(ui.icon("calendar",ui.border,21));navDays=navigationText("0",12);dayGroup.addView(navDays);counters.addView(dayGroup,new LinearLayout.LayoutParams(0,-1,1));
        navCoins=navigationText("0.00",12);navCoins.setSingleLine(true);navCoins.setAutoSizeTextTypeUniformWithConfiguration(8,12,1,android.util.TypedValue.COMPLEX_UNIT_SP);counters.addView(navCoins,new LinearLayout.LayoutParams(0,-1,1));bar.addView(counters,new LinearLayout.LayoutParams(0,ui.dp(32),2.3f));
        String[] labels={"Bulletin","Law","Tools","Settings"};int[] tabs={0,1,3,2};
        for(int i=0;i<labels.length;i++){final int tab=tabs[i];LinearLayout item=column();item.setGravity(Gravity.CENTER);item.setBackground(ui.button(tab==3?ui.text:ui.border));item.setFocusable(true);item.setSelected(tab==3);item.setContentDescription(labels[i]);TextView label=navigationText(labels[i],9);label.setTextColor(tab==3?ui.text:ui.muted);item.addView(label);bar.addView(item,new LinearLayout.LayoutParams(0,ui.dp(32),.65f));item.setOnClickListener(v->{startActivity(new android.content.Intent(this,MainActivity.class).putExtra("navigateTab",tab).putExtra("toolsHome",tab==3).addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP|android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP));finish();});}
        root.addView(bar);navigationHandler.removeCallbacks(navigationTick);navigationHandler.post(navigationTick);
    }
    private TextView navigationText(String value,int size){TextView text=new TextView(this);text.setText(value);text.setTextSize(size);text.setTextColor(ui.text);text.setGravity(Gravity.CENTER);return text;}
    @Override protected void onResume(){super.onResume();navigationHandler.removeCallbacks(navigationTick);if(navDays!=null)navigationHandler.post(navigationTick);}
    @Override protected void onPause(){navigationHandler.removeCallbacksAndMessages(null);super.onPause();}
    @Override protected void onDestroy(){navigationHandler.removeCallbacksAndMessages(null);super.onDestroy();}
    void message(String value) { Toast.makeText(this,value,Toast.LENGTH_LONG).show(); }
}