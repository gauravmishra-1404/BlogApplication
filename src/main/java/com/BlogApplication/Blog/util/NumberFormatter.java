package com.BlogApplication.Blog.util;

import java.text.NumberFormat;
import java.util.Locale;

/**
 * Compact display for large counts (view counts, likes, etc.) - the same "1.2K" / "3.4M" /
 * "5.6B" style YouTube and Twitter use. Backed by the JDK's built-in CLDR compact number
 * formatter rather than hand-written K/M/B/T thresholds, so it keeps scaling correctly (into
 * trillions and beyond) with no code changes needed as numbers grow.
 */
public class NumberFormatter {

    private NumberFormatter() {
    }

    public static String compact(long value) {
        NumberFormat format = NumberFormat.getCompactNumberInstance(Locale.US, NumberFormat.Style.SHORT);
        format.setMaximumFractionDigits(1);
        return format.format(value);
    }
}
