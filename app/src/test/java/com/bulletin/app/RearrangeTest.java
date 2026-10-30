package com.bulletin.app;

import org.junit.Test;
import java.time.Instant;
import java.time.ZoneId;
import static org.junit.Assert.*;

public class RearrangeTest {
    private final ZoneId zone = ZoneId.of("UTC");
    private final long now = Instant.parse("2026-10-30T12:00:00Z").toEpochMilli();
    private Game.Quest quest(Game g, String name) {
        Game.Quest q = new Game.Quest(); q.name = name; q.firstDay = g.day; g.addQuest(q); return q;
    }
    @Test public void categoryHeaderAppendsEvenAnExistingMemberWithoutReapplyingColor() {
        Game g = new Game(now, zone); Game.Quest a = quest(g, "A"), b = quest(g, "B");
        Game.Category c = new Game.Category(); c.hue = 80; g.addCategory(c);
        g.assignCategory(a, c.id); g.assignCategory(b, c.id); a.hue = 200;
        g.moveToCategoryEnd(a, c.id);
        assertEquals(a, g.quests.get(g.quests.size() - 1)); assertEquals(200, a.hue); assertEquals(c.id, a.categoryId);
    }
    @Test public void movingOutsideCategoriesAppendsToBoardAndPreservesColorAcrossReload() throws Exception {
        Game g = new Game(now, zone); Game.Quest a = quest(g, "A"), b = quest(g, "B");
        a.hue = 70; Game.Category c = new Game.Category(); c.hue = 40; g.addCategory(c); g.assignCategory(a, c.id);
        g.moveToCategoryEnd(a, "");
        Game loaded = GameCodec.decode(GameCodec.encode(g).toString(), now, zone);
        Game.Quest moved = loaded.quests.get(loaded.quests.size() - 1);
        assertEquals(a.id, moved.id); assertEquals("", moved.categoryId); assertEquals(70, moved.hue);
        assertEquals(a.id, loaded.boardOrder.get(loaded.boardOrder.size() - 1));
    }
    @Test public void deletingCategoryRetainsMemberQuestsTheirOrderColorsAndHistory() {
        Game g = new Game(now, zone); Game.Quest a = quest(g, "A"), b = quest(g, "B");
        a.hue = 150; Game.Category c = new Game.Category(); c.hue = 100; g.addCategory(c); g.assignCategory(a, c.id); g.assignCategory(b, c.id);
        g.history.add(new Game.HistoryEntry(Game.QUEST_HISTORY, a.id, a.name, now));
        g.deleteCategory(c);
        assertTrue(g.categories.isEmpty()); assertEquals(2, g.quests.size()); assertEquals(1, g.history.size());
        assertEquals("", a.categoryId); assertEquals("", b.categoryId); assertEquals(150, a.hue);
        assertEquals(a.id, g.boardOrder.get(0)); assertEquals(b.id, g.boardOrder.get(1));
    }
    @Test public void lawReorderPersistsWithoutChangingDefinitionsOrHistory() throws Exception {
        Game g = new Game(now, zone); Game.Fine a = new Game.Fine(), b = new Game.Fine(); a.name = "A"; b.name = "B";
        g.addLaw(a); g.addLaw(b); g.incurFine(a, now);
        assertTrue(g.moveLaw(a.id, b.id)); assertFalse(g.moveLaw(a.id, a.id)); assertFalse(g.moveLaw("missing", a.id));
        Game loaded = GameCodec.decode(GameCodec.encode(g).toString(), now, zone);
        assertEquals(b.id, loaded.fines.get(0).id); assertEquals(a.id, loaded.fines.get(1).id);
        assertEquals(a.id, loaded.history.get(0).id); assertEquals(1, loaded.finesToday);
    }
    @Test public void rearrangeUnlocksIndependentlyOnSecondCreationAndPersistsAfterDeletion() throws Exception {
        Game g = new Game(now, zone); Game.Quest a = quest(g, "A"); Game.Fine law = new Game.Fine(); g.addLaw(law);
        assertFalse(g.questRearrangeUnlocked()); assertFalse(g.lawRearrangeUnlocked());
        quest(g, "B"); assertTrue(g.questRearrangeUnlocked()); assertFalse(g.lawRearrangeUnlocked());
        g.addLaw(new Game.Fine()); g.deleteQuest(a); g.deleteLaw(law);
        Game loaded = GameCodec.decode(GameCodec.encode(g).toString(), now, zone);
        assertTrue(loaded.questRearrangeUnlocked()); assertTrue(loaded.lawRearrangeUnlocked());
    }
    @Test public void inactiveAppearanceIncludesFutureAndCompletedSinglesButNotRepeatableCompletions() {
        Game g = new Game(now, zone); Game.Quest q = quest(g, "A"); assertTrue(g.questActiveToday(q));
        q.firstDay++; assertFalse(g.questActiveToday(q)); q.firstDay--;
        q.completedDay = g.day; q.completions = 1; assertFalse(g.questActiveToday(q));
        q.repeatable = true; assertTrue(g.questActiveToday(q));
    }
}
