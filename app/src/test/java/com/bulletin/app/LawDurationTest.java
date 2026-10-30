package com.bulletin.app;

import org.junit.Test;
import static org.junit.Assert.*;

public class LawDurationTest {
    @Test public void separateHoursAndMinutesCombineAndRetainOlderShortAllowances() {
        assertEquals(5400000, LawDuration.parse("1", "30")); assertEquals(300000, LawDuration.parse("0", "5"));
        assertEquals(1800000, LawDuration.parse("", "30")); assertEquals(3600000, LawDuration.parse("1", ""));
        for (long millis : new long[]{1000, 20000, 1500000, 5400000, 86400000})
            assertEquals(millis, LawDuration.parse(String.valueOf(millis / 3600000), LawDuration.format(millis % 3600000, false)));
        assertThrows(IllegalArgumentException.class, () -> LawDuration.parse("1.5", "0"));
        assertThrows(IllegalArgumentException.class, () -> LawDuration.parse("0", "60"));
        assertThrows(IllegalArgumentException.class, () -> LawDuration.parse("24", "1"));
    }
    @Test public void selectedUnitAndFractionalValuesProduceTheExpectedDuration() {
        assertEquals(300000, LawDuration.parse("5", false)); assertEquals(7200000, LawDuration.parse("2", true));
        assertEquals(5400000, LawDuration.parse("1.5", true)); assertEquals(30000, LawDuration.parse("0.5", false));
        assertEquals(5400000, LawDuration.parse("1,5", true));
    }
    @Test public void existingSecondBasedAndHourBasedDurationsRoundTripInEitherUnit() {
        for (long millis : new long[]{1000, 20000, 1500000, 5400000, 86400000}) for (boolean hours : new boolean[]{false, true})
            assertEquals(millis, LawDuration.parse(LawDuration.format(millis, hours), hours));
    }
    @Test public void invalidOutOfRangeOrUnrepresentableValuesCannotBeSaved() {
        for (String text : new String[]{"", "NaN", "-1", "0", "1441", "1e999", "1,2.3"})
            assertThrows(RuntimeException.class, () -> LawDuration.parse(text, false));
        assertThrows(IllegalArgumentException.class, () -> LawDuration.parse("25", true));
    }
}
