package com.timecraft.returns.repository;

import com.timecraft.returns.entity.Refund;
import com.timecraft.returns.entity.RefundStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface RefundRepository extends JpaRepository<Refund, Long> {

    List<Refund> findByOrderIdOrderByCreatedAtDesc(Long orderId);

    Optional<Refund> findByProviderRefundId(String providerRefundId);

    Page<Refund> findAllByOrderByCreatedAtDesc(Pageable pageable);

    @Query("""
            SELECT COALESCE(SUM(r.amount), 0) FROM Refund r
            WHERE r.status = :status
            AND r.createdAt >= :from AND r.createdAt < :to
            """)
    BigDecimal sumAmountByStatusBetween(
            @Param("status") RefundStatus status,
            @Param("from") Instant from,
            @Param("to") Instant to
    );

    @Query("""
            SELECT COUNT(r) FROM Refund r
            WHERE r.status = :status
            AND r.createdAt >= :from AND r.createdAt < :to
            """)
    long countByStatusBetween(
            @Param("status") RefundStatus status,
            @Param("from") Instant from,
            @Param("to") Instant to
    );
}
