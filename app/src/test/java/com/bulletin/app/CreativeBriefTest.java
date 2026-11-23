package com.bulletin.app;
import org.junit.Test;
import static org.junit.Assert.*;
import java.io.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;

public class CreativeBriefTest {
    BriefDictionary dictionary()throws Exception {
        Path path=Path.of("src/main/assets/creative-brief.tsv");if(!Files.exists(path))path=Path.of("app/src/main/assets/creative-brief.tsv");
        try(Reader input=Files.newBufferedReader(path,StandardCharsets.UTF_8)){return BriefDictionary.read(input);}
    }
    @Test public void dictionariesHaveExactly2100DistinctCategoryAppropriateEntries()throws Exception {
        BriefDictionary d=dictionary();int total=0;
        for(String category:BriefDictionary.CATEGORIES){List<String> words=d.words(category);int expected=category.equals("Theme")||category.equals("Ambience")?500:100;assertEquals(expected,words.size());Set<String> seen=new HashSet<>();for(String word:words){assertFalse(word.trim().isEmpty());assertFalse(java.util.regex.Pattern.compile("(^|[\\s-])[a-z]").matcher(word).find());assertTrue(seen.add(word.toLowerCase(Locale.ROOT)));}total+=words.size();}
        assertEquals(2100,total);assertTrue(d.words("Theme").containsAll(Arrays.asList("Steam Punk","Fantasy")));assertFalse(d.words("Theme").contains("Justice"));assertFalse(d.words("Theme").contains("Oil Painting"));assertFalse(d.words("Theme").contains("Sumi-E"));assertTrue(d.words("Ambience").containsAll(Arrays.asList("Eerie","Enchanted","Chaotic","Hot")));assertTrue(d.words("Age").contains("Newborn"));assertTrue(d.words("Animal").contains("Fox"));
    }
    @Test(expected=IOException.class)public void incompleteDictionaryIsRejected()throws Exception {BriefDictionary.read(new StringReader("Theme\tAdventure\n"));}
    @Test(expected=IOException.class)public void caseInsensitiveDuplicateDictionaryWordsAreRejected()throws Exception {BriefDictionary.read(new StringReader("Theme\tAdventure\nTheme\tadventure\n"));}
    @Test public void generationUsesEveryFieldInOrderAndNewBriefChangesEachWord()throws Exception {
        BriefDictionary d=dictionary();List<String> fields=new ArrayList<>(Arrays.asList(BriefDictionary.CATEGORIES));CreativeBrief b=new CreativeBrief(fields,new Random(17));b.generate(d);assertTrue(b.generated);assertEquals(fields.size(),b.words.size());List<String> first=new ArrayList<>(b.words);
        b.generate(d);for(int i=0;i<fields.size();i++){assertTrue(d.words(fields.get(i)).contains(b.words.get(i)));assertNotEquals(first.get(i),b.words.get(i));}
        b.generated=false;assertEquals(Arrays.asList(BriefDictionary.CATEGORIES),b.fields);
    }
    @Test public void repeatedCategoryFieldsAvoidDuplicateWordsWithinTheBrief()throws Exception {
        List<String> fields=new ArrayList<>();for(int i=0;i<100;i++)fields.add("Animal");CreativeBrief b=new CreativeBrief(fields,new Random(2));b.generate(dictionary());assertEquals(100,new HashSet<>(b.words).size());
        b.generate(dictionary());assertEquals(100,new HashSet<>(b.words).size());
    }
    @Test public void creatureEmotionAndConflictUseAllEntriesWithoutRepeatsAndRestore()throws Exception {
        BriefDictionary dictionary=dictionary();for(String category:Arrays.asList("Emotion","Internal Conflict")){
            List<String> fields=new ArrayList<>(Collections.nCopies(100,category));CreativeBrief brief=new CreativeBrief(fields,new Random(41));brief.generate(dictionary);assertEquals(new HashSet<>(dictionary.words(category)),new HashSet<>(brief.words));
            CreativeBrief restored=new CreativeBrief(fields);restored.restore(brief.snapshot(),dictionary);assertTrue(restored.generated);assertEquals(brief.words,restored.words);brief.generate(dictionary);assertEquals(100,new HashSet<>(brief.words).size());
        }
    }
    @Test public void generatedBriefAndScrollPositionsSurviveSessionRestoration()throws Exception {
        BriefDictionary d=dictionary();List<String> fields=new ArrayList<>(Arrays.asList("Theme","Age","Personality"));CreativeBrief b=new CreativeBrief(fields,new Random(4));b.editScroll=800;b.generate(d);b.briefScroll=150;CreativeBrief loaded=new CreativeBrief(fields);loaded.restore(b.snapshot(),d);assertTrue(loaded.generated);assertEquals(b.words,loaded.words);assertEquals(800,loaded.editScroll);assertEquals(150,loaded.briefScroll);
        loaded.restore(new String[]{"1","0","0","unknown","unknown","unknown"},d);assertEquals(b.words,loaded.words);
    }
    @Test public void briefListsRoundTripAlongsideChoiceListsAndOlderSavesStillLoad()throws Exception {
        long now=Instant.parse("2026-11-01T12:00:00Z").toEpochMilli();ZoneId zone=ZoneId.of("UTC");Game g=new Game(now,zone);Game.ChoiceList brief=new Game.ChoiceList();brief.name="Characters";brief.items.addAll(Arrays.asList("Theme","Animal","Animal","Age","Emotion","Internal Conflict"));g.addBriefList(brief);Game.ChoiceList choice=new Game.ChoiceList();choice.name="Activities";choice.items.add("Draw");g.addChoiceList(choice);
        for(String text:new String[]{GameCodec.encodeLegacy(g).toString(),GameCodec.encode(g).toString(),SaveCompression.pack(GameCodec.encode(g).toString())}){Game loaded=GameCodec.decode(text,now,zone);assertEquals(1,loaded.briefLists.size());assertEquals(brief.id,loaded.briefLists.get(0).id);assertEquals(brief.items,loaded.briefLists.get(0).items);assertEquals("Draw",loaded.choiceLists.get(0).items.get(0));}
        assertTrue(GameCodec.decode(GameCodec.encode(new Game(now,zone)).toString(),now,zone).briefLists.isEmpty());assertTrue(brief.id.startsWith("b"));assertNotEquals(brief.id,choice.id);
    }
    @Test(expected=GameCodec.FutureVersion.class)public void futureSavesAreProtectedBeforeReadingNewToolData()throws Exception {
        long now=Instant.parse("2026-11-01T12:00:00Z").toEpochMilli();ZoneId zone=ZoneId.of("UTC");
        org.json.JSONObject save=GameCodec.encodeLegacy(new Game(now,zone)).put("version",GameCodec.VERSION+1).put("briefLists",new org.json.JSONArray().put(new org.json.JSONObject().put("id","future").put("name","Future").put("items",new org.json.JSONArray().put("Future category"))));
        GameCodec.decode(save.toString(),now,zone);
    }
    @Test public void questAdvancedExpansionPersistsInLegacyCompactAndCompressedSaves()throws Exception {
        long now=Instant.parse("2026-11-01T12:00:00Z").toEpochMilli();ZoneId zone=ZoneId.of("UTC");Game g=new Game(now,zone);assertFalse(g.questAdvancedExpanded);g.questAdvancedExpanded=true;
        for(String text:new String[]{GameCodec.encodeLegacy(g).toString(),GameCodec.encode(g).toString(),SaveCompression.pack(GameCodec.encode(g).toString())})assertTrue(GameCodec.decode(text,now,zone).questAdvancedExpanded);
        g.questAdvancedExpanded=false;assertFalse(GameCodec.decode(GameCodec.encode(g).toString(),now,zone).questAdvancedExpanded);
    }
    @Test public void titlesStartOpenAndSessionSnapshotsRemainReadable()throws Exception {
        BriefDictionary d=dictionary();List<String> fields=new ArrayList<>(Arrays.asList("Theme"));CreativeBrief b=new CreativeBrief(fields);assertTrue(b.showTitles);b.generate(d);assertTrue(b.showTitles);b.showTitles=false;
        CreativeBrief restored=new CreativeBrief(fields);restored.restore(b.snapshot(),d);assertFalse(restored.showTitles);assertTrue(restored.generated);
        CreativeBrief fresh=new CreativeBrief(fields);assertTrue(fresh.showTitles);
        CreativeBrief old=new CreativeBrief(fields);old.restore(new String[]{"1","0","0",b.words.get(0)},d);assertTrue(old.generated);assertTrue(old.showTitles);
    }
    @Test public void eyePreferenceDefaultsOpenAndPersistsBothStatesInEverySaveFormat()throws Exception {
        long now=Instant.parse("2026-11-01T12:00:00Z").toEpochMilli();ZoneId zone=ZoneId.of("UTC");Game g=new Game(now,zone);assertTrue(g.briefShowTitles);
        for(boolean visible:new boolean[]{false,true}){g.briefShowTitles=visible;for(String text:new String[]{GameCodec.encodeLegacy(g).toString(),GameCodec.encode(g).toString(),SaveCompression.pack(GameCodec.encode(g).toString())})assertEquals(visible,GameCodec.decode(text,now,zone).briefShowTitles);}
        org.json.JSONObject older=GameCodec.encodeLegacy(g);older.remove("briefShowTitles");assertTrue(GameCodec.decode(older.toString(),now,zone).briefShowTitles);
    }
}
