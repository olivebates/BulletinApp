package com.bulletin.app;

import org.junit.Test;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import static org.junit.Assert.*;

public class AnalyticsGroupsTest {
    private final ZoneId zone = ZoneId.of("UTC");
    private final long now = Instant.parse("2026-10-30T12:00:00Z").toEpochMilli();
    private Game.Quest quest(Game g, String name, String category) {
        Game.Quest q = new Game.Quest(); q.name = name; q.repeatable = true; q.forever = true; q.firstDay = g.day;
        g.addQuest(q); g.assignCategory(q, category); return q;
    }
    private void record(Game g, Game.Quest q, long amount) {
        g.history.add(new Game.HistoryEntry(Game.QUEST_HISTORY, q.id, q.name, now)); g.focus.add(new Game.FocusEntry(q.id, q.name, g.day, amount));
    }
    @Test public void analyticsSectionsStartExpandedAndSaveIndependentlyFromBoardAndOtherPages() throws Exception {
        Game g = new Game(now, zone); Game.Category c = new Game.Category(); c.id = "home"; c.collapsed = true; g.addCategory(c);
        org.json.JSONObject old = GameCodec.encodeLegacy(g); old.remove("collapsedAnalyticsSections"); old.put("version", 15);
        Game migrated = GameCodec.decode(old.toString(), now, zone);
        assertTrue(migrated.category(c.id).collapsed); assertTrue(migrated.collapsedAnalyticsSections.isEmpty());
        migrated.collapsedAnalyticsSections.add("0:0:home");
        Game loaded = GameCodec.decode(GameCodec.encodeLegacy(migrated).toString(), now, zone);
        assertTrue(loaded.collapsedAnalyticsSections.contains("0:0:home"));
        assertFalse(loaded.collapsedAnalyticsSections.contains("0:2:home"));
        assertFalse(loaded.collapsedAnalyticsSections.contains("1:0:home"));
        loaded.collapsedAnalyticsSections.clear(); assertTrue(loaded.category(c.id).collapsed);
    }
    @Test public void pieExcludesSinglesInactiveAndHistoricalBeforeComputingPercentages() {
        Game g = new Game(now, zone); Game.Quest active = quest(g, "Active", ""), single = quest(g, "Single", ""), future = quest(g, "Future", "");
        record(g, active, 1000); record(g, single, 999999); record(g, future, 999999); single.repeatable = false; future.firstDay++;
        g.history.add(new Game.HistoryEntry(Game.QUEST_HISTORY, "old", "Old", now)); g.focus.add(new Game.FocusEntry("old", "Old", g.day, 999999));
        g.randomMode = true; g.setRandomCategoryEnabled("", false);
        Statistics data = new Statistics(g, false, now, zone);
        assertEquals(1000, data.total); assertEquals(1, data.slices.size()); assertEquals(active.id, data.slices.get(0).id);
    }
    @Test public void groupsFollowBoardCategoryOrderAndKeepUncategorizedMembersSeparate() {
        Game g = new Game(now, zone); Game.Category first = new Game.Category(), second = new Game.Category(), empty = new Game.Category();
        first.name = "First"; second.name = "Second"; g.addCategory(first); g.addCategory(second); g.addCategory(empty);
        Game.Quest a = quest(g, "A", first.id), b = quest(g, "B", second.id), u = quest(g, "U", ""), inactive = quest(g, "Inactive", first.id); inactive.firstDay++;
        List<String> ids = new ArrayList<>(); for (Game.Quest q : g.activeRepeatableQuests()) ids.add(q.id);
        Map<String, List<String>> groups = g.analyticsGroups(ids, false);
        assertEquals(Arrays.asList(second.id, first.id, ""), new ArrayList<>(groups.keySet()));
        assertEquals(Arrays.asList(a.id), groups.get(first.id)); assertEquals(Arrays.asList(b.id), groups.get(second.id)); assertEquals(Arrays.asList(u.id), groups.get(""));
        first.collapsed = true; assertEquals(groups, g.analyticsGroups(ids, false));
    }
    @Test public void lawGroupingIncludesOnlyExistingLawsAndKeepsTheirBoardOrder() {
        Game g = new Game(now, zone); Game.Fine a = new Game.Fine(), b = new Game.Fine(); g.addLaw(a); g.addLaw(b); g.moveLaw(a.id, b.id);
        Map<String, List<String>> groups = g.analyticsGroups(Arrays.asList(a.id, "old", b.id), true);
        assertEquals(1, groups.size()); assertEquals(Arrays.asList(b.id, a.id), groups.get(""));
    }
}
