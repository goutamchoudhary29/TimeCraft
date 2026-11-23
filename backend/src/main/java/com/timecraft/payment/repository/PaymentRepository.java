package com.timecraft.payment.repository;

import com.timecraft.payment.entity.Payment;
import com.timecraft.payment.entity.PaymentAttemptStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByProviderOrderId(String providerOrderId);

    Optional<Payment> findByProviderPaymentId(String providerPaymentId);

    List<Payment> findByOrderIdOrderByCreatedAtDesc(Long orderId);

    Optional<Payment> findFirstByOrderIdAndStatusInOrderByCreatedAtDesc(Long orderId, List<PaymentAttemptStatus> statuses);

    Page<Payment> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
