package com.bulletin.app;

import org.junit.Test;
import org.json.JSONObject;
import java.time.Instant;
import java.time.ZoneId;
import static org.junit.Assert.*;

public class GameCodecTest {
    @Test public void newInstallHasDemoListsButExistingAndDeletedListsStayUnchanged() throws Exception {
        Game fresh = GameCodec.decode("", NOW, ZONE);
        assertEquals(2, fresh.choiceLists.size()); assertEquals(2, fresh.briefLists.size());
        assertEquals("Exercises (Demo)", fresh.choiceLists.get(0).name); assertEquals(7, fresh.choiceLists.get(0).items.size());
        assertEquals("Recipes (Demo)", fresh.choiceLists.get(1).name); assertEquals(5, fresh.choiceLists.get(1).items.size());
        assertEquals(java.util.Arrays.asList("Location", "Time Period", "Architecture"), fresh.briefLists.get(0).items);
        assertEquals(java.util.Arrays.asList("Body Type", "Age", "Clothes Style", "Personality"), fresh.briefLists.get(1).items);
        Game restored = GameCodec.decode(GameCodec.encode(fresh).toString(), NOW, ZONE);
        assertEquals(2, restored.choiceLists.size()); assertEquals(2, restored.briefLists.size());
        fresh.choiceLists.clear(); fresh.briefLists.clear();
        restored = GameCodec.decode(GameCodec.encode(fresh).toString(), NOW, ZONE);
        assertTrue(restored.choiceLists.isEmpty()); assertTrue(restored.briefLists.isEmpty());
        Game existing = new Game(NOW, ZONE);
        restored = GameCodec.decode(GameCodec.encodeLegacy(existing).toString(), NOW, ZONE);
        assertTrue(restored.choiceLists.isEmpty()); assertTrue(restored.briefLists.isEmpty());
    }
    @Test public void additionalTutorialFlagsSurviveCompactSave() throws Exception {
        Game original = new Game(NOW, ZONE);
        Game fresh = GameCodec.decode(GameCodec.encodeLegacy(original).toString(), NOW, ZONE);
        assertFalse(fresh.timelineHintShown); assertFalse(fresh.choiceHintShown);
        assertFalse(fresh.rearrangeVisited); assertFalse(fresh.rearrangeExitHintShown);
        original.timelineHintShown = original.choiceHintShown = original.rearrangeVisited = original.rearrangeExitHintShown = true;
        Game restored = GameCodec.decode(GameCodec.encode(original).toString(), NOW, ZONE);
        assertTrue(restored.timelineHintShown); assertTrue(restored.choiceHintShown);
        assertTrue(restored.rearrangeVisited); assertTrue(restored.rearrangeExitHintShown);
    }
    @Test public void welcomeIsFirstLaunchOnlyAndOlderSavesSkipIt() throws Exception {
        Game fresh = new Game(NOW, ZONE); assertFalse(fresh.welcomeShown);
        assertFalse(GameCodec.decode(GameCodec.encode(fresh).toString(), NOW, ZONE).welcomeShown);
        fresh.welcomeShown = true;
        assertTrue(GameCodec.decode(GameCodec.encode(fresh).toString(), NOW, ZONE).welcomeShown);
        JSONObject old = GameCodec.encodeLegacy(fresh); old.remove("welcomeShown");
        assertTrue(GameCodec.decode(old.toString(), NOW, ZONE).welcomeShown);
    }
    @Test public void energyHintSurvivesCompactSaveAndDefaultsOffForOldSaves() throws Exception {
        Game original = new Game(NOW, ZONE);
        assertFalse(GameCodec.decode(GameCodec.encodeLegacy(original).toString(), NOW, ZONE).energyHintShown);
        original.energyHintShown = true;
        assertTrue(GameCodec.decode(GameCodec.encode(original).toString(), NOW, ZONE).energyHintShown);
        JSONObject old = GameCodec.encodeLegacy(original); old.remove("energyHintShown");
        assertFalse(GameCodec.decode(old.toString(), NOW, ZONE).energyHintShown);
    }
    @Test public void undoAndMissedReviewSurviveRestart() throws Exception {
        Game g = new Game(NOW, ZONE);
        Game.Quest done = new Game.Quest(); done.name = "Done"; done.firstDay = g.day; g.quests.add(done);
        g.completeInstant(done, NOW);
        Game restored = GameCodec.decode(GameCodec.encodeLegacy(g).toString(), NOW + 1, ZONE);
        assertEquals(1, restored.undo.size()); assertTrue(restored.undoOneOff(NOW + 2)); assertTrue(restored.canStart(restored.quests.get(0)));
        done.required = true; done.completedDay = Long.MIN_VALUE;
        g.advance(NOW + 86400000L, ZONE);
        restored = GameCodec.decode(GameCodec.encodeLegacy(g).toString(), NOW + 86400001L, ZONE);
        assertTrue(restored.undo.isEmpty()); assertEquals(1, restored.missedReviews.size());
        assertEquals("Done", restored.missedReviews.get(0).names.get(0));
    }
    @Test public void pendingCelebrationSurvivesRestart() throws Exception {
        Game g = sample(); g.pendingConfetti = 2;
        Game loaded = GameCodec.decode(GameCodec.encodeLegacy(g).toString(), NOW, ZONE);
        assertEquals(2, loaded.pendingConfetti);
    }
    private static final long NOW = Instant.parse("2026-10-29T12:00:00Z").toEpochMilli();
    private static final ZoneId ZONE = ZoneId.of("UTC");
    private Game sample() {
        Game g = new Game(NOW, ZONE); g.days = 12; g.coins = 12340; g.nextBounty = 170;
        g.pendingCoins = 200; g.completedToday = 3; g.finesToday = 2; g.hue = 240;
        Game.Quest q = new Game.Quest(); q.name = "Existing quest"; q.firstDay = g.day - 4;
        q.forever = true; q.repeatable = true; q.required = true; q.interval = 2;
        q.completedDay = g.day - 2; q.completions = 2; q.prerequisites.add("Existing prerequisite"); g.quests.add(q);
        Game.Fine f = new Game.Fine(); f.name = "Existing fine"; g.fines.add(f); g.start(q, NOW); return g;
    }
    private JSONObject legacy(int version) throws Exception {
        JSONObject o = GameCodec.encodeLegacy(sample()); o.put("version", version).put("coins", 1234).put("pendingCoins", 20).put("nextBounty", 17);
        o.remove("firstOpenedDay"); o.remove("history"); o.remove("balances");
        JSONObject s = o.getJSONObject("session"); s.remove("rewardMinutes"); s.remove("referenceMinutes");
        if (version == 1) {
            o.remove("timedMillisToday"); o.getJSONArray("quests").getJSONObject(0).remove("questMinutes");
            s.remove("timed"); s.remove("required"); s.remove("accountedAt");
        }
        return o;
    }
    @Test public void versionOneMigratesCurrencyAndKeepsExistingDefinitionsAndSession() throws Exception {
        Game migrated = GameCodec.decode(legacy(1).toString(), NOW + 10000, ZONE);
        assertEquals(12340, migrated.coins); assertEquals(200, migrated.pendingCoins); assertEquals(170, migrated.nextBounty);
        assertEquals(12, migrated.days); assertEquals(3, migrated.completedToday); assertEquals(2, migrated.finesToday);
        assertEquals(240, migrated.hue, 0); assertEquals("Existing fine", migrated.fines.get(0).name);
        assertEquals("Existing prerequisite", migrated.quests.get(0).prerequisites.get(0)); assertEquals(-1, migrated.quests.get(0).questMinutes);
        assertTrue(migrated.session.timed); assertTrue(migrated.session.required); assertEquals(NOW + 1500000, migrated.session.endsAt);
        assertEquals(10000, migrated.focusMillisToday(NOW + 10000)); assertEquals(GameCodec.VERSION, GameCodec.encodeLegacy(migrated).getInt("version"));
    }
    @Test public void versionTwoConvertsDebtAndPreservesKnownCompletionDates() throws Exception {
        JSONObject old = legacy(2).put("coins", -30).put("pendingCoins", 0);
        Game migrated = GameCodec.decode(old.toString(), NOW, ZONE);
        assertEquals(-300, migrated.coins); assertEquals(1, migrated.history.size());
        Game.HistoryEntry e = migrated.history.get(0); assertEquals(2, e.count); assertTrue(e.dateOnly);
        assertEquals(migrated.day - 2, Game.localDay(e.at, ZONE)); assertEquals(migrated.day - 2, migrated.firstOpenedDay);
        assertEquals(1, migrated.balances.size()); assertEquals(-300, migrated.balances.get(0).cents);
    }
    @Test public void euroConversionIsNeverAppliedTwice() throws Exception {
        Game migrated = GameCodec.decode(legacy(2).toString(), NOW, ZONE);
        Game restored = GameCodec.decode(GameCodec.encodeLegacy(migrated).toString(), NOW, ZONE);
        assertEquals(12340, restored.coins); assertEquals(170, restored.nextBounty); assertEquals(200, restored.pendingCoins);
        assertEquals(1, restored.history.size());
    }
    @Test public void signedBalancesUntimedSessionsAndTimingSurviveRoundTrip() throws Exception {
        Game g = sample(); g.coins = -3030; g.pendingCoins = 0; g.timedMillisToday = 123456;
        g.session.timed = false; g.session.endsAt = Long.MAX_VALUE; g.quests.get(0).questMinutes = 0;
        Game loaded = GameCodec.decode(GameCodec.encodeLegacy(g).toString(), NOW, ZONE);
        assertEquals(-3030, loaded.coins); assertEquals(123456, loaded.timedMillisToday); assertFalse(loaded.session.timed);
        assertEquals(Long.MAX_VALUE, loaded.session.endsAt); assertEquals(g.session.accountedAt, loaded.session.accountedAt);
    }
    @Test public void eventsAndBalanceSamplesSurviveRelaunchWithoutDuplication() throws Exception {
        Game g = sample(); g.pauseQuest(NOW + 1000); g.questMinutes = 0; g.restMinutes = 0;
        Game.Quest manual = new Game.Quest(); manual.firstDay = g.day; manual.name = "Instant"; g.quests.add(manual);
        g.completeInstant(manual, NOW + 3000); g.incurFine(g.fines.get(0), NOW + 4000);
        Game loaded = GameCodec.decode(GameCodec.encodeLegacy(g).toString(), NOW + 5000, ZONE);
        assertEquals(2, loaded.history.size()); assertEquals(g.history.get(0).id, loaded.history.get(0).id);
        assertEquals(g.history.get(1).at, loaded.history.get(1).at); assertEquals(g.balances.size(), loaded.balances.size());
        assertEquals(g.balances.get(g.balances.size() - 1).cents, loaded.balances.get(loaded.balances.size() - 1).cents);
    }
    @Test public void rewardReferenceIsPersistedAcrossSettingsChangesAndRelaunch() throws Exception {
        Game g = sample(); g.pauseQuest(NOW); g.questMinutes = 15; g.quests.get(0).questMinutes = 5; g.start(g.quests.get(0), NOW);
        g.questMinutes = 30; long quoted = g.activeBounty();
        Game loaded = GameCodec.decode(GameCodec.encodeLegacy(g).toString(), NOW, ZONE);
        assertEquals(quoted, loaded.activeBounty()); assertEquals(25, loaded.session.referenceMinutes); assertEquals(25, loaded.session.rewardMinutes);
    }
    @Test(expected = GameCodec.FutureVersion.class) public void futureSchemasAreRejectedWithoutResettingTheirPayload() throws Exception {
        GameCodec.decode(GameCodec.encodeLegacy(sample()).put("version", 99).toString(), NOW, ZONE);
    }
    @Test public void legacyZeroMinuteDefaultIsRaisedToOneAndFirstOpenedDaySurvives() throws Exception {
        Game g = sample(); g.questMinutes = 0; g.firstOpenedDay -= 100;
        Game loaded = GameCodec.decode(GameCodec.encodeLegacy(g).toString(), NOW, ZONE);
        assertEquals(1, loaded.questMinutes); assertEquals(g.firstOpenedDay, loaded.firstOpenedDay);
    }
    @Test public void pausedTimeAndRewardReferenceSurviveRelaunch() throws Exception {
        Game g = sample(); g.pauseQuest(NOW + 12345);
        Game loaded = GameCodec.decode(GameCodec.encodeLegacy(g).toString(), NOW + 20000, ZONE);
        Game.Quest q = loaded.quests.get(0); assertEquals(1500000 - 12345, q.remainingMillis);
        assertEquals(25, q.pausedReferenceMinutes); assertEquals(25, q.pausedRewardMinutes);
        assertEquals(12345, loaded.timedMillisToday); assertNull(loaded.session);
        loaded.questMinutes = 30; assertTrue(loaded.start(q, NOW + 30000));
        assertEquals(NOW + 30000 + 1500000 - 12345, loaded.session.endsAt);
        assertEquals(25, loaded.session.referenceMinutes);
    }
    @Test public void versionThreeMigratesWithoutConvertingEurosAgain() throws Exception {
        JSONObject previous = GameCodec.encodeLegacy(sample()).put("version", 3);
        JSONObject q = previous.getJSONArray("quests").getJSONObject(0);
        q.remove("remainingMillis"); q.remove("pausedRewardMinutes"); q.remove("pausedReferenceMinutes");
        Game loaded = GameCodec.decode(previous.toString(), NOW, ZONE);
        assertEquals(12340, loaded.coins); assertEquals(170, loaded.nextBounty); assertEquals(-1, loaded.quests.get(0).remainingMillis);
        assertEquals(GameCodec.VERSION, GameCodec.encodeLegacy(loaded).getInt("version"));
    }
}
