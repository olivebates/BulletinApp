package com.bulletin.app;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;
import java.time.*;

public class ChoiceRandomizerTest {
    List<String> items(String... values) { return new ArrayList<>(Arrays.asList(values)); }
    @Test public void picksTwoDistinctNonblankItemsAndHandlesSmallLists() {
        ChoiceRandomizer r=new ChoiceRandomizer(items("A"," ","B","C"),new Random(5));r.start();assertEquals(2,r.options.size());assertNotEquals(r.options.get(0),r.options.get(1));assertFalse(r.options.contains(1));
        r=new ChoiceRandomizer(items("A"));r.start();assertEquals(Arrays.asList(0),r.options);assertTrue(r.choose(0));assertEquals(Arrays.asList(0),r.options);
        r=new ChoiceRandomizer(items(" "));r.start();assertTrue(r.options.isEmpty());assertFalse(r.choose(0));
    }
    @Test public void unseenItemsComeFirstAndPreviousPairIsAvoidedWhenPossible() {
        ChoiceRandomizer r=new ChoiceRandomizer(items("A","B","C","D","E"),new Random(2));r.start();Set<Integer> old=new HashSet<>(r.options);int chosen=r.options.get(0);r.choose(chosen);
        for(int next:r.options){assertFalse(old.contains(next));assertNotEquals(chosen,next);}
        Set<Integer> selected=new HashSet<>();selected.add(chosen);
        while(selected.size()<5){long unseen=5-selected.size();if(unseen>=2)for(int next:r.options)assertFalse(selected.contains(next));
            int next=r.options.stream().filter(i->!selected.contains(i)).findFirst().get();selected.add(next);r.choose(next);}
    }
    @Test public void exhaustedSessionOffersOldestSelectionsFirst() {
        ChoiceRandomizer r=new ChoiceRandomizer(items("A","B","C","D"),new Random(3));r.start();List<Integer> order=new ArrayList<>();
        while(order.size()<4){int next=r.options.stream().filter(i->!order.contains(i)).findFirst().get();order.add(next);r.choose(next);}
        assertEquals(order.subList(0,2),r.options);r.choose(order.get(0));assertEquals(order.subList(1,3),r.options);
        r.choose(order.get(1));assertEquals(order.subList(2,4),r.options);
    }
    @Test public void stopRejectsSelectionsAndNewSessionForgetsEarlierSelections() {
        ChoiceRandomizer r=new ChoiceRandomizer(items("A","B","C"),new Random(7));r.start();r.choose(r.options.get(0));r.stop();assertFalse(r.enabled);assertTrue(r.options.isEmpty());assertFalse(r.choose(0));r.start();assertEquals(2,r.options.size());
    }
    @Test public void duplicateNamesRemainSeparateChoices() {
        ChoiceRandomizer r=new ChoiceRandomizer(items("A","A"));r.start();assertEquals(2,r.options.size());int first=r.options.get(0);r.choose(first);assertNotEquals(Integer.valueOf(first),r.options.get(0));
    }
    @Test public void namedListsRoundTripWithOrderBlanksUnicodeAndDuplicateNames()throws Exception {
        long now=Instant.parse("2026-11-01T12:00:00Z").toEpochMilli();ZoneId zone=ZoneId.of("UTC");Game g=new Game(now,zone);
        for(int n=0;n<2;n++){Game.ChoiceList list=new Game.ChoiceList();list.name="Plans";list.items.addAll(items("Read 📚","","Walk","Read 📚"));g.choiceLists.add(list);}
        for(String text:new String[]{GameCodec.encodeLegacy(g).toString(),GameCodec.encode(g).toString(),SaveCompression.pack(GameCodec.encode(g).toString())}){
            Game loaded=GameCodec.decode(text,now,zone);assertEquals(2,loaded.choiceLists.size());for(int n=0;n<2;n++){assertEquals(g.choiceLists.get(n).id,loaded.choiceLists.get(n).id);assertEquals("Plans",loaded.choiceLists.get(n).name);assertEquals(g.choiceLists.get(n).items,loaded.choiceLists.get(n).items);}}
        assertTrue(GameCodec.decode(GameCodec.encode(new Game(now,zone)).toString(),now,zone).choiceLists.isEmpty());
    }
    @Test public void sessionSnapshotKeepsPairAndOldestSelectionOrder() {
        List<String> values=items("A","B","C","D");ChoiceRandomizer original=new ChoiceRandomizer(values,new Random(3));original.start();List<Integer> order=new ArrayList<>();
        while(order.size()<4){int next=original.options.stream().filter(i->!order.contains(i)).findFirst().get();order.add(next);original.choose(next);}
        ChoiceRandomizer restored=new ChoiceRandomizer(values,new Random(9));restored.restore(original.snapshot());
        assertTrue(restored.enabled);assertEquals(original.options,restored.options);assertArrayEquals(original.snapshot(),restored.snapshot());
        restored.choose(order.get(0));assertEquals(order.subList(1,3),restored.options);
    }
    @Test public void invalidOrStaleSessionSnapshotsDoNotCreateInvalidChoices() {
        ChoiceRandomizer r=new ChoiceRandomizer(items("A","B"));r.restore(null);r.restore(new long[]{1});assertFalse(r.enabled);
        r.restore(new long[]{1,0,0,0,0,0});assertFalse(r.enabled);r.restore(new long[]{1,0,0,8,0,0});assertFalse(r.enabled);
        r.restore(new long[]{1,0,0,1,8,0});assertFalse(r.enabled);
        r.start();r.stop();ChoiceRandomizer restored=new ChoiceRandomizer(items("A","B"));restored.restore(r.snapshot());assertFalse(restored.enabled);assertTrue(restored.options.isEmpty());
    }
}