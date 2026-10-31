package com.bulletin.app;
import org.junit.Test;
import static org.junit.Assert.*;
public class DenseTextTest {
    @Test public void roundTripsAllFinalGroupSizesAndEverySingleByte()throws Exception{
        for(int i=0;i<256;i++){byte[] b={(byte)i};assertArrayEquals(b,DenseText.decode(DenseText.encode(b)));}
        java.util.Random random=new java.util.Random(8234);for(int size=0;size<1000;size++){byte[] b=new byte[size];random.nextBytes(b);String text=DenseText.encode(b);assertArrayEquals(b,DenseText.decode(text));assertEquals(size/4*5+(size%4==0?0:size%4+1),text.length());}
    }
    @Test public void rejectsInvalidLengthCharactersAndOverflow()throws Exception{
        for(String bad:new String[]{"0","\"\"","     ","#####"}){try{DenseText.decode(bad);fail("Accepted invalid dense text");}catch(java.io.IOException expected){}}
    }
}