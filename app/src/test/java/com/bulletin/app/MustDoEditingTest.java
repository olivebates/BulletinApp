package com.bulletin.app;
import org.junit.Test;
import java.time.*;
import static org.junit.Assert.*;
public class MustDoEditingTest {
    final ZoneId zone=ZoneId.of("UTC");final long now=Instant.parse("2026-11-11T12:00:00Z").toEpochMilli();
    Game.Category category(Game g,boolean all,int target){Game.Category c=new Game.Category();c.mustDo=true;c.requireAll=all;c.requiredTasks=target;g.addCategory(c);return c;}
    Game.Quest quest(Game g,Game.Category c){Game.Quest q=new Game.Quest();q.firstDay=g.day;q.forever=true;g.addQuest(q);if(c!=null)g.assignCategory(q,c.id);return q;}
    @Test public void categoryUnlocksAtItsTargetOrThreeCompletedTasks(){for(int target:new int[]{1,2,5}){Game g=new Game(now,zone);Game.Category c=category(g,false,target);Game.Quest[] tasks={quest(g,c),quest(g,c),quest(g,c)};assertFalse(g.canChangeCategoryRequirements(c));for(int i=0;i<Math.min(target,3);i++){if(i>0)assertFalse(g.canChangeCategoryRequirements(c));g.completeInstant(tasks[i],now+i);}assertTrue(g.canChangeCategoryRequirements(c));}}
    @Test public void allModeNeedsDistinctCompletedTasksForTheThreeTaskException(){Game g=new Game(now,zone);Game.Category c=category(g,true,1);Game.Quest[] tasks={quest(g,c),quest(g,c),quest(g,c),quest(g,c)};g.completeInstant(tasks[0],now);g.completeInstant(tasks[1],now+1);assertFalse(g.canChangeCategoryRequirements(c));g.completeInstant(tasks[2],now+2);assertTrue(g.canChangeCategoryRequirements(c));}
    @Test public void taskMustDoCannotBeRemovedUntilCompletedAndRelocksNextDay()throws Exception {Game g=new Game(now,zone);Game.Quest q=quest(g,null);q.required=true;assertFalse(g.canRemoveMustDo(q));g.completeInstant(q,now);assertTrue(g.canRemoveMustDo(q));g=GameCodec.decode(GameCodec.encode(g).toString(),now+1,zone);q=g.quests.get(0);assertTrue(g.canRemoveMustDo(q));g.advance(now+86400000L,zone);assertFalse(g.canRemoveMustDo(q));}
    @Test public void categoryLockPersistsAndResetsWithTheDay()throws Exception {Game g=new Game(now,zone);Game.Category c=category(g,false,5);for(int i=0;i<3;i++)g.completeInstant(quest(g,c),now+i);assertTrue(g.canChangeCategoryRequirements(c));g=GameCodec.decode(GameCodec.encode(g).toString(),now+4,zone);c=g.categories.get(0);assertTrue(g.canChangeCategoryRequirements(c));g.advance(now+86400000L,zone);assertFalse(g.canChangeCategoryRequirements(c));}
    @Test public void newOptionalEmptyAndZeroQuotaCategoriesAreEditable(){Game g=new Game(now,zone);assertTrue(g.canChangeCategoryRequirements(null));Game.Category c=category(g,false,5);assertTrue(g.canChangeCategoryRequirements(c));quest(g,c);c.mustDo=false;assertTrue(g.canChangeCategoryRequirements(c));c.mustDo=true;c.requiredTasks=0;assertTrue(g.canChangeCategoryRequirements(c));}
}
