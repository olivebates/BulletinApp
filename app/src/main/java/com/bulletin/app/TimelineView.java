package com.bulletin.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.View;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** A row shows the rolling three-calendar-month window, with one gold mark per event. */
@android.annotation.SuppressLint("ViewConstructor") // Created from code with an explicit game and entity, never inflated from XML.
final class TimelineView extends View {
    private final Game game;
    private final Ui ui;
    private final String kind, id, name;
    private final TextPaint paint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final android.util.SparseIntArray stacks = new android.util.SparseIntArray();
    private final int totalCount;
    private final boolean showName;
    private static final DateTimeFormatter LABEL = DateTimeFormatter.ofPattern("d MMM");
    TimelineView(Context context, Game game, Ui ui, String kind, String id, String name, boolean showName) {
        super(context); this.game = game; this.ui = ui; this.kind = kind; this.id = id; this.name = name;
        this.showName = showName;
        setMinimumHeight(ui.dp(48));
        long start = game.timelineStart(game.day); int count = 0;
        for (Game.HistoryEntry e : game.history) if (e.kind.equals(kind) && e.id.equals(id)
                && Game.localDay(e.at, ZoneId.systemDefault()) >= start) count += e.count;
        totalCount = count;
        setContentDescription(name + ": " + count + (kind.equals(Game.QUEST_HISTORY) ? " completions" : " fine reports")
            + " since " + LocalDate.ofEpochDay(start)); setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
    }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float left = ui.dp(10), right = getWidth() - ui.dp(10), y = ui.dp(26);
        paint.setColor(ui.text); paint.setTextSize(ui.dp(11)); paint.setStyle(Paint.Style.FILL);
        String countLabel = "×" + totalCount;
        float countWidth = paint.measureText(countLabel);
        if (showName) canvas.drawText(TextUtils.ellipsize(name, paint, Math.max(1, right - left - countWidth - ui.dp(8)), TextUtils.TruncateAt.END).toString(), left, ui.dp(13), paint);
        paint.setColor(Ui.GOLD); canvas.drawText(countLabel, right - countWidth, ui.dp(13), paint);
        paint.setColor(ui.border); paint.setStrokeWidth(2); canvas.drawLine(left, y, right, y, paint);
        ZoneId zone = ZoneId.systemDefault();
        LocalDate startDay = LocalDate.ofEpochDay(game.timelineStart(game.day)), endDay = LocalDate.ofEpochDay(game.day);
        long start = startDay.atStartOfDay(zone).toInstant().toEpochMilli();
        long end = endDay.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli();
        stacks.clear();
        for (Game.HistoryEntry e : game.history) {
            if (!e.kind.equals(kind) || !e.id.equals(id) || e.at < start || e.at >= end) continue;
            float x = left + (right - left) * (float) ((double) (e.at - start) / Math.max(1, end - start));
            int bucket = (int) (x / ui.dp(5)), stack = stacks.get(bucket, 0);
            for (int n = 0; n < Math.min(9, e.count); n++) {
                int level = (stack + n) % 3;
                float offset = level == 0 ? 0 : level == 1 ? -ui.dp(4) : ui.dp(4);
                paint.setColor(Ui.GOLD); canvas.drawCircle(x, y + offset, ui.dp(2), paint);
            }
            stacks.put(bucket, stack + e.count);
        }
        paint.setColor(ui.muted); paint.setTextSize(ui.dp(8));
        canvas.drawText(startDay.format(LABEL), left, ui.dp(44), paint);
        String last = endDay.format(LABEL); canvas.drawText(last, right - paint.measureText(last), ui.dp(44), paint);
        if (endDay.toEpochDay() - startDay.toEpochDay() > 3) {
            String middle = startDay.plusDays((endDay.toEpochDay() - startDay.toEpochDay()) / 2).format(LABEL);
            canvas.drawText(middle, (left + right - paint.measureText(middle)) / 2, ui.dp(44), paint);
        }
    }
}
