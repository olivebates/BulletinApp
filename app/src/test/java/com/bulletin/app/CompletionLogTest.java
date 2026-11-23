package com.bulletin.app;
import org.junit.Test;
import java.time.*;
import java.util.*;
import static org.junit.Assert.*;
public class CompletionLogTest {
    private final ZoneId zone=ZoneId.of("UTC");
    private final long now=Instant.parse("2026-11-11T12:00:00Z").toEpochMilli();
    private Game.Quest quest(Game g,boolean repeat){Game.Quest q=new Game.Quest();q.name="Task";q.repeatable=repeat;q.forever=true;q.firstDay=g.day;g.addQuest(q);return q;}
    private long timedCompletion(Game g,Game.Quest q,long start){assertTrue(g.start(q,start));long end=g.session.endsAt;g.advance(end,zone);return end;}
    @Test public void repeatsEarnMoneyAndCountWithoutAnotherDailyCheck() throws Exception {
        Game g=new Game(now,zone);Game.Quest q=quest(g,true);g.energy=Game.DEAD;
        timedCompletion(g,q,now);long firstCoins=g.coins;g.completionLog.acknowledge(q.id);
        long ready=g.session.endsAt;g.advance(ready,zone);timedCompletion(g,q,ready+1);
        assertEquals(2,q.completions);assertTrue(g.coins>firstCoins);assertTrue(g.completionLog.pending.isEmpty());assertEquals(Game.REST,g.session.phase);
        g=GameCodec.decode(GameCodec.encode(g).toString(),g.session.endsAt,zone);g.advance(g.session.endsAt,zone);q=g.quests.get(0);
        timedCompletion(g,q,now+600000);assertEquals(3,q.completions);assertTrue(g.completionLog.pending.isEmpty());
        long tomorrow=now+86400000L;g.advance(tomorrow,zone);timedCompletion(g,q,tomorrow);
        assertEquals(1,q.completions);assertEquals(1,g.completionLog.pending.size());
    }
    @Test public void repeatsDoNotQueueAnotherCheckWhileFirstOneIsStillPending(){Game g=new Game(now,zone);Game.Quest q=quest(g,true);g.energy=Game.DEAD;timedCompletion(g,q,now);long first=g.completionLog.earned(q.id),ready=g.session.endsAt;g.advance(ready,zone);timedCompletion(g,q,ready+1);assertEquals(2,q.completions);assertEquals(1,g.completionLog.pending.size());assertEquals(first,g.completionLog.earned(q.id));}
    @Test public void oldRepeatReceiptsCannotReopenAnAcknowledgedDay() throws Exception {CompletionLog old=new CompletionLog();old.record("a",now,20,50,false);old.acknowledge("a");old.record("a",now+1,20,50,false);old.record("b",now+2,20,50,false);old.record("b",now+3,20,50,false);CompletionLog loaded=new CompletionLog();loaded.decode(old.encode());assertEquals(1,loaded.pending.size());assertEquals("b",loaded.pending.get(0).id);}
    @Test public void daysAreUniqueOrderedAndSpecificToAcknowledgedTask(){CompletionLog log=new CompletionLog();log.record("a",now,20,30,false);log.record("a",now+1,20,40,false);log.record("a",now+2,18,50,false);log.record("b",now+3,19,90,true);assertEquals(120,log.earned("a"));log.acknowledge("a");assertEquals(Arrays.asList(18L,20L),new ArrayList<>(log.days("a")));assertTrue(log.days("b").isEmpty());assertEquals(1,log.pending.size());assertEquals("b",log.pending.get(0).id);}
    @Test public void acknowledgementDoesNotAwardTwiceOrRestartRest() throws Exception {Game g=new Game(now,zone);Game.Quest q=quest(g,true);g.energy=Game.DEAD;assertTrue(g.start(q,now));long end=g.session.endsAt;g.advance(end,zone);assertEquals(Game.REST,g.session.phase);long restEnd=g.session.endsAt,coins=g.coins;assertEquals(g.history.get(0).coinsDelta,g.completionLog.earned(q.id));g.completionLog.acknowledge(q.id);g.completionLog.acknowledge(q.id);assertEquals(coins,g.coins);assertEquals(restEnd,g.session.endsAt);assertEquals(Collections.singletonList(g.day),new ArrayList<>(g.completionLog.days(q.id)));}
    @Test public void pendingTaskAndRewardSurviveCompressedSaveAndReload() throws Exception {Game g=new Game(now,zone);Game.Quest a=quest(g,false),b=quest(g,false);g.completeInstant(a,now);g.completeInstant(b,now+1);long reward=g.completionLog.earned(a.id);g=GameCodec.decode(SaveCompression.pack(GameCodec.encode(g).toString()),now+2,zone);assertEquals(2,g.completionLog.pending.size());assertEquals(reward,g.completionLog.earned(a.id));g.completionLog.acknowledge(a.id);g=GameCodec.decode(GameCodec.encode(g).toString(),now+2,zone);assertEquals(1,g.completionLog.pending.size());assertEquals(1,g.completionLog.days(a.id).size());assertEquals(b.id,g.completionLog.pending.get(0).id);}
    @Test public void undoAndDeletionRemovePendingCelebrations(){Game g=new Game(now,zone);Game.Quest a=quest(g,false),b=quest(g,false);g.completeInstant(a,now);g.completeInstant(b,now+1);g.completionLog.acknowledge(a.id);assertTrue(g.restoreOneOff(a.id,now+2));assertTrue(g.completionLog.days(a.id).isEmpty());g.deleteQuest(b);assertTrue(g.completionLog.pending.isEmpty());}
    @Test public void oldWeeklyHistoryDoesNotInventCompletionDates(){CompletionLog log=new CompletionLog();Game.HistoryEntry daily=new Game.HistoryEntry(Game.QUEST_HISTORY,"a","A",now);daily.precision=1;daily.bucketDay=100;Game.HistoryEntry weekly=new Game.HistoryEntry(Game.QUEST_HISTORY,"b","B",now);weekly.precision=7;weekly.bucketDay=90;log.importHistory(Arrays.asList(daily,weekly),zone);assertEquals(Collections.singletonList(100L),new ArrayList<>(log.days("a")));assertTrue(log.days("b").isEmpty());assertTrue(log.pending.isEmpty());}
}
