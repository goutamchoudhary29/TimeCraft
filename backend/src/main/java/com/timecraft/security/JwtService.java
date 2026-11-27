package com.timecraft.security;

import com.timecraft.authentication.entity.RoleName;
import com.timecraft.authentication.entity.User;
import com.timecraft.config.AppProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.Date;

/**
 * Issues and verifies short-lived customer JWT access tokens.
 * Admin authentication NEVER uses JWTs; it uses a separate HTTP session chain.
 */
@Service
@RequiredArgsConstructor
public class JwtService {

    private final AppProperties properties;
    private final Environment environment;
    private SecretKey signingKey;

    @PostConstruct
    void init() {
        String secret = properties.jwt().secret();
        boolean prod = Arrays.asList(environment.getActiveProfiles()).contains("prod");
        if (secret == null || secret.isBlank() || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("JWT_SECRET must be set and at least 32 bytes long");
        }
        if (prod && secret.startsWith("dev-only")) {
            throw new IllegalStateException("Refusing to start in prod profile with the dev fallback JWT_SECRET");
        }
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateCustomerAccessToken(User user) {
        Instant now = Instant.now();
        Instant expiry = now.plus(properties.jwt().effectiveAccessTokenTtl());
        return Jwts.builder()
                .issuer(properties.jwt().issuer())
                .subject(String.valueOf(user.getId()))
                .claim("email", user.getEmail())
                .claim("role", RoleName.CUSTOMER.name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(signingKey)
                .compact();
    }

    public Claims parseAndValidateCustomerToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(signingKey)
                .requireIssuer(properties.jwt().issuer())
                .build()
                .parseSignedClaims(token)
                .getPayload();
        String role = claims.get("role", String.class);
        if (!RoleName.CUSTOMER.name().equals(role)) {
            throw new IllegalArgumentException("Token is not a customer access token");
        }
        return claims;
    }

    public long accessTokenTtlSeconds() {
        return properties.jwt().effectiveAccessTokenTtl().toSeconds();
    }
}
