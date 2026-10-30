package com.bulletin.app;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Decimal minutes/hours retain older second-based allowances when reopened and saved. */
final class LawDuration {
    static String format(long millis, boolean hours) {
        return BigDecimal.valueOf(millis).divide(BigDecimal.valueOf(hours ? 3600000 : 60000), hours ? 9 : 6, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }
    static long parse(String text, boolean hours) {
        long millis = new BigDecimal(text.trim().replace(',', '.')).multiply(BigDecimal.valueOf(hours ? 3600000 : 60000)).setScale(0, RoundingMode.HALF_UP).longValueExact();
        if (millis < 1000 || millis > 86400000) throw new IllegalArgumentException("Duration outside allowance range");
        return millis;
    }
    static long parse(String hoursText, String minutesText) {
        BigDecimal hours = new BigDecimal(hoursText.trim().isEmpty() ? "0" : hoursText.trim().replace(',', '.'));
        BigDecimal minutes = new BigDecimal(minutesText.trim().isEmpty() ? "0" : minutesText.trim().replace(',', '.'));
        if (hours.signum() < 0 || hours.stripTrailingZeros().scale() > 0 || hours.compareTo(BigDecimal.valueOf(24)) > 0
                || minutes.signum() < 0 || minutes.compareTo(BigDecimal.valueOf(60)) >= 0) throw new IllegalArgumentException("Invalid hours or minutes");
        return parse(hours.multiply(BigDecimal.valueOf(60)).add(minutes).toPlainString(), false);
    }
}
