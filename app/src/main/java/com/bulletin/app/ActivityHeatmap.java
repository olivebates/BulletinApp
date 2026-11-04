package com.bulletin.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@android.annotation.SuppressLint("ViewConstructor")
final class ActivityHeatmap extends View {
    private final Statistics.Series series;
    private final Statistics data;
    private final Ui ui;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final long start;
    private final int columns;
    ActivityHeatmap(Context context, Statistics data, Statistics.Series series, Ui ui) {
        super(context); this.data = data; this.series = series; this.ui = ui;
        start = data.firstDay - (LocalDate.ofEpochDay(data.firstDay).getDayOfWeek().getValue() - 1);
        columns = (int) ((data.lastDay - start) / 7 + 1);
        setMinimumWidth(Math.max(context.getResources().getDisplayMetrics().widthPixels - ui.dp(32), ui.dp(600)));
        setMinimumHeight(ui.dp(140));
        setContentDescription(series.name + ". Daily " + (data.laws ? "law reports" : "focus time") + " from " + LocalDate.ofEpochDay(data.firstDay) + " to " + LocalDate.ofEpochDay(data.lastDay));
    }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float step = Math.min(ui.dp(13), (getWidth() - ui.dp(28)) / (float) columns), left = ui.dp(24), top = ui.dp(22);
        paint.setTextSize(ui.dp(8)); paint.setColor(ui.muted);
        canvas.drawText("M", 0, top + step * .75f, paint); canvas.drawText("W", 0, top + step * 2.75f, paint); canvas.drawText("F", 0, top + step * 4.75f, paint);
        int previousMonth = -1;
        for (long day = start; day <= data.lastDay; day++) {
            int col = (int) ((day - start) / 7), row = (int) ((day - start) % 7);
            if (day < data.firstDay) continue;
            LocalDate date = LocalDate.ofEpochDay(day);
            if (date.getMonthValue() != previousMonth && row == 0) {
                paint.setColor(ui.muted); paint.setAlpha(255); canvas.drawText(date.format(DateTimeFormatter.ofPattern("MMM")), left + col * step, ui.dp(13), paint); previousMonth = date.getMonthValue();
            }
            long value = series.days.getOrDefault(day, 0L);
            paint.setColor(ui.border); paint.setAlpha(series.heatAlpha(value));
            canvas.drawRoundRect(left + col * step, top + row * step, left + (col + 1) * step - 2, top + (row + 1) * step - 2, 1, 1, paint);
        }
        paint.setAlpha(255); paint.setColor(ui.muted); paint.setTextSize(ui.dp(9));
        canvas.drawText(LocalDate.ofEpochDay(data.firstDay) + " — " + LocalDate.ofEpochDay(data.lastDay), left, getHeight() - ui.dp(8), paint);
    }
}
