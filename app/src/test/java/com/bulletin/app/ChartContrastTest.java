package com.bulletin.app;
import org.junit.Test;
import static org.junit.Assert.*;
public class ChartContrastTest{
 @Test public void firstTwelveSlicesUseDistinctContrastingColorsOnBothBoards(){java.util.Set<Integer> colors=new java.util.HashSet<>();int previous=0;for(int i=0;i<12;i++){Statistics.Series s=new Statistics.Series("q"+i,"Task",0);int color=ActivityChart.color(s,i,false);assertTrue(colors.add(color));assertEquals(color,ActivityChart.color(s,i,true));if(i>0){int r=((color>>16)&255)-((previous>>16)&255),g=((color>>8)&255)-((previous>>8)&255),b=(color&255)-(previous&255);assertTrue(r*r+g*g+b*b>4000);}previous=color;}}
 @Test public void allTimeRangeIncludesOldDailyAndWeeklyTotals(){assertEquals(Long.MIN_VALUE,Statistics.rangeStart(20757,4));}
}