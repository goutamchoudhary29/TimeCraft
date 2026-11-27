package com.timecraft.config;

import com.timecraft.common.util.Money;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

/**
 * Typed checkout rules bound from {@code timecraft.checkout.*}.
 * Monetary thresholds always use {@link BigDecimal}.
 */
@ConfigurationProperties(prefix = "timecraft.checkout")
public record CheckoutProperties(BigDecimal maxCodAmount) {

    public static final BigDecimal DEFAULT_MAX_COD_AMOUNT = new BigDecimal("500000.00");

    public BigDecimal effectiveMaxCodAmount() {
        if (maxCodAmount == null || maxCodAmount.signum() <= 0) {
            return DEFAULT_MAX_COD_AMOUNT;
        }
        return Money.of(maxCodAmount);
    }
}
