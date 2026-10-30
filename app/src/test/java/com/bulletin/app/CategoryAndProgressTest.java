package com.bulletin.app;

import org.junit.Test;
import java.time.Instant;
import java.time.ZoneId;
import static org.junit.Assert.*;

public class CategoryAndProgressTest {
    private final ZoneId zone = ZoneId.of("UTC");
    private final long now = Instant.parse("2026-10-30T12:00:00Z").toEpochMilli();
    private final long midnight = Instant.parse("2026-10-31T00:00:00Z").toEpochMilli();
    private Game.Quest quest(Game g) {
        Game.Quest q = new Game.Quest(); q.firstDay = g.day; q.forever = true; q.bountySince = now; g.addQuest(q); return q;
    }
    @Test public void coloredCategoryNeverOverwritesQuestHue() {
        Game g = new Game(now, zone); Game.Quest q = quest(g); q.hue = 20;
        Game.Category c = new Game.Category(); c.hue = 90; g.addCategory(c);
        g.assignCategory(q, c.id); assertEquals(20, q.hue); assertFalse(g.boardOrder.contains(q.id));
        q.hue = 150; g.assignCategory(q, c.id); assertEquals(150, q.hue);
        c.hue = 180; assertEquals(150, q.hue);
        g.assignCategory(q, ""); assertEquals(150, q.hue); assertTrue(g.boardOrder.contains(q.id));
        g.assignCategory(q, c.id); assertEquals(150, q.hue);
    }
    @Test public void movingBetweenCategoriesKeepsIndividualColors() {
        Game g = new Game(now, zone); Game.Quest q = quest(g); q.hue = 32;
        Game.Category a = new Game.Category(), b = new Game.Category(); b.hue = 250; g.addCategory(a); g.addCategory(b);
        g.assignCategory(q, a.id); assertEquals(32, q.hue);
        g.moveToCategoryEnd(q, b.id); assertEquals(32, q.hue); assertEquals(b.id, q.categoryId);
    }
    @Test public void categoryColorOverridesAppearanceWithoutChangingSavedQuestColor() throws Exception {
        Game g = new Game(now, zone); Game.Quest q = quest(g); q.hue = 32;
        Game.Category c = new Game.Category(); c.hue = 210; g.addCategory(c);
        g.assignCategory(q, c.id); assertEquals(210, g.questHue(q), 0); assertEquals(32, q.hue);
        c.hue = 170; assertEquals(170, g.questHue(q), 0);
        q.hue = 80; assertEquals(170, g.questHue(q), 0);
        Game loaded = GameCodec.decode(GameCodec.encode(g).toString(), now, zone);
        Game.Quest restored = loaded.quests.get(0); assertEquals(170, loaded.questHue(restored), 0);
        loaded.assignCategory(restored, ""); assertEquals(80, loaded.questHue(restored), 0);
        c.hue = -1; assertEquals(80, g.questHue(q), 0);
        c.hue = 210; g.deleteCategory(c); assertEquals(80, g.questHue(q), 0);
    }
    @Test public void categoriesStartAtTheTopAndCanMoveAmongUngroupedQuests() {
        Game g = new Game(now, zone); Game.Quest q = quest(g); Game.Category c = new Game.Category(); g.addCategory(c);
        assertEquals(c.id, g.orderedBoardItems().get(0));
        assertTrue(g.moveBoardItem(c.id, q.id)); assertEquals(q.id, g.orderedBoardItems().get(0));
        assertFalse(g.moveBoardItem(c.id, c.id));
    }
    @Test public void membershipCollapseOrderHueAndHintSurviveRelaunch() throws Exception {
        Game g = new Game(now, zone); Game.Quest q = quest(g); q.hue = 75; Game.Category c = new Game.Category(); c.name = "Home"; c.hue = 201; c.collapsed = true;
        g.addCategory(c); g.assignCategory(q, c.id); g.swipeHintShown = true; g.lastSeenAt = now;
        Game loaded = GameCodec.decode(GameCodec.encode(g).toString(), now + 1000, zone);
        assertEquals(c.id, loaded.quests.get(0).categoryId); assertEquals(75, loaded.quests.get(0).hue); assertEquals(201, loaded.category(c.id).hue);
        assertTrue(loaded.category(c.id).collapsed); assertTrue(loaded.swipeHintShown); assertEquals(now, loaded.lastSeenAt);
        assertEquals(g.boardOrder, loaded.boardOrder);
    }
    @Test public void inactiveOfflineDaysNeverIncreaseTheDayCounter() {
        Game g = new Game(now, zone); g.advance(midnight + 3 * 86400000L, zone);
        assertEquals(0, g.days); assertEquals(-400, g.coins);
    }
    @Test public void completedQuestEarnsExactlyOneDayAcrossSeveralOfflineDays() {
        Game g = new Game(now, zone); g.completeInstant(quest(g), now); g.advance(midnight + 2 * 86400000L, zone);
        assertEquals(1, g.days); g.advance(midnight + 2 * 86400000L, zone); assertEquals(1, g.days);
    }
    @Test public void completingAnOptionalQuestDoesNotBypassUnfinishedRequiredQuests() {
        Game g = new Game(now, zone); g.days = 10; quest(g).required = true; g.completeInstant(quest(g), now);
        g.advance(midnight, zone); assertEquals(8, g.days); assertEquals(1, g.missedReviews.size());
    }
    @Test public void oneOffRewardIsThirtyPercentAtEveryEnergyAndMatchesTheAward() {
        for (int energy = Game.EXTRA; energy <= Game.RELAX; energy++) {
            Game g = new Game(now, zone); g.energy = energy; g.nextBounty = 101; Game.Quest q = quest(g);
            assertEquals(31, g.bountyFor(q)); g.completeInstant(q, now); assertEquals(31, g.coins);
        }
    }
    @Test public void offlineTimerFinishesAndAgesBountiesWithoutForegroundTicks() throws Exception {
        Game g = new Game(now, zone); Game.Quest q = quest(g); q.repeatable = true; g.energy = Game.EXTREME; g.start(q, now);
        Game loaded = GameCodec.decode(GameCodec.encode(g).toString(), now + 7200000, zone);
        loaded.advance(now + 7200000, zone); assertEquals(362, loaded.coins); assertNull(loaded.session); assertEquals(1, loaded.completedToday);
    }
    @Test public void graphReasonsRemainSeparateAtMidnightAndSurviveReload() throws Exception {
        Game g = new Game(now, zone); g.coins = 10000; g.recordBalance(now, "Starting funds");
        g.completeInstant(quest(g), now + 1); g.advance(midnight, zone);
        Game loaded = GameCodec.decode(GameCodec.encode(g).toString(), midnight, zone);
        assertEquals("Daily rent", loaded.balances.get(loaded.balances.size() - 1).cause);
        assertEquals("Daily interest", loaded.balances.get(loaded.balances.size() - 2).cause);
        assertTrue(loaded.balances.stream().anyMatch(p -> p.cause.startsWith("Quest completed:")));
    }
}
