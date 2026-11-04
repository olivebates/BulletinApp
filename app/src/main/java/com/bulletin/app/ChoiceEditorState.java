package com.bulletin.app;

/** Temporary editor position, separate from the saved choices and randomizer session. */
final class ChoiceEditorState {
    int scrollY, activeIndex = -1, selectionStart, selectionEnd;
    boolean keyboardVisible;
    int[] snapshot() { return new int[]{scrollY, activeIndex, selectionStart, selectionEnd, keyboardVisible ? 1 : 0}; }
    void restore(int[] values) {
        if (values == null || values.length != 5) return;
        scrollY = Math.max(0, values[0]); activeIndex = Math.max(-1, values[1]);
        selectionStart = Math.max(0, values[2]); selectionEnd = Math.max(0, values[3]);
        keyboardVisible = values[4] == 1;
    }
}