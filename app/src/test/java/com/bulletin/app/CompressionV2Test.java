package com.bulletin.app;
import org.junit.Test;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.zip.*;
import org.tukaani.xz.*;
import static org.junit.Assert.*;
public class CompressionV2Test {
    private final String json="{\"version\":17,\"day\":20757,\"quests\":[{\"id\":\"one\",\"name\":\"Focus \ud83c\udfaf\",\"firstDay\":20757}],\"history\":[{\"id\":\"one\",\"at\":1793448000000,\"coins\":-12345}]}";
    private byte[] compress(byte[] input,int codec)throws Exception{
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        if(codec==1){Deflater deflater=new Deflater(9,true);try(DeflaterOutputStream stream=new DeflaterOutputStream(out,deflater)){stream.write(input);}finally{deflater.end();}}
        else{LZMA2Options options=new LZMA2Options(9);options.setDictSize(4096);if(codec==2){try(LZMAOutputStream stream=new LZMAOutputStream(out,options,true)){stream.write(input);}}else{try(FinishableOutputStream stream=options.getOutputStream(new FinishableWrapperOutputStream(out))){stream.write(input);}}}
        return out.toByteArray();
    }
    private String frame(int codec,boolean transform,int outer)throws Exception{
        byte[] bytes=json.getBytes(StandardCharsets.UTF_8),data=transform?JsonPacking.encode(bytes,100000):bytes;
        data=compress(data,codec);if(outer>0)data=compress(data,outer);
        ByteArrayOutputStream out=new ByteArrayOutputStream();out.write(codec|(transform?4:0)|(outer<<4));long size=bytes.length;while(size>=128){out.write((int)size|128);size>>>=7;}out.write((int)size);
        CRC32 crc=new CRC32();crc.update(bytes);for(int i=0;i<4;i++)out.write((int)(crc.getValue()>>>(8*i)));out.write(data);
        return "B2:"+Base64.getEncoder().withoutPadding().encodeToString(out.toByteArray());
    }
    @Test public void everyCodecTransformationAndSecondLayerIsReadable()throws Exception{
        for(int codec=1;codec<=3;codec++)for(boolean transform:new boolean[]{false,true})for(int outer=0;outer<=3;outer++)assertEquals(json,SaveCompression.unpack(frame(codec,transform,outer)));
    }
    @Test public void legacyB1AndRawJsonStillImport()throws Exception{
        String old=SaveCompression.packLegacy(json);assertTrue(old.startsWith("B1:"));assertEquals(json,SaveCompression.unpack(old));assertEquals(json,SaveCompression.unpack(json));
        Game g=new Game(1793448000000L,ZoneId.of("UTC"));Game.Quest q=new Game.Quest();q.name="Focus \ud83c\udfaf";q.firstDay=g.day;g.addQuest(q);
        assertEquals(q.name,SaveImports.decode(SaveCompression.packLegacy(GameCodec.encode(g).toString()),1793448000000L,ZoneId.of("UTC")).quests.get(0).name);
    }
    @Test public void chooserNeverMakesTheStringLongerAndFastSavesRemainLossless()throws Exception{
        Game g=new Game(1793448000000L,ZoneId.of("UTC"));Game.Quest q=new Game.Quest();q.id="focus";q.name="Focus";q.firstDay=g.day;g.addQuest(q);
        for(int i=0;i<500;i++)g.focus.add(new Game.FocusEntry(q.id,q.name,g.day-i,60000));
        for(String value:new String[]{json,GameCodec.encode(g).toString(),"{}","{\"long\":-9223372036854775808}"}){
            String packed=SaveCompression.pack(value),fast=SaveCompression.packFast(value);assertTrue(packed.length()<=SaveCompression.packLegacy(value).length());assertTrue(packed.length()<=fast.length());assertEquals(value,SaveCompression.unpack(packed));assertEquals(value,SaveCompression.unpack(fast));
        }
    }
    @Test public void corruptChecksumTruncationTrailingBytesAndUnknownFlagsAreRejected()throws Exception{
        String valid=frame(2,true,3);byte[] bytes=Base64.getDecoder().decode(valid.substring(3));bytes[3]^=32;
        String corrupt="B2:"+Base64.getEncoder().withoutPadding().encodeToString(bytes);
        for(String value:new String[]{corrupt,valid.substring(0,valid.length()/2),valid+"AAAA","B2:AAAA","B3:unknown"}){try{SaveCompression.unpack(value);fail("Accepted corrupt B2");}catch(IOException expected){}}
    }
    @Test public void oversizedDeclaredLengthIsRejectedBeforeDecompression()throws Exception{
        ByteArrayOutputStream out=new ByteArrayOutputStream();out.write(3);long size=(long)SaveCompression.MAX_BYTES+1;while(size>=128){out.write((int)size|128);size>>>=7;}out.write((int)size);out.write(new byte[4]);
        try{SaveCompression.unpack("B2:"+Base64.getEncoder().withoutPadding().encodeToString(out.toByteArray()));fail("Accepted oversized data");}catch(IOException expected){}
    }
    @Test public void futureGameVersionsRemainProtectedInsideNewTransport()throws Exception{
        org.json.JSONObject future=new org.json.JSONObject(json).put("version",GameCodec.VERSION+1);
        try{SaveImports.decode(SaveCompression.pack(future.toString()),System.currentTimeMillis(),ZoneId.of("UTC"));fail("Accepted future save");}catch(GameCodec.FutureVersion expected){}
    }
}