package com.timecraft.authentication.service;

import com.timecraft.audit.service.AuditLogService;
import com.timecraft.authentication.dto.AuthDtos.AuthTokenResponse;
import com.timecraft.authentication.dto.AuthDtos.AuthUserDto;
import com.timecraft.authentication.dto.AuthDtos.ChangePasswordRequest;
import com.timecraft.authentication.dto.AuthDtos.ForgotPasswordRequest;
import com.timecraft.authentication.dto.AuthDtos.GoogleLoginRequest;
import com.timecraft.authentication.dto.AuthDtos.LoginRequest;
import com.timecraft.authentication.dto.AuthDtos.RegisterOtpResponse;
import com.timecraft.authentication.dto.AuthDtos.RegisterRequest;
import com.timecraft.authentication.dto.AuthDtos.ResetPasswordRequest;
import com.timecraft.authentication.dto.AuthDtos.VerifyOtpRequest;
import com.timecraft.authentication.entity.AccountStatus;
import com.timecraft.authentication.entity.EmailVerificationToken;
import com.timecraft.authentication.entity.PasswordResetToken;
import com.timecraft.authentication.entity.RefreshToken;
import com.timecraft.authentication.entity.Role;
import com.timecraft.authentication.entity.RoleName;
import com.timecraft.authentication.entity.User;
import com.timecraft.authentication.repository.EmailVerificationTokenRepository;
import com.timecraft.authentication.repository.PasswordResetTokenRepository;
import com.timecraft.authentication.repository.RefreshTokenRepository;
import com.timecraft.authentication.repository.RoleRepository;
import com.timecraft.authentication.repository.UserRepository;
import com.timecraft.common.util.RequestUtils;
import com.timecraft.common.util.TokenUtils;
import com.timecraft.config.AppProperties;
import com.timecraft.customer.entity.CustomerProfile;
import com.timecraft.customer.repository.CustomerProfileRepository;
import com.timecraft.exception.BusinessException;
import com.timecraft.exception.ResourceNotFoundException;
import com.timecraft.notification.service.NotificationService;
import com.timecraft.security.JwtService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Customer authentication, refresh-token rotation with reuse detection,
 * 6-digit email OTP verification, and OTP-based password reset.
 */
@Service
@RequiredArgsConstructor
public class AuthenticationService {

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final int MAX_OTP_ATTEMPTS = 5;
    private static final Duration LOCK_DURATION = Duration.ofMinutes(15);
    private static final Duration OTP_TTL = Duration.ofMinutes(10);
    private static final Duration OTP_RESEND_COOLDOWN = Duration.ofSeconds(60);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final CustomerProfileRepository customerProfileRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final EmailVerificationTokenRepository emailVerificationTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final com.timecraft.admin.repository.StoreSettingRepository storeSettingRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AppProperties properties;
    private final NotificationService notificationService;
    private final AuditLogService auditLogService;

    @Transactional
    public RegisterOtpResponse register(RegisterRequest request, HttpServletRequest httpRequest) {
        String email = request.email().trim().toLowerCase();
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw BusinessException.conflict("EMAIL_ALREADY_EXISTS", "An account with this email already exists");
        }

        Role customerRole = roleRepository.findByName(RoleName.CUSTOMER)
                .orElseThrow(() -> new IllegalStateException("CUSTOMER role not initialized"));

        User user = User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(request.password()))
                .firstName(request.firstName().trim())
                .lastName(request.lastName().trim())
                .phone(request.phone() != null && !request.phone().isBlank() ? request.phone().trim() : null)
                .emailVerified(false)
                .accountStatus(AccountStatus.ACTIVE)
                .roles(Set.of(customerRole))
                .build();
        user = userRepository.save(user);

        customerProfileRepository.save(CustomerProfile.builder()
                .user(user)
                .marketingOptIn(false)
                .build());

        OtpDispatchResult dispatch = createAndSendVerificationOtp(user, false);
        notificationService.createNotification(user, "WELCOME",
                "Welcome to TimeCraft",
                "Enter the 6-digit OTP sent to your email to verify your account and start shopping.",
                "/verify-otp?email=" + email);

        String devOtp = (!dispatch.mailDelivered() && properties.mail().logLinks()) ? dispatch.otp() : null;
        String msg = dispatch.mailDelivered()
                ? "A 6-digit verification OTP has been sent to your email."
                : "OTP generated (configure SMTP in run-dev.cmd to send real emails).";
        return new RegisterOtpResponse(email, true, dispatch.mailDelivered(), devOtp, msg);
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public AuthTokenResponse verifyRegistrationOtp(VerifyOtpRequest request, HttpServletRequest httpRequest) {
        String email = request.email().trim().toLowerCase();
        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> BusinessException.badRequest("INVALID_OTP", "Invalid email or verification code"));

        if (user.isEmailVerified()) {
            return issueTokens(user, UUID.randomUUID().toString(), httpRequest);
        }

        EmailVerificationToken token = emailVerificationTokenRepository
                .findTopByUserIdAndUsedAtIsNullOrderByCreatedAtDesc(user.getId())
                .orElseThrow(() -> BusinessException.badRequest("OTP_NOT_FOUND",
                        "No active verification code found. Please request a new OTP."));

        if (token.getExpiresAt().isBefore(Instant.now())) {
            token.setUsedAt(Instant.now());
            emailVerificationTokenRepository.save(token);
            throw BusinessException.badRequest("OTP_EXPIRED", "Verification code has expired. Please request a new OTP.");
        }
        if (token.getAttemptCount() >= MAX_OTP_ATTEMPTS) {
            token.setUsedAt(Instant.now());
            emailVerificationTokenRepository.save(token);
            throw BusinessException.badRequest("OTP_MAX_ATTEMPTS",
                    "Maximum verification attempts exceeded. Please request a new OTP.");
        }

        String candidateHash = hashOtp(email, request.otp().trim());
        boolean matched = MessageDigest.isEqual(
                candidateHash.getBytes(StandardCharsets.UTF_8),
                token.getTokenHash().getBytes(StandardCharsets.UTF_8)
        );
        if (!matched) {
            int nextAttempts = token.getAttemptCount() + 1;
            token.setAttemptCount(nextAttempts);
            if (nextAttempts >= MAX_OTP_ATTEMPTS) {
                token.setUsedAt(Instant.now());
            }
            emailVerificationTokenRepository.save(token);
            int remaining = Math.max(0, MAX_OTP_ATTEMPTS - nextAttempts);
            throw BusinessException.badRequest("INVALID_OTP",
                    remaining > 0
                            ? "Incorrect verification code. " + remaining + " attempt(s) remaining."
                            : "Maximum verification attempts exceeded. Please request a new OTP.");
        }

        token.setUsedAt(Instant.now());
        emailVerificationTokenRepository.save(token);

        user.setEmailVerified(true);
        user.setLastLoginAt(Instant.now());
        user = userRepository.save(user);

        return issueTokens(user, UUID.randomUUID().toString(), httpRequest);
    }

    @Transactional
    public RegisterOtpResponse resendRegistrationOtp(String email) {
        String normalized = email.trim().toLowerCase();
        var userOpt = userRepository.findByEmailIgnoreCase(normalized);
        if (userOpt.isEmpty()) {
            return new RegisterOtpResponse(normalized, true, false, null,
                    "If the account exists and is unverified, a fresh 6-digit OTP has been sent.");
        }
        User user = userOpt.get();
        if (user.isEmailVerified() || user.getAccountStatus() != AccountStatus.ACTIVE) {
            return new RegisterOtpResponse(normalized, false, false, null,
                    "Account is already verified or inactive.");
        }
        OtpDispatchResult dispatch = createAndSendVerificationOtp(user, true);
        String devOtp = (!dispatch.mailDelivered() && properties.mail().logLinks()) ? dispatch.otp() : null;
        String msg = dispatch.mailDelivered()
                ? "A fresh 6-digit OTP has been sent to your email."
                : "Fresh OTP generated (configure SMTP in run-dev.cmd to send real emails).";
        return new RegisterOtpResponse(normalized, true, dispatch.mailDelivered(), devOtp, msg);
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public AuthTokenResponse login(LoginRequest request, HttpServletRequest httpRequest) {
        String email = request.email().trim().toLowerCase();
        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> BusinessException.unauthorized("INVALID_CREDENTIALS", "Invalid email or password"));

        if (user.getAccountStatus() != AccountStatus.ACTIVE) {
            throw BusinessException.forbidden("This account has been deactivated. Please contact customer support.");
        }
        if (user.isLocked()) {
            throw BusinessException.forbidden("Account is temporarily locked due to failed login attempts. Please try again later.");
        }
        if (!user.hasRole(RoleName.CUSTOMER)) {
            throw BusinessException.forbidden("Please use the Admin Portal at /admin/login for administrator accounts.");
        }

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            int attempts = user.getFailedLoginAttempts() + 1;
            user.setFailedLoginAttempts(attempts);
            if (attempts >= MAX_FAILED_ATTEMPTS) {
                user.setLockedUntil(Instant.now().plus(LOCK_DURATION));
            }
            userRepository.save(user);
            throw BusinessException.unauthorized("INVALID_CREDENTIALS", "Invalid email or password");
        }

        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        user.setLastLoginAt(Instant.now());
        userRepository.save(user);

        return issueTokens(user, UUID.randomUUID().toString(), httpRequest);
    }

    @Transactional
    public AuthTokenResponse loginWithGoogle(GoogleLoginRequest request, HttpServletRequest httpRequest) {
        String email;
        String firstName;
        String lastName;

        if (request.credential() != null && !request.credential().isBlank()) {
            var verified = verifyGoogleIdToken(request.credential().trim());
            email = verified.email();
            firstName = verified.firstName();
            lastName = verified.lastName();
        } else if (properties.google() != null && properties.google().devQuickLoginEnabled()
                && request.email() != null && !request.email().isBlank()) {
            email = request.email().trim().toLowerCase();
            firstName = request.firstName() != null && !request.firstName().isBlank() ? request.firstName().trim() : "Collector";
            lastName = request.lastName() != null && !request.lastName().isBlank() ? request.lastName().trim() : "Google";
        } else {
            throw BusinessException.badRequest("GOOGLE_TOKEN_REQUIRED",
                    "Google ID credential token is required");
        }

        Role customerRole = roleRepository.findByName(RoleName.CUSTOMER)
                .orElseThrow(() -> new IllegalStateException("CUSTOMER role not initialized"));

        User user = userRepository.findByEmailIgnoreCase(email).orElse(null);
        if (user == null) {
            user = User.builder()
                    .email(email)
                    .passwordHash(passwordEncoder.encode(TokenUtils.generateToken()))
                    .firstName(firstName)
                    .lastName(lastName)
                    .emailVerified(true)
                    .accountStatus(AccountStatus.ACTIVE)
                    .roles(Set.of(customerRole))
                    .lastLoginAt(Instant.now())
                    .build();
            user = userRepository.save(user);

            customerProfileRepository.save(CustomerProfile.builder()
                    .user(user)
                    .marketingOptIn(false)
                    .build());

            notificationService.createNotification(user, "WELCOME",
                    "Welcome to TimeCraft",
                    "Signed in with Google. Explore our curated collection of Swiss, Japanese and Indian timepieces.",
                    "/pages/profile.html");
        } else {
            if (user.getAccountStatus() != AccountStatus.ACTIVE) {
                throw BusinessException.forbidden("This account has been deactivated.");
            }
            if (!user.hasRole(RoleName.CUSTOMER)) {
                throw BusinessException.forbidden("Administrator accounts must use /admin/login.");
            }
            user.setEmailVerified(true);
            user.setFailedLoginAttempts(0);
            user.setLockedUntil(null);
            user.setLastLoginAt(Instant.now());
            user = userRepository.save(user);
        }

        return issueTokens(user, UUID.randomUUID().toString(), httpRequest);
    }

    private record GoogleIdentity(String email, String firstName, String lastName) {
    }

    private GoogleIdentity verifyGoogleIdToken(String token) {
        try {
            var client = java.net.http.HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(8))
                    .build();
            // Support both Google OAuth2 access_token (popup token client) and Google ID token (JWT)
            boolean isJwt = token.chars().filter(ch -> ch == '.').count() == 2;
            var uri = isJwt
                    ? java.net.URI.create("https://oauth2.googleapis.com/tokeninfo?id_token="
                            + java.net.URLEncoder.encode(token, java.nio.charset.StandardCharsets.UTF_8))
                    : java.net.URI.create("https://www.googleapis.com/oauth2/v3/userinfo");
            var builder = java.net.http.HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(8));
            if (!isJwt) {
                builder.header("Authorization", "Bearer " + token);
            }
            var res = client.send(builder.GET().build(), java.net.http.HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() != 200) {
                throw BusinessException.unauthorized("INVALID_GOOGLE_TOKEN", "Google token verification failed");
            }
            var node = new com.fasterxml.jackson.databind.ObjectMapper().readTree(res.body());
            String configuredClientId = resolveGoogleClientId();
            if (isJwt && configuredClientId != null && !configuredClientId.isBlank()
                    && !configuredClientId.equals(node.path("aud").asText())) {
                throw BusinessException.unauthorized("GOOGLE_AUDIENCE_MISMATCH", "Google token audience mismatch");
            }
            String email = node.path("email").asText("").trim().toLowerCase();
            if (email.isBlank()) {
                throw BusinessException.unauthorized("GOOGLE_EMAIL_MISSING", "Google account email not present");
            }
            String given = node.path("given_name").asText("Collector");
            String family = node.path("family_name").asText("Client");
            return new GoogleIdentity(email, given, family);
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw BusinessException.unauthorized("GOOGLE_AUTH_FAILED", "Could not verify Google credential");
        }
    }

    public String resolveGoogleClientId() {
        String envId = properties.google() != null ? properties.google().clientId() : null;
        if (envId != null && !envId.isBlank()) {
            return envId.trim();
        }
        return storeSettingRepository.findBySettingKey("auth.google_client_id")
                .map(com.timecraft.admin.entity.StoreSetting::getSettingValue)
                .map(String::trim)
                .orElse("");
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public AuthTokenResponse refresh(String rawRefreshToken, HttpServletRequest httpRequest) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw BusinessException.unauthorized("REFRESH_TOKEN_MISSING", "Refresh token is required");
        }
        String hash = TokenUtils.sha256(rawRefreshToken.trim());
        RefreshToken existing = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> BusinessException.unauthorized("INVALID_REFRESH_TOKEN", "Invalid refresh token"));

        if (existing.isRevoked()) {
            // Token reuse detected — revoke the entire token family immediately.
            refreshTokenRepository.revokeFamily(existing.getFamilyId(), Instant.now());
            throw BusinessException.unauthorized("REFRESH_TOKEN_REUSED",
                    "Refresh token has already been used. All sessions in this family were revoked.");
        }
        if (existing.isExpired()) {
            existing.setRevokedAt(Instant.now());
            refreshTokenRepository.save(existing);
            throw BusinessException.unauthorized("REFRESH_TOKEN_EXPIRED", "Refresh token has expired. Please sign in again.");
        }

        User user = existing.getUser();
        if (user.getAccountStatus() != AccountStatus.ACTIVE || !user.hasRole(RoleName.CUSTOMER)) {
            refreshTokenRepository.revokeAllForUser(user.getId(), Instant.now());
            throw BusinessException.forbidden("Account is not active");
        }

        String nextRaw = TokenUtils.generateToken();
        String nextHash = TokenUtils.sha256(nextRaw);

        existing.setRevokedAt(Instant.now());
        existing.setReplacedByHash(nextHash);
        refreshTokenRepository.save(existing);

        RefreshToken replacement = RefreshToken.builder()
                .user(user)
                .tokenHash(nextHash)
                .familyId(existing.getFamilyId())
                .expiresAt(Instant.now().plus(properties.jwt().refreshTokenTtl()))
                .createdIp(RequestUtils.clientIp(httpRequest))
                .userAgent(RequestUtils.userAgent(httpRequest))
                .build();
        refreshTokenRepository.save(replacement);

        String accessToken = jwtService.generateCustomerAccessToken(user);
        return new AuthTokenResponse(accessToken, nextRaw, "Bearer", jwtService.accessTokenTtlSeconds(), toUserDto(user));
    }

    @Transactional
    public void logout(String rawRefreshToken, Long currentUserId) {
        Instant now = Instant.now();
        if (rawRefreshToken != null && !rawRefreshToken.isBlank()) {
            String hash = TokenUtils.sha256(rawRefreshToken.trim());
            refreshTokenRepository.findByTokenHash(hash).ifPresent(rt -> {
                rt.setRevokedAt(now);
                refreshTokenRepository.save(rt);
            });
        }
        if (currentUserId != null) {
            refreshTokenRepository.revokeAllForUser(currentUserId, now);
        }
    }

    @Transactional
    public void verifyEmail(String rawToken) {
        String hash = TokenUtils.sha256(rawToken.trim());
        EmailVerificationToken token = emailVerificationTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> BusinessException.badRequest("INVALID_VERIFICATION_TOKEN",
                        "Invalid or expired email verification link"));
        if (!token.isUsable()) {
            throw BusinessException.badRequest("VERIFICATION_TOKEN_EXPIRED",
                    "This verification link has expired or has already been used");
        }
        token.setUsedAt(Instant.now());
        emailVerificationTokenRepository.save(token);

        User user = token.getUser();
        user.setEmailVerified(true);
        userRepository.save(user);
    }

    @Transactional
    public void resendVerification(String email) {
        resendRegistrationOtp(email);
    }

    @Transactional
    public RegisterOtpResponse requestPasswordReset(ForgotPasswordRequest request) {
        String normalized = request.email().trim().toLowerCase();
        var userOpt = userRepository.findByEmailIgnoreCase(normalized);
        if (userOpt.isEmpty()) {
            return new RegisterOtpResponse(normalized, true, false, null,
                    "If an account exists for that email, a 6-digit password reset code has been sent.");
        }
        User user = userOpt.get();
        if (user.getAccountStatus() != AccountStatus.ACTIVE || !user.hasRole(RoleName.CUSTOMER)) {
            return new RegisterOtpResponse(normalized, true, false, null,
                    "If an account exists for that email, a 6-digit password reset code has been sent.");
        }

        Instant now = Instant.now();
        passwordResetTokenRepository.findTopByUserIdAndUsedAtIsNullOrderByCreatedAtDesc(user.getId())
                .ifPresent(prev -> {
                    if (prev.getLastSentAt() != null
                            && prev.getLastSentAt().plus(OTP_RESEND_COOLDOWN).isAfter(now)) {
                        long waitSec = Duration.between(now, prev.getLastSentAt().plus(OTP_RESEND_COOLDOWN)).toSeconds() + 1;
                        throw BusinessException.tooManyRequests(
                                "Please wait " + waitSec + " seconds before requesting another reset code.");
                    }
                    prev.setUsedAt(now);
                    passwordResetTokenRepository.save(prev);
                });

        String otp = generateSixDigitOtp();
        PasswordResetToken token = PasswordResetToken.builder()
                .user(user)
                .tokenHash(hashOtp(normalized, otp))
                .expiresAt(now.plus(OTP_TTL))
                .attemptCount(0)
                .lastSentAt(now)
                .build();
        passwordResetTokenRepository.save(token);
        boolean delivered = notificationService.sendPasswordResetOtpEmail(user, otp);
        String devOtp = (!delivered && properties.mail().logLinks()) ? otp : null;
        String msg = delivered
                ? "A 6-digit password reset code has been sent to your email."
                : "Password reset OTP generated (local dev mode).";
        return new RegisterOtpResponse(normalized, true, delivered, devOtp, msg);
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public void resetPassword(ResetPasswordRequest request) {
        PasswordResetToken token;
        if (request.email() != null && !request.email().isBlank() && request.otp() != null && !request.otp().isBlank()) {
            String normalized = request.email().trim().toLowerCase();
            User user = userRepository.findByEmailIgnoreCase(normalized)
                    .orElseThrow(() -> BusinessException.badRequest("INVALID_RESET_OTP", "Invalid email or reset code"));
            token = passwordResetTokenRepository.findTopByUserIdAndUsedAtIsNullOrderByCreatedAtDesc(user.getId())
                    .orElseThrow(() -> BusinessException.badRequest("INVALID_RESET_OTP",
                            "No active password reset code found. Please request a new code."));
            if (!token.isUsable()) {
                throw BusinessException.badRequest("RESET_OTP_EXPIRED", "Password reset code has expired");
            }
            if (token.getAttemptCount() >= MAX_OTP_ATTEMPTS) {
                token.setUsedAt(Instant.now());
                passwordResetTokenRepository.save(token);
                throw BusinessException.badRequest("RESET_OTP_MAX_ATTEMPTS",
                        "Maximum attempts exceeded. Please request a new reset code.");
            }
            String candidateHash = hashOtp(normalized, request.otp().trim());
            if (!MessageDigest.isEqual(candidateHash.getBytes(StandardCharsets.UTF_8),
                    token.getTokenHash().getBytes(StandardCharsets.UTF_8))) {
                token.setAttemptCount(token.getAttemptCount() + 1);
                if (token.getAttemptCount() >= MAX_OTP_ATTEMPTS) {
                    token.setUsedAt(Instant.now());
                }
                passwordResetTokenRepository.save(token);
                throw BusinessException.badRequest("INVALID_RESET_OTP", "Incorrect password reset code");
            }
        } else if (request.token() != null && !request.token().isBlank()) {
            String hash = TokenUtils.sha256(request.token().trim());
            token = passwordResetTokenRepository.findByTokenHash(hash)
                    .orElseThrow(() -> BusinessException.badRequest("INVALID_RESET_TOKEN",
                            "Invalid or expired password reset link"));
            if (!token.isUsable()) {
                throw BusinessException.badRequest("RESET_TOKEN_EXPIRED",
                        "This password reset link has expired or has already been used");
            }
        } else {
            throw BusinessException.badRequest("RESET_CODE_REQUIRED", "Email and 6-digit OTP are required");
        }

        token.setUsedAt(Instant.now());
        passwordResetTokenRepository.save(token);

        User user = token.getUser();
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        userRepository.save(user);

        refreshTokenRepository.revokeAllForUser(user.getId(), Instant.now());
        auditLogService.log(user.getId(), user.getEmail(), "CUSTOMER_PASSWORD_RESET",
                "USER", String.valueOf(user.getId()), "Customer completed password reset", RequestUtils.currentClientIp());
    }

    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw BusinessException.badRequest("INVALID_CURRENT_PASSWORD", "Current password is incorrect");
        }
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
        refreshTokenRepository.revokeAllForUser(userId, Instant.now());
    }

    @Transactional(readOnly = true)
    public AuthUserDto getCurrentUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
        return toUserDto(user);
    }

    public static AuthUserDto toUserDto(User user) {
        Set<String> roles = user.getRoles().stream().map(r -> r.getName().name()).collect(Collectors.toSet());
        return new AuthUserDto(
                user.getId(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getPhone(),
                user.isEmailVerified(),
                roles,
                user.getCreatedAt()
        );
    }

    private record OtpDispatchResult(String otp, boolean mailDelivered) {
    }

    private OtpDispatchResult createAndSendVerificationOtp(User user, boolean enforceCooldown) {
        Instant now = Instant.now();
        emailVerificationTokenRepository.findTopByUserIdAndUsedAtIsNullOrderByCreatedAtDesc(user.getId())
                .ifPresent(prev -> {
                    if (enforceCooldown && prev.getLastSentAt() != null
                            && prev.getLastSentAt().plus(OTP_RESEND_COOLDOWN).isAfter(now)) {
                        long waitSec = Duration.between(now, prev.getLastSentAt().plus(OTP_RESEND_COOLDOWN)).toSeconds() + 1;
                        throw BusinessException.tooManyRequests(
                                "Please wait " + waitSec + " seconds before resending OTP.");
                    }
                    prev.setUsedAt(now);
                    emailVerificationTokenRepository.save(prev);
                });

        String otp = generateSixDigitOtp();
        EmailVerificationToken token = EmailVerificationToken.builder()
                .user(user)
                .tokenHash(hashOtp(user.getEmail(), otp))
                .expiresAt(now.plus(OTP_TTL))
                .attemptCount(0)
                .lastSentAt(now)
                .build();
        emailVerificationTokenRepository.save(token);
        boolean delivered = notificationService.sendVerificationOtpEmail(user, otp);
        return new OtpDispatchResult(otp, delivered);
    }

    public static String generateSixDigitOtp() {
        return String.format("%06d", SECURE_RANDOM.nextInt(1_000_000));
    }

    public static String hashOtp(String email, String otp) {
        return TokenUtils.sha256(email.trim().toLowerCase() + ":" + otp.trim());
    }

    private AuthTokenResponse issueTokens(User user, String familyId, HttpServletRequest httpRequest) {
        String rawRefresh = TokenUtils.generateToken();
        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .tokenHash(TokenUtils.sha256(rawRefresh))
                .familyId(familyId)
                .expiresAt(Instant.now().plus(properties.jwt().refreshTokenTtl()))
                .createdIp(RequestUtils.clientIp(httpRequest))
                .userAgent(RequestUtils.userAgent(httpRequest))
                .build();
        refreshTokenRepository.save(refreshToken);

        String accessToken = jwtService.generateCustomerAccessToken(user);
        return new AuthTokenResponse(accessToken, rawRefresh, "Bearer", jwtService.accessTokenTtlSeconds(), toUserDto(user));
    }
}
