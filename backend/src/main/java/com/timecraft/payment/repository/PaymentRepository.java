package com.timecraft.payment.repository;

import com.timecraft.payment.entity.Payment;
import com.timecraft.payment.entity.PaymentAttemptStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    @EntityGraph(attributePaths = {"order"})
    Optional<Payment> findByProviderOrderId(String providerOrderId);

    @EntityGraph(attributePaths = {"order"})
    Optional<Payment> findByProviderPaymentId(String providerPaymentId);

    @EntityGraph(attributePaths = {"order"})
    List<Payment> findByOrderIdOrderByCreatedAtDesc(Long orderId);

    Optional<Payment> findFirstByOrderIdAndStatusInOrderByCreatedAtDesc(Long orderId, List<PaymentAttemptStatus> statuses);

    @EntityGraph(attributePaths = {"order"})
    Page<Payment> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
