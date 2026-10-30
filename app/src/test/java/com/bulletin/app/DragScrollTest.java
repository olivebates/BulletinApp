package com.bulletin.app;

import org.junit.Test;
import static org.junit.Assert.*;

public class DragScrollTest {
    @Test public void scrollingAcceleratesBeyondEitherEdgeAndStopsInTheMiddle() {
        assertEquals(0, DragScroll.speed(300, 600), 0);
        assertEquals(300, DragScroll.speed(600, 600), 0);
        assertEquals(-300, DragScroll.speed(0, 600), 0);
        assertTrue(DragScroll.speed(650, 600) > DragScroll.speed(600, 600));
        assertTrue(DragScroll.speed(-50, 600) < DragScroll.speed(0, 600));
        assertEquals(1000, DragScroll.speed(2000, 600), 0);
        assertEquals(-1000, DragScroll.speed(-2000, 600), 0);
        assertEquals(0, DragScroll.speed(0, 0), 0);
    }
}
