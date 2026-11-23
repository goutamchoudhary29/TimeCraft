package com.timecraft.customer.service;

import com.timecraft.audit.service.AuditLogService;
import com.timecraft.authentication.entity.AccountStatus;
import com.timecraft.authentication.entity.RoleName;
import com.timecraft.authentication.entity.User;
import com.timecraft.authentication.repository.RefreshTokenRepository;
import com.timecraft.authentication.repository.UserRepository;
import com.timecraft.common.dto.PageResponse;
import com.timecraft.customer.entity.CustomerProfile;
import com.timecraft.customer.repository.CustomerProfileRepository;
import com.timecraft.exception.ResourceNotFoundException;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class CustomerService {

    private final UserRepository userRepository;
    private final CustomerProfileRepository profileRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final AuditLogService auditLogService;

    public record ProfileDto(
            Long userId,
            String email,
            String firstName,
            String lastName,
            String phone,
            boolean emailVerified,
            AccountStatus accountStatus,
            LocalDate dateOfBirth,
            String gender,
            boolean marketingOptIn,
            Instant lastLoginAt,
            Instant createdAt
    ) {
    }

    public record UpdateProfileRequest(
            @NotBlank @Size(max = 80) String firstName,
            @NotBlank @Size(max = 80) String lastName,
            @Pattern(regexp = "^$|^[+0-9 ()-]{7,20}$", message = "Invalid phone number") String phone,
            LocalDate dateOfBirth,
            @Size(max = 20) String gender,
            boolean marketingOptIn
    ) {
    }

    @Transactional(readOnly = true)
    public ProfileDto getProfile(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer", userId));
        CustomerProfile profile = profileRepository.findByUserId(userId).orElse(null);
        return toDto(user, profile);
    }

    @Transactional
    public ProfileDto updateProfile(Long userId, UpdateProfileRequest req) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer", userId));
        user.setFirstName(req.firstName().trim());
        user.setLastName(req.lastName().trim());
        user.setPhone(req.phone() != null && !req.phone().isBlank() ? req.phone().trim() : null);
        userRepository.save(user);

        CustomerProfile profile = profileRepository.findByUserId(userId)
                .orElseGet(() -> CustomerProfile.builder().user(user).build());
        profile.setDateOfBirth(req.dateOfBirth());
        profile.setGender(req.gender() != null && !req.gender().isBlank() ? req.gender().trim() : null);
        profile.setMarketingOptIn(req.marketingOptIn());
        profile = profileRepository.save(profile);
        return toDto(user, profile);
    }

    @Transactional(readOnly = true)
    public PageResponse<ProfileDto> searchCustomers(String search, AccountStatus status, Pageable pageable) {
        String q = search != null && !search.isBlank() ? search.trim() : null;
        return PageResponse.of(
                userRepository.searchByRole(RoleName.CUSTOMER, q, status, pageable),
                u -> toDto(u, profileRepository.findByUserId(u.getId()).orElse(null))
        );
    }

    @Transactional
    public ProfileDto updateAccountStatus(Long customerId, AccountStatus status) {
        User user = userRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer", customerId));
        user.setAccountStatus(status);
        if (status == AccountStatus.DEACTIVATED) {
            refreshTokenRepository.revokeAllForUser(customerId, Instant.now());
        } else {
            user.setFailedLoginAttempts(0);
            user.setLockedUntil(null);
        }
        user = userRepository.save(user);
        auditLogService.logCurrentActor("CUSTOMER_STATUS_CHANGED", "USER", customerId,
                "Customer " + user.getEmail() + " status changed to " + status);
        return toDto(user, profileRepository.findByUserId(customerId).orElse(null));
    }

    private static ProfileDto toDto(User u, CustomerProfile p) {
        return new ProfileDto(
                u.getId(),
                u.getEmail(),
                u.getFirstName(),
                u.getLastName(),
                u.getPhone(),
                u.isEmailVerified(),
                u.getAccountStatus(),
                p != null ? p.getDateOfBirth() : null,
                p != null ? p.getGender() : null,
                p != null && p.isMarketingOptIn(),
                u.getLastLoginAt(),
                u.getCreatedAt()
        );
    }
}
