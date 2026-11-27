package com.timecraft.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

/**
 * Strongly typed application configuration. All secrets are bound from environment variables.
 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        String frontendBaseUrl,
        Jwt jwt,
        Cors cors,
        Cookies cookies,
        Tokens tokens,
        Admin admin,
        Cloudinary cloudinary,
        Razorpay razorpay,
        Mail mail,
        RateLimit rateLimit,
        Google google
) {

    public String effectiveBaseUrl() {
        String base = notBlank(frontendBaseUrl) ? frontendBaseUrl.trim() : "http://localhost:8080";
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base;
    }

    public record Jwt(String secret, String issuer, Duration accessTokenTtl, Long expirationMs, Duration refreshTokenTtl) {
        public Duration effectiveAccessTokenTtl() {
            if (expirationMs != null && expirationMs > 0) {
                return Duration.ofMillis(expirationMs);
            }
            return accessTokenTtl != null ? accessTokenTtl : Duration.ofMinutes(15);
        }
    }

    public record Cors(List<String> allowedOrigins) {
    }

    public record Cookies(boolean secure, String sameSite) {
    }

    public record Tokens(Duration emailVerificationTtl, Duration passwordResetTtl) {
    }

    public record Admin(String email, String password, String firstName, String lastName) {
    }

    public record Cloudinary(String cloudName, String apiKey, String apiSecret, String folder, long maxFileSizeBytes) {
        public boolean configured() {
            return notBlank(cloudName) && notBlank(apiKey) && notBlank(apiSecret);
        }
    }

    public record Razorpay(String keyId, String keySecret, String webhookSecret, String apiBaseUrl, boolean testSandboxEnabled) {
        public boolean configured() {
            return notBlank(keyId) && notBlank(keySecret);
        }
    }

    public record Mail(boolean enabled, String from, boolean logLinks) {
    }

    public record RateLimit(boolean enabled, String store, int authCapacity, Duration authWindow) {
    }

    public record Google(String clientId, boolean devQuickLoginEnabled) {
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
