package com.bulletin.app;
import org.junit.Test;
import org.json.JSONObject;
import java.time.*;
import static org.junit.Assert.*;
public class SoundVolumeTest {
    private final ZoneId zone=ZoneId.of("UTC");
    private final long now=Instant.parse("2026-11-11T12:00:00Z").toEpochMilli();
    @Test public void fiftyFiveKeepsEveryExistingMixLevel(){for(float level:new float[]{.2f,.25f,.3f,.45f,.55f,.75f})assertEquals(level,SoundVolume.scale(level,55),.00001f);}
    @Test public void zeroMutesAndHigherSettingsIncreaseAndClamp(){for(float level:new float[]{.2f,.45f,.55f,.75f}){assertEquals(0,SoundVolume.scale(level,0),0);assertTrue(SoundVolume.scale(level,100)>level);}assertEquals(1,SoundVolume.scale(.75f,100),0);assertEquals(0,SoundVolume.scale(.75f,-5),0);assertEquals(100,SoundVolume.percent(300));}
    @Test public void oldSavesDefaultToCurrentFiftyFiveVolume() throws Exception {Game g=new Game(now,zone);JSONObject old=GameCodec.encodeLegacy(g);old.remove("soundVolume");assertEquals(55,GameCodec.decode(old.toString(),now,zone).soundVolume);}
    @Test public void muteAndCustomVolumeSurviveCompressedSaveAndRelaunch() throws Exception {for(int percent:new int[]{0,28,55,100}){Game g=new Game(now,zone);g.soundVolume=percent;Game loaded=GameCodec.decode(SaveCompression.pack(GameCodec.encode(g).toString()),now,zone);assertEquals(percent,loaded.soundVolume);}}
    @Test public void invalidSavedPercentagesStayInRange() throws Exception {Game g=new Game(now,zone);JSONObject saved=GameCodec.encodeLegacy(g);saved.put("soundVolume",-1);assertEquals(0,GameCodec.decode(saved.toString(),now,zone).soundVolume);saved.put("soundVolume",101);assertEquals(100,GameCodec.decode(saved.toString(),now,zone).soundVolume);}
}
