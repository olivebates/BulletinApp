package com.bulletin.app;
import org.junit.Test;
import java.time.*;
import java.util.*;
import static org.junit.Assert.*;

public class TimelineEnergyTest {
    final ZoneId zone = ZoneId.of("UTC");
    final long now = Instant.parse("2026-11-01T12:00:00Z").toEpochMilli();
    Game.Quest quest(Game g) { Game.Quest q = new Game.Quest(); q.name = "Task"; q.firstDay = g.day; q.repeatable = true; q.forever = true; g.addQuest(q); return q; }
    @Test public void capturesStartingEnergyDespitePresetChangeAndCompactRelaunch() throws Exception {
        Game g = new Game(now,zone); Game.Quest q = quest(g); g.energy = Game.DEAD; g.start(q,now);
        g.energy = Game.EXTREME;
        Game loaded = GameCodec.decode(SaveCompression.pack(GameCodec.encode(g).toString()),now,zone);
        assertEquals(Game.DEAD,loaded.session.energy); loaded.advance(loaded.session.endsAt,zone);
        assertEquals(Game.DEAD,loaded.history.get(0).energy);
        loaded = GameCodec.decode(GameCodec.encode(loaded).toString(),now,zone);
        assertEquals(Game.DEAD,loaded.history.get(0).energy);
    }
    @Test public void pauseResumeKeepsOriginalEnergyAndCancelClearsIt() throws Exception {
        Game g = new Game(now,zone); Game.Quest q = quest(g); g.energy = Game.MINIMAL; g.start(q,now); g.pauseQuest(now+1000);
        g.energy = Game.EXTRA; g = GameCodec.decode(GameCodec.encode(g).toString(),now+2000,zone); q = g.quests.get(0);
        assertEquals(Game.MINIMAL,q.pausedEnergy); g.start(q,now+2000); assertEquals(Game.MINIMAL,g.session.energy);
        g.cancelQuest(q,now+3000); assertEquals(-1,q.pausedEnergy); g.start(q,now+4000); assertEquals(Game.EXTRA,g.session.energy);
    }
    @Test public void olderHistoryAndPausedSessionsStayUnknownRatherThanUsingCurrentPreset() throws Exception {
        Game g = new Game(now,zone); Game.Quest q = quest(g); g.energy = Game.RELAX;
        g.history.add(new Game.HistoryEntry(Game.QUEST_HISTORY,q.id,q.name,now-1000)); q.remainingMillis=10000;
        Game loaded = GameCodec.decode(GameCodec.encode(g).toString(),now,zone);
        assertEquals(-1,loaded.history.get(0).energy); loaded.start(loaded.quests.get(0),now); assertEquals(-1,loaded.session.energy);
        assertEquals(TimelineColors.intensity(.5),TimelineColors.energy(-1));
    }
    @Test public void paletteProgressesFromOriginalYellowToGreenInEnergyOrder() {
        int[] energies={Game.DEAD,Game.MINIMAL,Game.RELAX,Game.NORMAL,Game.EXTRA,Game.EXTREME};
        assertEquals(TimelineColors.YELLOW,TimelineColors.energy(Game.DEAD)); assertEquals(TimelineColors.GREEN,TimelineColors.energy(Game.EXTREME));
        int previous=256; for(int energy:energies){int red=(TimelineColors.energy(energy)>>16)&255;assertTrue(red<previous);previous=red;}
    }
    @Test public void dailySummariesCompareQuestDayTimeAcrossAllQuestsAndKeepTotalsOnReload() throws Exception {
        Game g=new Game(now,zone);Game.Quest a=quest(g),b=quest(g);
        long day=LocalDate.ofEpochDay(g.day).minusMonths(4).toEpochDay();
        for(int energy:new int[]{Game.DEAD,Game.EXTREME}){Game.HistoryEntry e=new Game.HistoryEntry(Game.QUEST_HISTORY,a.id,a.name,HistoryAggregation.millis(day,zone)+1000+energy);e.energy=energy;g.history.add(e);}
        g.history.add(new Game.HistoryEntry(Game.QUEST_HISTORY,b.id,b.name,HistoryAggregation.millis(day,zone)+5000));
        g.focus.add(new Game.FocusEntry(a.id,a.name,day,60000));g.focus.add(new Game.FocusEntry(a.id,a.name,day,60000));g.focus.add(new Game.FocusEntry(b.id,b.name,day,240000));
        HistoryAggregation.aggregate(g,zone);assertEquals(2,g.history.size());
        Game loaded=GameCodec.decode(GameCodec.encode(g).toString(),now,zone);
        Map<Game.HistoryEntry,Integer> colors=TimelineColors.summaries(loaded,zone);
        for(Game.HistoryEntry e:loaded.history){assertEquals(-1,e.energy);if(e.id.equals(a.id)){assertEquals(2,e.count);assertEquals(Integer.valueOf(TimelineColors.intensity(.5)),colors.get(e));}else assertEquals(Integer.valueOf(TimelineColors.GREEN),colors.get(e));}
    }
    @Test public void weeklySummariesUseWeeklyTotalsAndDoNotAlterLawColors() {
        Game g=new Game(now,zone);Game.Quest q=quest(g);long day=g.day-500;
        Game.HistoryEntry e=new Game.HistoryEntry(Game.QUEST_HISTORY,q.id,q.name,HistoryAggregation.millis(day,zone));e.energy=Game.DEAD;g.history.add(e);
        g.focus.add(new Game.FocusEntry(q.id,q.name,day,60000));
        Game.HistoryEntry law=new Game.HistoryEntry(Game.FINE_HISTORY,"law","Law",e.at);g.history.add(law);
        HistoryAggregation.aggregate(g,zone);Map<Game.HistoryEntry,Integer> colors=TimelineColors.summaries(g,zone);
        for(Game.HistoryEntry item:g.history)if(item.kind.equals(Game.QUEST_HISTORY))assertEquals(Integer.valueOf(TimelineColors.GREEN),colors.get(item));else assertFalse(colors.containsKey(item));
    }
}
