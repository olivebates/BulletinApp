package com.bulletin.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@android.annotation.SuppressLint("ViewConstructor") // Requires the live game; it is constructed programmatically.
final class BalanceGraph extends View {
    private final Game game;
    private final Ui ui;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path line = new Path();
    private int selected = -1;
    private long rangeStart, rangeEnd, rangeMin, rangeMax;
    private final android.text.TextPaint infoPaint = new android.text.TextPaint(Paint.ANTI_ALIAS_FLAG);
    BalanceGraph(Context context, Game game, Ui ui) {
        super(context); this.game = game; this.ui = ui; setMinimumHeight(ui.dp(180));
        setBackground(ui.panel(ui.border)); setContentDescription("Balance over time. Current balance: " + Money.format(game.coins));
        setClickable(true);
    }
    @Override public boolean onTouchEvent(android.view.MotionEvent event) {
        switch (event.getActionMasked()) {
            case android.view.MotionEvent.ACTION_DOWN:
            case android.view.MotionEvent.ACTION_MOVE:
                getParent().requestDisallowInterceptTouchEvent(true);
                double distance = Double.MAX_VALUE; selected = -1;
                for (int i = 0; i < game.balances.size(); i++) {
                    Game.BalancePoint point = game.balances.get(i);
                    float x = ui.dp(86) + (getWidth() - ui.dp(100)) * (float) ((double) (point.at - rangeStart) / Math.max(1, rangeEnd - rangeStart));
                    float y = getHeight() - ui.dp(30) - (getHeight() - ui.dp(75)) * (float) ((double) (point.cents - rangeMin) / Math.max(1, rangeMax - rangeMin));
                    double delta = Math.hypot(event.getX() - x, event.getY() - y);
                    if (delta <= distance) { distance = delta; selected = i; }
                }
                if (selected >= 0) { Game.BalancePoint point = game.balances.get(selected); setContentDescription(Money.format(point.cents) + ". " + point.cause); }
                invalidate(); return true;
            case android.view.MotionEvent.ACTION_UP:
                performClick();
                // Fall through: the cursor always returns to the current balance on release.
            case android.view.MotionEvent.ACTION_CANCEL:
                selected = -1; setContentDescription("Balance over time. Current balance: " + Money.format(game.coins));
                getParent().requestDisallowInterceptTouchEvent(false); invalidate(); return true;
            default: return super.onTouchEvent(event);
        }
    }
    @Override public boolean performClick() { super.performClick(); return true; }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float left = ui.dp(86), right = getWidth() - ui.dp(14), top = ui.dp(45), bottom = getHeight() - ui.dp(30);
        if (right <= left || bottom <= top) return;
        paint.setStyle(Paint.Style.FILL); paint.setTextSize(ui.dp(13)); paint.setColor(ui.text);
        canvas.drawText("Balance", ui.dp(12), ui.dp(24), paint);
        String balance = Money.format(game.coins); paint.setColor(Ui.GOLD);
        canvas.drawText(balance, Math.max(ui.dp(100), right - paint.measureText(balance)), ui.dp(24), paint);
        long min = Math.min(0, game.coins), max = Math.max(0, game.coins);
        for (Game.BalancePoint p : game.balances) { min = Math.min(min, p.cents); max = Math.max(max, p.cents); }
        if (min == max) max = min + 100;
        long start = game.balances.isEmpty() ? System.currentTimeMillis() : game.balances.get(0).at;
        long latest = game.balances.isEmpty() ? start : game.balances.get(game.balances.size() - 1).at;
        long end = Math.max(Math.max(start + 1, latest), System.currentTimeMillis());
        rangeStart = start; rangeEnd = end; rangeMin = min; rangeMax = max;
        paint.setTextSize(ui.dp(9)); paint.setStrokeWidth(1);
        for (int i = 0; i <= 2; i++) {
            float y = top + (bottom - top) * i / 2;
            paint.setColor(ui.border); paint.setAlpha(70); canvas.drawLine(left, y, right, y, paint); paint.setAlpha(255);
            paint.setColor(ui.muted); canvas.drawText(Money.format(max - (max - min) * i / 2), ui.dp(8), y + ui.dp(3), paint);
        }
        line.reset(); boolean first = true;
        for (Game.BalancePoint point : game.balances) {
            float x = left + (right - left) * (float) ((double) (point.at - start) / (end - start));
            float y = bottom - (bottom - top) * (float) ((double) (point.cents - min) / (max - min));
            if (first) { line.moveTo(x, y); first = false; } else line.lineTo(x, y);
        }
        float lastY = bottom - (bottom - top) * (float) ((double) (game.coins - min) / (max - min));
        if (first) line.moveTo(left, lastY); line.lineTo(right, lastY);
        paint.setColor(Ui.GOLD); paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(ui.dp(1.5f)); canvas.drawPath(line, paint);
        paint.setStyle(Paint.Style.FILL);
        Game.BalancePoint inspected = selected >= 0 && selected < game.balances.size() ? game.balances.get(selected) : null;
        float dotX = inspected == null ? right : left + (right - left) * (float) ((double) (inspected.at - start) / (end - start));
        float dotY = inspected == null ? lastY : bottom - (bottom - top) * (float) ((double) (inspected.cents - min) / (max - min));
        canvas.drawCircle(dotX, dotY, ui.dp(4), paint);
        paint.setColor(ui.muted); paint.setTextSize(ui.dp(10));
        DateTimeFormatter date = DateTimeFormatter.ofPattern("d MMM").withZone(ZoneId.systemDefault());
        canvas.drawText(date.format(Instant.ofEpochMilli(start)), left, getHeight() - ui.dp(10), paint);
        String last = date.format(Instant.ofEpochMilli(end)); canvas.drawText(last, right - paint.measureText(last), getHeight() - ui.dp(10), paint);
        if (inspected != null) {
            paint.setColor(ui.background); canvas.drawRect(ui.dp(8), ui.dp(31), getWidth() - ui.dp(8), ui.dp(82), paint);
            paint.setColor(ui.border); paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(1);
            canvas.drawRect(ui.dp(8), ui.dp(31), getWidth() - ui.dp(8), ui.dp(82), paint); paint.setStyle(Paint.Style.FILL);
            paint.setTextSize(ui.dp(11)); paint.setColor(inspected.cents < 0 ? Ui.RED : Ui.GOLD);
            String value = Money.format(inspected.cents) + "  ·  " + DateTimeFormatter.ofPattern("d MMM yyyy HH:mm").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(inspected.at));
            canvas.drawText(value, ui.dp(16), ui.dp(50), paint);
            paint.setColor(ui.text);
            infoPaint.set(paint);
            String cause = android.text.TextUtils.ellipsize(inspected.cause, infoPaint, getWidth() - ui.dp(32), android.text.TextUtils.TruncateAt.END).toString();
            canvas.drawText(cause, ui.dp(16), ui.dp(70), paint);
        }
    }
}
