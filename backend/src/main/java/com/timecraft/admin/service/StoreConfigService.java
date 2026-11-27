package com.timecraft.admin.service;

import com.timecraft.admin.entity.NewsletterSubscriber;
import com.timecraft.admin.entity.StoreSetting;
import com.timecraft.admin.entity.SupportTicket;
import com.timecraft.admin.entity.SupportTicketStatus;
import com.timecraft.admin.repository.NewsletterSubscriberRepository;
import com.timecraft.admin.repository.StoreSettingRepository;
import com.timecraft.admin.repository.SupportTicketRepository;
import com.timecraft.audit.service.AuditLogService;
import com.timecraft.authentication.repository.UserRepository;
import com.timecraft.common.dto.PageResponse;
import com.timecraft.common.util.Money;
import com.timecraft.common.util.ReferenceNumberGenerator;
import com.timecraft.config.CheckoutProperties;
import com.timecraft.exception.ResourceNotFoundException;
import com.timecraft.security.SecurityUtils;
import com.timecraft.security.TimeCraftPrincipal;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class StoreConfigService {

    private final StoreSettingRepository storeSettingRepository;
    private final SupportTicketRepository supportTicketRepository;
    private final NewsletterSubscriberRepository newsletterRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;
    private final CheckoutProperties checkoutProperties;

    public record ContactTicketRequest(
            @NotBlank @Size(max = 120) String name,
            @NotBlank @Email @Size(max = 255) String email,
            @NotBlank @Size(max = 200) String subject,
            @NotBlank @Size(max = 4000) String message,
            @Size(max = 30) String orderNumber
    ) {
    }

    public record NewsletterRequest(@NotBlank @Email @Size(max = 255) String email) {
    }

    public record SupportTicketDto(
            Long id,
            String ticketNumber,
            String name,
            String email,
            String subject,
            String message,
            String orderNumber,
            SupportTicketStatus status,
            String adminReply,
            Instant repliedAt,
            Instant createdAt
    ) {
        public static SupportTicketDto from(SupportTicket t) {
            return new SupportTicketDto(t.getId(), t.getTicketNumber(), t.getName(), t.getEmail(),
                    t.getSubject(), t.getMessage(), t.getOrderNumber(), t.getStatus(),
                    t.getAdminReply(), t.getRepliedAt(), t.getCreatedAt());
        }
    }

    @Transactional(readOnly = true)
    public String getString(String key, String fallback) {
        return storeSettingRepository.findBySettingKey(key)
                .map(StoreSetting::getSettingValue)
                .orElse(fallback);
    }

    @Transactional(readOnly = true)
    public BigDecimal getDecimal(String key, String fallback) {
        String raw = getString(key, fallback);
        try {
            return Money.of(raw);
        } catch (Exception e) {
            return Money.of(fallback);
        }
    }

    @Transactional(readOnly = true)
    public int getInt(String key, int fallback) {
        String raw = getString(key, String.valueOf(fallback));
        try {
            return Integer.parseInt(raw.trim());
        } catch (Exception e) {
            return fallback;
        }
    }

    @Transactional(readOnly = true)
    public boolean getBoolean(String key, boolean fallback) {
        String raw = getString(key, String.valueOf(fallback));
        return Boolean.parseBoolean(raw.trim());
    }

    @Transactional(readOnly = true)
    public BigDecimal getMaxCodAmount() {
        return checkoutProperties.effectiveMaxCodAmount();
    }

    @Transactional(readOnly = true)
    public Map<String, String> getPublicStorefrontConfig() {
        Map<String, String> map = new LinkedHashMap<>();
        for (StoreSetting s : storeSettingRepository.findAllByOrderBySettingGroupAscSettingKeyAsc()) {
            map.put(s.getSettingKey(), s.getSettingValue());
        }
        BigDecimal maxCod = getMaxCodAmount();
        map.put("cod_max_amount", maxCod.toPlainString());
        map.put("max_cod_amount", maxCod.toPlainString());
        return map;
    }

    @Transactional(readOnly = true)
    public List<StoreSetting> listAllSettings() {
        return storeSettingRepository.findAllByOrderBySettingGroupAscSettingKeyAsc();
    }

    @Transactional
    public void updateSettings(Map<String, String> updates) {
        Long actorId = SecurityUtils.currentPrincipal().map(TimeCraftPrincipal::getId).orElse(null);
        updates.forEach((key, value) -> {
            if (value != null) {
                storeSettingRepository.findBySettingKey(key).ifPresent(setting -> {
                    setting.setSettingValue(value.trim());
                    setting.setUpdatedBy(actorId);
                    storeSettingRepository.save(setting);
                });
            }
        });
        auditLogService.logCurrentActor("STORE_SETTINGS_UPDATED", "STORE_SETTING", "bulk",
                "Updated " + updates.size() + " store settings");
    }

    @Transactional
    public SupportTicketDto submitTicket(ContactTicketRequest req) {
        Long userId = SecurityUtils.currentPrincipal().map(TimeCraftPrincipal::getId).orElse(null);
        SupportTicket ticket = SupportTicket.builder()
                .ticketNumber(ReferenceNumberGenerator.next("TCK"))
                .user(userId != null ? userRepository.findById(userId).orElse(null) : null)
                .name(req.name().trim())
                .email(req.email().trim().toLowerCase())
                .subject(req.subject().trim())
                .message(req.message().trim())
                .orderNumber(req.orderNumber() != null && !req.orderNumber().isBlank() ? req.orderNumber().trim() : null)
                .status(SupportTicketStatus.OPEN)
                .build();
        return SupportTicketDto.from(supportTicketRepository.save(ticket));
    }

    @Transactional(readOnly = true)
    public PageResponse<SupportTicketDto> listTickets(SupportTicketStatus status, Pageable pageable) {
        var page = status != null
                ? supportTicketRepository.findByStatusOrderByCreatedAtDesc(status, pageable)
                : supportTicketRepository.findAllByOrderByCreatedAtDesc(pageable);
        return PageResponse.of(page, SupportTicketDto::from);
    }

    @Transactional
    public SupportTicketDto replyToTicket(Long ticketId, String reply, SupportTicketStatus status) {
        SupportTicket ticket = supportTicketRepository.findById(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("SupportTicket", ticketId));
        ticket.setAdminReply(reply != null ? reply.trim() : null);
        ticket.setStatus(status != null ? status : SupportTicketStatus.RESOLVED);
        ticket.setRepliedBy(SecurityUtils.currentPrincipal().map(TimeCraftPrincipal::getId).orElse(null));
        ticket.setRepliedAt(Instant.now());
        ticket = supportTicketRepository.save(ticket);
        auditLogService.logCurrentActor("SUPPORT_TICKET_UPDATED", "SUPPORT_TICKET", ticket.getId(),
                "Ticket " + ticket.getTicketNumber() + " status=" + ticket.getStatus());
        return SupportTicketDto.from(ticket);
    }

    @Transactional
    public void subscribeNewsletter(NewsletterRequest req) {
        String email = req.email().trim().toLowerCase();
        NewsletterSubscriber sub = newsletterRepository.findByEmailIgnoreCase(email)
                .orElseGet(() -> NewsletterSubscriber.builder().email(email).active(true).build());
        sub.setActive(true);
        newsletterRepository.save(sub);
    }
}
