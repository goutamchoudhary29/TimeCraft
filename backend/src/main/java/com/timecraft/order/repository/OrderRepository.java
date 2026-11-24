package com.timecraft.order.repository;

import com.timecraft.order.entity.Order;
import com.timecraft.order.entity.OrderStatus;
import com.timecraft.order.entity.PaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    Optional<Order> findByOrderNumber(String orderNumber);

    Optional<Order> findByIdAndUserId(Long id, Long userId);

    Optional<Order> findByOrderNumberAndUserId(String orderNumber, Long userId);

    Optional<Order> findByUserIdAndIdempotencyKey(Long userId, String idempotencyKey);

    Page<Order> findByUserIdOrderByPlacedAtDesc(Long userId, Pageable pageable);

    List<Order> findByStatusAndPaymentStatusAndPaymentExpiresAtBefore(
            OrderStatus status, PaymentStatus paymentStatus, Instant cutoff);

    long countByStatusIn(Collection<OrderStatus> statuses);

    long countByPaymentMethod(com.timecraft.order.entity.PaymentMethod paymentMethod);

    @Query("""
            SELECT COALESCE(SUM(o.grandTotal), 0) FROM Order o
            WHERE o.paymentStatus IN ('PAID', 'PARTIALLY_REFUNDED')
            AND o.status NOT IN ('CANCELLED', 'FAILED', 'REFUNDED')
            """)
    BigDecimal sumTotalRevenue();

    @Query("""
            SELECT COALESCE(SUM(o.grandTotal), 0) FROM Order o
            WHERE o.paymentStatus IN ('PAID', 'PARTIALLY_REFUNDED')
            AND o.status NOT IN ('CANCELLED', 'FAILED', 'REFUNDED')
            AND o.placedAt >= :from AND o.placedAt < :to
            """)
    BigDecimal sumRevenueBetween(@Param("from") Instant from, @Param("to") Instant to);

    @Query("""
            SELECT COUNT(o) FROM Order o
            WHERE o.placedAt >= :from AND o.placedAt < :to
            AND o.status NOT IN ('CANCELLED', 'FAILED')
            """)
    long countActiveOrdersBetween(@Param("from") Instant from, @Param("to") Instant to);

    @Query("""
            SELECT o FROM Order o
            WHERE (:status IS NULL OR o.status = :status)
            AND (:paymentStatus IS NULL OR o.paymentStatus = :paymentStatus)
            AND (:search IS NULL OR LOWER(o.orderNumber) LIKE LOWER(CONCAT('%', :search, '%'))
                 OR LOWER(o.customerEmail) LIKE LOWER(CONCAT('%', :search, '%'))
                 OR LOWER(o.customerName) LIKE LOWER(CONCAT('%', :search, '%')))
            """)
    Page<Order> searchAdmin(
            @Param("search") String search,
            @Param("status") OrderStatus status,
            @Param("paymentStatus") PaymentStatus paymentStatus,
            Pageable pageable
    );
}
