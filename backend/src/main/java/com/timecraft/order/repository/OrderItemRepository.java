package com.timecraft.order.repository;

import com.timecraft.order.entity.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    @Query("""
            SELECT oi FROM OrderItem oi
            JOIN oi.order o
            WHERE o.user.id = :userId
            AND oi.product.id = :productId
            AND o.status = 'DELIVERED'
            ORDER BY o.deliveredAt DESC
            """)
    List<OrderItem> findDeliveredItemsForUserAndProduct(@Param("userId") Long userId, @Param("productId") Long productId);

    Optional<OrderItem> findByIdAndOrderId(Long id, Long orderId);

    @Query("""
            SELECT oi.product.id, oi.productName, oi.brandName, SUM(oi.quantity), SUM(oi.lineTotal)
            FROM OrderItem oi
            JOIN oi.order o
            WHERE o.paymentStatus IN ('PAID', 'PARTIALLY_REFUNDED')
            AND o.status NOT IN ('CANCELLED', 'FAILED', 'REFUNDED')
            AND o.placedAt >= :from AND o.placedAt < :to
            GROUP BY oi.product.id, oi.productName, oi.brandName
            ORDER BY SUM(oi.quantity) DESC
            """)
    List<Object[]> findTopSellingProductsBetween(@Param("from") Instant from, @Param("to") Instant to);

    @Query("""
            SELECT oi.brandName, SUM(oi.quantity), SUM(oi.lineTotal)
            FROM OrderItem oi
            JOIN oi.order o
            WHERE o.paymentStatus IN ('PAID', 'PARTIALLY_REFUNDED')
            AND o.status NOT IN ('CANCELLED', 'FAILED', 'REFUNDED')
            AND o.placedAt >= :from AND o.placedAt < :to
            GROUP BY oi.brandName
            ORDER BY SUM(oi.lineTotal) DESC
            """)
    List<Object[]> findTopBrandsBetween(@Param("from") Instant from, @Param("to") Instant to);
}
