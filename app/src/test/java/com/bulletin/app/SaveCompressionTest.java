package com.bulletin.app;

import org.junit.Test;
import java.time.Instant;
import java.time.ZoneId;
import org.json.JSONObject;
import static org.junit.Assert.*;

public class SaveCompressionTest {
    private final long now=Instant.parse("2026-10-31T12:00:00Z").toEpochMilli();
    private final ZoneId zone=ZoneId.of("UTC");
    @Test public void compressedSaveRoundTripsUnicodeAndAllProgress() throws Exception {
        Game g=new Game(now,zone);Game.Quest q=new Game.Quest();q.name="Focus 🎯 æøå";q.firstDay=g.day;g.addQuest(q);g.coins=12345;g.balanceRangeStart=100;g.balanceRangeEnd=200;
        for(int i=0;i<100;i++)g.focus.add(new Game.FocusEntry(q.id,q.name,g.day-i,60000));
        String json=GameCodec.encodeLegacy(g).toString(),packed=SaveCompression.pack(json);
        assertTrue(SaveCompression.isPacked(packed));assertTrue(packed.length()<json.length()/2);assertEquals(json,SaveCompression.unpack(packed));
        Game loaded=GameCodec.decode(packed,now,zone);assertEquals(g.coins,loaded.coins);assertEquals(q.name,loaded.quests.get(0).name);assertEquals(100,loaded.focus.size());assertEquals(100,loaded.balanceRangeStart);assertEquals(200,loaded.balanceRangeEnd);
    }
    @Test public void legacyJsonAndVersion16StillLoadAndImport() throws Exception {
        JSONObject old=GameCodec.encodeLegacy(new Game(now,zone));old.put("version",16);old.remove("balanceRangeStart");old.remove("balanceRangeEnd");old.put("coins",4567);
        assertEquals(old.toString(),SaveCompression.unpack(old.toString()));
        Game loaded=SaveImports.decode(old.toString(),now,zone);assertEquals(4567,loaded.coins);assertEquals(0,loaded.balanceRangeStart);
        assertEquals(4567,SaveImports.decode(SaveCompression.pack(old.toString()),now,zone).coins);
    }
    @Test public void wrappedCompressedStringCanBePasted() throws Exception {
        String packed=SaveCompression.pack("{\"day\":1,\"quests\":[]}");
        assertEquals(SaveCompression.unpack(packed),SaveCompression.unpack("  "+packed.substring(0,8)+"\n"+packed.substring(8)+"  "));
    }
    @Test public void corruptTruncatedAndUnknownFormatsAreRejected() throws Exception {
        String good=SaveCompression.pack(GameCodec.encodeLegacy(new Game(now,zone)).toString());
        for(String bad:new String[]{"B1:%%%",good.substring(0,good.length()/2),"B2:future",good+"AAAA"}) {
            try{SaveCompression.unpack(bad);fail("Accepted bad transport");}catch(java.io.IOException expected){}
        }
    }
    @Test public void invalidEmptyAndNewerImportsAreRejectedWithoutMutatingOriginal() throws Exception {
        Game current=new Game(now,zone);current.coins=8765;JSONObject newer=GameCodec.encodeLegacy(current).put("version",GameCodec.VERSION+1);
        for(String bad:new String[]{"","{}","[]","{\"day\":\"invalid\",\"quests\":[]}","{\"day\":1,\"quests\":{}}",newer.toString()}) {
            try{SaveImports.decode(bad,now,zone);fail("Accepted invalid import");}catch(Exception expected){}
        }
        assertEquals(8765,current.coins);
    }
}