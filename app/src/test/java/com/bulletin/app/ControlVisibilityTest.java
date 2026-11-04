package com.bulletin.app;

import org.junit.Test;
import java.time.Instant;
import java.time.ZoneId;
import static org.junit.Assert.*;

public class ControlVisibilityTest {
    private final long now = Instant.parse("2026-10-29T12:00:00Z").toEpochMilli();
    private final ZoneId zone = ZoneId.of("UTC");
    private Game.Quest add(Game g, boolean repeatable) {
        Game.Quest q = new Game.Quest(); q.firstDay = g.day; q.repeatable = repeatable; g.addQuest(q); return q;
    }
    @Test public void thirdCreatedQuestPermanentlyUnlocksControlsAcrossDeletionAndRestart() throws Exception {
        Game g = new Game(now, zone);
        Game.Quest a = add(g, false), b = add(g, false); assertFalse(g.questControlsUnlocked());
        Game.Quest c = add(g, false); assertTrue(g.questControlsUnlocked());
        g.deleteQuest(a); g.deleteQuest(b); g.deleteQuest(c);
        assertTrue(GameCodec.decode(GameCodec.encodeLegacy(g).toString(), now, zone).questControlsUnlocked());
    }
    @Test public void firstCreatedLawPermanentlyUnlocksLawControls() throws Exception {
        Game g = new Game(now, zone); assertFalse(g.lawControlsUnlocked());
        Game.Fine law = new Game.Fine(); g.addLaw(law); assertTrue(g.lawControlsUnlocked());
        g.deleteLaw(law); assertTrue(GameCodec.decode(GameCodec.encodeLegacy(g).toString(), now, zone).lawControlsUnlocked());
    }
    @Test public void energyRequiresDueRepeatableQuestOrRunningTimedSession() {
        Game g = new Game(now, zone); add(g, false); assertFalse(g.hasActiveRepeatableQuests());
        Game.Quest q = add(g, true); q.firstDay++; assertFalse(g.hasActiveRepeatableQuests());
        q.firstDay--; assertTrue(g.hasActiveRepeatableQuests());
        assertTrue(g.start(q, now)); q.firstDay++; g.session.phase = Game.REST; assertFalse(g.hasActiveRepeatableQuests());
        g.session.phase = Game.QUEST; g.deleteQuest(q); assertNull(g.session); assertFalse(g.hasActiveRepeatableQuests());

    }
    @Test public void oldSaveUsesDistinctKnownEntitiesAndHistoryForMilestones() throws Exception {
        Game g = new Game(now, zone); add(g, false); add(g, false);
        g.history.add(new Game.HistoryEntry(Game.QUEST_HISTORY, "expired", "Old quest", now));
        g.history.add(new Game.HistoryEntry(Game.QUEST_HISTORY, "expired", "Old quest", now + 1));
        g.history.add(new Game.HistoryEntry(Game.FINE_HISTORY, "removed-law", "Old law", now));
        org.json.JSONObject old = GameCodec.encodeLegacy(g); old.remove("questsCreated"); old.remove("lawsCreated");
        Game loaded = GameCodec.decode(old.toString(), now, zone);
        assertEquals(3, loaded.questsCreated); assertEquals(1, loaded.lawsCreated);
        assertTrue(loaded.questControlsUnlocked()); assertTrue(loaded.lawControlsUnlocked());
    }
}
