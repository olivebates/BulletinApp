package com.bulletin.app;

import android.content.Context;
import android.graphics.*;
import android.os.*;
import android.view.*;
import java.time.*;
import java.time.format.TextStyle;
import java.util.Locale;

/** Three real calendar months; only the earned square for today can be pressed. */
@android.annotation.SuppressLint("ViewConstructor")
final class MustDoCalendar extends View {
    private final Ui ui;
    private final MustDoLog log;
    private final long today;
    private final YearMonth first;
    private final Runnable released, celebration, finished;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path check=new Path(),segment=new Path();
    private final PathMeasure measure=new PathMeasure();
    private final Handler handler=new Handler(Looper.getMainLooper());
    private CompletionColumn.WaveRenderer waveRenderer;
    private boolean holding,celebrating;
    private long pressedAt,releasedAt,started;
    private float releasedRadius,releasedPressure;
    private final Runnable pulse=new Runnable(){@Override public void run(){if(holding){Feedback.vibrate(getContext(),false);handler.postDelayed(this,90);}}};
    MustDoCalendar(Context context,Ui ui,MustDoLog log,long today,Runnable released,Runnable celebration,Runnable finished){
        super(context);this.ui=ui;this.log=log;this.today=today;first=log.firstMonth(today);this.released=released;this.celebration=celebration;this.finished=finished;
        setClickable(true);setFocusable(true);setContentDescription("Three months of Must Do completions. Check the large centered square to celebrate completing your Must Dos today.");
    }
    void setWaveRenderer(CompletionColumn.WaveRenderer renderer){waveRenderer=renderer;}
    private float layoutScale=1,panelWidth,panelHeight,cellPitch,panelLeft,panelsTop,panelGap;
    private final RectF hero=new RectF();
    private Shader glow;
    @Override protected void onMeasure(int width,int height){setMeasuredDimension(resolveSize(ui.dp(480),width),resolveSize(ui.dp(310),height));}
    @Override protected void onSizeChanged(int width,int height,int oldWidth,int oldHeight){
        float groupWidth=Math.max(1,Math.min(width-ui.dp(16),ui.dp(480)));
        float cardWidth=(groupWidth-ui.dp(24))/3;
        float pitch=(cardWidth-ui.dp(20))/7;
        float cardHeight=ui.dp(44)+6*pitch;
        float naturalHeight=ui.dp(160)+cardHeight;
        layoutScale=Math.min(1,height/naturalHeight);
        panelWidth=cardWidth*layoutScale;panelHeight=cardHeight*layoutScale;cellPitch=pitch*layoutScale;panelGap=ui.dp(12)*layoutScale;
        panelLeft=(width-groupWidth*layoutScale)/2;
        float side=ui.dp(88)*layoutScale,top=(height-naturalHeight*layoutScale)/2+ui.dp(24)*layoutScale;
        hero.set((width-side)/2,top,(width+side)/2,top+side);
        panelsTop=hero.bottom+ui.dp(32)*layoutScale;
        glow=new RadialGradient(hero.centerX(),hero.centerY(),Math.max(1,side),new int[]{0x22f4d17e,0x0af4d17e,0x00f4d17e},new float[]{0,.55f,1},Shader.TileMode.CLAMP);
    }
    private boolean inBox(float x,float y){float extra=Math.max(0,(ui.dp(48)-hero.width())/2);return x>=hero.left-extra&&x<=hero.right+extra&&y>=hero.top-extra&&y<=hero.bottom+extra;}
    private float heldRadius(long now){return HoldWave.radius(this,hero.centerX(),hero.centerY())*CompletionVisuals.waveGrowth(now-pressedAt);}
    @Override public boolean onTouchEvent(MotionEvent event){
        if(celebrating||!log.hasPending(today))return false;
        switch(event.getActionMasked()){
            case MotionEvent.ACTION_DOWN:
                if(!inBox(event.getX(),event.getY()))return false;
                holding=true;pressedAt=SystemClock.uptimeMillis();releasedAt=0;getParent().requestDisallowInterceptTouchEvent(true);pulse.run();invalidate();return true;
            case MotionEvent.ACTION_MOVE:if(!inBox(event.getX(),event.getY()))stopHolding();return true;
            case MotionEvent.ACTION_UP:boolean activate=holding&&inBox(event.getX(),event.getY());stopHolding();if(activate)performClick();return true;
            case MotionEvent.ACTION_CANCEL:stopHolding();return true;
            default:return true;
        }
    }
    private void stopHolding(){if(holding){releasedAt=SystemClock.uptimeMillis();releasedRadius=heldRadius(releasedAt);releasedPressure=CompletionVisuals.pressAmount(releasedAt-pressedAt);}holding=false;handler.removeCallbacks(pulse);getParent().requestDisallowInterceptTouchEvent(false);invalidate();}
    @Override public boolean performClick(){
        super.performClick();if(celebrating||!log.hasPending(today))return true;
        long now=SystemClock.uptimeMillis();if(releasedAt==0||now-releasedAt>80){releasedAt=now;releasedRadius=0;releasedPressure=0;}
        celebrating=true;started=now+CompletionVisuals.COLLAPSE_MILLIS;released.run();invalidate();
        handler.postDelayed(()->{if(isAttachedToWindow()){celebration.run();invalidate();}},CompletionVisuals.COLLAPSE_MILLIS+CompletionVisuals.CHECK_MILLIS);
        handler.postDelayed(()->{if(isAttachedToWindow())finished.run();},CompletionVisuals.COLLAPSE_MILLIS+CompletionVisuals.CHECK_MILLIS+CompletionVisuals.HOLD_MILLIS);
        return true;
    }
    @Override protected void onDetachedFromWindow(){holding=false;handler.removeCallbacksAndMessages(null);if(waveRenderer!=null)waveRenderer.draw(this,0,0,0,ui.border);super.onDetachedFromWindow();}
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);long now=SystemClock.uptimeMillis();boolean motion=Feedback.motion(getContext());
        float radius=motion?(holding?heldRadius(now):releasedAt>0?releasedRadius*(1-CompletionVisuals.collapseProgress(now-releasedAt)):0):0;
        if(waveRenderer!=null)waveRenderer.draw(this,hero.centerX(),hero.centerY(),radius,ui.border);
        float progress=celebrating?CompletionVisuals.checkProgress(now-started):0;
        if(!motion&&celebrating&&now>=started)progress=1;
        paint.setStyle(Paint.Style.FILL);paint.setColor(Color.WHITE);paint.setShader(glow);canvas.drawCircle(hero.centerX(),hero.centerY(),hero.width(),paint);paint.setShader(null);
        int saved=canvas.save();
        if(motion){float pressure=holding?CompletionVisuals.pressAmount(now-pressedAt):releasedAt>0?releasedPressure*CompletionVisuals.springAmount(now-releasedAt-CompletionVisuals.COLLAPSE_MILLIS):0;canvas.scale(1-.04f*pressure,1-.08f*pressure,hero.centerX(),hero.centerY());}
        float corner=ui.dp(12)*layoutScale;
        paint.setColor(holding?withAlpha(ui.border,120):withAlpha(Ui.GOLD,22));canvas.drawRoundRect(hero,corner,corner,paint);
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(ui.dp(2)*layoutScale);paint.setColor(Ui.GOLD);canvas.drawRoundRect(hero,corner,corner,paint);
        drawCheck(canvas,hero.left,hero.top,hero.width(),progress);
        canvas.restoreToCount(saved);
        YearMonth currentMonth=YearMonth.from(LocalDate.ofEpochDay(today));
        for(int column=0;column<3;column++){
            YearMonth month=first.plusMonths(column);boolean active=month.equals(currentMonth);
            float left=panelLeft+column*(panelWidth+panelGap),pad=ui.dp(10)*layoutScale;
            paint.setStyle(Paint.Style.FILL);paint.setColor(withAlpha(ui.text,7));canvas.drawRoundRect(left,panelsTop,left+panelWidth,panelsTop+panelHeight,ui.dp(6)*layoutScale,ui.dp(6)*layoutScale,paint);
            paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(1);paint.setColor(withAlpha(active?Ui.GOLD:ui.border,active?100:55));canvas.drawRoundRect(left,panelsTop,left+panelWidth,panelsTop+panelHeight,ui.dp(6)*layoutScale,ui.dp(6)*layoutScale,paint);
            String label=month.getMonth().getDisplayName(TextStyle.SHORT,Locale.ENGLISH).toUpperCase(Locale.ENGLISH);
            paint.setStyle(Paint.Style.FILL);paint.setColor(active?Ui.GOLD:ui.muted);paint.setTypeface(Typeface.DEFAULT_BOLD);paint.setTextAlign(Paint.Align.CENTER);paint.setTextSize(ui.dp(12)*layoutScale);
            canvas.drawText(label,left+panelWidth/2,panelsTop+ui.dp(21)*layoutScale,paint);
            int offset=month.atDay(1).getDayOfWeek().getValue()-1;
            for(int day=1;day<=month.lengthOfMonth();day++){
                long date=month.atDay(day).toEpochDay();boolean current=date==today,done=log.completed(date)&&!current;
                int slot=offset+day-1;float size=cellPitch*.72f;
                float x=left+pad+(slot%7+.5f)*cellPitch-size/2,y=panelsTop+ui.dp(32)*layoutScale+(slot/7+.5f)*cellPitch-size/2;
                paint.setStyle(Paint.Style.FILL);paint.setColor(withAlpha(current?Ui.GOLD:Ui.GREEN,current?30:done?55:0));canvas.drawRoundRect(x,y,x+size,y+size,layoutScale,layoutScale,paint);
                paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(current?Math.max(1,ui.dp(1)*layoutScale):1);paint.setColor(current?Ui.GOLD:withAlpha(done?Ui.GREEN:ui.border,done?200:date>today?40:110));canvas.drawRoundRect(x,y,x+size,y+size,layoutScale,layoutScale,paint);
                drawCheck(canvas,x,y,size,done?1:current?progress:0);
            }
        }
        if(celebrating&&now-started<CompletionVisuals.CHECK_MILLIS||motion&&(holding||releasedAt>0&&now-releasedAt<CompletionVisuals.COLLAPSE_MILLIS+CompletionVisuals.SPRING_MILLIS))postInvalidateOnAnimation();
    }
    private static int withAlpha(int color,int alpha){return (color&0x00ffffff)|(alpha<<24);}
    private void drawCheck(Canvas canvas,float left,float top,float size,float progress){
        if(progress<=0)return;
        check.reset();check.moveTo(left+size*.23f,top+size*.51f);check.lineTo(left+size*.43f,top+size*.70f);check.lineTo(left+size*.78f,top+size*.29f);
        measure.setPath(check,false);segment.reset();measure.getSegment(0,measure.getLength()*progress,segment,true);
        paint.setStyle(Paint.Style.STROKE);paint.setColor(Ui.GREEN);paint.setStrokeWidth(Math.max(1,size*.065f));paint.setStrokeCap(Paint.Cap.ROUND);paint.setStrokeJoin(Paint.Join.ROUND);canvas.drawPath(segment,paint);paint.setStrokeCap(Paint.Cap.BUTT);
    }
}
