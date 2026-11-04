package com.bulletin.app;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

/** Idempotent age-based aggregation. Today's restore/undo records are never coarsened. */
final class HistoryAggregation {
    static long week(long day){return day-(LocalDate.ofEpochDay(day).getDayOfWeek().getValue()-1);}
    static long millis(long day,ZoneId zone){return LocalDate.ofEpochDay(day).atStartOfDay(zone).toInstant().toEpochMilli();}
    static void aggregate(Game g,ZoneId zone){
        long daily=LocalDate.ofEpochDay(g.day).minusMonths(3).toEpochDay(),year=LocalDate.ofEpochDay(g.day).minusYears(1).toEpochDay();
        List<Game.HistoryEntry> history=new ArrayList<>();Map<String,Game.HistoryEntry> buckets=new LinkedHashMap<>();
        for(Game.HistoryEntry e:g.history){
            long day=e.bucketDay!=Long.MIN_VALUE?e.bucketDay:Game.localDay(e.at,zone);
            if(day>=daily&&e.precision==0){history.add(e);continue;}
            int precision=day<year||e.precision==7?7:1;long start=precision==7?week(day):day,end=precision==7?Math.min(start+6,year-1):day;
            if(e.endDay!=Long.MIN_VALUE)end=Math.max(end,e.endDay);
            String key=e.kind+'\0'+e.id+'\0'+precision+'\0'+start;Game.HistoryEntry b=buckets.get(key);
            if(b==null){b=new Game.HistoryEntry(e.kind,e.id,e.name,millis(start,zone));b.count=0;b.dateOnly=true;b.precision=precision;b.bucketDay=start;b.endDay=end;b.coinsKnown=true;buckets.put(key,b);}
            b.count=Game.add(b.count,e.count);b.coinsDelta=signedAdd(b.coinsDelta,e.coinsDelta);b.coinsKnown&=e.coinsKnown;b.endDay=Math.max(b.endDay,end);b.name=e.name;
        }
        history.addAll(buckets.values());history.sort(Comparator.comparingLong(e->e.at));g.history.clear();g.history.addAll(history);
        Map<String,Game.FocusEntry> focus=new LinkedHashMap<>();
        for(Game.FocusEntry e:g.focus){boolean weekly=e.day<year||e.endDay>e.day;long start=weekly?week(e.day):e.day,end=weekly?Math.max(e.endDay,Math.min(start+6,year-1)):e.day;
            String key=e.id+'\0'+start;Game.FocusEntry b=focus.get(key);if(b==null){b=new Game.FocusEntry(e.id,e.name,start,0);b.endDay=end;focus.put(key,b);}b.millis=Game.add(b.millis,e.millis);b.endDay=Math.max(b.endDay,end);b.name=e.name;
        }
        g.focus.clear();g.focus.addAll(focus.values());g.focus.sort(Comparator.comparingLong(e->e.day));
        aggregateBalances(g,daily,year,zone);
    }
    private static long signedAdd(long a,long b){return Math.max(-Game.LIMIT,Math.min(Game.LIMIT,a+b));}
    private static final class BalanceBucket{
        long at,close,start,end,open,low,high;int precision;
        BalanceBucket(Game.BalancePoint p,long opening,long start,long end,int precision){at=p.at;close=p.cents;this.start=start;this.end=end;open=opening;low=Math.min(opening,p.minimum);high=Math.max(opening,p.maximum);this.precision=precision;}
        Game.BalancePoint point(){return new Game.BalancePoint(at,close,"",start,end,open,low,high,precision);}
    }
    private static void aggregateBalances(Game g,long daily,long year,ZoneId zone){
        if(g.balances.isEmpty())return;List<Game.BalancePoint> out=new ArrayList<>();Map<String,BalanceBucket> buckets=new LinkedHashMap<>();long first=g.balances.get(0).periodStart>0?g.balances.get(0).periodStart:g.balances.get(0).at,previous=g.balances.get(0).opening;
        for(Game.BalancePoint p:g.balances){long day=Game.localDay(p.periodStart>0?p.periodStart:p.at,zone);
            if(day>=daily&&p.precision==0){out.add(p);previous=p.cents;continue;}
            int precision=day<year||p.precision==7?7:1;long start=precision==7?week(day):day,end=precision==7?Math.min(start+6,year-1):day;
            long startAt=Math.max(first,millis(start,zone)),endAt=Math.max(p.periodEnd,millis(end+1,zone));String key=precision+":"+start;BalanceBucket b=buckets.get(key);
            long opening=p.periodStart>0?p.opening:previous;
            if(b==null){b=new BalanceBucket(p,opening,startAt,endAt,precision);buckets.put(key,b);}else{b.at=Math.max(b.at,p.at);b.close=p.cents;b.low=Math.min(b.low,Math.min(opening,p.minimum));b.high=Math.max(b.high,Math.max(opening,p.maximum));b.end=Math.max(b.end,endAt);}
            previous=p.cents;
        }
        for(BalanceBucket b:buckets.values())out.add(b.point());out.sort(Comparator.comparingLong(p->p.at));g.balances.clear();g.balances.addAll(out);
    }
}