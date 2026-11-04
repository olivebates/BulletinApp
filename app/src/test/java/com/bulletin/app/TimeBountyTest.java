package com.bulletin.app;
import org.junit.Test;
import static org.junit.Assert.*;
import java.time.*;
import org.json.*;

public class TimeBountyTest {
    final ZoneId zone=ZoneId.of("UTC");final long now=Instant.parse("2026-11-01T08:00:00Z").toEpochMilli();
    Game.Quest quest(Game g,boolean repeat,boolean required){Game.Quest q=new Game.Quest();q.name="Task";q.firstDay=g.day;q.forever=true;q.repeatable=repeat;q.required=required;g.addQuest(q);return q;}
    long finish(Game g,Game.Quest q,long at){assertTrue(g.start(q,at));long end=g.session.endsAt;g.advance(end,zone);if(g.session!=null){end=g.session.endsAt;g.advance(end,zone);}return end;}
    @Test public void shortQuestsCarryTimeUntil25MinutesRatherThanIncreasingPerCompletion(){
        Game g=new Game(now,zone);Game.Quest q=quest(g,true,false);g.energy=Game.DEAD;long at=now;
        for(int n=0;n<12;n++)at=finish(g,q,at);assertEquals(100,g.nextBounty);assertEquals(24*60000,g.bountyProgressMillis());
        finish(g,q,at);assertEquals(110,g.nextBounty);assertEquals(60000,g.bountyProgressMillis());
    }
    @Test public void longerQuestsEarnMultipleIncreasesAndRemaindersCrossEnergyChanges(){
        Game g=new Game(now,zone);Game.Quest q=quest(g,true,false);g.energy=Game.EXTREME;long at=finish(g,q,now);
        assertEquals(134,g.nextBounty);assertEquals(15*60000,g.bountyProgressMillis());g.energy=Game.EXTRA;finish(g,q,at);
        assertEquals(163,g.nextBounty);assertEquals(10*60000,g.bountyProgressMillis());
    }
    @Test public void restRequiredInstantAndCanceledQuestsDoNotAddGrowthCredit(){
        Game g=new Game(now,zone);long at=finish(g,quest(g,true,true),now);assertEquals(100,g.nextBounty);assertEquals(0,g.bountyProgressMillis());
        g.completeInstant(quest(g,false,false),at);assertEquals(100,g.nextBounty);Game.Quest q=quest(g,true,false);g.start(q,at);g.cancelQuest(q,at+600000);assertEquals(0,g.bountyProgressMillis());
        g.energy=Game.DEAD;finish(g,q,at+600001);assertEquals(120000,g.bountyProgressMillis());assertEquals(100,g.nextBounty);
    }
    @Test public void pausedFocusAndRemainingTimeSurviveCompressedReloadWithoutCountingWait()throws Exception {
        Game g=new Game(now,zone);Game.Quest q=quest(g,true,false);g.start(q,now);g.pauseQuest(now+600000);assertEquals(600000,q.pausedActiveMillis);
        g=GameCodec.decode(SaveCompression.pack(GameCodec.encode(g).toString()),now+3600000,zone);q=g.quests.get(0);finish(g,q,now+3600000);
        assertEquals(110,g.nextBounty);assertEquals(0,g.bountyProgressMillis());assertEquals(1500000,g.bountyChanges.get(0).bountyMillis);
    }
    @Test public void undoTimedOneOffReplaysLaterCreditsAndReturnsTheirExactRewards(){
        Game g=new Game(now,zone);Game.Quest a=quest(g,false,false),b=quest(g,false,false);a.timed=b.timed=true;g.energy=Game.EXTRA;long at=finish(g,a,now);assertEquals(110,g.nextBounty);assertEquals(20*60000,g.bountyProgressMillis());
        g.energy=Game.NORMAL;finish(g,b,at);assertEquals(121,g.nextBounty);assertTrue(g.restoreOneOff(a.id,at+1800000));assertEquals(110,g.nextBounty);assertEquals(0,g.bountyProgressMillis());assertEquals(33,g.coins);
        assertTrue(g.restoreOneOff(b.id,at+1800001));assertEquals(100,g.nextBounty);assertEquals(0,g.bountyProgressMillis());assertEquals(0,g.coins);
    }
    @Test public void fineAndMidnightResetPartialGrowth(){
        Game g=new Game(now,zone);Game.Quest q=quest(g,true,false);g.energy=Game.MINIMAL;long at=finish(g,q,now);assertEquals(420000,g.bountyProgressMillis());g.incurFine();assertEquals(0,g.bountyProgressMillis());assertEquals(100,g.nextBounty);
        at=finish(g,q,at);assertEquals(420000,g.bountyProgressMillis());g.advance(Instant.parse("2026-11-02T00:00:00Z").toEpochMilli(),zone);assertEquals(0,g.bountyProgressMillis());assertEquals(100,g.nextBounty);
    }
    @Test public void partialCreditsSurviveSaveAndLegacyEarnedIncreasesRemainIntact()throws Exception {
        Game g=new Game(now,zone);g.nextBounty=121;g.bountyBase=100;g.bountyChanges.add(new Game.HistoryEntry("raise","old1","",now-2));g.bountyChanges.add(new Game.HistoryEntry("raise","old2","",now-1));
        Game.Quest q=quest(g,true,false);g.energy=Game.MINIMAL;long at=finish(g,q,now);assertEquals(121,g.nextBounty);assertEquals(420000,g.bountyProgressMillis());
        g=GameCodec.decode(SaveCompression.pack(GameCodec.encode(g).toString()),at,zone);g.energy=Game.EXTRA;finish(g,g.quests.get(0),at);assertEquals(148,g.nextBounty);assertEquals(120000,g.bountyProgressMillis());
    }
    @Test public void oldPausedSaveInfersAlreadyWorkedTimeWithoutRepricingEarnedBounty()throws Exception {
        Game g=new Game(now,zone);g.nextBounty=145;Game.Quest q=quest(g,true,false);g.start(q,now);g.pauseQuest(now+600000);JSONObject old=GameCodec.encodeLegacy(g);old.getJSONArray("quests").getJSONObject(0).remove("pausedActiveMillis");
        Game loaded=GameCodec.decode(old.toString(),now+1800000,zone);assertEquals(145,loaded.nextBounty);assertEquals(600000,loaded.quests.get(0).pausedActiveMillis);finish(loaded,loaded.quests.get(0),now+1800000);assertEquals(160,loaded.nextBounty);
    }
    @Test public void oldActiveSaveInfersElapsedTimeAtItsLastAccountingPoint()throws Exception {
        Game g=new Game(now,zone);Game.Quest q=quest(g,true,false);g.start(q,now);g.session.accountedAt=now+600000;g.session.activeMillis=600000;g.focus.add(new Game.FocusEntry(q.id,q.name,g.day,600000));g.timedMillisToday=600000;
        JSONObject old=GameCodec.encodeLegacy(g);old.getJSONObject("session").remove("activeMillis");Game loaded=GameCodec.decode(old.toString(),now+600000,zone);assertEquals(600000,loaded.session.activeMillis);loaded.advance(loaded.session.endsAt,zone);assertEquals(110,loaded.nextBounty);
    }
    @Test public void undoLegacyRaiseKeepsNewPartialTimeCredits(){
        Game g=new Game(now,zone);Game.Quest old=quest(g,false,false);g.completeInstant(old,now);g.nextBounty=110;g.bountyBase=100;g.undo.get(0).bountyAfter=110;g.bountyChanges.add(new Game.HistoryEntry("raise",old.id,"",now));
        Game.Quest q=quest(g,true,false);g.energy=Game.MINIMAL;long at=finish(g,q,now+1);assertTrue(g.restoreOneOff(old.id,at+1));assertEquals(100,g.nextBounty);assertEquals(420000,g.bountyProgressMillis());assertEquals(31,g.coins);
    }
    @Test public void undoTimeBeforeAFineDoesNotReverseLaterGrowth(){
        Game g=new Game(now,zone);Game.Quest a=quest(g,false,false),b=quest(g,false,false);a.timed=b.timed=true;g.energy=Game.MINIMAL;long at=finish(g,a,now);g.incurFine();g.energy=Game.NORMAL;at=finish(g,b,at);
        assertEquals(110,g.nextBounty);assertTrue(g.restoreOneOff(a.id,at+1));assertEquals(110,g.nextBounty);assertEquals(0,g.bountyProgressMillis());
    }
}