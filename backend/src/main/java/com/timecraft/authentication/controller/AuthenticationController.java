package com.timecraft.authentication.controller;

import com.timecraft.authentication.dto.AuthDtos.AuthTokenResponse;
import com.timecraft.authentication.dto.AuthDtos.AuthUserDto;
import com.timecraft.authentication.dto.AuthDtos.ChangePasswordRequest;
import com.timecraft.authentication.dto.AuthDtos.ForgotPasswordRequest;
import com.timecraft.authentication.dto.AuthDtos.GoogleLoginRequest;
import com.timecraft.authentication.dto.AuthDtos.LoginRequest;
import com.timecraft.authentication.dto.AuthDtos.RefreshRequest;
import com.timecraft.authentication.dto.AuthDtos.RegisterRequest;
import com.timecraft.authentication.dto.AuthDtos.ResendVerificationRequest;
import com.timecraft.authentication.dto.AuthDtos.ResetPasswordRequest;
import com.timecraft.authentication.dto.AuthDtos.VerifyEmailRequest;
import com.timecraft.authentication.service.AuthenticationService;
import com.timecraft.cart.service.CartService;
import com.timecraft.common.dto.MessageResponse;
import com.timecraft.config.AppProperties;
import com.timecraft.security.SecurityUtils;
import com.timecraft.security.TimeCraftPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.Arrays;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Customer Authentication")
public class AuthenticationController {

    private static final String REFRESH_COOKIE = "tc_refresh_token";

    private final AuthenticationService authenticationService;
    private final CartService cartService;
    private final AppProperties properties;

    @PostMapping("/register")
    @Operation(summary = "Register a new CUSTOMER account")
    public ResponseEntity<AuthTokenResponse> register(
            @Valid @RequestBody RegisterRequest request,
            @RequestHeader(value = "X-Guest-Token", required = false) String guestToken,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse
    ) {
        AuthTokenResponse response = authenticationService.register(request, httpRequest);
        if (guestToken != null && !guestToken.isBlank()) {
            cartService.mergeGuestCartIntoUserCart(guestToken, response.user().id());
        }
        writeRefreshCookie(httpResponse, response.refreshToken(), properties.jwt().refreshTokenTtl());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    @Operation(summary = "Authenticate a CUSTOMER and merge any guest cart")
    public ResponseEntity<AuthTokenResponse> login(
            @Valid @RequestBody LoginRequest request,
            @RequestHeader(value = "X-Guest-Token", required = false) String guestToken,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse
    ) {
        AuthTokenResponse response = authenticationService.login(request, httpRequest);
        if (guestToken != null && !guestToken.isBlank()) {
            cartService.mergeGuestCartIntoUserCart(guestToken, response.user().id());
        }
        writeRefreshCookie(httpResponse, response.refreshToken(), properties.jwt().refreshTokenTtl());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/google")
    @Operation(summary = "Continue with Google (verifies Google ID token or dev quick-login and merges guest cart)")
    public ResponseEntity<AuthTokenResponse> loginWithGoogle(
            @Valid @RequestBody GoogleLoginRequest request,
            @RequestHeader(value = "X-Guest-Token", required = false) String guestToken,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse
    ) {
        AuthTokenResponse response = authenticationService.loginWithGoogle(request, httpRequest);
        if (guestToken != null && !guestToken.isBlank()) {
            cartService.mergeGuestCartIntoUserCart(guestToken, response.user().id());
        }
        writeRefreshCookie(httpResponse, response.refreshToken(), properties.jwt().refreshTokenTtl());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/google-config")
    @Operation(summary = "Return public Google OAuth Client ID and dev quick-login availability")
    public ResponseEntity<java.util.Map<String, Object>> googleConfig() {
        String clientId = authenticationService.resolveGoogleClientId();
        boolean devQuick = properties.google() == null || properties.google().devQuickLoginEnabled();
        return ResponseEntity.ok(java.util.Map.of("clientId", clientId, "devQuickLoginEnabled", devQuick));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Rotate refresh token and issue a new JWT access token")
    public ResponseEntity<AuthTokenResponse> refresh(
            @RequestBody(required = false) RefreshRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse
    ) {
        String rawRefresh = request != null && request.refreshToken() != null && !request.refreshToken().isBlank()
                ? request.refreshToken()
                : extractCookie(httpRequest, REFRESH_COOKIE);
        AuthTokenResponse response = authenticationService.refresh(rawRefresh, httpRequest);
        writeRefreshCookie(httpResponse, response.refreshToken(), properties.jwt().refreshTokenTtl());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    @Operation(summary = "Revoke refresh token and clear refresh cookie")
    public ResponseEntity<MessageResponse> logout(
            @RequestBody(required = false) RefreshRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse
    ) {
        String rawRefresh = request != null && request.refreshToken() != null && !request.refreshToken().isBlank()
                ? request.refreshToken()
                : extractCookie(httpRequest, REFRESH_COOKIE);
        Long userId = SecurityUtils.currentPrincipal().map(TimeCraftPrincipal::getId).orElse(null);
        authenticationService.logout(rawRefresh, userId);
        writeRefreshCookie(httpResponse, "", Duration.ZERO);
        return ResponseEntity.ok(MessageResponse.of("Signed out successfully"));
    }

    @PostMapping("/verify-email")
    @Operation(summary = "Verify customer email address with one-time token")
    public ResponseEntity<MessageResponse> verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        authenticationService.verifyEmail(request.token());
        return ResponseEntity.ok(MessageResponse.of("Your email address has been verified"));
    }

    @GetMapping("/verify-email")
    @Operation(summary = "Verify customer email via GET link")
    public ResponseEntity<MessageResponse> verifyEmailGet(@RequestParam("token") String token) {
        authenticationService.verifyEmail(token);
        return ResponseEntity.ok(MessageResponse.of("Your email address has been verified"));
    }

    @PostMapping("/resend-verification")
    @Operation(summary = "Resend email verification link")
    public ResponseEntity<MessageResponse> resendVerification(@Valid @RequestBody ResendVerificationRequest request) {
        authenticationService.resendVerification(request.email());
        return ResponseEntity.ok(MessageResponse.of("If the account exists and is unverified, a verification link has been sent"));
    }

    @PostMapping("/forgot-password")
    @Operation(summary = "Request a password reset email")
    public ResponseEntity<MessageResponse> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authenticationService.requestPasswordReset(request);
        return ResponseEntity.ok(MessageResponse.of("If an account exists for that email, a password reset link has been sent"));
    }

    @PostMapping("/reset-password")
    @Operation(summary = "Reset password using a one-time reset token")
    public ResponseEntity<MessageResponse> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authenticationService.resetPassword(request);
        return ResponseEntity.ok(MessageResponse.of("Your password has been updated. Please sign in."));
    }

    @PostMapping("/change-password")
    @Operation(summary = "Change password for the currently signed-in customer")
    public ResponseEntity<MessageResponse> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        authenticationService.changePassword(SecurityUtils.requireUserId(), request);
        return ResponseEntity.ok(MessageResponse.of("Password updated successfully"));
    }

    @GetMapping("/me")
    @Operation(summary = "Get the currently authenticated customer")
    public ResponseEntity<AuthUserDto> me() {
        return ResponseEntity.ok(authenticationService.getCurrentUser(SecurityUtils.requireUserId()));
    }

    private void writeRefreshCookie(HttpServletResponse response, String value, Duration maxAge) {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE, value)
                .httpOnly(true)
                .secure(properties.cookies().secure())
                .sameSite(properties.cookies().sameSite())
                .path("/api/v1/auth")
                .maxAge(maxAge)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private static String extractCookie(HttpServletRequest request, String name) {
        if (request.getCookies() == null) {
            return null;
        }
        return Arrays.stream(request.getCookies())
                .filter(c -> name.equals(c.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
    }
}
