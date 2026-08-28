package com.tradeagent.market.util;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

/** Time formatting helpers for API payloads. */
public final class TimeUtil {
    /** Prevent construction of this utility class. */
    private TimeUtil() {
    }

    /** Return the current local timestamp formatted without fractional seconds. */
    public static String nowIsoSeconds() {
        return LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }
}
