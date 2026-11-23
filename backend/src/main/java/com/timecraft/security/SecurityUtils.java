package com.timecraft.security;

import com.timecraft.exception.BusinessException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static Optional<TimeCraftPrincipal> currentPrincipal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof TimeCraftPrincipal principal) {
            return Optional.of(principal);
        }
        return Optional.empty();
    }

    public static TimeCraftPrincipal requirePrincipal() {
        return currentPrincipal()
                .orElseThrow(() -> BusinessException.unauthorized("UNAUTHORIZED", "Authentication required"));
    }

    public static Long requireUserId() {
        return requirePrincipal().getId();
    }
}
