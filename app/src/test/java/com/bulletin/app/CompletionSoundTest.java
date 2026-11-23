package com.bulletin.app;

import org.junit.Test;
import java.time.*;
import java.util.*;
import static org.junit.Assert.*;

public class CompletionSoundTest {
    private final ZoneId zone=ZoneId.of("UTC");
    private final long now=Instant.parse("2026-11-11T12:00:00Z").toEpochMilli();
    private Game.Quest quest(Game g,boolean repeatable) {
        Game.Quest q=new Game.Quest();q.name="Task";q.firstDay=g.day;q.forever=true;q.repeatable=repeatable;g.addQuest(q);return q;
    }
    private List<Integer> sounds(List<Game.Event> events,long at) {
        List<Integer> result=new ArrayList<>();GameStore.playCompletionSounds(events,at,result::add);return result;
    }
    @Test public void taskAndRestSoundAtTheirDeadlinesWithoutCheckingTheBox() {
        Game g=new Game(now,zone);g.energy=Game.DEAD;Game.Quest q=quest(g,true);assertTrue(g.start(q,now));
        long end=g.session.endsAt;assertTrue(sounds(g.advance(end-1,zone),end-1).isEmpty());
        assertEquals(Collections.singletonList(Synth.VICTORY),sounds(g.advance(end,zone),end));
        assertFalse(g.completionLog.pending.isEmpty());long restEnd=g.session.endsAt;
        assertTrue(sounds(g.advance(restEnd-1,zone),restEnd-1).isEmpty());
        assertEquals(Collections.singletonList(Synth.DING),sounds(g.advance(restEnd,zone),restEnd));
        assertFalse(g.completionLog.pending.isEmpty());assertTrue(sounds(g.advance(restEnd+1,zone),restEnd+1).isEmpty());
    }
    @Test public void instantAndRepeatCompletionsStillNotify() {
        Game g=new Game(now,zone);assertEquals(Collections.singletonList(Synth.VICTORY),sounds(g.completeInstant(quest(g,false),now),now));
        g.energy=Game.DEAD;Game.Quest repeated=quest(g,true);assertTrue(g.start(repeated,now+1));long end=g.session.endsAt;
        sounds(g.advance(end,zone),end);g.completionLog.acknowledge(repeated.id);long ready=g.session.endsAt;g.advance(ready,zone);
        assertTrue(g.start(repeated,ready+1));end=g.session.endsAt;
        assertEquals(Collections.singletonList(Synth.VICTORY),sounds(g.advance(end,zone),end));
        assertFalse(g.completionLog.pending.stream().anyMatch(e->e.id.equals(repeated.id)));
    }
    @Test public void catchingUpOnlyPlaysRecentMilestones() {
        List<Game.Event> events=Arrays.asList(new Game.Event("victory",now-45000),new Game.Event("ready",now));
        assertEquals(Collections.singletonList(Synth.DING),sounds(events,now));
        assertTrue(sounds(events,now+5000).isEmpty());
    }
    @Test public void milestonesInOneSyncBothNotifyImmediately() {
        List<Game.Event> events=Arrays.asList(new Game.Event("victory",now-100),new Game.Event("ready",now));
        assertEquals(Arrays.asList(Synth.VICTORY,Synth.DING),sounds(events,now));
    }
}
