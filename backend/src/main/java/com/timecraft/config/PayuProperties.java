package com.timecraft.config;

import com.timecraft.exception.BusinessException;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * PayU hosted checkout configuration bound from {@code timecraft.payment.payu.*}.
 * Credentials are supplied exclusively via environment variables ({@code PAYU_MERCHANT_KEY},
 * {@code PAYU_MERCHANT_SALT}, {@code PAYU_BASE_URL}) with dev/test defaults confined to non-prod profiles.
 */
@ConfigurationProperties(prefix = "timecraft.payment.payu")
public record PayuProperties(
        String merchantKey,
        String merchantSalt,
        String baseUrl
) {
    public static final String DEFAULT_PROD_ACTION_URL = "https://secure.payu.in/_payment";

    public boolean configured() {
        return merchantKey != null && !merchantKey.isBlank()
                && merchantSalt != null && !merchantSalt.isBlank();
    }

    public String effectiveMerchantKey() {
        if (merchantKey == null || merchantKey.isBlank()) {
            throw BusinessException.serviceUnavailable("PAYU_NOT_CONFIGURED",
                    "PayU credentials (PAYU_MERCHANT_KEY and PAYU_MERCHANT_SALT) are not configured.");
        }
        return merchantKey.trim();
    }

    public String effectiveMerchantSalt() {
        if (merchantSalt == null || merchantSalt.isBlank()) {
            throw BusinessException.serviceUnavailable("PAYU_NOT_CONFIGURED",
                    "PayU credentials (PAYU_MERCHANT_KEY and PAYU_MERCHANT_SALT) are not configured.");
        }
        return merchantSalt.trim();
    }

    public String effectiveBaseUrl() {
        String base = (baseUrl != null && !baseUrl.isBlank()) ? baseUrl.trim() : DEFAULT_PROD_ACTION_URL;
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base;
    }

    public String effectiveActionUrl() {
        String base = effectiveBaseUrl();
        return base.endsWith("/_payment") ? base : (base + "/_payment");
    }
}
