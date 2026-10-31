package com.bulletin.app;

import org.junit.Test;
import java.time.Instant;
import java.time.ZoneId;
import static org.junit.Assert.*;

public class CategoryQuotaTest {
    private final ZoneId zone = ZoneId.of("UTC");
    private final long now = Instant.parse("2026-10-30T12:00:00Z").toEpochMilli();
    private final long midnight = Instant.parse("2026-10-31T00:00:00Z").toEpochMilli();
    private Game.Category category(Game g, boolean all, int count) { Game.Category c = new Game.Category(); c.name = "Daily"; c.mustDo = true; c.requireAll = all; c.requiredTasks = count; g.addCategory(c); return c; }
    private Game.Quest quest(Game g, Game.Category c, boolean repeat) { Game.Quest q = new Game.Quest(); q.name = "Task"; q.repeatable = repeat; q.forever = true; q.firstDay = g.day; g.addQuest(q); if (c != null) g.assignCategory(q, c.id); return q; }
    private long finish(Game g, Game.Quest q, long at) {
        assertTrue(g.start(q, at));
        if (g.session.timed) { long end = g.session.endsAt; g.advance(end, zone); if (g.session != null) { end = g.session.endsAt; g.advance(end, zone); } return end; }
        g.completeManual(at); return at;
    }
    @Test public void numericQuotaCountsRepeatedCompletionsAndItsRemainingSlotsDeterminePenalty() {
        Game g = new Game(now, zone); g.energy = Game.DEAD; Game.Category c = category(g, false, 3); Game.Quest q = quest(g, c, true); long at = now;
        assertTrue(g.isRequired(q)); assertEquals(9, g.requiredPenalty().days);
        at = finish(g, q, at); assertEquals(2, g.categoryRemaining(c, g.day)); assertEquals(5, g.requiredPenalty().days); assertTrue(g.isRequired(q));
        at = finish(g, q, at + 1); assertEquals(1, g.categoryRemaining(c, g.day)); assertEquals(2, g.requiredPenalty().days); assertTrue(g.isRequired(q));
        finish(g, q, at + 1); assertFalse(g.categoryActive(c)); assertFalse(g.isRequired(q)); assertEquals("Daily (3/3)", g.categoryTitle(c)); assertEquals(0, g.requiredPenalty().days); assertEquals(2, g.pendingConfetti);
    }
    @Test public void allQuotaCountsDistinctQuestsAndCompletedRepeatablesImmediatelyBecomeOptional() {
        Game g = new Game(now, zone); g.energy = Game.DEAD; Game.Category c = category(g, true, 1); Game.Quest a = quest(g, c, true), b = quest(g, c, true); long at = finish(g, a, now);
        assertFalse(g.isRequired(a)); assertTrue(g.isRequired(b)); assertEquals(1, g.categoryDone(c, g.day)); assertEquals(1, g.categoryRemaining(c, g.day));
        at = finish(g, a, at + 1); assertEquals(1, g.categoryDone(c, g.day)); assertEquals(1, g.categoryRemaining(c, g.day));
        finish(g, b, at + 1); assertFalse(g.categoryActive(c)); assertEquals("Daily (2/2)", g.categoryTitle(c));
    }
    @Test public void categoryQuotaNeverDoubleCountsIndividuallyRequiredMembers() {
        Game g = new Game(now, zone); Game.Category c = category(g, false, 1); Game.Quest a = quest(g, c, false), b = quest(g, c, false); a.required = b.required = true;
        assertEquals(2, g.requiredPenalty().days); finish(g, a, now); assertFalse(g.isRequired(b)); assertEquals(0, g.requiredPenalty().days);
        quest(g, null, false).required = true; assertEquals(2, g.requiredPenalty().days);
    }
    @Test public void completedCategoryStaysNormalUntilTheNextDayEvenIfAMemberIsAdded() {
        Game g = new Game(now, zone); Game.Category c = category(g, true, 1); Game.Quest a = quest(g, c, false); finish(g, a, now); Game.Quest later = quest(g, c, false);
        assertFalse(g.isRequired(later)); assertEquals("Daily (1/1)", g.categoryTitle(c));
        g.advance(midnight, zone); assertTrue(g.isRequired(a)); assertTrue(g.isRequired(later)); assertEquals("Daily (0/2)", g.categoryTitle(c)); assertEquals(1, g.days);
    }
    @Test public void unfinishedCategorySettlesOnceUsingRemainingSlotsAndNamesTheCategory() {
        Game g = new Game(now, zone); g.days = 10; g.coins = 10000; Game.Category c = category(g, false, 3); Game.Quest a = quest(g, c, false); finish(g, a, now);
        g.advance(midnight, zone); assertEquals(5, g.days); assertEquals(1, g.missedReviews.size()); assertEquals("Daily (1/3)", g.missedReviews.get(0).names.get(0));
        assertEquals(5000, g.missedReviews.get(0).coinsLost); assertEquals(0, g.categoryDone(c, g.day));
        long coins = g.coins; g.advance(midnight, zone); assertEquals(coins, g.coins); assertEquals(1, g.missedReviews.size());
    }
    @Test public void progressAllTargetAndTimedSingleFlagsSurviveRestart() throws Exception {
        Game g = new Game(now, zone); Game.Category c = category(g, true, 1); Game.Quest a = quest(g, c, false), b = quest(g, c, false); b.timed = true;
        finish(g, a, now); Game loaded = GameCodec.decode(GameCodec.encode(g).toString(), now + 1, zone); Game.Category restored = loaded.categories.get(0);
        assertTrue(restored.mustDo); assertTrue(restored.requireAll); assertEquals(1, loaded.categoryDone(restored, loaded.day)); assertTrue(loaded.quests.get(1).timed); assertTrue(loaded.isRequired(loaded.quests.get(1)));
    }
    @Test public void restoringCompletedSingleReopensTheCategoryQuotaAndClearsFinalCelebration() {
        Game g = new Game(now, zone); Game.Category c = category(g, true, 1); Game.Quest q = quest(g, c, false); finish(g, q, now);
        assertEquals(2, g.pendingConfetti); assertTrue(g.restoreOneOff(q.id, now + 1)); assertTrue(g.categoryActive(c)); assertTrue(g.isRequired(q)); assertEquals(0, g.pendingConfetti); assertEquals(2, g.requiredPenalty().days);
    }
    @Test public void emptyOrNotDueCategoriesDoNotCreateUnfulfillablePenalties() {
        Game g = new Game(now, zone); Game.Category c = category(g, false, 3); assertEquals(0, g.requiredPenalty().days);
        Game.Quest q = quest(g, c, true); q.firstDay++; assertEquals(0, g.requiredPenalty().days); assertFalse(g.isRequired(q));
    }
    @Test public void numericQuotaProgressDoesNotCountEarlierCompletionsOutsideThatCategory() {
        Game g = new Game(now, zone); Game.Category c = category(g, false, 2); Game.Quest q = quest(g, null, false); finish(g, q, now); g.assignCategory(q, c.id);
        assertEquals(0, g.categoryDone(c, g.day)); assertEquals(2, g.categoryRemaining(c, g.day));
    }
    @Test public void timedSingleUsesPresetTimingCompletesOnceAndCanBeRestoredFromTrash() {
        Game g = new Game(now, zone); g.energy = Game.DEAD; Game.Quest q = quest(g, null, false); q.timed = true;
        assertEquals(20000, g.durationFor(q)); assertTrue(g.completeInstant(q, now).isEmpty()); assertTrue(g.hasActiveRepeatableQuests());
        assertTrue(g.start(q, now)); g.advance(now + 19999, zone); assertFalse(q.done(g.day)); g.advance(now + 20000, zone);
        assertTrue(q.done(g.day)); assertFalse(g.availableQuests().contains(q)); assertEquals(30, g.coins); assertEquals(Game.REST, g.session.phase);
        g.advance(now + 30000, zone); assertNull(g.session); assertFalse(g.canStart(q)); assertTrue(g.completedOneOffs().contains(q));
        assertTrue(g.restoreOneOff(q.id, now + 31000)); assertTrue(g.canStart(q)); assertEquals(0, g.coins);
    }
    @Test public void timedSingleCanBePausedAndResumedAcrossRestart() throws Exception {
        Game g = new Game(now, zone); g.energy = Game.DEAD; Game.Quest q = quest(g, null, false); q.timed = true; g.start(q, now); g.pauseQuest(now + 5000);
        Game loaded = GameCodec.decode(GameCodec.encode(g).toString(), now + 10000, zone); Game.Quest resumed = loaded.quests.get(0);
        assertTrue(resumed.timed); assertEquals(15000, loaded.remainingFor(resumed, now + 10000)); assertTrue(loaded.start(resumed, now + 10000));
        loaded.advance(now + 25000, zone); assertTrue(resumed.done(loaded.day)); assertEquals(30, loaded.coins);
    }
    @Test public void randomChoicesPrioritizeCategoryRequiredMembersAndThenBecomeOptional() {
        Game g = new Game(now, zone); Game.Category c = category(g, false, 1); Game.Quest q = quest(g, c, false); quest(g, null, false);
        g.randomMode = true; assertEquals(q, g.boardQuests().get(0)); finish(g, q, now); assertFalse(g.categoryActive(c)); assertEquals(0, g.requiredPenalty().days);
    }
}
