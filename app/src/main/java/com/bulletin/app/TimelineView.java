package com.bulletin.app;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.View;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** All history is available; a shared range is applied to each row, with coarser summary marks. */
@android.annotation.SuppressLint("ViewConstructor")
final class TimelineView extends View {
    private final Game game;private final Ui ui;private final String kind,id,name;private final boolean showName;
    private final TextPaint paint=new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private java.util.Map<Game.HistoryEntry,Integer> summaryColors = new java.util.IdentityHashMap<>();
    private TimelineRange.Window selectedWindow;private long selectedCount;private final java.util.List<Game.HistoryEntry> visible=new java.util.ArrayList<>();
    TimelineView(Context context,Game game,Ui ui,String kind,String id,String name,boolean showName){super(context);this.game=game;this.ui=ui;this.kind=kind;this.id=id;this.name=name;this.showName=showName;setMinimumHeight(ui.dp(48));setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);refreshRange();}
    void refreshRange(){refreshRange(TimelineRange.window(game,kind,System.currentTimeMillis(),ZoneId.systemDefault()));}
    void refreshRange(TimelineRange.Window window){
        summaryColors = TimelineColors.summaries(game);selectedWindow=window;selectedCount=0;visible.clear();ZoneId zone=ZoneId.systemDefault();
        for(Game.HistoryEntry e:game.history)if(e.kind.equals(kind)&&e.id.equals(id)&&TimelineRange.overlaps(e,window.start,window.end,zone)){visible.add(e);selectedCount=Game.add(selectedCount,e.count);}
        setContentDescription(name+": "+selectedCount+(kind.equals(Game.QUEST_HISTORY)?" completions":" fine reports")+" in selected range. Summary periods are included in full.");invalidate();
    }
    @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);ZoneId zone=ZoneId.systemDefault();TimelineRange.Window w=selectedWindow;
        float left=ui.dp(10),right=getWidth()-ui.dp(10),y=ui.dp(26);paint.setStyle(Paint.Style.FILL);paint.setColor(ui.text);paint.setTextSize(ui.dp(11));String count="×"+selectedCount;float width=paint.measureText(count);
        if(showName)canvas.drawText(TextUtils.ellipsize(name,paint,Math.max(1,right-left-width-ui.dp(8)),TextUtils.TruncateAt.END).toString(),left,ui.dp(13),paint);
        paint.setColor(Ui.GOLD);canvas.drawText(count,right-width,ui.dp(13),paint);paint.setColor(ui.border);paint.setStrokeWidth(2);canvas.drawLine(left,y,right,y,paint);
        android.util.SparseIntArray stacks=new android.util.SparseIntArray();
        for(Game.HistoryEntry e:visible){if(!e.kind.equals(kind)||!e.id.equals(id)||!TimelineRange.overlaps(e,w.start,w.end,zone))continue;
            long first=Math.max(w.start,TimelineRange.first(e,zone)),last=Math.min(w.end,TimelineRange.last(e,zone));float x1=left+(right-left)*(float)((double)(first-w.start)/Math.max(1,w.end-w.start)),x2=left+(right-left)*(float)((double)(last-w.start)/Math.max(1,w.end-w.start));
            paint.setColor(summaryColors.getOrDefault(e,Game.FINE_HISTORY.equals(e.kind)?TimelineColors.YELLOW:TimelineColors.energy(e.energy)));if(e.precision>0||e.dateOnly){paint.setAlpha(150);paint.setStrokeWidth(ui.dp(e.precision==7?4:2));canvas.drawLine(x1,y,x2,y,paint);paint.setAlpha(255);canvas.drawCircle((x1+x2)/2,y,ui.dp(2),paint);}
            else{int bucket=(int)(x1/ui.dp(5)),stack=stacks.get(bucket,0);for(int n=0;n<Math.min(9,e.count);n++){int level=(stack+n)%3;canvas.drawCircle(x1,y+(level==0?0:level==1?-ui.dp(4):ui.dp(4)),ui.dp(2),paint);}stacks.put(bucket,stack+(int)Math.min(9,e.count));}
        }
        DateTimeFormatter date=DateTimeFormatter.ofPattern("d MMM yyyy").withZone(zone);paint.setColor(ui.muted);paint.setTextSize(ui.dp(8));canvas.drawText(date.format(Instant.ofEpochMilli(w.start)),left,ui.dp(44),paint);String end=date.format(Instant.ofEpochMilli(w.end));canvas.drawText(end,right-paint.measureText(end),ui.dp(44),paint);
    }
}