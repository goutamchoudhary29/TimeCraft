package com.timecraft.returns.service;

import com.timecraft.admin.service.StoreConfigService;
import com.timecraft.audit.service.AuditLogService;
import com.timecraft.authentication.entity.User;
import com.timecraft.authentication.repository.UserRepository;
import com.timecraft.common.dto.PageResponse;
import com.timecraft.common.util.Money;
import com.timecraft.common.util.ReferenceNumberGenerator;
import com.timecraft.exception.BusinessException;
import com.timecraft.exception.ResourceNotFoundException;
import com.timecraft.inventory.service.InventoryService;
import com.timecraft.notification.service.NotificationService;
import com.timecraft.order.entity.Order;
import com.timecraft.order.entity.OrderItem;
import com.timecraft.order.entity.OrderStatus;
import com.timecraft.order.entity.PaymentStatus;
import com.timecraft.order.repository.OrderItemRepository;
import com.timecraft.order.repository.OrderRepository;
import com.timecraft.order.service.OrderService;
import com.timecraft.payment.entity.Payment;
import com.timecraft.payment.service.RazorpayPaymentService;
import com.timecraft.returns.entity.Refund;
import com.timecraft.returns.entity.RefundStatus;
import com.timecraft.returns.entity.ReturnItem;
import com.timecraft.returns.entity.ReturnRequest;
import com.timecraft.returns.entity.ReturnRequestStatus;
import com.timecraft.returns.repository.RefundRepository;
import com.timecraft.returns.repository.ReturnRequestRepository;
import com.timecraft.security.SecurityUtils;
import com.timecraft.security.TimeCraftPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Customer return requests (validated against delivery date + configurable return window),
 * admin review/approval, inventory restoration on receipt, and verified refund processing.
 */
@Service
@RequiredArgsConstructor
public class ReturnAndRefundService {

    private final ReturnRequestRepository returnRequestRepository;
    private final RefundRepository refundRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final UserRepository userRepository;
    private final OrderService orderService;
    private final InventoryService inventoryService;
    private final RazorpayPaymentService paymentService;
    private final StoreConfigService storeConfigService;
    private final NotificationService notificationService;
    private final AuditLogService auditLogService;

    public record ReturnItemInput(
            @NotNull Long orderItemId,
            @Min(1) int quantity,
            @Size(max = 255) String conditionNote
    ) {
    }

    public record CreateReturnRequest(
            @NotNull Long orderId,
            @NotBlank @Size(max = 40) String reasonCode,
            @Size(max = 1000) String reasonDetails,
            @NotEmpty List<@Valid ReturnItemInput> items
    ) {
    }

    public record AdminDecisionRequest(
            @NotNull ReturnRequestStatus status,
            @Size(max = 1000) String adminNote
    ) {
    }

    public record InitiateRefundRequest(
            @NotNull Long orderId,
            Long returnRequestId,
            BigDecimal amount,
            @NotBlank @Size(max = 255) String reason
    ) {
    }

    public record ReturnLineDto(
            Long id,
            Long orderItemId,
            String productName,
            String variantName,
            String sku,
            int quantity,
            String conditionNote
    ) {
        public static ReturnLineDto from(ReturnItem ri) {
            OrderItem oi = ri.getOrderItem();
            return new ReturnLineDto(ri.getId(), oi.getId(), oi.getProductName(),
                    oi.getVariantName(), oi.getSku(), ri.getQuantity(), ri.getConditionNote());
        }
    }

    public record ReturnRequestDto(
            Long id,
            String returnNumber,
            Long orderId,
            String orderNumber,
            Long userId,
            String customerName,
            ReturnRequestStatus status,
            String reasonCode,
            String reasonDetails,
            String adminNote,
            Instant reviewedAt,
            Instant createdAt,
            List<ReturnLineDto> items
    ) {
        public static ReturnRequestDto from(ReturnRequest r) {
            return new ReturnRequestDto(
                    r.getId(),
                    r.getReturnNumber(),
                    r.getOrder().getId(),
                    r.getOrder().getOrderNumber(),
                    r.getUser().getId(),
                    r.getUser().getFullName(),
                    r.getStatus(),
                    r.getReasonCode(),
                    r.getReasonDetails(),
                    r.getAdminNote(),
                    r.getReviewedAt(),
                    r.getCreatedAt(),
                    r.getItems().stream().map(ReturnLineDto::from).toList()
            );
        }
    }

    public record RefundDto(
            Long id,
            String refundNumber,
            Long orderId,
            String orderNumber,
            Long paymentId,
            Long returnRequestId,
            BigDecimal amount,
            String reason,
            RefundStatus status,
            String providerRefundId,
            String failureReason,
            Instant processedAt,
            Instant createdAt
    ) {
        public static RefundDto from(Refund r) {
            return new RefundDto(
                    r.getId(),
                    r.getRefundNumber(),
                    r.getOrder().getId(),
                    r.getOrder().getOrderNumber(),
                    r.getPayment().getId(),
                    r.getReturnRequest() != null ? r.getReturnRequest().getId() : null,
                    r.getAmount(),
                    r.getReason(),
                    r.getStatus(),
                    r.getProviderRefundId(),
                    r.getFailureReason(),
                    r.getProcessedAt(),
                    r.getCreatedAt()
            );
        }
    }

    @Transactional
    public ReturnRequestDto submitReturnRequest(Long userId, CreateReturnRequest req) {
        Order order = orderService.findCustomerOrderEntity(userId, String.valueOf(req.orderId()));
        if (order.getStatus() != OrderStatus.DELIVERED || order.getDeliveredAt() == null) {
            throw BusinessException.badRequest("RETURN_NOT_ELIGIBLE",
                    "Only delivered orders are eligible for a return request");
        }
        int windowDays = storeConfigService.getInt("returns.window_days", 7);
        Instant deadline = order.getDeliveredAt().plus(Duration.ofDays(windowDays));
        if (Instant.now().isAfter(deadline)) {
            throw BusinessException.badRequest("RETURN_WINDOW_EXPIRED",
                    "The " + windowDays + "-day return window for order " + order.getOrderNumber() + " has expired");
        }

        ReturnRequest rr = ReturnRequest.builder()
                .returnNumber(ReferenceNumberGenerator.next("RET"))
                .order(order)
                .user(order.getUser())
                .status(ReturnRequestStatus.REQUESTED)
                .reasonCode(req.reasonCode().trim())
                .reasonDetails(req.reasonDetails())
                .build();

        for (ReturnItemInput in : req.items()) {
            OrderItem oi = orderItemRepository.findByIdAndOrderId(in.orderItemId(), order.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("OrderItem", in.orderItemId()));
            if (in.quantity() <= 0 || in.quantity() > oi.getReturnableQuantity()) {
                throw BusinessException.badRequest("INVALID_RETURN_QUANTITY",
                        "Return quantity for " + oi.getProductName() + " exceeds returnable units (" + oi.getReturnableQuantity() + ")");
            }
            rr.getItems().add(ReturnItem.builder()
                    .returnRequest(rr)
                    .orderItem(oi)
                    .quantity(in.quantity())
                    .conditionNote(in.conditionNote())
                    .build());
        }

        rr = returnRequestRepository.save(rr);
        orderService.transitionFromSystem(order, OrderStatus.RETURN_REQUESTED,
                "Customer submitted return request " + rr.getReturnNumber());
        notificationService.notifyReturnAndRefund(order.getUser(),
                "Return Request Received · " + rr.getReturnNumber(),
                "Return Request " + rr.getReturnNumber(),
                "We have received your return request for order " + order.getOrderNumber()
                        + ". Our concierge team will review it within 24 hours.",
                rr.getId());
        return ReturnRequestDto.from(rr);
    }

    @Transactional
    public ReturnRequestDto reviewReturnRequest(Long returnId, AdminDecisionRequest req) {
        ReturnRequest rr = returnRequestRepository.findById(returnId)
                .orElseThrow(() -> new ResourceNotFoundException("ReturnRequest", returnId));
        User reviewer = SecurityUtils.currentPrincipal()
                .map(TimeCraftPrincipal::getId)
                .flatMap(userRepository::findById)
                .orElse(null);

        Order order = rr.getOrder();
        ReturnRequestStatus next = req.status();
        if (next == ReturnRequestStatus.APPROVED) {
            if (rr.getStatus() != ReturnRequestStatus.REQUESTED) {
                throw BusinessException.badRequest("INVALID_RETURN_TRANSITION", "Only REQUESTED returns can be approved");
            }
            rr.setStatus(ReturnRequestStatus.APPROVED);
        } else if (next == ReturnRequestStatus.REJECTED) {
            if (rr.getStatus() != ReturnRequestStatus.REQUESTED) {
                throw BusinessException.badRequest("INVALID_RETURN_TRANSITION", "Only REQUESTED returns can be rejected");
            }
            rr.setStatus(ReturnRequestStatus.REJECTED);
            if (order.getStatus() == OrderStatus.RETURN_REQUESTED) {
                orderService.transitionFromSystem(order, OrderStatus.DELIVERED,
                        "Return request " + rr.getReturnNumber() + " rejected");
            }
        } else if (next == ReturnRequestStatus.RECEIVED) {
            if (rr.getStatus() != ReturnRequestStatus.APPROVED) {
                throw BusinessException.badRequest("INVALID_RETURN_TRANSITION", "Return must be APPROVED before marking RECEIVED");
            }
            rr.setStatus(ReturnRequestStatus.RECEIVED);
            for (ReturnItem ri : rr.getItems()) {
                OrderItem oi = ri.getOrderItem();
                oi.setReturnedQuantity(Math.min(oi.getQuantity(), oi.getReturnedQuantity() + ri.getQuantity()));
                orderItemRepository.save(oi);
                inventoryService.restoreVariantQuantity(oi.getVariant(), ri.getQuantity(),
                        "RETURN", rr.getId(), "Returned from order " + order.getOrderNumber());
            }
            if (order.getStatus() == OrderStatus.RETURN_REQUESTED) {
                orderService.transitionFromSystem(order, OrderStatus.RETURNED,
                        "Returned items inspected and received (" + rr.getReturnNumber() + ")");
            }
        } else {
            throw BusinessException.badRequest("UNSUPPORTED_RETURN_STATUS",
                    "Use APPROVED, REJECTED or RECEIVED when reviewing a return request");
        }

        rr.setAdminNote(req.adminNote());
        rr.setReviewedBy(reviewer);
        rr.setReviewedAt(Instant.now());
        rr = returnRequestRepository.save(rr);

        auditLogService.logCurrentActor("RETURN_REQUEST_" + next.name(), "RETURN_REQUEST", rr.getId(),
                "Return " + rr.getReturnNumber() + " status=" + next);
        notificationService.notifyReturnAndRefund(rr.getUser(),
                "Return Request " + next.name() + " · " + rr.getReturnNumber(),
                "Return " + rr.getReturnNumber() + " " + next.name(),
                "Your return request for order " + order.getOrderNumber() + " is now " + next.name()
                        + (req.adminNote() != null ? " (" + req.adminNote() + ")" : "."),
                rr.getId());
        return ReturnRequestDto.from(rr);
    }

    @Transactional
    public RefundDto initiateRefund(InitiateRefundRequest req) {
        Order order = orderService.requireOrderEntity(req.orderId());
        ReturnRequest returnRequest = null;
        if (req.returnRequestId() != null) {
            returnRequest = returnRequestRepository.findById(req.returnRequestId())
                    .orElseThrow(() -> new ResourceNotFoundException("ReturnRequest", req.returnRequestId()));
            if (!returnRequest.getOrder().getId().equals(order.getId())) {
                throw BusinessException.badRequest("RETURN_ORDER_MISMATCH",
                        "Return request does not belong to order " + order.getOrderNumber());
            }
            if (returnRequest.getStatus() != ReturnRequestStatus.RECEIVED
                    && returnRequest.getStatus() != ReturnRequestStatus.APPROVED) {
                throw BusinessException.badRequest("RETURN_NOT_READY_FOR_REFUND",
                        "Return request must be APPROVED or RECEIVED before issuing a refund");
            }
        } else if (order.getStatus() != OrderStatus.CANCELLED && order.getStatus() != OrderStatus.RETURNED) {
            throw BusinessException.badRequest("ORDER_NOT_ELIGIBLE_FOR_REFUND",
                    "Order must be CANCELLED or RETURNED before a refund can be processed");
        }

        Payment payment = paymentService.requireCapturedPaymentForOrder(order.getId());
        BigDecimal refundAmount = req.amount() != null
                ? Money.of(req.amount())
                : (returnRequest != null ? calculateReturnItemsAmount(returnRequest) : payment.getRefundableAmount());
        refundAmount = Money.min(refundAmount, payment.getRefundableAmount());
        if (refundAmount.signum() <= 0) {
            throw BusinessException.badRequest("ZERO_REFUND_AMOUNT", "No refundable amount remaining on this payment");
        }

        User initiator = SecurityUtils.currentPrincipal()
                .map(TimeCraftPrincipal::getId)
                .flatMap(userRepository::findById)
                .orElse(null);

        String refundNumber = ReferenceNumberGenerator.next("RFD");
        Refund refund = Refund.builder()
                .refundNumber(refundNumber)
                .order(order)
                .payment(payment)
                .returnRequest(returnRequest)
                .amount(refundAmount)
                .reason(req.reason().trim())
                .status(RefundStatus.PENDING)
                .initiatedBy(initiator)
                .build();
        refund = refundRepository.save(refund);

        String providerRefundId = paymentService.executeRefund(payment, refundAmount, refundNumber);
        refund.setProviderRefundId(providerRefundId);
        refund.setStatus(RefundStatus.PROCESSED);
        refund.setProcessedAt(Instant.now());
        refund = refundRepository.save(refund);

        boolean fullRefund = payment.getRefundableAmount().signum() == 0;
        order.setPaymentStatus(fullRefund ? PaymentStatus.REFUNDED : PaymentStatus.PARTIALLY_REFUNDED);
        orderRepository.save(order);
        if (fullRefund && order.getStatus().canTransitionTo(OrderStatus.REFUNDED)) {
            orderService.transitionFromSystem(order, OrderStatus.REFUNDED,
                    "Refund " + refundNumber + " processed (" + providerRefundId + ")");
        }
        if (returnRequest != null) {
            returnRequest.setStatus(ReturnRequestStatus.REFUNDED);
            returnRequestRepository.save(returnRequest);
        }

        auditLogService.logCurrentActor("REFUND_PROCESSED", "REFUND", refund.getId(),
                "Refund " + refundNumber + " amount=" + refundAmount + " for order " + order.getOrderNumber());
        notificationService.notifyReturnAndRefund(order.getUser(),
                "Refund Processed · " + refundNumber,
                "Refund of ₹" + refundAmount + " Processed",
                "We have processed your refund of ₹" + refundAmount + " for order " + order.getOrderNumber()
                        + " (Reference: " + providerRefundId + ").",
                refund.getId());
        return RefundDto.from(refund);
    }

    @Transactional(readOnly = true)
    public List<ReturnRequestDto> listCustomerReturns(Long userId) {
        return returnRequestRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(ReturnRequestDto::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<ReturnRequestDto> listAllReturnsAdmin(ReturnRequestStatus status, Pageable pageable) {
        var page = status != null
                ? returnRequestRepository.findByStatusOrderByCreatedAtDesc(status, pageable)
                : returnRequestRepository.findAllByOrderByCreatedAtDesc(pageable);
        return PageResponse.of(page, ReturnRequestDto::from);
    }

    @Transactional(readOnly = true)
    public PageResponse<RefundDto> listAllRefundsAdmin(Pageable pageable) {
        return PageResponse.of(refundRepository.findAllByOrderByCreatedAtDesc(pageable), RefundDto::from);
    }

    private static BigDecimal calculateReturnItemsAmount(ReturnRequest rr) {
        BigDecimal sum = Money.ZERO;
        for (ReturnItem item : rr.getItems()) {
            OrderItem oi = item.getOrderItem();
            BigDecimal perUnit = oi.getLineTotal()
                    .divide(BigDecimal.valueOf(oi.getQuantity()), 2, RoundingMode.HALF_UP);
            sum = sum.add(perUnit.multiply(BigDecimal.valueOf(item.getQuantity())));
        }
        return Money.of(sum);
    }
}
