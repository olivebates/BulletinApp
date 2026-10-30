package com.bulletin.app;

import org.junit.Test;
import java.time.Instant;
import java.time.ZoneId;
import static org.junit.Assert.*;

public class UndoneFineTest {
    private final long now = Instant.parse("2026-10-29T12:00:00Z").toEpochMilli();
    private final long midnight = Instant.parse("2026-10-30T00:00:00Z").toEpochMilli();
    private final ZoneId zone = ZoneId.of("UTC");
    private Game game(int count) {
        Game g = new Game(now, zone);
        for (int i = 0; i < count; i++) { Game.Quest q = new Game.Quest(); q.firstDay = g.day; q.required = q.forever = true; g.addQuest(q); }
        return g;
    }
    @Test public void positiveBalancesCannotGoBelowZeroFromTheUndoneFine() {
        Game g = game(10);
        for (long balance : new long[]{1, 50, 500, 10000, 100000}) {
            long fine = g.requiredPenalty().coins(balance); assertTrue(fine >= 0); assertTrue(fine <= balance);
        }
        g.coins = 50; g.advance(midnight, zone);
        assertEquals(50, g.missedReviews.get(0).coinsLost);
        assertEquals(-100, g.coins); // The separate daily rent is still due after the fine reaches zero.
    }
    @Test public void zeroAndNegativeBalancesGetOneFlatTenEuroFineRegardlessOfQuestCount() {
        for (int count : new int[]{1, 2, 20}) {
            Game g = game(count);
            assertEquals(1000, g.requiredPenalty().coins(0)); assertEquals(1000, g.requiredPenalty().coins(-100));
            assertEquals(1000, g.requiredPenalty().coins(-14000));
        }
    }
    @Test public void debtFloorClipsTheFineWithoutIncreasingAnAlreadyLowerBalance() {
        Game g = game(3);
        assertEquals(500, g.requiredPenalty().coins(-14500));
        assertEquals(1, g.requiredPenalty().coins(-14999));
        assertEquals(0, g.requiredPenalty().coins(-15000));
        assertEquals(0, g.requiredPenalty().coins(-16000));
        g.coins = -14500; g.advance(midnight, zone);
        assertEquals(-15000, g.coins); assertEquals(500, g.missedReviews.get(0).coinsLost);
    }
    @Test public void repeatedOfflineMissedDaysStopAtTheDebtFloor() {
        Game g = game(2); g.coins = -14000;
        g.advance(midnight + 4 * 86400000L, zone);
        assertEquals(-15000, g.coins); assertEquals(5, g.missedReviews.size());
        assertEquals(1000, g.missedReviews.get(0).coinsLost);
        for (int i = 1; i < 5; i++) assertEquals(0, g.missedReviews.get(i).coinsLost);
        g.advance(midnight + 4 * 86400000L, zone); assertEquals(-15000, g.coins);
    }
    @Test public void lawReportingRetainsItsOwnPenaltyRules() {
        Game g = game(0); assertEquals(0, g.requiredPenalty().coins(-14500));
        g.coins = -14900; g.incurFine(); assertEquals(-15900, g.coins);
    }
}
