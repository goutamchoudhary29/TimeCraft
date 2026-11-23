package com.timecraft.common.util;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Generates human-friendly, non-sequential business reference numbers.
 */
public final class ReferenceNumberGenerator {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyMMdd");

    private ReferenceNumberGenerator() {
    }

    public static String next(String prefix) {
        return prefix + "-" + LocalDate.now(ZoneOffset.UTC).format(DATE) + "-" + TokenUtils.randomAlphanumeric(6);
    }
}
