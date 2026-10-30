package com.bulletin.app;

import org.junit.Test;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.Random;
import static org.junit.Assert.*;

public class DailyRulesTest {
    private final ZoneId zone = ZoneId.of("UTC");
    private final long now = Instant.parse("2026-10-29T12:00:00Z").toEpochMilli();
    private final long midnight = Instant.parse("2026-10-30T00:00:00Z").toEpochMilli();
    private Game.Quest quest(Game g, boolean repeatable) {
        Game.Quest q = new Game.Quest(); q.firstDay = g.day; q.repeatable = repeatable; g.addQuest(q); return q;
    }
    @Test public void rentChargesEachOfflineDayOnlyOnceAndStopsAtMinusOneHundredEuros() {
        Game g = new Game(now, zone); g.advance(midnight + 2 * 86400000L, zone); assertEquals(-300, g.coins);
        g.advance(midnight + 2 * 86400000L, zone); assertEquals(-300, g.coins);
        g.coins = -9950; g.advance(midnight + 3 * 86400000L, zone); assertEquals(-10000, g.coins);
        g.advance(midnight + 4 * 86400000L, zone); assertEquals(-10000, g.coins);
        g.incurFine(); assertEquals(-11000, g.coins);
        g.advance(midnight + 5 * 86400000L, zone); assertEquals(-11000, g.coins);
    }
    @Test public void rentFollowsDailyInterest() {
        Game g = new Game(now, zone); g.coins = 10000; g.pendingCoins = 10000;
        g.advance(midnight, zone); assertEquals(9904, g.coins); assertEquals(9904, g.pendingCoins);
    }
    @Test public void correctingMissedTasksStillChargesRentWhenThePenaltyHadPassedTheRentFloor() {
        Game g = new Game(now, zone); g.coins = -9500; quest(g, false).required = true;
        g.advance(midnight, zone); assertEquals(-10500, g.coins);
        g.resolveMissed(true, midnight + 1, zone); assertEquals(-9600, g.coins);
    }
    @Test public void finishingAChoiceRefreshesBothOptions() {
        Game g = new Game(now, zone); g.questMinutes = 1; g.restMinutes = 0;
        for (int i = 0; i < 6; i++) quest(g, true);
        g.randomMode = true; g.refreshRandomQuests(new Random(1)); HashSet<String> previous = new HashSet<>(g.randomQuestIds);
        assertTrue(g.start(g.boardQuests().get(0), now)); g.advance(now + 1500000, zone);
        assertEquals(2, g.boardQuests().size()); assertTrue(g.randomQuestIds.stream().noneMatch(previous::contains));
    }
    @Test public void selectedCompletedSingleCanBeRestoredOutOfOrderWithItsRewardAndBountyReversed() {
        Game g = new Game(now, zone); Game.Quest first = quest(g, false), second = quest(g, false), third = quest(g, false);
        g.completeInstant(first, now); g.completeInstant(second, now + 1); g.completeInstant(third, now + 2);
        assertEquals(100, g.coins); assertEquals(134, g.nextBounty);
        assertTrue(g.restoreOneOff(first.id, now + 3)); assertEquals(70, g.coins); assertEquals(121, g.nextBounty);
        assertFalse(first.done(g.day)); assertEquals(2, g.completedOneOffs().size());
        assertTrue(g.restoreOneOff(third.id, now + 4)); assertEquals(33, g.coins); assertEquals(110, g.nextBounty);
        assertTrue(g.restoreOneOff(second.id, now + 5)); assertEquals(0, g.coins); assertEquals(100, g.nextBounty);
        assertFalse(g.restoreOneOff(first.id, now + 6));
    }
    @Test public void restoringAnOlderCompletionDoesNotUndoAFeeReset() {
        Game g = new Game(now, zone); g.coins = 10000; Game.Quest q = quest(g, false); g.completeInstant(q, now);
        g.incurFine(); assertTrue(g.restoreOneOff(q.id, now + 1)); assertEquals(100, g.nextBounty);
    }
    @Test public void allRestoreRecordsAndBountyChangesSurviveRestart() throws Exception {
        Game g = new Game(now, zone);
        for (int i = 0; i < 15; i++) g.completeInstant(quest(g, false), now + i);
        Game loaded = GameCodec.decode(GameCodec.encode(g).toString(), now + 20, zone);
        assertEquals(15, loaded.completedOneOffs().size()); assertEquals(15, loaded.undo.size());
        assertTrue(loaded.restoreOneOff(loaded.completedOneOffs().get(0).id, now + 21));
        long base = 100; for (int i = 0; i < 14; i++) base = Game.raiseBounty(base);
        assertEquals(base, loaded.nextBounty);
    }
    @Test public void deletingAnEntityDeletesAllOfItsTimelineHistoryAndRestoreRecords() {
        Game g = new Game(now, zone); Game.Quest a = quest(g, false), b = quest(g, false);
        g.completeInstant(a, now); g.completeInstant(b, now + 1);
        Game.Fine law = new Game.Fine(); g.fines.add(law); g.incurFine(law, now + 2); g.incurFine(law, now + 3);
        g.deleteQuest(a); g.deleteLaw(law);
        assertEquals(1, g.history.size()); assertEquals(b.id, g.history.get(0).id);
        assertFalse(g.timelineEntities(Game.QUEST_HISTORY).containsKey(a.id));
        assertFalse(g.timelineEntities(Game.FINE_HISTORY).containsKey(law.id));
        assertEquals(1, g.undo.size()); assertFalse(g.restoreOneOff(a.id, now + 4));
    }
    @Test public void upgradingAnOlderSaveRecoversMoreThanTenCompletedTasks() throws Exception {
        Game g = new Game(now, zone);
        for (int i = 0; i < 15; i++) g.completeInstant(quest(g, false), now + i);
        org.json.JSONObject old = GameCodec.encode(g).put("version", 5);
        old.remove("bountyChanges"); old.remove("bountyBase");
        org.json.JSONArray latest = new org.json.JSONArray(), all = old.getJSONArray("undo");
        for (int i = 5; i < all.length(); i++) latest.put(all.get(i));
        old.put("undo", latest);
        Game loaded = GameCodec.decode(old.toString(), now + 20, zone);
        assertEquals(15, loaded.undo.size());
        assertTrue(loaded.restoreOneOff(loaded.quests.get(0).id, now + 21));
        long base = 100; for (int i = 0; i < 14; i++) base = Game.raiseBounty(base);
        assertEquals(base, loaded.nextBounty);
    }
}
