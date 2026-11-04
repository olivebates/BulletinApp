package com.bulletin.app;
import org.junit.Test;
import static org.junit.Assert.*;
public class ChoiceEditorStateTest {
    @Test public void remembersScrollFocusCursorAndKeyboardIndependentlyPerList() {
        ChoiceEditorState first=new ChoiceEditorState();first.scrollY=801;first.activeIndex=12;first.selectionStart=2;first.selectionEnd=5;first.keyboardVisible=true;
        ChoiceEditorState restored=new ChoiceEditorState();restored.restore(first.snapshot());assertArrayEquals(first.snapshot(),restored.snapshot());
        ChoiceEditorState second=new ChoiceEditorState();assertEquals(0,second.scrollY);assertEquals(-1,second.activeIndex);assertFalse(second.keyboardVisible);
    }
    @Test public void malformedStateKeepsDefaultsAndInvalidOffsetsAreBounded() {
        ChoiceEditorState s=new ChoiceEditorState();s.restore(null);s.restore(new int[]{3});assertEquals(-1,s.activeIndex);
        s.restore(new int[]{-10,-4,-5,-8,3});assertArrayEquals(new int[]{0,-1,0,0,0},s.snapshot());
    }
}