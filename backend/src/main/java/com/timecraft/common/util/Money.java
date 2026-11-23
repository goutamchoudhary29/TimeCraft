package com.timecraft.common.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Monetary helpers. All money is BigDecimal with scale 2 and HALF_UP rounding.
 */
public final class Money {

    public static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    public static final BigDecimal HUNDRED = new BigDecimal("100");

    private Money() {
    }

    public static BigDecimal of(BigDecimal value) {
        return value == null ? ZERO : value.setScale(2, RoundingMode.HALF_UP);
    }

    public static BigDecimal of(String value) {
        return of(new BigDecimal(value));
    }

    public static BigDecimal percentOf(BigDecimal amount, BigDecimal percent) {
        return of(amount.multiply(percent).divide(HUNDRED, 6, RoundingMode.HALF_UP));
    }

    public static BigDecimal discountPercent(BigDecimal regular, BigDecimal selling) {
        if (regular == null || selling == null || regular.signum() <= 0 || selling.compareTo(regular) >= 0) {
            return ZERO;
        }
        return regular.subtract(selling).multiply(HUNDRED).divide(regular, 2, RoundingMode.HALF_UP);
    }

    /** Converts rupees to paise (smallest currency unit) as required by Razorpay. */
    public static long toMinorUnits(BigDecimal amount) {
        return of(amount).movePointRight(2).longValueExact();
    }

    public static BigDecimal fromMinorUnits(long minor) {
        return BigDecimal.valueOf(minor).movePointLeft(2).setScale(2, RoundingMode.HALF_UP);
    }

    public static BigDecimal max(BigDecimal a, BigDecimal b) {
        return a.compareTo(b) >= 0 ? a : b;
    }

    public static BigDecimal min(BigDecimal a, BigDecimal b) {
        return a.compareTo(b) <= 0 ? a : b;
    }
}
