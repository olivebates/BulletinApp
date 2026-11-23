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
    @Test public void successiveMissesCostTenPercentThenTenPercentMorePerSlot() {
        assertEquals(1000, game(1).requiredPenalty().coins(10000));
        assertEquals(2100, game(2).requiredPenalty().coins(10000));
        assertEquals(3310, game(3).requiredPenalty().coins(10000));
        assertEquals(4641, game(4).requiredPenalty().coins(10000));
        assertEquals(993, game(3).requiredPenalty().coins(50));
    }
    @Test public void undoneFinesCanTakePositiveBalancesBelowZero() {
        Game g = game(3);
        for (long balance : new long[]{1, 50, 500, 10000, 100000}) {
            long fine = g.requiredPenalty().coins(balance); assertTrue(fine >= 993); assertTrue(fine <= Game.LIMIT);
        }
        g.coins = 50; g.advance(midnight, zone);
        assertEquals(993, g.missedReviews.get(0).coinsLost);
        assertEquals(-1043, g.coins); // The separate daily rent is still due.
    }
    @Test public void zeroAndNegativeBalancesStillPayAtLeastThreeEurosPerSlot() {
        for (int count : new int[]{1, 2, 20}) {
            Game g = game(count);
            assertTrue(g.requiredPenalty().coins(0)>=300L*count); assertEquals(g.requiredPenalty().coins(0), g.requiredPenalty().coins(-100));
            assertEquals(g.requiredPenalty().coins(0), g.requiredPenalty().coins(-14000));
        }
    }
    @Test public void negativeBalancesPayTheMinimumSequence() {
        Game g = game(3);
        assertEquals(993, g.requiredPenalty().coins(-14500));
        assertEquals(993, g.requiredPenalty().coins(-14999));
        assertEquals(993, g.requiredPenalty().coins(-15000));
        assertEquals(993, g.requiredPenalty().coins(-16000));
        g.coins = -14500; g.advance(midnight, zone);
        assertEquals(-15493, g.coins); assertEquals(993, g.missedReviews.get(0).coinsLost);
    }
    @Test public void repeatedOfflineMissedDaysSettleTheirMinimumsExactlyOnce() {
        Game g = game(2); g.coins = -14000;
        g.advance(midnight + 4 * 86400000L, zone);
        assertEquals(-17150, g.coins); assertEquals(5, g.missedReviews.size());
        assertEquals(630, g.missedReviews.get(0).coinsLost);
        for (int i = 1; i < 5; i++) assertEquals(630, g.missedReviews.get(i).coinsLost);
        g.advance(midnight + 4 * 86400000L, zone); assertEquals(-17150, g.coins);
    }
    @Test public void lawReportingRetainsItsOwnPenaltyRules() {
        Game g = game(0); assertEquals(0, g.requiredPenalty().coins(-14500));
        g.coins = -14900; g.incurFine(); assertEquals(-15200, g.coins);
    }
}
