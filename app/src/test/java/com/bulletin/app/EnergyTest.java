package com.bulletin.app;

import org.junit.Test;
import java.time.Instant;
import java.time.ZoneId;
import static org.junit.Assert.*;

public class EnergyTest {
    private final long now = Instant.parse("2026-10-29T12:00:00Z").toEpochMilli();
    private final ZoneId zone = ZoneId.of("UTC");
    private Game.Quest quest(Game g, boolean repeatable) {
        Game.Quest q = new Game.Quest(); q.firstDay = g.day; q.repeatable = repeatable; g.addQuest(q); return q;
    }
    @Test public void fixedQuestAndRestDurationsMatchEveryPreset() {
        Game g = new Game(now, zone); Game.Quest q = quest(g, true);
        int[] modes = {Game.EXTREME, Game.EXTRA, Game.NORMAL, Game.RELAX, Game.MINIMAL, Game.DEAD};
        long[] durations = {5400000, 2700000, 1500000, 900000, 420000, 20000};
        long[] rests = {1200000, 600000, 300000, 240000, 120000, 10000};
        for (int i = 0; i < modes.length; i++) { g.energy = modes[i]; assertEquals(durations[i], g.remainingFor(q, now)); assertEquals(rests[i], g.energyRestMillis()); }
        g.questMinutes = 1; g.energy = Game.LOW; assertEquals(420000, g.remainingFor(q, now));
    }
    @Test public void bountiesScaleFromNormalAndRoundUpToWholeCents() {
        Game g = new Game(now, zone); Game.Quest q = quest(g, true);
        int[] modes = {Game.EXTREME, Game.EXTRA, Game.NORMAL, Game.RELAX, Game.MINIMAL, Game.DEAD}; long[] rewards = {360, 180, 100, 60, 28, 2};
        for (int i = 0; i < modes.length; i++) { g.energy = modes[i]; assertEquals(rewards[i], g.bountyFor(q)); assertEquals(rewards[i], g.previewBounty()); }
        g.energy = Game.LOW; g.nextBounty = 101; assertEquals(29, g.bountyFor(q));
    }
    @Test public void singleQuestsStayInstantAndUseThirtyPercentRegardlessOfEnergy() {
        Game g = new Game(now, zone); Game.Quest q = quest(g, false); g.energy = Game.EXTRA;
        assertFalse(g.isTimed(q)); assertEquals(30, g.bountyFor(q));
        g.completeInstant(q, now); assertEquals(30, g.coins); assertNull(g.session);
        assertTrue(g.restoreOneOff(q.id, now + 1)); assertEquals(0, g.coins);
    }
    @Test public void requiredDiscountCombinesWithEnergyBeforeRounding() {
        Game g = new Game(now, zone); Game.Quest q = quest(g, false); q.required = true;
        g.energy = Game.EXTRA; assertEquals(18, g.bountyFor(q));
        g.energy = Game.MINIMAL; assertEquals(3, g.bountyFor(q));
        g.energy = Game.DEAD; assertEquals(1, g.bountyFor(q));
    }
    @Test public void activeAndPausedQuestsKeepAcceptedDurationAndRewardAcrossChanges() throws Exception {
        Game g = new Game(now, zone); g.restMinutes = 0; Game.Quest q = quest(g, true); g.energy = Game.DEAD;
        assertTrue(g.start(q, now)); assertEquals(now + 20000, g.session.endsAt); assertEquals(2, g.activeBounty());
        g.energy = Game.EXTRA; g.questMinutes = 30; assertEquals(2, g.activeBounty());
        assertTrue(g.cancelQuest(now + 5000)); assertEquals(15000, g.remainingFor(q, now + 5000)); assertEquals(2, g.bountyFor(q));
        Game loaded = GameCodec.decode(GameCodec.encode(g).toString(), now + 10000, zone);
        Game.Quest resumed = loaded.quests.get(0); assertEquals(Game.EXTRA, loaded.energy);
        assertTrue(loaded.start(resumed, now + 10000)); assertEquals(now + 25000, loaded.session.endsAt);
        assertEquals(2, loaded.activeBounty()); loaded.advance(now + 25000, zone); assertEquals(2, loaded.coins);
        assertEquals(10000, Game.restFor(loaded.session));
    }
    @Test public void extremeDurationAndRestSurvivePauseAndRelaunch() throws Exception {
        Game g = new Game(now, zone); g.questMinutes = 240; g.energy = Game.EXTREME; Game.Quest q = quest(g, true);
        assertTrue(g.start(q, now)); assertEquals(now + 90 * 60000L, g.session.endsAt);
        g.cancelQuest(now + 1000);
        Game loaded = GameCodec.decode(GameCodec.encode(g).toString(), now + 2000, zone);
        assertEquals(90 * 60000L - 1000, loaded.quests.get(0).remainingMillis);
        assertEquals(360, loaded.bountyFor(loaded.quests.get(0)));
    }
    @Test public void energyPersistsAndOlderSavesDefaultToNormal() throws Exception {
        Game g = new Game(now, zone); g.energy = Game.LOW;
        org.json.JSONObject encoded = GameCodec.encode(g);
        assertEquals(Game.LOW, GameCodec.decode(encoded.toString(), now, zone).energy);
        encoded.remove("energy"); assertEquals(Game.NORMAL, GameCodec.decode(encoded.toString(), now, zone).energy);
    }
    @Test public void proportionalRewardsDoNotOverflowAtExtremeBalances() {
        assertEquals(Game.LIMIT, Game.proportionalBounty(Game.LIMIT, false, 28800000, 14400000));
        assertEquals(Game.LIMIT / 2, Game.proportionalBounty(Game.LIMIT, false, 7200000, 14400000));
        assertTrue(Game.proportionalBounty(Game.LIMIT, false, 20000, 14400000) > 0);
    }
    @Test public void deadUsesExactlyTwentySecondsOfQuestAndTenSecondsOfRest() {
        Game g = new Game(now, zone); g.energy = Game.DEAD; Game.Quest q = quest(g, true);
        g.start(q, now); g.advance(now + 19999, zone); assertEquals(Game.QUEST, g.session.phase);
        g.advance(now + 20000, zone); assertEquals(Game.REST, g.session.phase); assertEquals(now + 30000, g.session.endsAt);
        g.advance(now + 29999, zone); assertNotNull(g.session);
        g.advance(now + 30000, zone); assertNull(g.session);
    }
    @Test public void legacyRestMinutesArePreservedWithoutNewRestField() throws Exception {
        Game g = new Game(now, zone); g.start(quest(g, true), now);
        org.json.JSONObject saved = GameCodec.encode(g).put("version", 9);
        saved.getJSONObject("session").remove("restMillis"); saved.getJSONObject("session").put("restMinutes", 4);
        Game restored = GameCodec.decode(saved.toString(), now, zone);
        assertEquals(240000, Game.restFor(restored.session));
        restored.advance(restored.session.endsAt, zone); assertEquals(Game.REST, restored.session.phase);
        assertEquals(now + 1500000 + 240000, restored.session.endsAt);
    }
}
