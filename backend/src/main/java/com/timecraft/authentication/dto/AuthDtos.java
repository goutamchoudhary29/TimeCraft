package com.timecraft.authentication.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.Set;

/**
 * Customer authentication request and response DTOs.
 * Notice that RegisterRequest has NO role field — public registration creates CUSTOMER accounts only.
 */
public final class AuthDtos {

    private AuthDtos() {
    }

    public record RegisterRequest(
            @NotBlank(message = "First name is required")
            @Size(max = 80, message = "First name must be at most 80 characters")
            String firstName,

            @NotBlank(message = "Last name is required")
            @Size(max = 80, message = "Last name must be at most 80 characters")
            String lastName,

            @NotBlank(message = "Email is required")
            @Email(message = "Please enter a valid email address")
            @Size(max = 255)
            String email,

            @NotBlank(message = "Password is required")
            @Size(min = 8, max = 72, message = "Password must be between 8 and 72 characters")
            @Pattern(
                    regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).+$",
                    message = "Password must include at least one uppercase letter, one lowercase letter and one digit"
            )
            String password,

            @Pattern(regexp = "^$|^[+0-9 ()-]{7,20}$", message = "Please enter a valid phone number")
            String phone
    ) {
    }

    public record LoginRequest(
            @NotBlank(message = "Email is required")
            @Email(message = "Please enter a valid email address")
            String email,

            @NotBlank(message = "Password is required")
            String password
    ) {
    }

    public record GoogleLoginRequest(
            String credential,
            @Email(message = "Please enter a valid Google email address")
            String email,
            @Size(max = 80)
            String firstName,
            @Size(max = 80)
            String lastName
    ) {
    }

    public record RefreshRequest(String refreshToken) {
    }

    public record VerifyEmailRequest(@NotBlank(message = "Verification token is required") String token) {
    }

    public record ResendVerificationRequest(
            @NotBlank(message = "Email is required")
            @Email(message = "Please enter a valid email address")
            String email
    ) {
    }

    public record ForgotPasswordRequest(
            @NotBlank(message = "Email is required")
            @Email(message = "Please enter a valid email address")
            String email
    ) {
    }

    public record ResetPasswordRequest(
            @NotBlank(message = "Reset token is required")
            String token,

            @NotBlank(message = "New password is required")
            @Size(min = 8, max = 72, message = "Password must be between 8 and 72 characters")
            @Pattern(
                    regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).+$",
                    message = "Password must include at least one uppercase letter, one lowercase letter and one digit"
            )
            String newPassword
    ) {
    }

    public record ChangePasswordRequest(
            @NotBlank(message = "Current password is required")
            String currentPassword,

            @NotBlank(message = "New password is required")
            @Size(min = 8, max = 72, message = "Password must be between 8 and 72 characters")
            @Pattern(
                    regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).+$",
                    message = "Password must include at least one uppercase letter, one lowercase letter and one digit"
            )
            String newPassword
    ) {
    }

    public record AuthUserDto(
            Long id,
            String email,
            String firstName,
            String lastName,
            String phone,
            boolean emailVerified,
            Set<String> roles,
            Instant createdAt
    ) {
    }

    public record AuthTokenResponse(
            String accessToken,
            String refreshToken,
            String tokenType,
            long expiresInSeconds,
            AuthUserDto user
    ) {
    }
}
