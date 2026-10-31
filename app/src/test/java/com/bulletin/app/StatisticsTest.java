package com.bulletin.app;

import org.junit.Test;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import static org.junit.Assert.*;

public class StatisticsTest {
    private final ZoneId zone = ZoneId.of("UTC");
    private final long now = Instant.parse("2026-10-30T12:00:00Z").toEpochMilli();
    private Game.Quest quest(Game g, String name) { Game.Quest q = new Game.Quest(); q.name = name; q.repeatable = true; q.forever = true; q.firstDay = g.day; g.addQuest(q); return q; }
    private void completed(Game g, Game.Quest q, long millis) {
        g.history.add(new Game.HistoryEntry(Game.QUEST_HISTORY, q.id, q.name, now)); g.focus.add(new Game.FocusEntry(q.id, q.name, g.day, millis));
    }
    @Test public void pieAndBarsGroupStrictlyBelowOnePercentAndExcludeUncompletedQuests() {
        Game g = new Game(now, zone); Game.Quest a = quest(g, "A"), b = quest(g, "B"), c = quest(g, "C"), d = quest(g, "D"), never = quest(g, "Never");
        completed(g, a, 98000); completed(g, b, 1000); completed(g, c, 500); completed(g, d, 500);
        g.focus.add(new Game.FocusEntry(never.id, never.name, g.day, 999999));
        Statistics data = new Statistics(g, false, now, zone);
        assertEquals(100000, data.total); assertEquals(3, data.slices.size());
        assertEquals(a.id, data.slices.get(0).id); assertEquals(b.id, data.slices.get(1).id);
        assertEquals("other", data.slices.get(2).id); assertEquals(1000, data.slices.get(2).value);
    }
    @Test public void pausesAndRelaunchPreserveActualFocusAndExcludeRest() throws Exception {
        Game g = new Game(now, zone); Game.Quest q = quest(g, "Focus"); g.energy = Game.DEAD;
        g.start(q, now); g.pauseQuest(now + 5000); assertEquals(5000, g.focus.get(0).millis);
        g = GameCodec.decode(GameCodec.encode(g).toString(), now + 10000, zone); q = g.quests.get(0);
        g.start(q, now + 10000); g.advance(now + 35000, zone);
        assertEquals(20000, g.focus.get(0).millis); assertNull(g.session);
        Statistics data = new Statistics(g, false, now + 35000, zone); assertEquals(20000, data.total);
    }
    @Test public void midnightSplitsTimeAcrossTheDaysItWasActuallySpent() {
        Game g = new Game(now, zone); Game.Quest q = quest(g, "Focus"); g.energy = Game.DEAD;
        long midnight = Instant.parse("2026-10-31T00:00:00Z").toEpochMilli();
        long previousDay = g.day; g.start(q, midnight - 5000); g.advance(midnight + 15000, zone);
        assertEquals(2, g.focus.size()); assertEquals(previousDay, g.focus.get(0).day); assertEquals(5000, g.focus.get(0).millis);
        assertEquals(previousDay + 1, g.focus.get(1).day); assertEquals(15000, g.focus.get(1).millis);
    }
    @Test public void heatmapsShareOneMaximumAndIgnoreDaysOutsideThePastYear() {
        Game g = new Game(now, zone); Game.Quest a = quest(g, "A"), b = quest(g, "B");
        completed(g, a, 20000); completed(g, b, 10000);
        g.focus.add(new Game.FocusEntry(a.id, a.name, g.day, 15000));
        g.focus.add(new Game.FocusEntry(b.id, b.name, g.day - 400, 9999999));
        Statistics data = new Statistics(g, false, now, zone);
        assertEquals(35000, data.maximum); assertEquals(365, data.lastDay - data.firstDay + 1);
        assertEquals(Long.valueOf(35000), data.series.get(0).days.get(g.day));
    }
    @Test public void lawChartsUseReportCountsOnlyForActiveLaws() {
        Game g = new Game(now, zone); Game.Fine law = new Game.Fine(); law.name = "Law"; g.addLaw(law);
        g.history.add(new Game.HistoryEntry(Game.FINE_HISTORY, law.id, law.name, now));
        Game.HistoryEntry old = new Game.HistoryEntry(Game.FINE_HISTORY, "historic", "Historical", now); old.count = 4; g.history.add(old);
        Statistics data = new Statistics(g, true, now, zone); assertEquals(1, data.total); assertEquals(1, data.maximum); assertEquals(1, data.slices.size());
    }
    @Test public void olderSavesDoNotInventUnrecordedQuestTime() throws Exception {
        Game g = new Game(now, zone); Game.Quest q = quest(g, "Old"); completed(g, q, 20000);
        org.json.JSONObject saved = GameCodec.encode(g); saved.remove("focus"); saved.remove("analyticsSince"); saved.put("version", 11);
        Game loaded = GameCodec.decode(saved.toString(), now, zone);
        assertEquals(now, loaded.analyticsSince); assertTrue(loaded.focus.isEmpty()); assertEquals(0, new Statistics(loaded, false, now, zone).total);
    }
    @Test public void deletingAQuestRemovesItsRecordedTimeAndChartEntries() {
        Game g = new Game(now, zone); Game.Quest q = quest(g, "Delete"); completed(g, q, 1000); g.deleteQuest(q);
        assertTrue(g.focus.isEmpty()); assertTrue(new Statistics(g, false, now, zone).series.isEmpty());
    }
    @Test public void returningToOriginalArrangementRestoresCategoryHueAndOrderWithoutGameplayRollback() {
        Game g = new Game(now, zone); Game.Quest a = quest(g, "A"), b = quest(g, "B"); a.hue = 210;
        Game.Category c = new Game.Category(); c.hue = 40; g.addCategory(c); BoardArrangement snapshot = new BoardArrangement(g);
        List<String> original = new java.util.ArrayList<>(g.boardOrder);
        g.moveToCategoryEnd(a, c.id); g.moveQuest(a.id, b.id); g.coins = 321; a.completions = 1;
        snapshot.restore(g); assertEquals("", a.categoryId); assertEquals(210, a.hue); assertEquals(a, g.quests.get(0));
        assertEquals(original, g.boardOrder); assertEquals(321, g.coins); assertEquals(1, a.completions);
    }
    @Test public void originalLawOrderRestoresAndDeletedQuestsAreNotResurrected() {
        Game g = new Game(now, zone); Game.Quest q = quest(g, "Gone"); Game.Fine a = new Game.Fine(), b = new Game.Fine(); g.addLaw(a); g.addLaw(b);
        BoardArrangement snapshot = new BoardArrangement(g); g.moveLaw(a.id, b.id); g.deleteQuest(q); snapshot.restore(g);
        assertEquals(a, g.fines.get(0)); assertEquals(b, g.fines.get(1)); assertTrue(g.quests.isEmpty()); assertFalse(g.boardOrder.contains(q.id));
    }
}
