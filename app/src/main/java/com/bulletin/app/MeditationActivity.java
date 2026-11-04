package com.bulletin.app;

import android.os.*;
import android.content.*;
import android.content.res.ColorStateList;
import android.widget.*;
import android.view.Gravity;
import android.text.InputType;
import java.io.*;
import java.util.*;

public final class MeditationActivity extends ToolScreen {
    private EditText minutes,seconds;
    private Button start;
    private TextView countdown;
    private ScrollView scroll;
    private long lastAppliedInterval;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable tick=new Runnable(){public void run(){boolean running=MeditationService.active!=null;start.setText(running?"Pause":"Start");countdown.setText(running?TimerService.format(MeditationService.remaining()):"");handler.postDelayed(this,250);}};
    @Override public void onCreate(Bundle state){
        super.onCreate(state);title("Meditation Bell");
        scroll=new ScrollView(this);scroll.setFillViewport(true);
        LinearLayout content=column();scroll.addView(content);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        TextView description=text("Let the periodic sound bring you back to your 5 senses.",14);description.setTextColor(ui.border);content.addView(description);
        SharedPreferences prefs=getSharedPreferences("meditation",0);
        LinearLayout fields=row();minutes=field("Minutes");seconds=field("Seconds");
        minutes.setText(state!=null?state.getString("minutes"):prefs.getString("minutes","1"));seconds.setText(state!=null?state.getString("seconds"):prefs.getString("seconds","0"));
        lastAppliedInterval=BellSequence.interval(minutes.getText().toString(),seconds.getText().toString());
        LinearLayout.LayoutParams minuteParams=new LinearLayout.LayoutParams(0,ui.dp(80),1);minuteParams.setMargins(ui.dp(8),ui.dp(8),ui.dp(8),ui.dp(8));fields.addView(labeled(minutes,"Minutes"),minuteParams);
        LinearLayout.LayoutParams secondParams=new LinearLayout.LayoutParams(0,ui.dp(80),1);secondParams.setMargins(ui.dp(8),ui.dp(8),ui.dp(8),ui.dp(8));fields.addView(labeled(seconds,"Seconds"),secondParams);content.addView(fields);
        LinearLayout volumeHeading=row();TextView volumeLabel=text("Volume",12);volumeLabel.setTextColor(ui.border);volumeHeading.addView(volumeLabel,new LinearLayout.LayoutParams(0,-2,1));TextView volumeValue=text("",12);volumeValue.setTextColor(ui.border);volumeHeading.addView(volumeValue);content.addView(volumeHeading);
        SeekBar volume=new SeekBar(this);volume.setMax(100);volume.setContentDescription("Meditation bell volume");volume.setProgressTintList(ColorStateList.valueOf(ui.border));volume.setThumbTintList(ColorStateList.valueOf(ui.border));volume.setProgressBackgroundTintList(ColorStateList.valueOf(ui.muted));volume.setProgress(Math.max(0,Math.min(100,prefs.getInt("volume",55))));volumeValue.setText(volume.getProgress()+"%");
        LinearLayout.LayoutParams volumeParams=new LinearLayout.LayoutParams(-1,ui.dp(36));volumeParams.setMargins(ui.dp(8),0,ui.dp(8),ui.dp(8));content.addView(volume,volumeParams);
        volume.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar bar,int value,boolean fromUser){volumeValue.setText(value+"%");if(fromUser){prefs.edit().putInt("volume",value).apply();if(MeditationService.active!=null)MeditationService.active.setBellVolume(value);}}public void onStartTrackingTouch(SeekBar bar){}public void onStopTrackingTouch(SeekBar bar){}});
        LinearLayout startRow=row();start=button("Start");action(startRow,start);content.addView(startRow);
        countdown=text("",18);countdown.setGravity(Gravity.CENTER);content.addView(countdown);
        TextView quote=text("",20);quote.setGravity(Gravity.CENTER);quote.setTextIsSelectable(true);quote.setMinHeight(ui.dp(140));
        String selected=state==null?null:state.getString("quote");
        try(BufferedReader reader=new BufferedReader(new InputStreamReader(getAssets().open("meditation-quotes.tsv"),java.nio.charset.StandardCharsets.UTF_8))){List<String> quotes=new ArrayList<>();String line;while((line=reader.readLine())!=null){String[] parts=line.split("\t",2);if(parts.length==2)quotes.add(parts[1]);}if(selected==null||!quotes.contains(selected))selected=quotes.get(new Random().nextInt(quotes.size()));}catch(IOException e){selected="Could not load meditation quotes.";}
        quote.setText(selected);quote.setTag(selected);content.addView(quote,new LinearLayout.LayoutParams(-1,-2,1));root.setTag(quote);
        LinearLayout footer=row();Button back=button("Back");back.setOnClickListener(v->finish());action(footer,back);root.addView(footer);
        start.setOnClickListener(v->{
            if(MeditationService.active!=null){startService(new Intent(this,MeditationService.class).setAction("pause"));start.setText("Start");return;}
            long interval=BellSequence.interval(minutes.getText().toString(),seconds.getText().toString());
            if(interval<0){message("Enter minutes and seconds (0–59), totaling at least one second.");return;}
            lastAppliedInterval=interval;prefs.edit().putString("minutes",minutes.getText().toString()).putString("seconds",seconds.getText().toString()).apply();startForegroundService(new Intent(this,MeditationService.class).putExtra("interval",interval));start.setText("Pause");
            if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS},20);
        });
        android.text.TextWatcher changes=new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){handler.removeCallbacks(applyInterval);handler.postDelayed(applyInterval,350);}public void afterTextChanged(android.text.Editable e){}};
        minutes.addTextChangedListener(changes);seconds.addTextChangedListener(changes);
        addToolNavigation();
        if(state!=null){int position=state.getInt("scroll");scroll.post(()->scroll.scrollTo(0,position));}
    }
    private LinearLayout labeled(EditText field,String label){LinearLayout box=column();TextView caption=text(label,12);caption.setTextColor(ui.border);caption.setPadding(ui.dp(4),0,ui.dp(4),ui.dp(4));box.addView(caption);box.addView(field,new LinearLayout.LayoutParams(-1,0,1));return box;}
    private final Runnable applyInterval=()->{
        long interval=BellSequence.interval(minutes.getText().toString(),seconds.getText().toString());if(interval<0||interval==lastAppliedInterval)return;lastAppliedInterval=interval;
        getSharedPreferences("meditation",0).edit().putString("minutes",minutes.getText().toString()).putString("seconds",seconds.getText().toString()).apply();
        if(MeditationService.active!=null)startService(new Intent(this,MeditationService.class).setAction("interval").putExtra("interval",interval));
    };
    private EditText field(String label){EditText f=new EditText(this);f.setTextColor(ui.text);f.setHintTextColor(ui.muted);f.setHint(label);f.setContentDescription(label);f.setSingleLine(true);f.setInputType(InputType.TYPE_CLASS_NUMBER);f.setPadding(ui.dp(12),0,ui.dp(12),0);f.setBackground(ui.panel(ui.border));return f;}
    @Override protected void onResume(){super.onResume();handler.post(tick);}
    @Override protected void onPause(){handler.removeCallbacksAndMessages(null);applyInterval.run();super.onPause();}
    @Override protected void onSaveInstanceState(Bundle out){out.putString("minutes",minutes.getText().toString());out.putString("seconds",seconds.getText().toString());out.putString("quote",(String)((TextView)root.getTag()).getTag());out.putInt("scroll",scroll.getScrollY());super.onSaveInstanceState(out);}
}