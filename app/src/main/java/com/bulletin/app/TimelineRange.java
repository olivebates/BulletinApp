package com.bulletin.app;
import java.time.ZoneId;
final class TimelineRange {
    static final class Window{final long minimum,maximum,start,end;Window(long minimum,long maximum,long start,long end){this.minimum=minimum;this.maximum=maximum;this.start=start;this.end=end;}}
    static Window window(Game g,String kind,long now,ZoneId zone){
        long minimum=HistoryAggregation.millis(g.timelineStart(g.day),zone),maximum=Math.max(minimum+1,now);
        for(Game.HistoryEntry e:g.history)if(e.kind.equals(kind)){long first=e.bucketDay!=Long.MIN_VALUE?HistoryAggregation.millis(e.bucketDay,zone):e.at;minimum=Math.min(minimum,first);maximum=Math.max(maximum,e.at);}
        long chosenStart=kind.equals(Game.QUEST_HISTORY)?g.questTimelineStart:g.lawTimelineStart,chosenEnd=kind.equals(Game.QUEST_HISTORY)?g.questTimelineEnd:g.lawTimelineEnd;
        long start=chosenStart<=0?minimum:Math.max(minimum,Math.min(maximum-1,chosenStart)),end=chosenEnd<=0?maximum:Math.max(start+1,Math.min(maximum,chosenEnd));return new Window(minimum,maximum,start,end);
    }
    static long first(Game.HistoryEntry e,ZoneId zone){return e.precision>0||e.dateOnly?HistoryAggregation.millis(e.bucketDay!=Long.MIN_VALUE?e.bucketDay:Game.localDay(e.at,zone),zone):e.at;}
    static long last(Game.HistoryEntry e,ZoneId zone){if(e.precision==0&&!e.dateOnly)return e.at;long day=e.endDay!=Long.MIN_VALUE?e.endDay:e.bucketDay!=Long.MIN_VALUE?e.bucketDay:Game.localDay(e.at,zone);return HistoryAggregation.millis(day+1,zone)-1;}
    static boolean overlaps(Game.HistoryEntry e,long start,long end,ZoneId zone){return first(e,zone)<=end&&last(e,zone)>=start;}
}