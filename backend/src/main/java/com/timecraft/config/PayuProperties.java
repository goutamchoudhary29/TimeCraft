package com.timecraft.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * PayU hosted checkout configuration bound from {@code timecraft.payment.payu.*}.
 * Defaults to PayU India's test sandbox environment when environment variables are unset.
 */
@ConfigurationProperties(prefix = "timecraft.payment.payu")
public record PayuProperties(
        String merchantKey,
        String merchantSalt,
        String baseUrl
) {
    public static final String DEFAULT_TEST_KEY = "otqqIX";
    public static final String DEFAULT_TEST_SALT = "Vx6TKHBnxErLEvbrTmLZGMb4eVEAtyQM";
    public static final String DEFAULT_TEST_BASE_URL = "https://test.payu.in";

    public String effectiveMerchantKey() {
        return (merchantKey != null && !merchantKey.isBlank()) ? merchantKey.trim() : DEFAULT_TEST_KEY;
    }

    public String effectiveMerchantSalt() {
        return (merchantSalt != null && !merchantSalt.isBlank()) ? merchantSalt.trim() : DEFAULT_TEST_SALT;
    }

    public String effectiveBaseUrl() {
        String base = (baseUrl != null && !baseUrl.isBlank()) ? baseUrl.trim() : DEFAULT_TEST_BASE_URL;
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
