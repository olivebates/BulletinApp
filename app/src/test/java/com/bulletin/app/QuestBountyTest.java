package com.bulletin.app;

import org.junit.Test;
import java.time.Instant;
import java.time.ZoneId;
import static org.junit.Assert.*;

public class QuestBountyTest {
    private final long now = Instant.parse("2026-10-29T12:00:00Z").toEpochMilli();
    private final ZoneId zone = ZoneId.of("UTC");
    private Game.Quest quest(Game g, boolean repeatable) {
        Game.Quest q = new Game.Quest(); q.firstDay = g.day; q.bountySince = now; q.repeatable = repeatable; g.addQuest(q); return q;
    }
    @Test public void growthCompoundsOnlyAtWholeHoursAndCapsAtSevenTimesBaseline() {
        assertEquals(10000, Game.agedBounty(10000, now, now + 3599999));
        assertEquals(10040, Game.agedBounty(10000, now, now + 3600000));
        assertEquals(10081, Game.agedBounty(10000, now, now + 7200000));
        assertEquals(70000, Game.agedBounty(10000, now, now + 1000 * 3600000L));
        assertEquals(Game.LIMIT, Game.agedBounty(Game.LIMIT, now, now + 3600000));
    }
    @Test public void growthIsIndividualAndUsesCurrentEnergyAndBaseBounty() {
        Game g = new Game(now, zone); g.nextBounty = 10000;
        Game.Quest old = quest(g, true), fresh = quest(g, true); old.bountySince = now - 3600000;
        assertEquals(10040, g.bountyFor(old, now)); assertEquals(10000, g.bountyFor(fresh, now));
        g.energy = Game.EXTRA; assertEquals(18072, g.bountyFor(old, now));
        g.nextBounty = 20000; assertEquals(36144, g.bountyFor(old, now));
    }
    @Test public void completionPaysTheGrownAmountAndResetsOnlyThatQuestsWaitingTime() {
        Game g = new Game(now, zone); g.questMinutes = 1; g.restMinutes = 0; g.nextBounty = 10000;
        Game.Quest completed = quest(g, true), waiting = quest(g, true);
        completed.bountySince = waiting.bountySince = now - 3600000;
        assertTrue(g.start(completed, now)); g.advance(now + 1500000, zone);
        assertEquals(10040, g.coins); assertEquals(now + 1500000, completed.bountySince);
        assertEquals(11000, g.bountyFor(completed)); assertEquals(11044, g.bountyFor(waiting));
    }
    @Test public void timedQuestStillGrowsUntilItActuallyCompletes() {
        Game g = new Game(now, zone); g.energy = Game.EXTREME; g.nextBounty = 10000;
        Game.Quest q = quest(g, true); assertTrue(g.start(q, now));
        assertEquals(36144, g.activeBounty(now + 3600000));
        g.advance(now + 5400000, zone); assertEquals(36144, g.coins);
    }
    @Test public void restoreReturnsTheOriginalWaitingTimeAndExactGrownReward() {
        Game g = new Game(now, zone); g.nextBounty = 10000; Game.Quest q = quest(g, false); q.bountySince = now - 7200000;
        g.completeInstant(q, now); assertEquals(3025, g.coins);
        assertTrue(g.restoreOneOff(q.id, now + 1)); assertEquals(0, g.coins);
        assertEquals(now - 7200000, q.bountySince); assertEquals(3025, g.bountyFor(q, now + 1));
    }
    @Test public void waitingTimeAndActiveQuoteSurviveRestart() throws Exception {
        Game g = new Game(now, zone); Game.Quest q = quest(g, true); q.bountySince = now - 3600000;
        g.start(q, now);
        Game loaded = GameCodec.decode(GameCodec.encode(g).toString(), now + 1000, zone);
        assertEquals(q.bountySince, loaded.quests.get(0).bountySince);
        assertEquals(g.activeBounty(now + 1000), loaded.activeBounty(now + 1000));
    }
}
