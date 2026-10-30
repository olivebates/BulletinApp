package com.bulletin.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

@android.annotation.SuppressLint("ViewConstructor")
final class ActivityChart extends View {
    private final Statistics data;
    private final Ui ui;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF oval = new RectF();
    ActivityChart(Context context, Statistics data, Ui ui) {
        super(context); this.data = data; this.ui = ui;
        setContentDescription((data.laws ? "Law reports" : "Quest focus time") + " pie chart. Total " + Statistics.amount(data.total, data.laws));
    }
    static int color(Statistics.Series s, int index, boolean laws) {
        if (s.id.equals("other")) return Color.rgb(145, 153, 157);
        return Color.HSVToColor(new float[]{laws ? 0 : s.hue, .48f + (index % 3) * .12f, .94f - (index % 4) * .09f});
    }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float radius = Math.min(getWidth() / 2f - ui.dp(12), getHeight() / 2f - ui.dp(22));
        float x = getWidth() / 2f, y = getHeight() / 2f - ui.dp(8);
        oval.set(x - radius, y - radius, x + radius, y + radius);
        float angle = -90;
        if (data.total == 0) { paint.setColor(ui.border); paint.setAlpha(45); canvas.drawOval(oval, paint); paint.setAlpha(255); }
        for (int i = 0; i < data.slices.size(); i++) {
            Statistics.Series s = data.slices.get(i); float sweep = (float) (360d * s.value / Math.max(1, data.total));
            paint.setColor(color(s, i, data.laws)); canvas.drawArc(oval, angle, sweep, true, paint); angle += sweep;
        }
        paint.setColor(ui.text); paint.setTextSize(ui.dp(12));
        String total = Statistics.amount(data.total, data.laws); canvas.drawText(total, x - paint.measureText(total) / 2, getHeight() - ui.dp(5), paint);
    }
}
