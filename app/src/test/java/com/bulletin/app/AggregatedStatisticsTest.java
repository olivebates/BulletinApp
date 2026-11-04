package com.bulletin.app;
import org.junit.Test;
import java.time.*;
import static org.junit.Assert.*;
public class AggregatedStatisticsTest{
 private final long now=Instant.parse("2026-10-31T12:00:00Z").toEpochMilli();private final ZoneId zone=ZoneId.of("UTC");
 @Test public void allTimePieTotalsAndCurrentYearHeatmapSurviveAggregation()throws Exception{
  Game g=new Game(now,zone);Game.Quest q=new Game.Quest();q.name="Focus";q.repeatable=true;q.forever=true;q.firstDay=g.day-1500;g.addQuest(q);Game.Fine law=new Game.Fine();law.name="Law";g.addLaw(law);
  for(int age:new int[]{0,10,100,400,1000}){long day=g.day-age,at=HistoryAggregation.millis(day,zone)+3600000;g.history.add(new Game.HistoryEntry(Game.QUEST_HISTORY,q.id,q.name,at));g.history.add(new Game.HistoryEntry(Game.FINE_HISTORY,law.id,law.name,at));g.focus.add(new Game.FocusEntry(q.id,q.name,day,(age+1)*1000L));}
  Statistics before=new Statistics(g,false,now,zone),lawBefore=new Statistics(g,true,now,zone);long max=before.maximum;
  HistoryAggregation.aggregate(g,zone);Game restored=GameCodec.decode(SaveCompression.pack(GameCodec.encode(g).toString()),now,zone);
  Statistics after=new Statistics(restored,false,now,zone,Statistics.rangeStart(g.day,4)),lawAfter=new Statistics(restored,true,now,zone,Statistics.rangeStart(g.day,4));
  assertEquals(before.total,after.total);assertEquals(lawBefore.total,lawAfter.total);assertEquals(max,after.maximum);assertEquals(1000,new Statistics(restored,false,now,zone,g.day).total);
  assertEquals(1,new Statistics(restored,true,now,zone,g.day).total);
 }
 @Test public void compactSummaryMetadataAndMoneySurviveSaveAndRestore()throws Exception{
  Game g=new Game(now,zone);long day=g.day-500;Game.HistoryEntry e=new Game.HistoryEntry(Game.FINE_HISTORY,"l1","Law",HistoryAggregation.millis(day,zone));e.count=3;e.coinsKnown=true;e.coinsDelta=-250;g.history.add(e);HistoryAggregation.aggregate(g,zone);
  Game restored=GameCodec.decode(GameCodec.encode(g).toString(),now,zone);Game.HistoryEntry summary=restored.history.get(0);assertEquals(7,summary.precision);assertEquals(3,summary.count);assertEquals(-250,summary.coinsDelta);assertTrue(summary.coinsKnown);assertEquals(g.history.get(0).bucketDay,summary.bucketDay);assertEquals(g.history.get(0).endDay,summary.endDay);
 }
}