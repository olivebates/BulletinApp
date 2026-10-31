package com.bulletin.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;
import android.view.MotionEvent;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** Two handles on one track select the first and last displayed dates. */
@android.annotation.SuppressLint("ViewConstructor")
final class BalanceRangeSlider extends View {
    interface Listener { void changed(long start, long end, boolean finished); }
    private final Ui ui; private final long minimum, maximum; private long start, end;
    private boolean movingStart = true; private final Listener listener;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    BalanceRangeSlider(Context context, Ui ui, long minimum, long maximum, long start, long end, Listener listener) {
        super(context); this.ui=ui; this.minimum=minimum; this.maximum=Math.max(minimum+1,maximum); this.listener=listener;
        this.start=start<=0?minimum:Math.max(minimum,Math.min(this.maximum-1,start));
        this.end=end<=0?this.maximum:Math.max(this.start+1,Math.min(this.maximum,end));
        setFocusable(true); setClickable(true); describe();
    }
    private String date(long at) { return DateTimeFormatter.ofPattern("d MMM yyyy").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(at)); }
    private void describe() { setContentDescription("Balance date range: "+date(start)+" to "+date(end)+". Drag the left or right handle."); }
    private float x(long at) { return ui.dp(16)+(getWidth()-ui.dp(32))*(float)((double)(at-minimum)/(maximum-minimum)); }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas); paint.setColor(ui.muted);paint.setTextSize(ui.dp(11));canvas.drawText(date(start),ui.dp(8),ui.dp(16),paint);
        String last=date(end);canvas.drawText(last,getWidth()-ui.dp(8)-paint.measureText(last),ui.dp(16),paint);
        paint.setStrokeWidth(ui.dp(3));paint.setColor(ui.border);paint.setAlpha(70);canvas.drawLine(ui.dp(16),ui.dp(40),getWidth()-ui.dp(16),ui.dp(40),paint);
        paint.setAlpha(255);paint.setColor(Ui.GOLD);canvas.drawLine(x(start),ui.dp(40),x(end),ui.dp(40),paint);
        paint.setColor(ui.border);canvas.drawCircle(x(start),ui.dp(40),ui.dp(9),paint);canvas.drawCircle(x(end),ui.dp(40),ui.dp(9),paint);
    }
    private void update(long at, boolean finished) {
        if(movingStart) start=Math.max(minimum,Math.min(end-1,at));else end=Math.min(maximum,Math.max(start+1,at));
        describe();invalidate();listener.changed(start==minimum?0:start,end==maximum?0:end,finished);
    }
    @Override public boolean onTouchEvent(MotionEvent event) {
        switch(event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                movingStart=Math.abs(event.getX()-x(start))<=Math.abs(event.getX()-x(end));getParent().requestDisallowInterceptTouchEvent(true);break;
            case MotionEvent.ACTION_MOVE:break;
            case MotionEvent.ACTION_UP:performClick();getParent().requestDisallowInterceptTouchEvent(false);break;
            case MotionEvent.ACTION_CANCEL:getParent().requestDisallowInterceptTouchEvent(false);listener.changed(start==minimum?0:start,end==maximum?0:end,true);return true;
            default:return super.onTouchEvent(event);
        }
        double fraction=Math.max(0,Math.min(1,(event.getX()-ui.dp(16))/Math.max(1,getWidth()-ui.dp(32))));
        update(minimum+(long)((maximum-minimum)*fraction),event.getActionMasked()==MotionEvent.ACTION_UP);return true;
    }
    @Override public boolean onKeyDown(int key, android.view.KeyEvent event) {
        if(key==android.view.KeyEvent.KEYCODE_DPAD_UP||key==android.view.KeyEvent.KEYCODE_DPAD_DOWN){movingStart=!movingStart;return true;}
        if(key==android.view.KeyEvent.KEYCODE_DPAD_LEFT||key==android.view.KeyEvent.KEYCODE_DPAD_RIGHT){update((movingStart?start:end)+(key==android.view.KeyEvent.KEYCODE_DPAD_LEFT?-1:1)*86400000L,true);return true;}
        return super.onKeyDown(key,event);
    }
    @Override public boolean performClick(){super.performClick();return true;}
}