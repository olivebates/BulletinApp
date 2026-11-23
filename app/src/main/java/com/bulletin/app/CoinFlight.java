package com.bulletin.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** A touch-transparent overlay. Each packet lands at the actual coin counter location. */
final class CoinFlight extends View {
    private static final class Particle {
        long born;
        float x, y, tx, ty, bend;
    }
    private final List<Particle> particles = new ArrayList<>();
    private static final class Confetto {
        float x, delay, duration, drift, phase, spin, size;
        int color; boolean round;
    }
    private final java.util.Random random = new java.util.Random();
    private final List<Confetto> confetti = new ArrayList<>();
    private final int[] burstColors = new int[20];
    private final float[] burstAngles = new float[20], burstLengths = new float[20];
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float density;
    private long failureAt;
    private long deathAt;
    private long rewardAt;
    private long rainAt;
    private int rainCount;
    private String rewardText;
    private float rewardX, rewardY;
    private float waveX,waveY,waveRadius;
    private int waveColor;
    private final int[] waveSource=new int[2],waveOrigin=new int[2];
    private final android.graphics.Path wavePath=new android.graphics.Path();
    CoinFlight(Context context) {
        super(context);
        density = getResources().getDisplayMetrics().density;
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        setClickable(false);
    }
    void fail() { failureAt = android.os.SystemClock.uptimeMillis(); invalidate(); }
    void completionWave(View source,float x,float y,float radius,int color) {
        completionWave(source,x,y,radius,color,true);
    }
    void completionWave(View source,float x,float y,float radius,int color,boolean fitToScreen) {
        if(radius<=0&&waveRadius<=0)return;
        source.getLocationOnScreen(waveSource);getLocationOnScreen(waveOrigin);
        waveX=waveSource[0]-waveOrigin[0]+x;waveY=waveSource[1]-waveOrigin[1]+y;waveRadius=radius;waveColor=color;postInvalidateOnAnimation();
        float edge=radius*1.07f+12*density;
        if(radius>0&&fitToScreen){waveX=Math.max(edge,Math.min(getWidth()-edge,waveX));waveY=Math.max(edge,Math.min(getHeight()-edge,waveY));}
    }
    void clearCompletionWave(){waveRadius=0;invalidate();}
    void death() {
        if (!Feedback.motion(getContext())) return;
        deathAt = android.os.SystemClock.uptimeMillis(); setClickable(true); invalidate();
    }
    void rain(boolean grand) {
        if (!Feedback.motion(getContext())) return;
        rainAt = android.os.SystemClock.uptimeMillis(); rainCount = grand ? 220 : 90; invalidate();
        confetti.clear();
        for (int i = 0; i < rainCount; i++) {
            Confetto c = new Confetto(); c.x = random.nextFloat(); c.delay = random.nextFloat() * .9f;
            c.duration = 2.1f + random.nextFloat() * .95f; c.drift = (random.nextFloat() - .5f) * 100;
            c.phase = random.nextFloat() * 6.28f; c.spin = (random.nextFloat() - .5f) * 600;
            c.size = 2 + random.nextFloat() * 4; c.round = random.nextBoolean(); c.color = confettiColor(); confetti.add(c);
        }
    }
    private int confettiColor() {
        return android.graphics.Color.HSVToColor(new float[]{random.nextFloat() * 360, .55f + random.nextFloat() * .45f, .9f + random.nextFloat() * .1f});
    }
    void reward(String text, float x, float y) {
        int[] origin = new int[2]; getLocationOnScreen(origin);
        rewardX = x < 0 ? getWidth() / 2f : x - origin[0];
        rewardY = y < 0 ? getHeight() * .44f : y - origin[1];
        rewardText = text; rewardAt = android.os.SystemClock.uptimeMillis(); invalidate();
        for (int i = 0; i < burstColors.length; i++) {
            burstColors[i] = confettiColor(); burstAngles[i] = random.nextFloat() * 6.28f; burstLengths[i] = 45 + random.nextFloat() * 65;
        }
    }
    void launch(View target, int index, float x, float y) {
        if (!Feedback.motion(getContext())) return;
        int[] origin = new int[2], destination = new int[2];
        getLocationOnScreen(origin); target.getLocationOnScreen(destination);
        Particle p = new Particle();
        p.born = android.os.SystemClock.uptimeMillis();
        p.x = x < 0 ? getWidth() / 2f : x - origin[0]; p.y = y < 0 ? getHeight() / 2f : y - origin[1];
        p.tx = destination[0] - origin[0] + target.getWidth() / 2f;
        p.ty = destination[1] - origin[1] + target.getHeight() / 2f;
        p.bend = ((index % 5) - 2) * 26 * density;
        particles.add(p); invalidate();
    }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        long now = android.os.SystemClock.uptimeMillis();
        boolean motion=Feedback.motion(getContext());
        if (!motion) { particles.clear(); failureAt = rainAt = deathAt = 0; setClickable(false); }
        if(motion&&waveRadius>0){
            wavePath.reset();double phase=now/350d;
            for(int point=0;point<80;point++){
                double angle=point*Math.PI*2/80;
                float r=waveRadius*(1+.045f*(float)Math.sin(angle*5+phase)+.025f*(float)Math.sin(angle*9-phase*.7));
                float x=waveX+r*(float)Math.cos(angle),y=waveY+r*(float)Math.sin(angle);
                if(point==0)wavePath.moveTo(x,y);else wavePath.lineTo(x,y);
            }
            wavePath.close();paint.setStyle(Paint.Style.FILL);paint.setColor(waveColor);paint.setAlpha(255);canvas.drawPath(wavePath,paint);postInvalidateOnAnimation();
        }
        if (rainAt > 0 && now - rainAt < 4000) {
            float elapsed = (now - rainAt) / 1000f;
            paint.setStyle(Paint.Style.FILL);
            for (Confetto c : confetti) {
                float age = elapsed - c.delay;
                if (age < 0 || age > c.duration) continue;
                float progress = age / c.duration;
                float x = getWidth() * c.x + ((float) Math.sin(age * 3 + c.phase) * 20 + c.drift * progress) * density;
                float y = -20 * density + (getHeight() + 50 * density) * (progress * .7f + progress * progress * .3f);
                paint.setColor(c.color);
                paint.setAlpha((int) (255 * Math.min(1, (1 - progress) * 5)));
                int save = canvas.save(); canvas.rotate(c.phase * 60 + age * c.spin, x, y);
                float size = c.size * density;
                if (c.round) canvas.drawCircle(x, y, size / 2, paint);
                else canvas.drawRect(x - size / 2, y - size, x + size / 2, y + size, paint);
                canvas.restoreToCount(save);
            }
            paint.setAlpha(255); postInvalidateOnAnimation();
        }
        if (rewardAt > 0 && now - rewardAt < CompletionVisuals.REWARD_MILLIS) {
            float t = (now - rewardAt) / (float) CompletionVisuals.REWARD_MILLIS;
            paint.setStyle(Paint.Style.FILL); paint.setColor(Ui.GREEN); paint.setAlpha((int) (255 * CompletionVisuals.rewardAlpha(now-rewardAt)));
            paint.setTextSize(44 * density); paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD); paint.setTextAlign(Paint.Align.CENTER);
            paint.setTextSize(Math.min(44*density,paint.getTextSize()*(getWidth()-32*density)/Math.max(1,paint.measureText(rewardText))));
            paint.setShadowLayer(4*density,2*density,3*density,0xcc000000);
            canvas.drawText(rewardText, rewardX, rewardY - (motion?56 * density * t:0), paint);
            paint.clearShadowLayer();
            for (int i = 0; motion && i < burstColors.length; i++) {
                double angle = burstAngles[i];
                float radius = (12 + burstLengths[i] * t) * density;
                paint.setColor(burstColors[i]); paint.setAlpha((int) (255 * (1 - t)));
                float cx = rewardX + (float) Math.cos(angle) * radius;
                float cy = rewardY + (float) Math.sin(angle) * radius + 30 * density * t * t;
                canvas.drawRect(cx - 2 * density, cy - 2 * density, cx + 2 * density, cy + 2 * density, paint);
            }
            paint.setTypeface(null); paint.setTextAlign(Paint.Align.LEFT); paint.setAlpha(255); postInvalidateOnAnimation();
        }
        if (failureAt > 0 && now - failureAt < 240) {
            float t = (now - failureAt) / 240f;
            paint.setStyle(Paint.Style.FILL); paint.setColor(Ui.RED); paint.setAlpha((int) (22 * (1 - t)));
            canvas.drawRect(0, 0, getWidth(), getHeight(), paint);
            paint.setTextAlign(Paint.Align.LEFT); paint.setAlpha(255); postInvalidateOnAnimation();
        }
        Iterator<Particle> iterator = particles.iterator();
        while (iterator.hasNext()) {
            Particle p = iterator.next();
            float t = Math.min(1, (now - p.born) / 420f);
            if (t >= 1) { iterator.remove(); continue; }
            float eased = t * t;
            float x = p.x + (p.tx - p.x) * eased + (float) Math.sin(t * Math.PI) * p.bend;
            float y = p.y + (p.ty - p.y) * eased - (float) Math.sin(t * Math.PI) * 75 * density;
            paint.setStyle(Paint.Style.FILL); paint.setColor(Ui.GOLD);
            canvas.drawCircle(x, y, 9 * density, paint);
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(density); paint.setColor(0xff8f671e);
            canvas.drawCircle(x, y, 6 * density, paint);
            canvas.drawArc(x - 3 * density, y - 4 * density, x + 3 * density, y + 4 * density, 60, 240, false, paint);
            canvas.drawLine(x - 4 * density, y - density, x + density, y - density, paint);
            canvas.drawLine(x - 4 * density, y + density, x + density, y + density, paint);
        }
        if (!particles.isEmpty()) postInvalidateOnAnimation();
        if (deathAt > 0) {
            float t = (now - deathAt) / 1200f;
            if (t >= 1) { deathAt = 0; setClickable(false); }
            else {
                paint.setStyle(Paint.Style.FILL); paint.setColor(0xff200409);
                paint.setAlpha((int) (230 * Math.min(1, t * 5))); canvas.drawRect(0, 0, getWidth(), getHeight(), paint);
                paint.setAlpha(255); paint.setColor(Ui.RED);
                float cx = getWidth() / 2f, cy = getHeight() / 2f - 32 * density, radius = (34 + 12 * Math.min(1, t * 3)) * density;
                canvas.drawCircle(cx, cy, radius, paint);
                canvas.drawRect(cx - radius * .5f, cy, cx + radius * .5f, cy + radius * 1.15f, paint);
                paint.setColor(0xff200409); canvas.drawCircle(cx - radius * .36f, cy - radius * .12f, radius * .2f, paint);
                canvas.drawCircle(cx + radius * .36f, cy - radius * .12f, radius * .2f, paint);
                for (int i = -1; i <= 1; i++) canvas.drawRect(cx + i * radius * .27f - density, cy + radius * .72f, cx + i * radius * .27f + density, cy + radius * 1.2f, paint);
                paint.setColor(Ui.RED); paint.setTextSize(24 * density); paint.setTextAlign(Paint.Align.CENTER);
                canvas.drawText("You died", cx, cy + radius * 2.1f, paint); paint.setTextAlign(Paint.Align.LEFT);
                postInvalidateOnAnimation();
            }
        }
    }
}
