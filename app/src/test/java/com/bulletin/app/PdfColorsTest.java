package com.bulletin.app;
import org.junit.Test;
import static org.junit.Assert.*;
public class PdfColorsTest {
    private float[] apply(float[] matrix,float... color){float[] result=new float[3];for(int r=0;r<3;r++){result[r]=matrix[r*5+4];for(int c=0;c<3;c++)result[r]+=matrix[r*5+c]*color[c];}return result;}
    @Test public void independentInversionPreservesOriginalColorWhenDisabled(){assertArrayEquals(new float[]{10,80,230},apply(PdfColors.matrix(false,false),10,80,230),.001f);assertArrayEquals(new float[]{245,175,25},apply(PdfColors.matrix(true,false),10,80,230),.001f);}
    @Test public void sepiaUsesOneWarmMonochromeScale(){float[] result=apply(PdfColors.matrix(false,true),25,150,240);assertEquals(result[0]*.89f,result[1],.001f);assertEquals(result[0]*.70f,result[2],.001f);assertArrayEquals(new float[3],apply(PdfColors.matrix(false,true),0,0,0),.001f);}
    @Test public void combinedFilterAppliesSepiaAfterInversion(){float[] inverted=apply(PdfColors.matrix(true,false),25,150,240);assertArrayEquals(apply(PdfColors.matrix(false,true),inverted),apply(PdfColors.matrix(true,true),25,150,240),.001f);assertEquals(1,PdfColors.matrix(true,true)[18],0);}
}
