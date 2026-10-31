package com.bulletin.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@android.annotation.SuppressLint("ViewConstructor")
final class BalanceGraph extends View {
    private final Game game; private final Ui ui;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path line = new Path();
    private final android.text.TextPaint infoPaint = new android.text.TextPaint(Paint.ANTI_ALIAS_FLAG);
    private List<Game.BalancePoint> points = new ArrayList<>();
    private int selected = -1;
    private long rangeStart, rangeEnd, rangeMin, rangeMax, requestedStart, requestedEnd;
    BalanceGraph(Context context, Game game, Ui ui) {
        super(context); this.game = game; this.ui = ui; setMinimumHeight(ui.dp(180));
        setBackground(ui.panel(ui.border)); setContentDescription("Balance over selected dates"); setClickable(true);
        requestedStart = game.balanceRangeStart; requestedEnd = game.balanceRangeEnd;
    }
    void setRange(long start, long end) { requestedStart = start; requestedEnd = end; selected = -1; invalidate(); }
    private float x(long at) { return ui.dp(86) + (getWidth() - ui.dp(100)) * (float)((double)(at - rangeStart) / Math.max(1, rangeEnd - rangeStart)); }
    private float y(long cents) { return getHeight() - ui.dp(30) - (getHeight() - ui.dp(75)) * (float)((double)(cents - rangeMin) / Math.max(1, rangeMax - rangeMin)); }
    @Override public boolean onTouchEvent(android.view.MotionEvent event) {
        switch (event.getActionMasked()) {
            case android.view.MotionEvent.ACTION_DOWN:
            case android.view.MotionEvent.ACTION_MOVE:
                getParent().requestDisallowInterceptTouchEvent(true); double distance = Double.MAX_VALUE; selected = -1;
                for (int i = 0; i < points.size(); i++) {
                    Game.BalancePoint p = points.get(i); double delta = Math.hypot(event.getX() - x(p.at), event.getY() - y(p.cents));
                    if (delta <= distance) { distance = delta; selected = i; }
                }
                if (selected >= 0) { Game.BalancePoint p = points.get(selected); setContentDescription(Money.format(p.cents) + ". " + p.cause); }
                invalidate(); return true;
            case android.view.MotionEvent.ACTION_UP: performClick();
            case android.view.MotionEvent.ACTION_CANCEL:
                selected = -1; setContentDescription("Balance over selected dates"); getParent().requestDisallowInterceptTouchEvent(false); invalidate(); return true;
            default: return super.onTouchEvent(event);
        }
    }
    @Override public boolean performClick() { super.performClick(); return true; }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        long first = game.balances.isEmpty() ? System.currentTimeMillis() : game.balances.get(0).at;
        long latest = game.balances.isEmpty() ? first : game.balances.get(game.balances.size()-1).at;
        long availableEnd = Math.max(first + 1, Math.max(latest, System.currentTimeMillis()));
        rangeStart = requestedStart <= 0 ? first : Math.max(first, Math.min(availableEnd - 1, requestedStart));
        rangeEnd = requestedEnd <= 0 ? availableEnd : Math.max(rangeStart + 1, Math.min(availableEnd, requestedEnd));
        points = BalanceHistory.window(game.balances, rangeStart, rangeEnd, game.coins);
        rangeMin = 0; rangeMax = 0;
        for (Game.BalancePoint p : points) { rangeMin = Math.min(rangeMin, p.cents); rangeMax = Math.max(rangeMax, p.cents); }
        if (rangeMin == rangeMax) rangeMax = rangeMin + 100;
        float left = ui.dp(86), right = getWidth() - ui.dp(14), top = ui.dp(45), bottom = getHeight() - ui.dp(30);
        if (right <= left || bottom <= top) return;
        paint.setStyle(Paint.Style.FILL); paint.setTextSize(ui.dp(13)); paint.setColor(ui.text); canvas.drawText("Balance", ui.dp(12), ui.dp(24), paint);
        long endingBalance = points.get(points.size()-1).cents;
        String balance = Money.format(endingBalance); paint.setColor(endingBalance < 0 ? Ui.RED : Ui.GOLD);
        canvas.drawText(balance, Math.max(ui.dp(100), right - paint.measureText(balance)), ui.dp(24), paint);
        paint.setTextSize(ui.dp(9)); paint.setStrokeWidth(1);
        for (int i = 0; i <= 2; i++) {
            float yy = top + (bottom-top)*i/2; paint.setColor(ui.border); paint.setAlpha(70); canvas.drawLine(left, yy, right, yy, paint); paint.setAlpha(255);
            paint.setColor(ui.muted); canvas.drawText(Money.format(rangeMax-(rangeMax-rangeMin)*i/2), ui.dp(8), yy+ui.dp(3), paint);
        }
        line.reset(); boolean firstPoint = true;
        for (Game.BalancePoint p : points) { if (firstPoint) { line.moveTo(x(p.at), y(p.cents)); firstPoint = false; } else line.lineTo(x(p.at), y(p.cents)); }
        paint.setColor(Ui.GOLD); paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(ui.dp(1.5f)); canvas.drawPath(line, paint); paint.setStyle(Paint.Style.FILL);
        Game.BalancePoint inspected = selected >= 0 && selected < points.size() ? points.get(selected) : null;
        Game.BalancePoint dot = inspected != null ? inspected : points.get(points.size()-1); canvas.drawCircle(x(dot.at), y(dot.cents), ui.dp(4), paint);
        paint.setColor(ui.muted); paint.setTextSize(ui.dp(10)); DateTimeFormatter date = DateTimeFormatter.ofPattern("d MMM yyyy").withZone(ZoneId.systemDefault());
        canvas.drawText(date.format(Instant.ofEpochMilli(rangeStart)), left, getHeight()-ui.dp(10), paint);
        String last = date.format(Instant.ofEpochMilli(rangeEnd)); canvas.drawText(last, right-paint.measureText(last), getHeight()-ui.dp(10), paint);
        if (inspected != null) {
            paint.setColor(ui.background); canvas.drawRect(ui.dp(8),ui.dp(31),getWidth()-ui.dp(8),ui.dp(82),paint);
            paint.setColor(ui.border); paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(1); canvas.drawRect(ui.dp(8),ui.dp(31),getWidth()-ui.dp(8),ui.dp(82),paint);paint.setStyle(Paint.Style.FILL);
            paint.setTextSize(ui.dp(11));paint.setColor(inspected.cents < 0 ? Ui.RED : Ui.GOLD);
            String value=Money.format(inspected.cents)+"  ·  "+DateTimeFormatter.ofPattern("d MMM yyyy HH:mm").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(inspected.at));
            canvas.drawText(value,ui.dp(16),ui.dp(50),paint);paint.setColor(ui.text);infoPaint.set(paint);
            canvas.drawText(android.text.TextUtils.ellipsize(inspected.cause,infoPaint,getWidth()-ui.dp(32),android.text.TextUtils.TruncateAt.END).toString(),ui.dp(16),ui.dp(70),paint);
        }
    }
}