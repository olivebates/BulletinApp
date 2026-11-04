package com.bulletin.app;

import org.junit.Test;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import static org.junit.Assert.*;

public class BoardTest {
    private final long now = Instant.parse("2026-10-29T12:00:00Z").toEpochMilli();
    private final ZoneId zone = ZoneId.of("UTC");
    private Game.Quest add(Game g, boolean required, boolean repeatable) {
        Game.Quest q = new Game.Quest(); q.name = "Quest " + g.quests.size(); q.firstDay = g.day;
        q.required = required; q.repeatable = repeatable; g.addQuest(q); return q;
    }
    @Test public void newRequiredQuestsGoToTopAndManualOrderIsRetained() {
        Game g = new Game(now, zone); Game.Quest first = add(g, false, true), must = add(g, true, false), last = add(g, false, false);
        assertEquals(must, g.availableQuests().get(0));
        assertTrue(g.moveQuest(last.id, must.id)); assertEquals(last, g.availableQuests().get(0));
        assertTrue(g.moveQuest(first.id, last.id)); assertEquals(first, g.availableQuests().get(0));
        assertFalse(g.moveQuest("missing", must.id));
    }
    @Test public void shufflePinsMustFirstAndOptionalSingleSecond() {
        Game g = new Game(now, zone); add(g, true, false); add(g, false, false);
        for (int i = 0; i < 8; i++) add(g, false, true);
        g.randomMode = true;
        for (int seed = 0; seed < 100; seed++) {
            g.refreshRandomQuests(new Random(seed)); List<Game.Quest> board = g.boardQuests();
            assertEquals(2, board.size()); assertTrue(board.get(0).required);
            assertFalse(board.get(1).required); assertFalse(board.get(1).repeatable);
            assertEquals(2, new HashSet<>(g.randomQuestIds).size());
        }
    }
    @Test public void optionalSingleIsSecondWithoutAnyRequiredQuests() {
        Game g = new Game(now, zone); add(g, false, false); add(g, false, true); add(g, false, true);
        g.randomMode = true; g.refreshRandomQuests(new Random(1));
        assertFalse(g.boardQuests().get(1).repeatable);
    }
    @Test public void refreshAvoidsPreviousChoicesWhenPossible() {
        Game g = new Game(now, zone); for (int i = 0; i < 8; i++) add(g, false, true);
        g.randomMode = true; g.refreshRandomQuests(new Random(1)); HashSet<String> old = new HashSet<>(g.randomQuestIds);
        g.refreshRandomQuests(new Random(2)); assertTrue(g.randomQuestIds.stream().noneMatch(old::contains));
    }
    @Test public void smallPoolsNeverDuplicateAndCompletionRemovesTasks() {
        Game g = new Game(now, zone); Game.Quest only = add(g, false, false);
        g.randomMode = true; g.refreshRandomQuests(new Random(1)); assertEquals(1, g.boardQuests().size());
        g.completeInstant(only, now); assertTrue(g.boardQuests().isEmpty());
    }
    @Test public void twoChoicesRetainBothReservedSlotsWhenOnlyOneOfEachExists() {
        Game g = new Game(now, zone); Game.Quest must = add(g, true, false), single = add(g, false, false);
        add(g, false, true); add(g, false, true); g.randomMode = true;
        g.refreshRandomQuests(new Random(1)); g.refreshRandomQuests(new Random(2));
        assertEquals(2, g.boardQuests().size()); assertEquals(must, g.boardQuests().get(0)); assertEquals(single, g.boardQuests().get(1));
    }
    @Test public void reorderingShuffleCannotDisplaceReservedSlots() {
        Game g = new Game(now, zone); Game.Quest must = add(g, true, false), single = add(g, false, false), repeat = add(g, false, true);
        g.randomMode = true; g.refreshRandomQuests(new Random(1));
        g.moveQuest(repeat.id, must.id);
        assertEquals(must, g.boardQuests().get(0)); assertEquals(single, g.boardQuests().get(1));
    }
    @Test public void boardExcludesFutureAndCompletedAndRefreshesEachDay() {
        Game g = new Game(now, zone); Game.Quest future = add(g, true, false); future.firstDay++;
        Game.Quest done = add(g, false, false); g.completeInstant(done, now);
        Game.Quest daily = add(g, false, true); daily.forever = true;
        g.randomMode = true; g.refreshRandomQuests(new Random(1)); assertEquals(daily, g.boardQuests().get(0));
        g.advance(now + 86400000L, zone); assertTrue(g.boardQuests().get(0).required);
    }
    @Test public void colorsEmojisOrderAndShuffleSurviveRestart() throws Exception {
        Game g = new Game(now, zone); Game.Quest a = add(g, false, false), b = add(g, true, false);
        a.hue = 283; a.emoji = "🎨"; Game.Fine law = new Game.Fine(); law.emoji = "🚫"; g.fines.add(law);
        g.moveQuest(a.id, b.id); g.randomMode = true; g.refreshRandomQuests(new Random(1));
        Game loaded = GameCodec.decode(GameCodec.encodeLegacy(g).toString(), now, zone);
        assertEquals(a.id, loaded.quests.get(0).id); assertEquals(283, loaded.quests.get(0).hue);
        assertEquals("🎨", loaded.quests.get(0).emoji); assertEquals("🚫", loaded.fines.get(0).emoji);
        assertEquals(g.randomQuestIds, loaded.randomQuestIds); assertTrue(loaded.boardQuests().get(0).required);
    }
}
