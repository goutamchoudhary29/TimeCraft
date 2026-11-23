package com.timecraft.audit.repository;

import com.timecraft.audit.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    @Query("""
            SELECT a FROM AuditLog a
            WHERE (:action IS NULL OR a.action = :action)
            AND (:entityType IS NULL OR a.entityType = :entityType)
            AND (:search IS NULL OR LOWER(a.actorEmail) LIKE LOWER(CONCAT('%', :search, '%'))
                 OR LOWER(a.details) LIKE LOWER(CONCAT('%', :search, '%'))
                 OR LOWER(a.entityId) LIKE LOWER(CONCAT('%', :search, '%')))
            ORDER BY a.createdAt DESC
            """)
    Page<AuditLog> search(
            @Param("search") String search,
            @Param("action") String action,
            @Param("entityType") String entityType,
            Pageable pageable
    );
}
