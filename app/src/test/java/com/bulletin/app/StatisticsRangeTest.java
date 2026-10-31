package com.bulletin.app;

import org.junit.Test;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import static org.junit.Assert.*;

public class StatisticsRangeTest {
    private final ZoneId zone = ZoneId.of("UTC");
    private final long now = Instant.parse("2026-10-30T12:00:00Z").toEpochMilli();
    private Game.Quest quest(Game g, String name) {
        Game.Quest q = new Game.Quest(); q.name = name; q.repeatable = true; q.forever = true; q.firstDay = g.day;
        g.addQuest(q); g.history.add(new Game.HistoryEntry(Game.QUEST_HISTORY, q.id, q.name, now)); return q;
    }
    @Test public void rangeStartsUseInclusiveRollingWeeksMonthsAndYears() {
        long day = LocalDate.of(2026, 10, 30).toEpochDay();
        assertEquals(day, Statistics.rangeStart(day, 0)); assertEquals(day - 6, Statistics.rangeStart(day, 1));
        assertEquals(LocalDate.of(2026, 10, 1).toEpochDay(), Statistics.rangeStart(day, 2));
        assertEquals(LocalDate.of(2026, 7, 31).toEpochDay(), Statistics.rangeStart(day, 3));
        assertEquals(LocalDate.of(2025, 10, 31).toEpochDay(), Statistics.rangeStart(day, 4));
    }
    @Test public void questTimeAndLawReportsExcludeOutsideDatesAndIncludeFirstDay() {
        Game g = new Game(now, zone); Game.Quest q = quest(g, "Focus");
        g.focus.add(new Game.FocusEntry(q.id, q.name, g.day, 10000));
        g.focus.add(new Game.FocusEntry(q.id, q.name, g.day - 6, 20000));
        g.focus.add(new Game.FocusEntry(q.id, q.name, g.day - 7, 40000));
        g.focus.add(new Game.FocusEntry(q.id, q.name, g.day + 1, 80000));
        assertEquals(10000, new Statistics(g, false, now, zone, g.day).total);
        assertEquals(30000, new Statistics(g, false, now, zone, g.day - 6).total);
        Game.Fine law = new Game.Fine(); law.name = "Law"; g.addLaw(law);
        for (int offset : new int[]{0, -6, -7, 1}) g.history.add(new Game.HistoryEntry(Game.FINE_HISTORY, law.id, law.name, now + offset * 86400000L));
        assertEquals(1, new Statistics(g, true, now, zone, g.day).total);
        assertEquals(2, new Statistics(g, true, now, zone, g.day - 6).total);
    }
    @Test public void otherGroupingIsRecomputedAfterDateFiltering() {
        Game g = new Game(now, zone); Game.Quest a = quest(g, "Large"), b = quest(g, "Small");
        g.focus.add(new Game.FocusEntry(a.id, a.name, g.day, 100000));
        g.focus.add(new Game.FocusEntry(b.id, b.name, g.day, 1000));
        g.focus.add(new Game.FocusEntry(b.id, b.name, g.day - 20, 1000000));
        Statistics today = new Statistics(g, false, now, zone, g.day);
        assertEquals(101000, today.total); assertEquals("other", today.slices.get(1).id); assertEquals(1000, today.slices.get(1).value);
        Statistics month = new Statistics(g, false, now, zone, Statistics.rangeStart(g.day, 2));
        assertEquals(b.id, month.slices.get(0).id); assertEquals(1001000, month.slices.get(0).value);
    }
}