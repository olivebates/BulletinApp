package com.bulletin.app;

import org.junit.Test;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import static org.junit.Assert.*;

public class GameTest {
    static final ZoneId UTC = ZoneId.of("UTC");
    static final long NOW = Instant.parse("2026-10-29T12:00:00Z").toEpochMilli();
    static final long MIDNIGHT = Instant.parse("2026-10-30T00:00:00Z").toEpochMilli();
    private Game game() { Game g = new Game(NOW, UTC); g.questMinutes = 1; g.restMinutes = 0; return g; }
    private Game.Quest quest(Game g, boolean repeatable, boolean required, boolean forever) {
        Game.Quest q = new Game.Quest(); q.name = "Focus"; q.firstDay = g.day;
        q.repeatable = repeatable; q.required = required; q.forever = forever; g.quests.add(q); return q;
    }
    private void complete(Game g, Game.Quest q, long at) {
        assertTrue(g.start(q, at));
        if (g.session.timed) { g.advance(g.session.endsAt, UTC); if (g.session != null) g.advance(g.session.endsAt, UTC); } else g.completeManual(at);
    }
    @Test public void euroBountyCompoundsInWholeCents() {
        Game g = game(); Game.Quest q = quest(g, true, false, true);
        long[] rewards = {100, 110, 121, 134, 148, 163}; long total = 0;
        for (int i = 0; i < rewards.length; i++) { assertEquals(rewards[i], g.nextBounty); total += rewards[i]; complete(g, q, NOW + i * 1800000L); assertEquals(total, g.coins); }
    }
    @Test public void requiredRewardsDoNotIncreaseBounty() {
        Game g = game(); g.nextBounty = 134; Game.Quest q = quest(g, true, true, true);
        assertEquals(14, g.bountyFor(q)); complete(g, q, NOW); complete(g, q, NOW + 1800000);
        assertEquals(28, g.coins); assertEquals(134, g.nextBounty);
    }
    @Test public void fixedPresetsIgnoreLegacyDurationOverrides() {
        Game g = game(); g.questMinutes = 15; Game.Quest q = quest(g, true, false, true);
        q.questMinutes = 5; assertEquals(25, g.minutesFor(q)); assertEquals(100, g.bountyFor(q));
        g.questMinutes = 30; assertEquals(25, g.minutesFor(q)); assertEquals(100, g.bountyFor(q));
        q.questMinutes = 0; assertEquals(100, g.bountyFor(q));
    }
    @Test public void requiredAndTimeDiscountsCombineBeforeRounding() {
        assertEquals(4, Game.scaledBounty(100, true, 5, 15));
        assertEquals(10, Game.scaledBounty(100, true, 0, 15));
        assertEquals(100, Game.scaledBounty(100, false, 5, 0));
        assertTrue(Game.scaledBounty(Game.LIMIT, false, 239, 240) > 0);
    }
    @Test public void rewardReferenceAndDurationAreCapturedAtAcceptance() {
        Game g = game(); g.questMinutes = 15; Game.Quest q = quest(g, true, false, true); q.questMinutes = 5;
        g.start(q, NOW); g.questMinutes = 240; q.questMinutes = 200;
        assertEquals(NOW + 1500000, g.session.endsAt);
        assertEquals(100, g.activeBounty()); g.advance(NOW + 1500000, UTC);
        assertEquals(100, g.coins); assertEquals(110, g.nextBounty);
    }
    @Test public void nonMultipleQuestsAreManualRegardlessOfTimeField() {
        Game g = game(); Game.Quest q = quest(g, false, false, true); q.questMinutes = 5;
        g.start(q, NOW); assertFalse(g.session.timed); g.advance(NOW + 3600000, UTC); assertEquals(0, g.coins);
        assertEquals(0, g.focusMillisToday(NOW + 3600000)); g.completeManual(NOW + 3600000); assertEquals(30, g.coins);
    }
    @Test public void cancelledTimedQuestKeepsTimeButGivesNoRewardOrCompletion() {
        Game g = game(); Game.Quest q = quest(g, true, true, true); g.start(q, NOW);
        assertTrue(g.pauseQuest(NOW + 30000)); assertNull(g.session); assertEquals(30000, g.timedMillisToday);
        assertEquals(0, g.coins); assertEquals(100, g.nextBounty); assertFalse(q.done(g.day)); assertTrue(g.history.isEmpty());
        g.advance(NOW + 3600000, UTC); assertEquals(0, g.coins); assertTrue(g.canStart(q));
    }
    @Test public void cancelDoesNotCancelRest() {
        Game g = game(); g.start(quest(g, true, false, true), NOW); g.advance(NOW + 1500000, UTC);
        assertFalse(g.pauseQuest(NOW + 1500001)); assertEquals(Game.REST, g.session.phase);
    }
    @Test public void questThenRestUsesCapturedTimes() {
        Game g = game(); g.restMinutes = 4; Game.Quest q = quest(g, true, false, true); g.start(q, NOW);
        g.energy = Game.EXTREME; g.advance(NOW + 1499999, UTC); assertEquals(0, g.coins);
        g.advance(NOW + 1500000, UTC); assertEquals(Game.REST, g.session.phase); assertEquals(NOW + 1800000, g.session.endsAt);
        assertFalse(g.canStart(q)); g.advance(NOW + 1800000, UTC); assertNull(g.session);
    }
    @Test public void capturedLegacyZeroRestReopensBoardAndEmitsReadyEvent() {
        Game g = game(); g.start(quest(g, true, false, true), NOW); g.session.restMillis = 0;
        List<Game.Event> events = g.advance(NOW + 1500000, UTC);
        assertNull(g.session); assertEquals("victory", events.get(0).type); assertEquals("ready", events.get(1).type);
    }
    @Test public void syncAndManualDoubleTapCannotDuplicateAwardsOrHistory() {
        Game g = game(); Game.Quest q = quest(g, false, false, true); complete(g, q, NOW);
        assertTrue(g.completeManual(NOW + 1).isEmpty()); g.advance(NOW + 120000, UTC); g.advance(NOW + 120000, UTC);
        assertEquals(30, g.coins); assertEquals(1, g.history.size()); assertEquals(30, g.takePendingCoins()); assertEquals(0, g.takePendingCoins());
    }
    @Test public void onlyOneQuestCanRun() {
        Game g = game(); Game.Quest a = quest(g, true, false, true), b = quest(g, true, false, true);
        assertTrue(g.start(a, NOW)); assertFalse(g.start(b, NOW));
    }
    @Test public void singleQuestRenewsOnItsNextScheduledDay() {
        Game g = game(); Game.Quest q = quest(g, false, false, true); complete(g, q, NOW);
        assertFalse(g.canStart(q)); g.advance(MIDNIGHT, UTC); assertTrue(g.canStart(q)); assertEquals(100, g.nextBounty);
    }
    @Test public void recurrenceAndFutureDatesControlEligibilityAndPenalties() {
        Game g = game(); Game.Quest q = quest(g, false, true, true); q.firstDay += 2; q.interval = 3;
        assertFalse(g.canStart(q)); assertEquals(0, g.requiredPenalty().coins(0));
        assertTrue(q.due(q.firstDay)); assertFalse(q.due(q.firstDay + 1)); assertTrue(q.due(q.firstDay + 3));
    }
    @Test public void missedRequiredMinimumsAreTwentyThirtyFortyEuros() {
        Game g = game(); quest(g, false, true, false); assertEquals(2000, g.requiredPenalty().minimum); assertEquals(2, g.requiredPenalty().days);
        quest(g, false, true, false); assertEquals(5000, g.requiredPenalty().minimum); assertEquals(5, g.requiredPenalty().days);
        quest(g, false, true, false); assertEquals(9000, g.requiredPenalty().minimum); assertEquals(21, g.requiredPenalty().percent);
    }
    @Test public void higherPercentageWinsOverTheMinimum() {
        Game g = game(); quest(g, false, true, false); assertEquals(5000, g.requiredPenalty().coins(100000));
        assertEquals(1, g.requiredPenalty().coins(1)); assertEquals(1000, g.requiredPenalty().coins(-1));
    }
    @Test public void fineMinimumDoublesAndPositiveBalancesStopAtZero() {
        Game g = game(); g.coins = 10000; g.days = 20;
        g.incurFine(); assertEquals(8000, g.coins); assertEquals(4000, g.nextFine().minimum); assertEquals(18, g.days);
        g.incurFine(); assertEquals(4000, g.coins); g.incurFine(); assertEquals(0, g.coins);
        assertEquals(16000, g.nextFine().minimum);
        g.incurFine(); assertEquals(-1000, g.coins);
    }
    @Test public void finePercentageCanExceedMinimumAndRoundsUp() {
        Game g = game(); g.coins = 100001; g.incurFine(); assertEquals(90000, g.coins);
        assertEquals(1, new Game.Penalty(2, 5).coins(1));
    }
    @Test public void zeroOrNegativeBalanceFineAlwaysCostsTenEuros() {
        Game g = game(); g.incurFine(); assertEquals(-1000, g.coins); g.incurFine(); assertEquals(-2000, g.coins);
        assertEquals(0, g.days); assertEquals(0, g.nextFine().daysLost(0));
    }
    @Test public void midnightAppliesRequiredPenaltyThenInterest() {
        Game g = game(); g.days = 30; g.coins = 100000; for (int i = 0; i < 3; i++) quest(g, false, true, false);
        g.advance(MIDNIGHT, UTC); assertEquals(21, g.days); assertEquals(78932, g.coins); assertTrue(g.quests.isEmpty());
    }
    @Test public void aRequiredRepeatableQuestOnlyNeedsOneCompletion() {
        Game g = game(); complete(g, quest(g, true, true, true), NOW); assertEquals(0, g.requiredPenalty().days);
        g.advance(MIDNIGHT, UTC); assertEquals(1, g.days); assertEquals(-89, g.coins); assertEquals(2000, g.requiredPenalty().minimum);
    }
    @Test public void inactiveDaysDoNotEarnADayButStillEarnInterest() {
        Game g = game(); g.coins = 100; g.advance(MIDNIGHT, UTC); assertEquals(0, g.days); assertEquals(1, g.coins);
    }
    @Test public void dailyInterestIsPointZeroFourPercentRoundedUp() {
        assertEquals(0, Game.dailyInterest(-1)); assertEquals(0, Game.dailyInterest(0)); assertEquals(1, Game.dailyInterest(1));
        assertEquals(1, Game.dailyInterest(2500)); assertEquals(2, Game.dailyInterest(2501)); assertEquals(4, Game.dailyInterest(10000));
        Game g = game(); g.coins = 2501; g.advance(MIDNIGHT, UTC); g.advance(MIDNIGHT, UTC); assertEquals(2403, g.coins);
    }
    @Test public void offlineCompletionIsProcessedBeforeLaterMidnight() {
        Game g = game(); g.restMinutes = 4; g.start(quest(g, true, true, false), NOW); g.advance(MIDNIGHT, UTC);
        assertEquals(1, g.days); assertEquals(-89, g.coins); assertNull(g.session); assertEquals(1, g.history.size());
    }
    @Test public void midnightDeadlineAndNewDayRewardAreChronological() {
        Game g = game(); g.days = 10; g.coins = 10000; Game.Quest q = quest(g, true, true, true);
        g.start(q, MIDNIGHT - 30000); g.advance(MIDNIGHT + 1470000, UTC);
        assertEquals(8, g.days); assertEquals(7914, g.coins); assertFalse(q.done(g.day)); assertEquals(1470000, g.timedMillisToday);
        assertEquals(MIDNIGHT + 1470000, g.history.get(0).at);
    }
    @Test public void multipleOfflineDaysSettleExactlyOnce() {
        Game g = game(); g.days = 20; g.coins = 10000; quest(g, false, true, true).interval = 2;
        long later = MIDNIGHT + 2 * 86400000L; g.advance(later, UTC); assertEquals(16, g.days); assertEquals(5711, g.coins);
        g.advance(later, UTC); assertEquals(5711, g.coins); assertEquals(0, g.finesToday);
    }
    @Test public void liveFocusTimeExcludesRestAndManualTasks() {
        Game g = game(); g.restMinutes = 1; Game.Quest q = quest(g, true, false, true); g.start(q, NOW);
        assertEquals(20000, g.focusMillisToday(NOW + 20000)); g.advance(NOW + 1500000, UTC);
        assertEquals(1500000, g.focusMillisToday(NOW + 1600000)); g.advance(NOW + 1800000, UTC);
        Game.Quest manual = quest(g, false, false, true); g.start(manual, NOW + 1800000);
        assertEquals(1500000, g.focusMillisToday(NOW + 1810000));
    }
    @Test public void daylightSavingAndClockRollbackDoNotDoubleSettle() {
        ZoneId zone = ZoneId.of("Europe/Copenhagen");
        for (String value : new String[]{"2026-03-29", "2026-10-25"}) {
            LocalDate date = LocalDate.parse(value); long start = date.atStartOfDay(zone).toInstant().toEpochMilli();
            long next = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli(); Game g = new Game(start, zone); g.completedToday = 1;
            g.advance(next - 1, zone); assertEquals(0, g.days); g.advance(next, zone); assertEquals(1, g.days);
            g.advance(start, zone); g.advance(next, zone); assertEquals(1, g.days);
        }
    }
    @Test public void historyAttributesEveryFineAndQuestAndKeepsExpiredQuests() {
        Game g = game(); Game.Quest q = quest(g, false, false, false); complete(g, q, NOW);
        Game.Fine a = new Game.Fine(), b = new Game.Fine(); a.name = "A"; b.name = "B"; g.fines.add(a); g.fines.add(b);
        g.incurFine(a, NOW + 1000); g.incurFine(b, NOW + 2000); g.incurFine(a, NOW + 3000);
        assertEquals(4, g.history.size()); assertEquals(a.id, g.history.get(1).id); assertEquals(b.id, g.history.get(2).id);
        g.advance(MIDNIGHT, UTC); assertTrue(g.quests.isEmpty()); assertTrue(g.timelineEntities(Game.QUEST_HISTORY).containsKey(q.id));
    }
    @Test public void balanceHistoryTracksChangesIncludingNegativeBalancesWithoutDuplicates() {
        Game g = game(); complete(g, quest(g, false, false, true), NOW + 1000); g.recordBalance(NOW + 2000);
        assertEquals(2, g.balances.size()); Game.Fine f = new Game.Fine(); g.fines.add(f); g.incurFine(f, NOW + 3000);
        assertEquals(3, g.balances.size()); assertEquals(0, g.balances.get(2).cents);
        g.incurFine(f, NOW + 4000); assertEquals(-1000, g.balances.get(3).cents);
    }
    @Test public void timelineWindowIsThreeCalendarMonthsClampedToFirstOpen() {
        Game g = game(); assertEquals(g.day, g.timelineStart(g.day)); g.firstOpenedDay = LocalDate.parse("2026-01-01").toEpochDay();
        assertEquals(LocalDate.parse("2026-07-29").toEpochDay(), g.timelineStart(g.day));
        assertEquals(LocalDate.parse("2026-02-28").toEpochDay(), g.timelineStart(LocalDate.parse("2026-05-31").toEpochDay()));
    }
    @Test public void extremeBalancesAndFineEscalationDoNotOverflow() {
        Game g = game(); g.nextBounty = Game.LIMIT; g.coins = Game.LIMIT - 1; complete(g, quest(g, true, false, true), NOW);
        assertEquals(Game.LIMIT, g.coins); assertEquals(Game.LIMIT, g.nextBounty);
        g.finesToday = 1000; g.incurFine(); assertEquals(0, g.coins); g.incurFine(); assertEquals(-1000, g.coins);
    }
    @Test public void eurosAlwaysHaveTwoDecimalPlaces() {
        assertEquals("\u20ac0.01", Money.format(1)); assertEquals("\u20ac1.00", Money.format(100));
        assertEquals("-\u20ac20.00", Money.format(-2000)); assertEquals("\u20ac0.00", Money.format(0));
    }
    @Test public void instantCompletionAwardsOnceWithNoSessionRestOrFocusTime() {
        Game g = game(); g.restMinutes = 120; Game.Quest q = quest(g, false, true, true);
        List<Game.Event> events = g.completeInstant(q, NOW);
        assertEquals(1, events.size()); assertEquals("victory", events.get(0).type);
        assertNull(g.session); assertEquals(0, g.timedMillisToday); assertEquals(0, g.focusMillisToday(NOW + 10000));
        assertEquals(10, g.coins); assertEquals(100, g.nextBounty); assertEquals(1, g.history.size());
        assertTrue(g.completeInstant(q, NOW + 1).isEmpty()); assertEquals(10, g.coins);
    }
    @Test public void legacyZeroTimeRepeatableQuestsUseSettingsAndCannotCompleteInstantly() {
        Game g = game(); g.restMinutes = 4; Game.Quest q = quest(g, true, false, true); q.questMinutes = 0;
        g.completeInstant(q, NOW); g.completeInstant(q, NOW + 1000);
        assertNull(g.session); assertEquals(0, g.coins); assertTrue(g.history.isEmpty());
        assertEquals(1500000, g.remainingFor(q, NOW));
    }
    @Test public void instantCompletionCannotCompleteTimedOrConcurrentQuests() {
        Game g = game(); Game.Quest timed = quest(g, true, false, true), instant = quest(g, false, false, true);
        assertTrue(g.completeInstant(timed, NOW).isEmpty()); assertNull(g.session);
        g.start(timed, NOW); assertTrue(g.completeInstant(instant, NOW).isEmpty()); assertEquals(timed.id, g.session.questId);
    }
    @Test public void legacyManualSessionCannotStartRestWhenCompleted() {
        Game g = game(); Game.Quest q = quest(g, false, false, true); g.start(q, NOW); g.session.restMinutes = 10;
        g.completeManual(NOW + 10000); assertNull(g.session); assertEquals(0, g.timedMillisToday);
    }
    @Test public void repeatedCancelAndResumeRetainsRemainingTimeAndDoesNotDoubleCountWork() {
        Game g = game(); Game.Quest q = quest(g, true, false, true); g.start(q, NOW);
        g.pauseQuest(NOW + 20000); assertEquals(1480000, q.remainingMillis); assertEquals(1480000, g.remainingFor(q, NOW + 999999));
        g.start(q, NOW + 100000); assertEquals(NOW + 1580000, g.session.endsAt);
        g.pauseQuest(NOW + 110000); assertEquals(1470000, q.remainingMillis); assertEquals(30000, g.timedMillisToday);
        g.start(q, NOW + 200000); g.advance(NOW + 1670000, UTC);
        assertEquals(1500000, g.timedMillisToday); assertEquals(100, g.coins); assertEquals(1, g.history.size());
        assertEquals(-1, q.remainingMillis); assertEquals(1500000, g.remainingFor(q, NOW + 1700000));
    }
    @Test public void pausedQuestKeepsRewardDurationWhenSettingsChange() {
        Game g = game(); g.questMinutes = 15; Game.Quest q = quest(g, true, false, true); q.questMinutes = 5;
        g.start(q, NOW); g.pauseQuest(NOW + 60000); assertEquals(1440000, q.remainingMillis);
        g.energy = Game.EXTREME; q.questMinutes = 10; assertEquals(100, g.bountyFor(q));
        g.start(q, NOW + 100000); assertEquals(100, g.activeBounty()); assertEquals(NOW + 1540000, g.session.endsAt);
        g.advance(NOW + 1540000, UTC); assertEquals(100, g.coins); assertEquals(1500000, g.timedMillisToday);
    }
    @Test public void resumedQuestOnlyRestsAfterItsRemainingTimeFinishes() {
        Game g = game(); g.restMinutes = 1; Game.Quest q = quest(g, true, false, true);
        g.start(q, NOW); g.pauseQuest(NOW + 50000); assertNull(g.session);
        g.start(q, NOW + 100000); g.advance(NOW + 1549999, UTC); assertEquals(0, g.coins);
        g.advance(NOW + 1550000, UTC); assertEquals(Game.REST, g.session.phase); assertEquals(100, g.coins);
    }
    @Test public void mandatoryPenaltiesAtZeroAreFlatAndAbsentPenaltiesStayZero() {
        Game g = game(); assertEquals(0, g.requiredPenalty().coins(0));
        quest(g, false, true, false); quest(g, false, true, false);
        assertEquals(1000, g.requiredPenalty().coins(0)); assertEquals(50, g.requiredPenalty().coins(50));
        g.advance(MIDNIGHT, UTC); assertEquals(-1100, g.coins);
    }
    @Test public void reportingALawResetsBountyAndKeepsHistory() {
        Game g = game(); g.nextBounty = 163;
        Game.Fine law = new Game.Fine(); law.name = "No scrolling"; g.fines.add(law);
        g.incurFine(law, NOW);
        assertEquals(100, g.nextBounty); assertEquals(1, g.finesToday);
        assertEquals(law.id, g.history.get(0).id);
        g.nextBounty = 200; g.incurFine(new Game.Fine(), NOW + 1);
        assertEquals(200, g.nextBounty);
    }
    @Test public void repeatCompletionQueuesCelebrationAndLastRequiredQueuesLargerOne() {
        Game g = game(); complete(g, quest(g, true, false, true), NOW);
        assertEquals(1, g.pendingConfetti); g.pendingConfetti = 0;
        Game.Quest a = quest(g, false, true, false), b = quest(g, false, true, false);
        complete(g, a, NOW + 60000); assertEquals(0, g.pendingConfetti);
        complete(g, b, NOW + 60001); assertEquals(2, g.pendingConfetti);
    }
    @Test public void dailyRecurringQuestUsesOneDayInterval() {
        Game g = game(); Game.Quest q = quest(g, false, false, true);
        assertTrue(q.due(g.day)); assertTrue(q.due(g.day + 1)); assertTrue(q.due(g.day + 2));
        q.interval = 2; assertFalse(q.due(g.day + 1)); assertTrue(q.due(g.day + 2));
    }
    @Test public void undoRestoresTaskRewardHistoryAndBounty() {
        Game g = game(); Game.Quest q = quest(g, false, false, false); complete(g, q, NOW);
        assertEquals(1, g.undo.size()); assertTrue(g.undoOneOff(NOW + 1));
        assertEquals(0, g.coins); assertEquals(100, g.nextBounty); assertEquals(0, g.completedToday);
        assertTrue(g.canStart(q)); assertTrue(g.history.isEmpty()); assertFalse(g.undoOneOff(NOW + 2));
    }
    @Test public void allCompletedSinglesAreRestorableUntilMidnight() {
        Game g = game();
        for (int i = 0; i < 12; i++) complete(g, quest(g, false, false, false), NOW + i);
        assertEquals(12, g.undo.size()); assertEquals(12, g.completedOneOffs().size());
        for (int i = 0; i < 12; i++) assertTrue(g.undoOneOff(NOW + 20 + i));
        assertEquals(0, g.completedToday); assertEquals(0, g.history.size());
        complete(g, quest(g, false, false, false), NOW + 100);
        g.advance(MIDNIGHT, UTC); assertTrue(g.undo.isEmpty());
    }
    @Test public void missedReviewRefundsPenaltyAndRecordsForgottenTasksOnce() {
        Game g = game(); g.coins = 10000; g.days = 10;
        Game.Quest q = quest(g, false, true, false); q.name = "Must do";
        g.advance(MIDNIGHT, UTC); assertEquals(1, g.missedReviews.size());
        assertEquals("Must do", g.missedReviews.get(0).names.get(0));
        assertEquals(7904, g.coins); assertEquals(8, g.days);
        g.resolveMissed(true, MIDNIGHT + 1, UTC);
        assertEquals(9904, g.coins); assertEquals(11, g.days); assertEquals(q.id, g.history.get(0).id);
        g.resolveMissed(true, MIDNIGHT + 2, UTC); assertEquals(9904, g.coins);
    }
    @Test public void acknowledgingMissedTasksKeepsPenalty() {
        Game g = game(); quest(g, false, true, false); g.advance(MIDNIGHT, UTC);
        g.resolveMissed(false, MIDNIGHT + 1, UTC); assertEquals(-1100, g.coins);
        assertTrue(g.missedReviews.isEmpty()); assertTrue(g.history.isEmpty());
    }
    @Test public void completedSingleQuestsDisappearWithoutAllowingAnotherReward() {
        Game g = game(); Game.Quest repeat = quest(g, true, false, true);
        Game.Quest pending = quest(g, false, false, false);
        Game.Quest done = quest(g, false, false, false); complete(g, done, NOW);
        List<Game.Quest> board = g.availableQuests();
        assertEquals(2, board.size()); assertFalse(board.contains(done));
        assertFalse(g.canStart(done)); assertTrue(g.completeInstant(done, NOW + 1).isEmpty());
        assertEquals(30, g.coins);
    }
}
