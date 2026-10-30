package com.bulletin.app;

import org.junit.Test;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Random;
import static org.junit.Assert.*;

public class CategoryFilterTrashTest {
    private final ZoneId zone = ZoneId.of("UTC");
    private final long now = Instant.parse("2026-10-30T12:00:00Z").toEpochMilli();
    private Game.Quest quest(Game g, String category) {
        Game.Quest q = new Game.Quest(); q.name = "Focus"; q.firstDay = g.day; q.forever = true; q.repeatable = true; g.addQuest(q); g.assignCategory(q, category); return q;
    }
    private Game.Category category(Game g, String name) { Game.Category c = new Game.Category(); c.name = name; g.addCategory(c); return c; }
    @Test public void choicesAreStrictlyLimitedToCheckedCategoriesIncludingMustDoAndSingles() {
        Game g = new Game(now, zone); Game.Category a = category(g, "A"), b = category(g, "B");
        Game.Quest hidden = quest(g, b.id); hidden.required = true; hidden.repeatable = false;
        Game.Quest hiddenSingle = quest(g, ""); hiddenSingle.repeatable = false;
        Game.Quest allowed = quest(g, a.id), allowedSecond = quest(g, a.id);
        g.randomMode = true; g.setRandomCategoryEnabled(b.id, false); g.setRandomCategoryEnabled("", false);
        for (int i = 0; i < 20; i++) { g.refreshRandomQuests(new Random(i)); assertEquals(2, g.boardQuests().size()); assertTrue(g.boardQuests().contains(allowed)); assertTrue(g.boardQuests().contains(allowedSecond)); }
        g.setRandomCategoryEnabled(a.id, false); assertTrue(g.boardQuests().isEmpty());
        g.setRandomCategoryEnabled(b.id, true); assertEquals(hidden, g.boardQuests().get(0));
    }
    @Test public void filtersPersistAndDoNotLimitTheNormalBoardOrAnalytics() throws Exception {
        Game g = new Game(now, zone); Game.Category c = category(g, "C"); Game.Quest q = quest(g, c.id), other = quest(g, "");
        g.randomMode = true; g.setRandomCategoryEnabled(c.id, false);
        g.history.add(new Game.HistoryEntry(Game.QUEST_HISTORY, q.id, q.name, now)); g.focus.add(new Game.FocusEntry(q.id, q.name, g.day, 10000));
        Game loaded = GameCodec.decode(GameCodec.encode(g).toString(), now, zone);
        assertEquals(other.id, loaded.boardQuests().get(0).id); assertEquals(2, loaded.activeRepeatableQuests().size());
        assertEquals(10000, new Statistics(loaded, false, now, zone).total); assertEquals(2, new Statistics(loaded, false, now, zone).heatSeries.size());
        loaded.randomMode = false; assertEquals(2, loaded.boardQuests().size());
    }
    @Test public void activeTimelineAndHeatmapsExcludeSinglesInactiveAndHistoricalQuests() {
        Game g = new Game(now, zone); Game.Quest active = quest(g, ""), future = quest(g, ""), single = quest(g, "");
        future.firstDay++; single.repeatable = false;
        g.history.add(new Game.HistoryEntry(Game.QUEST_HISTORY, "expired", "Expired", now));
        g.focus.add(new Game.FocusEntry("expired", "Expired", g.day, 99999));
        g.focus.add(new Game.FocusEntry(future.id, future.name, g.day, 88888));
        g.focus.add(new Game.FocusEntry(active.id, active.name, g.day, 100));
        assertEquals(1, g.activeRepeatableQuests().size()); assertEquals(active, g.activeRepeatableQuests().get(0));
        Statistics data = new Statistics(g, false, now, zone); assertEquals(1, data.heatSeries.size()); assertEquals(active.id, data.heatSeries.get(0).id); assertEquals(100, data.maximum);
    }
    @Test public void deletedDefinitionSurvivesRestartAndRestoresWithoutHistoryOrRewardChanges() throws Exception {
        Game g = new Game(now, zone); Game.Category c = category(g, "C"); Game.Quest q = quest(g, c.id);
        q.name = "Restore me"; q.hue = 42; q.emoji = "x"; q.prerequisites.add("Prepare"); g.coins = 1234;
        g.history.add(new Game.HistoryEntry(Game.QUEST_HISTORY, q.id, q.name, now)); g.focus.add(new Game.FocusEntry(q.id, q.name, g.day, 1000));
        g.deleteQuest(q); assertTrue(g.quests.isEmpty()); assertTrue(g.history.isEmpty()); assertTrue(g.focus.isEmpty());
        Game loaded = GameCodec.decode(GameCodec.encode(g).toString(), now, zone); assertEquals(1, loaded.deletedQuests.size());
        assertTrue(loaded.restoreDeletedQuest(q.id, now + 1)); assertEquals(0, loaded.deletedQuests.size());
        Game.Quest restored = loaded.quests.get(0); assertEquals(c.id, restored.categoryId); assertEquals(42, restored.hue); assertEquals("x", restored.emoji); assertEquals("Prepare", restored.prerequisites.get(0));
        assertEquals(1234, loaded.coins); assertEquals(100, loaded.nextBounty); assertTrue(loaded.history.isEmpty()); assertEquals(1, loaded.questsCreated);
        assertFalse(loaded.restoreDeletedQuest(q.id, now + 2));
    }
    @Test public void oldOneOffRestoresForTodayAndMissingCategoryFallsBackToUncategorized() {
        Game g = new Game(now, zone); Game.Category c = category(g, "C"); Game.Quest q = quest(g, c.id);
        q.forever = false; q.repeatable = false; q.firstDay = g.day - 2; q.completedDay = g.day; q.completions = 1;
        g.deleteQuest(q); g.deleteCategory(c); assertTrue(g.restoreDeletedQuest(q.id, now));
        assertEquals("", q.categoryId); assertEquals(g.day, q.firstDay); assertFalse(q.done(g.day)); assertTrue(g.canStart(q));
    }
    @Test public void deletingARunningQuestStopsItAndCannotAwardItLater() {
        Game g = new Game(now, zone); g.energy = Game.DEAD; Game.Quest q = quest(g, ""); g.start(q, now);
        g.deleteQuest(q); g.advance(now + 60000, zone); assertNull(g.session); assertEquals(0, g.coins); assertTrue(g.history.isEmpty()); assertEquals(1, g.deletedQuests.size());
    }
    @Test public void archivedQuestsRemainAvailableAcrossMidnightsAndDoNotGenerateMissedFines() {
        Game g = new Game(now, zone); Game.Quest q = quest(g, ""); q.required = true; q.repeatable = false; g.deleteQuest(q);
        g.advance(now + 3 * 86400000L, zone); assertEquals(1, g.deletedQuests.size()); assertTrue(g.missedReviews.isEmpty());
    }
}
