package com.bulletin.app;

import org.junit.Test;
import java.time.Instant;
import java.time.ZoneId;
import static org.junit.Assert.*;

public class QuestCancellationTest {
    private final ZoneId zone = ZoneId.of("UTC");
    private final long now = Instant.parse("2026-10-30T12:00:00Z").toEpochMilli();
    private final long midnight = Instant.parse("2026-10-31T00:00:00Z").toEpochMilli();
    private Game.Quest quest(Game g) {
        Game.Quest q = new Game.Quest(); q.name = "Focus"; q.repeatable = true; q.forever = true; q.firstDay = g.day; g.addQuest(q); return q;
    }
    private void assertReset(Game.Quest q) {
        assertEquals(-1, q.remainingMillis); assertEquals(-1, q.pausedRewardMillis); assertEquals(-1, q.pausedReferenceMillis);
        assertEquals(-1, q.pausedRewardMinutes); assertEquals(-1, q.pausedReferenceMinutes); assertEquals(-1, q.pausedRestMillis);
    }
    @Test public void activeCancellationResetsTimerAndNeverCompletesOrRewardsIt() {
        Game g = new Game(now, zone); Game.Quest q = quest(g); g.start(q, now);
        assertTrue(g.cancelQuest(q, now + 60000)); assertNull(g.session); assertReset(q);
        assertEquals(60000, g.timedMillisToday); g.advance(now + 3600000, zone);
        assertEquals(0, g.coins); assertEquals(0, g.pendingCoins); assertEquals(0, g.completedToday); assertTrue(g.history.isEmpty());
        assertTrue(g.start(q, now + 3600000)); assertEquals(now + 5100000, g.session.endsAt);
    }
    @Test public void cancelPausedQuestKeepsAnotherRunningQuestAndPreviousCompletions() {
        Game g = new Game(now, zone); Game.Quest a = quest(g), b = quest(g); a.completedDay = g.day; a.completions = 2;
        g.start(a, now); g.pauseQuest(now + 10000); assertTrue(g.isPaused(a)); g.start(b, now + 20000);
        Game.Session running = g.session; assertTrue(g.cancelQuest(a, now + 30000)); assertReset(a);
        assertSame(running, g.session); assertEquals(2, a.completions); assertEquals(g.day, a.completedDay);
        assertFalse(g.cancelQuest(a, now + 30000)); assertEquals(0, g.coins); assertTrue(g.history.isEmpty());
    }
    @Test public void offlineMidnightDiscardsEveryPausedTimerAndCapturedRewardWithoutReward() throws Exception {
        Game g = new Game(now, zone); Game.Quest q = quest(g); g.start(q, now); g.pauseQuest(now + 60000);
        g.energy = Game.DEAD;
        Game loaded = GameCodec.decode(GameCodec.encode(g).toString(), now + 60000, zone); Game.Quest paused = loaded.quests.get(0);
        loaded.advance(midnight, zone); assertReset(paused); assertFalse(loaded.isPaused(paused)); assertNull(loaded.session);
        assertEquals(-100, loaded.coins); assertEquals(0, loaded.pendingCoins); assertEquals(0, loaded.completedToday); assertTrue(loaded.history.isEmpty());
        assertTrue(loaded.start(paused, midnight)); assertEquals(midnight + 20000, loaded.session.endsAt); assertEquals(10000, loaded.session.restMillis);
    }
    @Test public void cancelTimedSingleKeepsItAvailableAndDoesNotPutItInCompletedTrash() {
        Game g = new Game(now, zone); Game.Quest q = quest(g); q.repeatable = false; q.timed = true;
        g.start(q, now); g.pauseQuest(now + 10000); assertTrue(g.cancelQuest(q, now + 20000));
        assertTrue(g.quests.contains(q)); assertFalse(q.done(g.day)); assertTrue(g.undo.isEmpty()); assertReset(q);
        assertTrue(g.canStart(q));
    }
}