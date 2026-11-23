package com.bulletin.app;

import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

public class BeigePassagesTest {
    private String beige(String text){return "<span color=\"beige\">"+text+"</span>";}
    @Test public void headingsBlankLinesAndInlineFormattingStayInOnePassage(){
        List<BeigePassages.Passage> found=BeigePassages.find(beige("First")+"\n\n## **"+beige("Heading")+"**\n"+beige("Last"));
        assertEquals(1,found.size());assertEquals("First\n\nHeading\nLast",found.get(0).text);assertEquals(0,found.get(0).firstSpan);
    }
    @Test public void uncoloredHeadingSplitsPassagesAndRetainsJumpTarget(){
        List<BeigePassages.Passage> found=BeigePassages.find(beige("One")+"\n# Uncolored heading\n"+beige("Two"));
        assertEquals(2,found.size());assertEquals("Two",found.get(1).text);assertEquals(1,found.get(1).firstSpan);
    }
    @Test public void plainWordsAndPunctuationSplitButSpacesDoNot(){
        assertEquals(1,BeigePassages.find(beige("One")+" "+beige("Two")).size());
        assertEquals("One Two",BeigePassages.find(beige("One")+" "+beige("Two")).get(0).text);
        assertEquals(2,BeigePassages.find(beige("One")+" ordinary "+beige("Two")).size());
        assertEquals(2,BeigePassages.find(beige("One")+"!"+beige("Two")).size());
    }
    @Test public void multilineLegacySpanKeepsFollowingJumpIndexAligned(){
        List<BeigePassages.Passage> found=BeigePassages.find(beige("First\n## Heading\nLast")+"\nPlain\n"+beige("Next"));
        assertEquals(2,found.size());assertEquals("First\nHeading\nLast",found.get(0).text);assertEquals(3,found.get(1).firstSpan);
    }
    @Test public void codeExamplesAreNotBeigeResults(){
        List<BeigePassages.Passage> found=BeigePassages.find("```html\n"+beige("Example")+"\n```\n"+beige("Actual"));
        assertEquals(1,found.size());assertEquals("Actual",found.get(0).text);assertEquals(0,found.get(0).firstSpan);
    }
}
