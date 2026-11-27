package com.timecraft.payment.service;

import com.timecraft.exception.BusinessException;
import com.timecraft.order.entity.Order;
import com.timecraft.order.entity.OrderStatus;
import com.timecraft.order.entity.PaymentMethod;
import com.timecraft.order.entity.PaymentStatus;
import com.timecraft.order.repository.OrderRepository;
import com.timecraft.order.service.OrderService;
import com.timecraft.payment.entity.Payment;
import com.timecraft.payment.entity.PaymentAttemptStatus;
import com.timecraft.payment.entity.PaymentTransaction;
import com.timecraft.payment.repository.PaymentRepository;
import com.timecraft.payment.repository.PaymentTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Shared payment lifecycle helper eliminating duplicate order-payability checks,
 * gateway-switch attempt superseding, and transaction ledger recording across
 * {@link RazorpayPaymentService} and {@link PayuPaymentService}.
 */
@Component
@RequiredArgsConstructor
public class PaymentAttemptHelper {

    private final OrderService orderService;
    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentTransactionRepository transactionRepository;

    /**
     * Loads and validates that a customer order is eligible to accept an online payment
     * without creating duplicate orders. Updates the order's active payment method if switched.
     */
    public Order loadPayableCustomerOrder(Long userId, Long orderId, PaymentMethod targetMethod) {
        Order order = orderService.findCustomerOrderEntity(userId, String.valueOf(orderId));
        if (order.getPaymentStatus() == PaymentStatus.PAID) {
            throw BusinessException.conflict("ORDER_ALREADY_PAID", "This order has already been paid");
        }
        if (order.getStatus() != OrderStatus.PENDING) {
            throw BusinessException.badRequest("ORDER_NOT_PAYABLE",
                    "Order is in status " + order.getStatus() + " and cannot accept payment");
        }
        if (order.getPaymentExpiresAt() != null && order.getPaymentExpiresAt().isBefore(Instant.now())) {
            orderService.handlePaymentFailedOrExpired(order, "Payment reservation window expired", true);
            throw BusinessException.badRequest("PAYMENT_WINDOW_EXPIRED",
                    "Your reservation window expired. Please place a new order.");
        }
        if (order.getPaymentMethod() != targetMethod) {
            order.setPaymentMethod(targetMethod);
            order = orderRepository.save(order);
        }
        return order;
    }

    /**
     * Cancels open CREATED payment attempts belonging to another gateway (or all open attempts
     * when {@code retry == true}) and returns the most recent reusable CREATED attempt for
     * {@code targetProvider}, if any.
     */
    public Optional<Payment> reconcileOpenAttempts(Long orderId, String targetProvider, boolean retry) {
        List<Payment> openAttempts = paymentRepository.findByOrderIdOrderByCreatedAtDesc(orderId);
        Payment reusable = null;
        for (Payment attempt : openAttempts) {
            if (attempt.getStatus() == PaymentAttemptStatus.CREATED) {
                if (retry || !targetProvider.equalsIgnoreCase(attempt.getProvider())) {
                    attempt.setStatus(PaymentAttemptStatus.CANCELLED);
                    attempt.setFailureReason("Superseded by " + targetProvider + " payment initiation");
                    paymentRepository.save(attempt);
                    recordTransaction(attempt, "PAYMENT_SUPERSEDED", attempt.getAmount(), "CANCELLED",
                            attempt.getProviderOrderId(), "Superseded by fresh " + targetProvider + " payment attempt");
                } else if (reusable == null) {
                    reusable = attempt;
                }
            }
        }
        return Optional.ofNullable(reusable);
    }

    public void recordTransaction(Payment payment, String transactionType, BigDecimal amount,
                                  String status, String providerReference, String details) {
        transactionRepository.save(PaymentTransaction.builder()
                .payment(payment)
                .transactionType(transactionType)
                .amount(amount)
                .status(status)
                .providerReference(providerReference)
                .details(details)
                .build());
    }
}
