package com.bulletin.app;
import org.junit.Test;
import java.nio.charset.StandardCharsets;
import org.json.JSONArray;
import org.json.JSONObject;
import static org.junit.Assert.*;
public class JsonPackingTest {
    private void roundTrip(String json)throws Exception{byte[] bytes=json.getBytes(StandardCharsets.UTF_8);assertArrayEquals(bytes,JsonPacking.decode(JsonPacking.encode(bytes,1000000),1000000));}
    @Test public void preservesLexicalWhitespaceEscapesUnicodeAndExtremeIntegers()throws Exception{
        roundTrip("{\n\t\"name\":\"quote \\\" slash \\\\ emoji \ud83c\udfaf\",\"min\":-9223372036854775808,\"max\":9223372036854775807,\"decimal\":1.2300e+10,\"zero\":-0,\"flag\":true,\"nil\":null}");
        roundTrip("{\"at\":9223372036854775807,\"nested\":{\"at\":-9223372036854775808},\"at2\":-1,\"data\":[0,1,-1,9000000000000000]}");
    }
    @Test public void repeatedFieldsAndValuesRoundTripAcrossObjectsAndArrays()throws Exception{
        JSONArray rows=new JSONArray();java.util.Random random=new java.util.Random(7241);
        for(int i=0;i<500;i++)rows.put(new JSONObject().put("id","task-"+(i%5)).put("name","Focus \u00e6\u00f8\u00e5").put("at",1700000000000L+i*1000).put("day",20000-i).put("coins",random.nextLong()).put("value",random.nextDouble()).put("empty",JSONObject.NULL));
        roundTrip(new JSONObject().put("history",rows).toString());
    }
    @Test public void rejectsBadReferencesTruncatedStringsVarintsAndOversizedOutput()throws Exception{
        for(byte[] bad:new byte[][]{{(byte)129,127},{(byte)128,127},{(byte)131,(byte)128},{(byte)255}}){try{JsonPacking.decode(bad,1000);fail("Accepted malformed tokens");}catch(java.io.IOException expected){}}
        byte[] packed=JsonPacking.encode("{\"name\":\"very long string\"}".getBytes(StandardCharsets.UTF_8),1000);
        try{JsonPacking.decode(packed,4);fail("Ignored expansion limit");}catch(java.io.IOException expected){}
    }
}