package com.bulletin.app;

import org.junit.Test;
import static org.junit.Assert.*;

public class TimerFormatTest {
    @Test public void hourLongTimersIncludeHoursAndShortTimersKeepMinutesAndSeconds() {
        assertEquals("00:00", TimerService.format(0)); assertEquals("00:20", TimerService.format(20000));
        assertEquals("59:59", TimerService.format(3599000)); assertEquals("1:00:00", TimerService.format(3600000));
        assertEquals("1:30:00", TimerService.format(5400000)); assertEquals("24:00:00", TimerService.format(86400000));
    }
    @Test public void countdownRoundingAtAnHourBoundaryIsConsistent() {
        assertEquals("1:00:00", TimerService.format(3599001)); assertEquals("00:01", TimerService.format(1));
        assertEquals("00:00", TimerService.format(-1000));
    }
}
