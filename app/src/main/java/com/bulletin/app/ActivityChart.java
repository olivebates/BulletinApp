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
        // Keep the outline saturation, with darker fills and hue-only variation.
        float hue = ((index * 137.508f) % 360) / 60f;
        float chroma = .54f * .53f, low = .54f - chroma;
        float middle = chroma * (1 - Math.abs(hue % 2 - 1));
        float r = 0, g = 0, b = 0;
        switch ((int) hue) {
            case 0: r = chroma; g = middle; break;
            case 1: r = middle; g = chroma; break;
            case 2: g = chroma; b = middle; break;
            case 3: g = middle; b = chroma; break;
            case 4: r = middle; b = chroma; break;
            default: r = chroma; b = middle;
        }
        return 0xff000000 | (Math.round((r + low) * 255) << 16)
            | (Math.round((g + low) * 255) << 8) | Math.round((b + low) * 255);
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
            paint.setStyle(Paint.Style.FILL);paint.setColor(color(s,i,data.laws));canvas.drawArc(oval,angle,sweep,true,paint);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(ui.dp(1.5f));paint.setColor(ui.background);canvas.drawArc(oval,angle,sweep,true,paint);paint.setStyle(Paint.Style.FILL);angle+=sweep;
        }
        drawLabels(canvas, x, y, radius);
        paint.setColor(ui.text); paint.setTextSize(ui.dp(12));
        String total = Statistics.amount(data.total, data.laws); canvas.drawText(total, x - paint.measureText(total) / 2, getHeight() - ui.dp(5), paint);
    }
    private void drawLabels(Canvas canvas, float x, float y, float radius) {
        float angle = -90;
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(ui.dp(9));
        for (Statistics.Series s : data.slices) {
            double fraction = (double) s.value / Math.max(1, data.total);
            float sweep = (float) (360 * fraction);
            float middle = angle + sweep / 2;
            double radians = Math.toRadians(middle);
            boolean compact = fraction < .10;
            // Center each label on its own sector; small sectors use a radial single line.
            double halfSweep = Math.toRadians(sweep / 2);
            float distance = compact ? radius * .60f
                : (float) (radius * 2 / 3 * Math.sin(halfSweep) / Math.max(.00001, halfSweep));
            float labelX = x + (float) Math.cos(radians) * distance;
            float labelY = y + (float) Math.sin(radians) * distance;
            String percentage = String.format(java.util.Locale.getDefault(), "%.1f%%", fraction * 100);
            float width = compact ? radius * .72f : Math.min(ui.dp(94), radius * .95f);
            String suffix = compact ? " · " + percentage : "";
            String name = android.text.TextUtils.ellipsize(s.name, new android.text.TextPaint(paint),
                Math.max(0, width - paint.measureText(suffix)), android.text.TextUtils.TruncateAt.END).toString();
            int save = canvas.save();
            canvas.translate(labelX, labelY);
            if (compact) {
                float rotation = (middle % 360 + 360) % 360;
                if (rotation > 90 && rotation < 270) rotation += 180;
                canvas.rotate(rotation);
            }
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(ui.dp(1)); paint.setColor(ui.background);
            drawLabel(canvas, name, percentage, compact);
            paint.setStyle(Paint.Style.FILL); paint.setColor(ui.text);
            drawLabel(canvas, name, percentage, compact);
            canvas.restoreToCount(save);
            angle += sweep;
        }
        paint.setTextAlign(Paint.Align.LEFT);
    }
    private void drawLabel(Canvas canvas, String name, String percentage, boolean compact) {
        float centerBaseline = -(paint.ascent() + paint.descent()) / 2;
        if (compact) canvas.drawText(name + " · " + percentage, 0, centerBaseline, paint);
        else {
            canvas.drawText(name, 0, centerBaseline - ui.dp(5.5f), paint);
            canvas.drawText(percentage, 0, centerBaseline + ui.dp(5.5f), paint);
        }
    }
}