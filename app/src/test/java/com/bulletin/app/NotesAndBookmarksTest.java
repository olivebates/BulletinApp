package com.bulletin.app;

import org.junit.Test;
import org.json.JSONObject;
import java.time.*;
import static org.junit.Assert.*;

public class NotesAndBookmarksTest {
    private final long now=Instant.parse("2026-11-23T12:00:00Z").toEpochMilli();
    private final ZoneId zone=ZoneId.of("UTC");
    @Test public void videoLinksAcceptCommonFormatsAndRejectUntrustedHosts(){
        String id="dQw4w9WgXcQ";
        for(String link:new String[]{"https://youtu.be/"+id+"?t=30","https://www.youtube.com/watch?v="+id+"&list=abc","https://m.youtube.com/shorts/"+id,"https://youtube.com/live/"+id})assertEquals(id,YoutubeLibraryActivity.videoId(link));
        for(String link:new String[]{"https://youtube.com.evil.test/watch?v="+id,"https://evil.test/"+id,"javascript:alert(1)","https://youtube.com/watch?v=no"})assertEquals("",YoutubeLibraryActivity.videoId(link));
    }
    @Test public void bookmarkNamesOrderAndFractionalPlaybackPositionSurviveSaveExport()throws Exception{
        Game g=new Game(now,zone);g.youtubeBookmarks.put(new JSONObject().put("id","dQw4w9WgXcQ").put("name","Video one").put("seconds",123.75));g.youtubeBookmarks.put(new JSONObject().put("id","abcdefghijk").put("name","Video two").put("seconds",0));
        Game loaded=SaveImports.decode(SaveCompression.pack(GameCodec.encode(g).toString()),now,zone);assertEquals(g.youtubeBookmarks.toString(),loaded.youtubeBookmarks.toString());
    }
    @Test public void notionUsesFullReplacementAndMapsBeigeWithoutChangingLocalMarkup()throws Exception{
        class Recording extends NotionClient {JSONObject payload;Recording(){super("test");}@Override JSONObject request(String method,String path,JSONObject body)throws Exception{assertEquals("PATCH",method);assertEquals("pages/note/markdown",path);payload=body;return new JSONObject();}}
        Recording api=new Recording();String local="<span color=\"beige\">Remember</span>";api.replace("note",local);assertEquals("replace_content",api.payload.getString("type"));assertEquals("<span color=\"brown\">Remember</span>",api.payload.getJSONObject("replace_content").getString("new_str"));assertTrue(local.contains("beige"));
    }
    @Test public void oneTimeRewardsUseFifteenPercentAndPendingCollectionIsIdempotent()throws Exception{
        Game g=new Game(now,zone);Game.Quest q=new Game.Quest();q.name="Single";q.firstDay=g.day;q.bountySince=now;g.addQuest(q);assertEquals(15,g.bountyFor(q,now));g.completeInstant(q,now);assertEquals(15,g.coins);assertEquals(15,g.pendingCoins);
        Game loaded=GameCodec.decode(GameCodec.encode(g).toString(),now,zone);assertEquals(15,loaded.takePendingCoins());assertEquals(0,loaded.takePendingCoins());assertEquals(15,loaded.coins);loaded=GameCodec.decode(GameCodec.encode(loaded).toString(),now,zone);assertEquals(0,loaded.takePendingCoins());
    }
}
