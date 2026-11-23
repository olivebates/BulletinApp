package com.bulletin.app;

import android.content.Context;
import android.graphics.*;
import android.os.*;
import android.view.*;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.*;

/** One square per calendar day, followed by a larger release-to-celebrate box. */
@android.annotation.SuppressLint("ViewConstructor") // Created with a task's completion history and callbacks.
final class CompletionColumn extends View {
    interface WaveRenderer {void draw(View source,float x,float y,float radius,int color);}
    private WaveRenderer waveRenderer;
    void setWaveRenderer(WaveRenderer renderer){waveRenderer=renderer;}
    private final Ui ui;
    private final Set<Long> completedDays;
    private final long firstDay;
    private final int historyRows;
    private int visibleRows;
    private float originY;
    private final Runnable released, money, finished;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path check = new Path(), segment = new Path();
    private final PathMeasure measure = new PathMeasure();
    private final Rect clip = new Rect();
    private final Paint.FontMetrics fontMetrics = new Paint.FontMetrics();
    private final List<String> labels = new ArrayList<>();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final boolean available;
    private boolean holding, celebrating;
    private long started,pressedAt,releasedAt;
    private float releasedRadius,releasedPressure;
    private final Runnable pulse = new Runnable() {
        @Override public void run() { if(holding) {Feedback.vibrate(getContext(),false);handler.postDelayed(this,90);} }
    };
    CompletionColumn(Context context,Ui ui,CompletionLog log,String id,long taskFirstDay,long today,Runnable released,Runnable money,Runnable finished) {
        super(context);this.ui=ui;SortedSet<Long> days=log.completedDays(id);this.completedDays=new HashSet<>(days);this.available=log.pending.stream().anyMatch(e->e.id.equals(id));
        this.firstDay=CompletionVisuals.firstDay(today,Math.min(taskFirstDay,days.isEmpty()?today:days.first()));this.historyRows=(int)(today-firstDay);
        for(int i=0;i<historyRows;i++)labels.add(LocalDate.ofEpochDay(firstDay+i).getDayOfWeek().getDisplayName(TextStyle.FULL,Locale.ENGLISH));
        this.released=released;this.money=money;this.finished=finished;
        setFocusable(true);setClickable(available);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        setContentDescription("Recent task completions. Empty squares show missed days. Check the large Today box to celebrate this task.");
    }
    @Override protected void onMeasure(int width,int height) {
        int measuredHeight=resolveSize(ui.dp(historyRows*42+80),height);
        visibleRows=CompletionVisuals.visibleHistoryRows(historyRows,measuredHeight,ui.dp(42),ui.dp(80));
        originY=Math.max(0,measuredHeight-ui.dp(visibleRows*42+80));
        setMeasuredDimension(resolveSize(ui.dp(240),width),measuredHeight);
    }
    private float boxTop(int row) {return originY+ui.dp(row*42+4);}
    private boolean inBox(float x,float y) {float top=boxTop(visibleRows);return Math.abs(x-getWidth()/2f)<=ui.dp(36)&&y>=top-ui.dp(4)&&y<=top+ui.dp(68);}
    private float heldRadius(long now) {return .8f*HoldWave.radius(this,getWidth()/2f,boxTop(visibleRows)+ui.dp(32))*CompletionVisuals.waveGrowth(now-pressedAt);}
    @Override public boolean onTouchEvent(MotionEvent event) {
        if(!available||celebrating)return false;
        switch(event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if(!inBox(event.getX(),event.getY()))return false;
                holding=true;pressedAt=SystemClock.uptimeMillis();releasedAt=0;getParent().requestDisallowInterceptTouchEvent(true);pulse.run();invalidate();return true;
            case MotionEvent.ACTION_MOVE:
                if(!inBox(event.getX(),event.getY()))stopHolding();return true;
            case MotionEvent.ACTION_UP:
                boolean activate=holding&&inBox(event.getX(),event.getY());stopHolding();if(activate)performClick();return true;
            case MotionEvent.ACTION_CANCEL: stopHolding();return true;
            default:return true;
        }
    }
    private void stopHolding(){
        if(holding){releasedAt=SystemClock.uptimeMillis();releasedRadius=heldRadius(releasedAt);releasedPressure=CompletionVisuals.pressAmount(releasedAt-pressedAt);}
        holding=false;handler.removeCallbacks(pulse);getParent().requestDisallowInterceptTouchEvent(false);invalidate();
    }
    @Override public boolean performClick() {
        super.performClick();if(!available||celebrating)return true;
        long now=SystemClock.uptimeMillis();if(releasedAt==0||now-releasedAt>80){releasedAt=now;releasedRadius=0;releasedPressure=0;}
        celebrating=true;started=now+CompletionVisuals.COLLAPSE_MILLIS;released.run();invalidate();
        handler.postDelayed(()->{if(isAttachedToWindow()){money.run();invalidate();}},CompletionVisuals.COLLAPSE_MILLIS+CompletionVisuals.CHECK_MILLIS);
        handler.postDelayed(()->{if(isAttachedToWindow())finished.run();},CompletionVisuals.COLLAPSE_MILLIS+CompletionVisuals.CHECK_MILLIS+CompletionVisuals.HOLD_MILLIS);
        return true;
    }
    @Override protected void onDetachedFromWindow(){holding=false;handler.removeCallbacksAndMessages(null);if(waveRenderer!=null)waveRenderer.draw(this,0,0,0,ui.border);super.onDetachedFromWindow();}
    @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);
        long now=SystemClock.uptimeMillis();boolean motion=Feedback.motion(getContext());
        float radius=motion?(holding?heldRadius(now):releasedAt>0?releasedRadius*(1-CompletionVisuals.collapseProgress(now-releasedAt)):0):0;
        if(waveRenderer!=null)waveRenderer.draw(this,getWidth()/2f,boxTop(visibleRows)+ui.dp(32),radius,ui.border);
        canvas.getClipBounds(clip);int first=Math.max(0,(int)((clip.top-originY)/ui.dp(42))-2),last=Math.min(visibleRows,(int)((clip.bottom-originY)/ui.dp(42))+1);
        for(int i=first;i<=last;i++){
            int historyIndex=historyRows-visibleRows+i;
            boolean current=i==visibleRows,done=!current&&completedDays.contains(firstDay+historyIndex);float size=ui.dp(current?64:32);
            float center=getWidth()/2f,left=center-size/2,top=boxTop(i),right=left+size,bottom=top+size;
            int saved=canvas.save();
            if(current&&motion){float pressure=holding?CompletionVisuals.pressAmount(now-pressedAt):releasedAt>0?releasedPressure*CompletionVisuals.springAmount(now-releasedAt-CompletionVisuals.COLLAPSE_MILLIS):0;canvas.scale(1-.04f*pressure,1-.08f*pressure,center,(top+bottom)/2);}
            paint.setStyle(Paint.Style.FILL);paint.setColor(current?(holding||radius>0?ui.border:0xff4d4500):ui.background);canvas.drawRect(left,top,right,bottom,paint);
            paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(2);paint.setColor(current?0xffffff00:done?Ui.GREEN:ui.border);canvas.drawRect(left,top,right,bottom,paint);
            float progress=done?1:current&&celebrating?CompletionVisuals.checkProgress(now-started):0;
            if(current&&!motion&&celebrating&&now>=started)progress=1;
            if(progress>0){check.reset();check.moveTo(left+size*.1875f,top+size*.5f);check.lineTo(left+size*.40625f,top+size*.71875f);check.lineTo(left+size*.8125f,top+size*.25f);
                measure.setPath(check,false);segment.reset();measure.getSegment(0,measure.getLength()*progress,segment,true);paint.setColor(Ui.GREEN);paint.setStrokeWidth(ui.dp(current?5:3));paint.setStrokeCap(Paint.Cap.ROUND);paint.setStrokeJoin(Paint.Join.ROUND);canvas.drawPath(segment,paint);paint.setStrokeCap(Paint.Cap.BUTT);
            }
            canvas.restoreToCount(saved);
            paint.setStyle(Paint.Style.FILL);paint.setColor(current?ui.text:ui.muted);paint.setAlpha(255);
            paint.setTextSize(ui.dp(current?22:14));paint.setTypeface(current?Typeface.DEFAULT_BOLD:Typeface.DEFAULT);paint.setTextAlign(Paint.Align.RIGHT);paint.getFontMetrics(fontMetrics);
            canvas.drawText(current?"Today":labels.get(historyIndex),center-ui.dp(44),(top+bottom)/2-(fontMetrics.ascent+fontMetrics.descent)/2,paint);
            if(current&&celebrating&&progress<1)postInvalidateOnAnimation();
        }
        if(motion&&(holding||releasedAt>0&&now-releasedAt<CompletionVisuals.COLLAPSE_MILLIS+CompletionVisuals.SPRING_MILLIS))postInvalidateOnAnimation();
    }
}
