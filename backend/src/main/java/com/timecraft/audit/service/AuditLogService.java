package com.timecraft.audit.service;

import com.timecraft.audit.entity.AuditLog;
import com.timecraft.audit.repository.AuditLogRepository;
import com.timecraft.common.util.RequestUtils;
import com.timecraft.security.SecurityUtils;
import com.timecraft.security.TimeCraftPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records auditable administrative and security actions.
 * Never pass passwords, tokens or payment secrets into details.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(Long actorUserId, String actorEmail, String action,
                    String entityType, String entityId, String details, String ipAddress) {
        try {
            AuditLog entry = AuditLog.builder()
                    .actorUserId(actorUserId)
                    .actorEmail(actorEmail)
                    .action(action)
                    .entityType(entityType)
                    .entityId(entityId)
                    .details(truncate(details, 4000))
                    .ipAddress(ipAddress != null ? ipAddress : RequestUtils.currentClientIp())
                    .build();
            auditLogRepository.save(entry);
        } catch (Exception ex) {
            log.warn("Failed to persist audit log [{} {} {}]: {}", action, entityType, entityId, ex.getMessage());
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logCurrentActor(String action, String entityType, Object entityId, String details) {
        TimeCraftPrincipal actor = SecurityUtils.currentPrincipal().orElse(null);
        log(actor != null ? actor.getId() : null,
                actor != null ? actor.getEmail() : "system",
                action,
                entityType,
                entityId != null ? String.valueOf(entityId) : null,
                details,
                RequestUtils.currentClientIp());
    }

    @Transactional(readOnly = true)
    public Page<AuditLog> search(String search, String action, String entityType, Pageable pageable) {
        return auditLogRepository.search(blankToNull(search), blankToNull(action), blankToNull(entityType), pageable);
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
