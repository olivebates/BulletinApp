package com.bulletin.app;
import org.junit.Test;
import java.time.*;
import static org.junit.Assert.*;

public class MustDoLogTest {
    private final ZoneId zone=ZoneId.of("UTC");
    private final long now=Instant.parse("2026-11-12T12:00:00Z").toEpochMilli();
    private Game.Quest quest(Game g,boolean required,boolean repeat){Game.Quest q=new Game.Quest();q.name="Task";q.required=required;q.repeatable=repeat;q.forever=true;q.firstDay=g.day;g.addQuest(q);return q;}
    private long day(String date){return LocalDate.parse(date).toEpochDay();}
    @Test public void onlyLastOutstandingMustDoCreatesMilestone(){Game g=new Game(now,zone);Game.Quest a=quest(g,true,false),b=quest(g,true,false);g.completeInstant(a,now);assertFalse(g.mustDoLog.hasPending(g.day));g.completeInstant(b,now+1);assertTrue(g.mustDoLog.hasPending(g.day));assertEquals(2,g.completionLog.pending.size());}
    @Test public void optionalCompletionsCannotInventMustDoMilestone(){Game g=new Game(now,zone);g.completeInstant(quest(g,false,false),now);assertFalse(g.mustDoLog.completed(g.day));}
    @Test public void repeatThatFinishesCategoryQueuesMilestoneWithoutAnotherTaskCheck(){
        Game g=new Game(now,zone);Game.Category c=new Game.Category();c.name="Daily";c.mustDo=true;c.requiredTasks=3;g.addCategory(c);Game.Quest q=quest(g,false,true);q.categoryId=c.id;g.energy=Game.DEAD;
        long start=now;
        for(int i=0;i<3;i++){assertTrue(g.start(q,start));g.advance(g.session.endsAt,zone);if(i<2){assertFalse(g.mustDoLog.hasPending(g.day));g.completionLog.acknowledge(q.id);start=g.session.endsAt+1;g.advance(start,zone);}}
        assertTrue(g.completionLog.pending.isEmpty());assertTrue(g.mustDoLog.hasPending(g.day));assertEquals(Game.REST,g.session.phase);
        long coins=g.coins,rest=g.session.endsAt;g.mustDoLog.acknowledge(g.day);assertEquals(coins,g.coins);assertEquals(rest,g.session.endsAt);
    }
    @Test public void acknowledgedMilestoneDoesNotReappearForMoreRequirementsSameDay(){Game g=new Game(now,zone);g.completeInstant(quest(g,true,false),now);g.mustDoLog.acknowledge(g.day);g.completeInstant(quest(g,true,false),now+1);assertTrue(g.mustDoLog.completed(g.day));assertFalse(g.mustDoLog.hasPending(g.day));}
    @Test public void undoLastMustDoReopensMilestone(){Game g=new Game(now,zone);Game.Quest q=quest(g,true,false);g.completeInstant(q,now);g.mustDoLog.acknowledge(g.day);assertTrue(g.restoreOneOff(q.id,now+1));assertFalse(g.mustDoLog.completed(g.day));g.completeInstant(q,now+2);assertTrue(g.mustDoLog.hasPending(g.day));}
    @Test public void savePreservesHistoryPendingAndInitialMonth() throws Exception {Game g=new Game(now,zone);g.mustDoLog.record(day("2026-10-10"));g.mustDoLog.acknowledge(day("2026-10-10"));g.mustDoLog.record(g.day);g=GameCodec.decode(SaveCompression.pack(GameCodec.encode(g).toString()),now,zone);assertTrue(g.mustDoLog.completed(day("2026-10-10")));assertTrue(g.mustDoLog.hasPending(g.day));assertFalse(g.mustDoLog.hasPending(g.day+1));assertEquals(YearMonth.of(2026,10),g.mustDoLog.firstMonth(g.day));}
    @Test public void currentMonthMovesLeftMiddleRightThenWindowRollsAcrossYear(){MustDoLog log=new MustDoLog();log.record(day("2026-11-12"));assertEquals(YearMonth.of(2026,11),log.firstMonth(day("2026-11-30")));assertEquals(YearMonth.of(2026,11),log.firstMonth(day("2026-12-01")));assertEquals(YearMonth.of(2026,11),log.firstMonth(day("2027-01-01")));assertEquals(YearMonth.of(2026,12),log.firstMonth(day("2027-02-01")));assertEquals(YearMonth.of(2027,1),log.firstMonth(day("2027-03-01")));}
    @Test public void calendarUsesRealMonthLengthsIncludingLeapFebruary(){MustDoLog log=new MustDoLog();log.record(day("2027-12-31"));YearMonth first=log.firstMonth(day("2028-02-29"));assertEquals(31,first.lengthOfMonth());assertEquals(31,first.plusMonths(1).lengthOfMonth());assertEquals(29,first.plusMonths(2).lengthOfMonth());}
    @Test public void newCircleCapsAreSixtyPercentAndHalfOfDaily(){assertEquals(.6f*220*3,CompletionVisuals.dailyWaveRadius(3,1080),.001f);assertEquals(.6f*200*.65f,CompletionVisuals.dailyWaveRadius(3,200),.001f);assertEquals(CompletionVisuals.dailyWaveRadius(3,1080)/2,CompletionVisuals.milestoneWaveRadius(3,1080),0);}
}
