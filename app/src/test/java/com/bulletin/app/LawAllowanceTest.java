package com.bulletin.app;

import org.junit.Test;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import static org.junit.Assert.*;

public class LawAllowanceTest {
    private final ZoneId zone = ZoneId.of("UTC");
    private final long now = Instant.parse("2026-10-30T12:00:00Z").toEpochMilli();
    private final long midnight = Instant.parse("2026-10-31T00:00:00Z").toEpochMilli();
    private Game.Fine law(Game g, int mode, int amount, long time) {
        Game.Fine f = new Game.Fine(); f.name = "Limit"; g.configureLaw(f, mode, amount, time, now); g.addLaw(f); return f;
    }
    @Test public void amountAllowsExactlyItsLimitWithoutFinesOrAnalytics() {
        Game g = new Game(now, zone); Game.Fine f = law(g, Game.AMOUNT, 2, 10000); g.coins = 10000;
        assertTrue(g.useLawAmount(f)); assertTrue(g.useLawAmount(f)); assertEquals(2, f.amountUsed);
        assertFalse(g.useLawAmount(f)); assertEquals(2, f.amountUsed); assertEquals(10000, g.coins); assertEquals(0, g.finesToday);
        assertTrue(g.history.isEmpty()); assertEquals(0, new Statistics(g, true, now, zone).total);
    }
    @Test public void confirmedAmountOverstepIncrementsCounterAndAddsExactlyOneReport() {
        Game g = new Game(now, zone); Game.Fine f = law(g, Game.AMOUNT, 1, 10000); g.coins = 10000;
        g.useLawAmount(f); g.incurFine(f, now);
        assertEquals(2, f.amountUsed); assertEquals(8000, g.coins); assertEquals(1, g.finesToday); assertEquals(1, g.history.size());
        assertEquals(1, new Statistics(g, true, now, zone).total);
    }
    @Test public void timerPausesAndResumesWithoutConsumingPausedTime() {
        Game g = new Game(now, zone); Game.Fine f = law(g, Game.TIMED, 3, 20000);
        assertTrue(g.toggleLawTimer(f, now)); assertEquals(15000, f.remaining(now + 5000));
        assertTrue(g.toggleLawTimer(f, now + 5000)); assertEquals(0, f.endsAt); assertEquals(15000, f.remaining(now + 100000));
        assertTrue(g.toggleLawTimer(f, now + 100000)); assertEquals(now + 115000, f.endsAt);
    }
    @Test public void timerExpiryWarnsOnceStopsAtZeroAndNeverAutomaticallyFines() {
        Game g = new Game(now, zone); Game.Fine f = law(g, Game.TIMED, 3, 20000); g.toggleLawTimer(f, now);
        assertTrue(g.advance(now + 19999, zone).isEmpty());
        List<Game.Event> events = g.advance(now + 20000, zone);
        assertEquals(1, events.stream().filter(e -> e.type.equals("lawLimit")).count()); assertEquals(0, f.endsAt); assertEquals(0, f.remaining(now + 50000));
        assertEquals(f.id, events.get(0).id);
        assertTrue(g.advance(now + 50000, zone).isEmpty()); assertFalse(g.toggleLawTimer(f, now + 50000));
        assertEquals(0, g.coins); assertEquals(0, g.finesToday); assertTrue(g.history.isEmpty()); assertEquals(0, new Statistics(g, true, now, zone).total);
    }
    @Test public void onlyConfirmedTimedOverstepCreatesAnalytics() {
        Game g = new Game(now, zone); Game.Fine f = law(g, Game.TIMED, 3, 1000); g.toggleLawTimer(f, now); g.advance(now + 1000, zone);
        assertFalse(g.toggleLawTimer(f, now + 1001)); assertEquals(0, new Statistics(g, true, now, zone).total);
        g.incurFine(f, now + 1002); assertEquals(1, new Statistics(g, true, now, zone).total); assertEquals(0, f.remaining(now + 2000));
    }
    @Test public void runningAndPausedLawProgressPersistAndReconcileOfflineOnce() throws Exception {
        Game g = new Game(now, zone); Game.Fine running = law(g, Game.TIMED, 3, 20000), paused = law(g, Game.TIMED, 3, 30000);
        g.toggleLawTimer(running, now); g.toggleLawTimer(paused, now); g.toggleLawTimer(paused, now + 4000);
        Game loaded = GameCodec.decode(GameCodec.encodeLegacy(g).toString(), now + 60000, zone);
        List<Game.Event> events = loaded.advance(now + 60000, zone);
        assertEquals(1, events.stream().filter(e -> e.type.equals("lawLimit")).count()); assertEquals(0, loaded.fines.get(0).remaining(now + 60000));
        assertEquals(26000, loaded.fines.get(1).remaining(now + 60000)); assertTrue(loaded.advance(now + 60000, zone).isEmpty());
    }
    @Test public void midnightResetsAmountAndPausedOrExhaustedTimeAllowances() {
        Game g = new Game(now, zone); Game.Fine amount = law(g, Game.AMOUNT, 2, 10000), timer = law(g, Game.TIMED, 3, 1000);
        g.useLawAmount(amount); g.toggleLawTimer(timer, now); g.advance(now + 1000, zone); g.advance(midnight, zone);
        assertEquals(0, amount.amountUsed); assertEquals(1000, timer.remaining(midnight)); assertEquals(0, timer.endsAt); assertEquals(0, g.finesToday);
    }
    @Test public void runningLawKeepsRunningWithANewDailyAllowanceAtMidnight() {
        Game g = new Game(now, zone); Game.Fine timer = law(g, Game.TIMED, 3, 20000); g.toggleLawTimer(timer, midnight - 5000);
        g.advance(midnight + 1000, zone); assertEquals(19000, timer.remaining(midnight + 1000)); assertEquals(midnight + 20000, timer.endsAt);
    }
    @Test public void lawAndQuestTimersCanExpireAtTheSameInstantWithoutLosingEitherEvent() {
        Game g = new Game(now, zone); g.energy = Game.DEAD; Game.Quest q = new Game.Quest(); q.name = "Quest"; q.firstDay = g.day; q.repeatable = true; g.addQuest(q); g.start(q, now);
        Game.Fine timer = law(g, Game.TIMED, 3, 120000); g.toggleLawTimer(timer, now);
        List<Game.Event> events = g.advance(now + 120000, zone);
        assertEquals(1, events.stream().filter(e -> e.type.equals("lawLimit")).count()); assertEquals(1, events.stream().filter(e -> e.type.equals("victory")).count());
        assertEquals(120000, g.timedMillisToday); assertEquals(1, g.history.size()); assertEquals(Game.QUEST_HISTORY, g.history.get(0).kind);
        assertTrue(g.hasRunningTimers()); g.advance(now + 160000, zone); assertFalse(g.hasRunningTimers());
    }
    @Test public void multipleLawTimersExpireIndependentlyAndDeletionRemovesARunningTimer() {
        Game g = new Game(now, zone); Game.Fine a = law(g, Game.TIMED, 3, 1000), b = law(g, Game.TIMED, 3, 2000), c = law(g, Game.TIMED, 3, 3000);
        g.toggleLawTimer(a, now); g.toggleLawTimer(b, now); g.toggleLawTimer(c, now); g.deleteLaw(c);
        assertEquals(now + 2000, g.lastTimerDeadline()); assertEquals(a, g.nextRunningLaw());
        assertEquals(1, g.advance(now + 1500, zone).size()); assertEquals(b, g.nextRunningLaw());
        assertEquals(1, g.advance(now + 5000, zone).size()); assertFalse(g.hasRunningTimers());
    }
    @Test public void changingATimeLimitPreservesConsumedTimeAndModeChangesResetAllowance() {
        Game g = new Game(now, zone); Game.Fine timer = law(g, Game.TIMED, 3, 20000); g.toggleLawTimer(timer, now);
        g.configureLaw(timer, Game.TIMED, 3, 30000, now + 5000); assertEquals(25000, timer.remaining(now + 5000));
        g.configureLaw(timer, Game.AMOUNT, 2, 30000, now + 5000); assertEquals(0, timer.endsAt); g.useLawAmount(timer);
        g.configureLaw(timer, Game.TIMED, 2, 10000, now + 5000); assertEquals(0, timer.amountUsed); assertEquals(10000, timer.remaining(now + 5000));
    }
    @Test public void olderSavesDefaultToStrictAndAmountUsageSurvivesRoundTrip() throws Exception {
        Game g = new Game(now, zone); Game.Fine amount = law(g, Game.AMOUNT, 4, 10000); g.useLawAmount(amount);
        Game loaded = GameCodec.decode(GameCodec.encodeLegacy(g).toString(), now, zone); assertEquals(Game.AMOUNT, loaded.fines.get(0).mode); assertEquals(4, loaded.fines.get(0).amountLimit); assertEquals(1, loaded.fines.get(0).amountUsed);
        org.json.JSONObject saved = GameCodec.encodeLegacy(g).put("version", 13); saved.getJSONArray("fines").getJSONObject(0).remove("mode");
        assertEquals(Game.STRICT, GameCodec.decode(saved.toString(), now, zone).fines.get(0).mode);
    }
    @Test public void warningIsOneContinuousAlarmThatRisesAndFallsInVolume() {
        byte[] sound = Synth.wav(Synth.WARNING); assertEquals(44 + 26460 * 2, sound.length);
        java.nio.ByteBuffer samples = java.nio.ByteBuffer.wrap(sound).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        long[] levels = new long[5]; int[] windows = {50, 250, 590, 930, 1130};
        for (int window = 0; window < windows.length; window++) {
            long energy = 0;
            for (int i = windows[window] * 22050 / 1000; i < (windows[window] + 20) * 22050 / 1000; i++) energy += Math.abs((int) samples.getShort(44 + i * 2));
            levels[window] = energy; assertTrue(energy > 10000);
        }
        assertTrue(levels[0] < levels[1]); assertTrue(levels[1] < levels[2]);
        assertTrue(levels[2] > levels[3]); assertTrue(levels[3] > levels[4]);
        assertEquals(0, Synth.warningEnvelope(0), .0001); assertEquals(1, Synth.warningEnvelope(600), .0001); assertEquals(0, Synth.warningEnvelope(1200), .0001);
    }
}
