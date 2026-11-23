package com.timecraft.payment.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.timecraft.common.dto.PageResponse;
import com.timecraft.common.util.Money;
import com.timecraft.common.util.TokenUtils;
import com.timecraft.config.AppProperties;
import com.timecraft.exception.BusinessException;
import com.timecraft.exception.ResourceNotFoundException;
import com.timecraft.order.dto.OrderDtos.OrderDetailDto;
import com.timecraft.order.entity.Order;
import com.timecraft.order.entity.OrderStatus;
import com.timecraft.order.entity.PaymentStatus;
import com.timecraft.order.service.OrderService;
import com.timecraft.payment.entity.Payment;
import com.timecraft.payment.entity.PaymentAttemptStatus;
import com.timecraft.payment.entity.PaymentTransaction;
import com.timecraft.payment.entity.PaymentWebhookEvent;
import com.timecraft.payment.repository.PaymentRepository;
import com.timecraft.payment.repository.PaymentTransactionRepository;
import com.timecraft.payment.repository.PaymentWebhookEventRepository;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/**
 * Razorpay payment order creation, HMAC-SHA256 checkout signature verification,
 * idempotent webhook processing, payment transaction history and refund execution.
 * Secret keys never leave the backend.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RazorpayPaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentTransactionRepository transactionRepository;
    private final PaymentWebhookEventRepository webhookEventRepository;
    private final OrderService orderService;
    private final AppProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private static final String SANDBOX_KEY_ID = "rzp_test_sandbox_timecraft";
    private static final String SANDBOX_KEY_SECRET = "rzp_test_sandbox_secret_key_timecraft_2026";

    public record CreatePaymentRequest(@NotNull Long orderId) {
    }

    public record SandboxAuthorizeRequest(
            @NotNull Long orderId,
            @NotBlank String razorpayOrderId,
            String paymentMethod
    ) {
    }

    public record sandboxSignatureResponse(
            String razorpayOrderId,
            String razorpayPaymentId,
            String razorpaySignature,
            String paymentMethod
    ) {
    }

    public record VerifyPaymentRequest(
            @NotNull Long orderId,
            @NotBlank String razorpayOrderId,
            @NotBlank String razorpayPaymentId,
            @NotBlank String razorpaySignature,
            String paymentMethod
    ) {
    }

    public record PaymentFailureReport(
            @NotNull Long orderId,
            String razorpayOrderId,
            String razorpayPaymentId,
            String reason,
            boolean cancelled
    ) {
    }

    public record PaymentOrderResponse(
            Long paymentId,
            Long orderId,
            String orderNumber,
            String razorpayKeyId,
            String razorpayOrderId,
            long amountInMinorUnits,
            BigDecimal amount,
            String currency,
            String customerName,
            String customerEmail,
            String customerPhone,
            boolean sandboxMode
    ) {
    }

    public record PaymentDto(
            Long id,
            Long orderId,
            String orderNumber,
            String provider,
            String providerOrderId,
            String providerPaymentId,
            BigDecimal amount,
            String currency,
            PaymentAttemptStatus status,
            String method,
            String failureReason,
            BigDecimal refundedAmount,
            Instant paidAt,
            Instant createdAt
    ) {
        public static PaymentDto from(Payment p) {
            return new PaymentDto(
                    p.getId(),
                    p.getOrder().getId(),
                    p.getOrder().getOrderNumber(),
                    p.getProvider(),
                    p.getProviderOrderId(),
                    p.getProviderPaymentId(),
                    p.getAmount(),
                    p.getCurrency(),
                    p.getStatus(),
                    p.getMethod(),
                    p.getFailureReason(),
                    p.getRefundedAmount(),
                    p.getPaidAt(),
                    p.getCreatedAt()
            );
        }
    }

    @Transactional
    public PaymentOrderResponse createRazorpayOrder(Long userId, CreatePaymentRequest req) {
        Order order = orderService.findCustomerOrderEntity(userId, String.valueOf(req.orderId()));
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

        // Reuse existing CREATED payment attempt for idempotency
        var existingCreated = paymentRepository.findFirstByOrderIdAndStatusInOrderByCreatedAtDesc(
                order.getId(), List.of(PaymentAttemptStatus.CREATED));
        var cfg = properties.razorpay();
        boolean useSandbox = !cfg.configured() && cfg.testSandboxEnabled();

        if (existingCreated.isPresent() && (cfg.configured() || useSandbox)) {
            Payment p = existingCreated.get();
            return new PaymentOrderResponse(
                    p.getId(),
                    order.getId(),
                    order.getOrderNumber(),
                    useSandbox ? SANDBOX_KEY_ID : cfg.keyId(),
                    p.getProviderOrderId(),
                    Money.toMinorUnits(order.getGrandTotal()),
                    order.getGrandTotal(),
                    order.getCurrency(),
                    order.getCustomerName(),
                    order.getCustomerEmail(),
                    order.getShipPhone(),
                    useSandbox
            );
        }

        if (!cfg.configured() && !useSandbox) {
            throw BusinessException.serviceUnavailable("RAZORPAY_NOT_CONFIGURED",
                    "Razorpay credentials (RAZORPAY_KEY_ID and RAZORPAY_KEY_SECRET) are not configured. "
                            + "Set your Razorpay Test Mode keys in the environment as documented in README.md.");
        }

        long amountPaise = Money.toMinorUnits(order.getGrandTotal());
        String providerOrderId = useSandbox
                ? "order_test_" + TokenUtils.randomAlphanumeric(14)
                : callRazorpayCreateOrder(order, amountPaise);

        Payment payment = Payment.builder()
                .order(order)
                .provider("RAZORPAY")
                .providerOrderId(providerOrderId)
                .amount(order.getGrandTotal())
                .currency(order.getCurrency())
                .status(PaymentAttemptStatus.CREATED)
                .build();
        payment = paymentRepository.save(payment);
        recordTx(payment, "ORDER_CREATED", order.getGrandTotal(), "CREATED", providerOrderId,
                (useSandbox ? "[TEST SANDBOX] " : "") + "Razorpay order created for " + order.getOrderNumber());

        return new PaymentOrderResponse(
                payment.getId(),
                order.getId(),
                order.getOrderNumber(),
                useSandbox ? SANDBOX_KEY_ID : cfg.keyId(),
                providerOrderId,
                amountPaise,
                order.getGrandTotal(),
                order.getCurrency(),
                order.getCustomerName(),
                order.getCustomerEmail(),
                order.getShipPhone(),
                useSandbox
        );
    }

    /**
     * Generates a cryptographically signed test payment payload when running in local dev sandbox mode
     * (only enabled when live RAZORPAY_KEY_ID / RAZORPAY_KEY_SECRET are not configured and testSandboxEnabled=true).
     * The resulting payload still goes through the exact same HMAC-SHA256 verifyCheckoutPayment() verification.
     */
    @Transactional(readOnly = true)
    public sandboxSignatureResponse authorizeSandboxPayment(Long userId, SandboxAuthorizeRequest req) {
        var cfg = properties.razorpay();
        if (cfg.configured() || !cfg.testSandboxEnabled()) {
            throw BusinessException.forbidden("Razorpay dev sandbox is disabled when live/test API keys are configured");
        }
        Order order = orderService.findCustomerOrderEntity(userId, String.valueOf(req.orderId()));
        Payment payment = paymentRepository.findByProviderOrderId(req.razorpayOrderId().trim())
                .orElseThrow(() -> new ResourceNotFoundException("Payment order", req.razorpayOrderId()));
        if (!payment.getOrder().getId().equals(order.getId())) {
            throw BusinessException.forbidden("Payment order does not belong to this order");
        }
        String paymentId = "pay_test_" + TokenUtils.randomAlphanumeric(14);
        String signature = hmacSha256Hex(payment.getProviderOrderId() + "|" + paymentId, SANDBOX_KEY_SECRET);
        String method = req.paymentMethod() != null && !req.paymentMethod().isBlank() ? req.paymentMethod() : "UPI";
        return new sandboxSignatureResponse(payment.getProviderOrderId(), paymentId, signature, method);
    }

    @Transactional
    public OrderDetailDto verifyCheckoutPayment(Long userId, VerifyPaymentRequest req) {
        Order order = orderService.findCustomerOrderEntity(userId, String.valueOf(req.orderId()));
        Payment payment = paymentRepository.findByProviderOrderId(req.razorpayOrderId().trim())
                .orElseThrow(() -> new ResourceNotFoundException("Payment order", req.razorpayOrderId()));
        if (!payment.getOrder().getId().equals(order.getId())) {
            throw BusinessException.forbidden("Payment order does not belong to this order");
        }
        if (payment.getStatus() == PaymentAttemptStatus.CAPTURED && order.getPaymentStatus() == PaymentStatus.PAID) {
            return OrderDetailDto.from(order); // idempotent duplicate verification call
        }

        String secret = requireKeySecret();
        String expectedSignature = hmacSha256Hex(
                req.razorpayOrderId().trim() + "|" + req.razorpayPaymentId().trim(),
                secret
        );
        if (!TokenUtils.constantTimeEquals(expectedSignature, req.razorpaySignature().trim())) {
            payment.setStatus(PaymentAttemptStatus.FAILED);
            payment.setFailureReason("Signature verification failed");
            paymentRepository.save(payment);
            recordTx(payment, "SIGNATURE_MISMATCH", payment.getAmount(), "FAILED", req.razorpayPaymentId(),
                    "Checkout HMAC signature mismatch");
            throw BusinessException.badRequest("INVALID_PAYMENT_SIGNATURE",
                    "Payment signature verification failed");
        }

        payment.setProviderPaymentId(req.razorpayPaymentId().trim());
        payment.setMethod(req.paymentMethod() != null ? req.paymentMethod() : "RAZORPAY");
        payment.setStatus(PaymentAttemptStatus.CAPTURED);
        payment.setPaidAt(Instant.now());
        paymentRepository.save(payment);
        recordTx(payment, "PAYMENT_CAPTURED", payment.getAmount(), "CAPTURED",
                req.razorpayPaymentId(), "Verified checkout signature");

        Order confirmed = orderService.confirmOrderAfterVerifiedPayment(order.getId(), req.razorpayPaymentId());
        return OrderDetailDto.from(confirmed);
    }

    @Transactional
    public OrderDetailDto reportFailureOrCancellation(Long userId, PaymentFailureReport req) {
        Order order = orderService.findCustomerOrderEntity(userId, String.valueOf(req.orderId()));
        if (req.razorpayOrderId() != null && !req.razorpayOrderId().isBlank()) {
            paymentRepository.findByProviderOrderId(req.razorpayOrderId().trim()).ifPresent(p -> {
                if (p.getStatus() == PaymentAttemptStatus.CREATED) {
                    p.setStatus(req.cancelled() ? PaymentAttemptStatus.CANCELLED : PaymentAttemptStatus.FAILED);
                    p.setFailureReason(req.reason());
                    paymentRepository.save(p);
                    recordTx(p, req.cancelled() ? "PAYMENT_CANCELLED" : "PAYMENT_FAILED",
                            p.getAmount(), p.getStatus().name(), req.razorpayPaymentId(), req.reason());
                }
            });
        }
        orderService.handlePaymentFailedOrExpired(order,
                req.reason() != null ? req.reason() : "Payment attempt was not completed", false);
        return OrderDetailDto.from(order);
    }

    /**
     * Verifies X-Razorpay-Signature and processes webhook events idempotently using X-Razorpay-Event-Id.
     */
    @Transactional
    public void processWebhook(String rawBody, String signatureHeader, String eventIdHeader) {
        String webhookSecret = properties.razorpay().webhookSecret();
        if (webhookSecret == null || webhookSecret.isBlank()) {
            throw BusinessException.serviceUnavailable("WEBHOOK_SECRET_NOT_CONFIGURED",
                    "RAZORPAY_WEBHOOK_SECRET is not configured");
        }
        if (signatureHeader == null || signatureHeader.isBlank()) {
            throw BusinessException.badRequest("MISSING_WEBHOOK_SIGNATURE", "Missing X-Razorpay-Signature header");
        }
        String expected = hmacSha256Hex(rawBody, webhookSecret);
        if (!TokenUtils.constantTimeEquals(expected, signatureHeader.trim())) {
            throw BusinessException.badRequest("INVALID_WEBHOOK_SIGNATURE", "Webhook signature verification failed");
        }

        try {
            JsonNode root = objectMapper.readTree(rawBody);
            String eventType = root.path("event").asText("unknown");
            String eventId = (eventIdHeader != null && !eventIdHeader.isBlank())
                    ? eventIdHeader.trim()
                    : TokenUtils.sha256(rawBody);

            if (webhookEventRepository.existsByEventId(eventId)) {
                log.info("Duplicate Razorpay webhook event {} ignored (idempotent)", eventId);
                return;
            }

            PaymentWebhookEvent eventRecord = PaymentWebhookEvent.builder()
                    .eventId(eventId)
                    .eventType(eventType)
                    .payload(rawBody)
                    .processingStatus("RECEIVED")
                    .receivedAt(Instant.now())
                    .build();
            eventRecord = webhookEventRepository.save(eventRecord);

            JsonNode paymentEntity = root.path("payload").path("payment").path("entity");
            String providerOrderId = paymentEntity.path("order_id").asText(null);
            String providerPaymentId = paymentEntity.path("id").asText(null);
            String method = paymentEntity.path("method").asText("RAZORPAY");

            if ("payment.captured".equals(eventType) || "order.paid".equals(eventType)) {
                if (providerOrderId != null) {
                    paymentRepository.findByProviderOrderId(providerOrderId).ifPresent(p -> {
                        if (p.getStatus() != PaymentAttemptStatus.CAPTURED) {
                            p.setProviderPaymentId(providerPaymentId);
                            p.setMethod(method);
                            p.setStatus(PaymentAttemptStatus.CAPTURED);
                            p.setPaidAt(Instant.now());
                            paymentRepository.save(p);
                            recordTx(p, "WEBHOOK_CAPTURED", p.getAmount(), "CAPTURED", providerPaymentId,
                                    "Confirmed via webhook " + eventType);
                        }
                        orderService.confirmOrderAfterVerifiedPayment(p.getOrder().getId(),
                                providerPaymentId != null ? providerPaymentId : providerOrderId);
                    });
                }
            } else if ("payment.failed".equals(eventType)) {
                String errorDesc = paymentEntity.path("error_description").asText("Payment failed at gateway");
                if (providerOrderId != null) {
                    paymentRepository.findByProviderOrderId(providerOrderId).ifPresent(p -> {
                        if (p.getStatus() != PaymentAttemptStatus.CAPTURED) {
                            p.setStatus(PaymentAttemptStatus.FAILED);
                            p.setFailureReason(errorDesc);
                            paymentRepository.save(p);
                            recordTx(p, "WEBHOOK_FAILED", p.getAmount(), "FAILED", providerPaymentId, errorDesc);
                            orderService.handlePaymentFailedOrExpired(p.getOrder(), errorDesc, false);
                        }
                    });
                }
            }

            eventRecord.setProcessingStatus("PROCESSED");
            eventRecord.setProcessedAt(Instant.now());
            webhookEventRepository.save(eventRecord);
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Failed to process Razorpay webhook", ex);
            throw BusinessException.badRequest("INVALID_WEBHOOK_PAYLOAD", "Malformed webhook payload");
        }
    }

    /**
     * Executes a full or partial refund against a verified payment.
     */
    @Transactional
    public String executeRefund(Payment payment, BigDecimal refundAmount, String refundReference) {
        if (payment.getStatus() != PaymentAttemptStatus.CAPTURED
                && payment.getStatus() != PaymentAttemptStatus.PARTIALLY_REFUNDED) {
            throw BusinessException.badRequest("PAYMENT_NOT_REFUNDABLE",
                    "Only captured payments can be refunded");
        }
        BigDecimal amount = Money.of(refundAmount);
        if (amount.signum() <= 0 || amount.compareTo(payment.getRefundableAmount()) > 0) {
            throw BusinessException.badRequest("INVALID_REFUND_AMOUNT",
                    "Refund amount must be between 0.01 and " + payment.getRefundableAmount());
        }

        String providerRefundId = callRazorpayRefund(payment, amount, refundReference);
        BigDecimal newRefundedTotal = Money.of(payment.getRefundedAmount().add(amount));
        payment.setRefundedAmount(newRefundedTotal);
        boolean fullyRefunded = newRefundedTotal.compareTo(payment.getAmount()) >= 0;
        payment.setStatus(fullyRefunded ? PaymentAttemptStatus.REFUNDED : PaymentAttemptStatus.PARTIALLY_REFUNDED);
        paymentRepository.save(payment);

        recordTx(payment, "REFUND", amount, "PROCESSED", providerRefundId,
                "Refund " + refundReference + " processed");
        return providerRefundId;
    }

    @Transactional(readOnly = true)
    public List<PaymentDto> listForOrder(Long orderId) {
        return paymentRepository.findByOrderIdOrderByCreatedAtDesc(orderId).stream()
                .map(PaymentDto::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<PaymentDto> listAllAdmin(Pageable pageable) {
        return PageResponse.of(paymentRepository.findAllByOrderByCreatedAtDesc(pageable), PaymentDto::from);
    }

    @Transactional(readOnly = true)
    public Payment requireCapturedPaymentForOrder(Long orderId) {
        return paymentRepository.findFirstByOrderIdAndStatusInOrderByCreatedAtDesc(
                        orderId, List.of(PaymentAttemptStatus.CAPTURED, PaymentAttemptStatus.PARTIALLY_REFUNDED))
                .orElseThrow(() -> BusinessException.badRequest("NO_CAPTURED_PAYMENT",
                        "Order does not have a captured payment eligible for refund"));
    }

    public static String hmacSha256Hex(String payload, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to compute HMAC-SHA256", ex);
        }
    }

    private String callRazorpayCreateOrder(Order order, long amountPaise) {
        var cfg = properties.razorpay();
        try {
            String body = objectMapper.writeValueAsString(Map.of(
                    "amount", amountPaise,
                    "currency", order.getCurrency(),
                    "receipt", order.getOrderNumber(),
                    "notes", Map.of("orderNumber", order.getOrderNumber(), "customerEmail", order.getCustomerEmail())
            ));
            String basicAuth = Base64.getEncoder().encodeToString(
                    (cfg.keyId() + ":" + cfg.keySecret()).getBytes(StandardCharsets.UTF_8));
            HttpRequest request = HttpRequest.newBuilder(URI.create(cfg.apiBaseUrl() + "/orders"))
                    .timeout(Duration.ofSeconds(15))
                    .header("Authorization", "Basic " + basicAuth)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode json = objectMapper.readTree(response.body());
            if (response.statusCode() >= 300 || !json.hasNonNull("id")) {
                String msg = json.path("error").path("description").asText("Razorpay order creation failed");
                throw BusinessException.serviceUnavailable("RAZORPAY_API_ERROR", msg);
            }
            return json.get("id").asText();
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Razorpay order creation error", ex);
            throw BusinessException.serviceUnavailable("RAZORPAY_UNAVAILABLE",
                    "Could not reach Razorpay API to create payment order");
        }
    }

    public String callRazorpayRefund(Payment payment, BigDecimal amount, String refundReference) {
        var cfg = properties.razorpay();
        if (!cfg.configured() && cfg.testSandboxEnabled()) {
            return "rfnd_test_" + TokenUtils.randomAlphanumeric(14);
        }
        if (!cfg.configured() || payment.getProviderPaymentId() == null) {
            throw BusinessException.serviceUnavailable("RAZORPAY_NOT_CONFIGURED",
                    "Razorpay credentials are required to process gateway refunds");
        }
        try {
            String body = objectMapper.writeValueAsString(Map.of(
                    "amount", Money.toMinorUnits(amount),
                    "speed", "normal",
                    "receipt", refundReference
            ));
            String basicAuth = Base64.getEncoder().encodeToString(
                    (cfg.keyId() + ":" + cfg.keySecret()).getBytes(StandardCharsets.UTF_8));
            URI uri = URI.create(cfg.apiBaseUrl() + "/payments/" + payment.getProviderPaymentId() + "/refund");
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(15))
                    .header("Authorization", "Basic " + basicAuth)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode json = objectMapper.readTree(response.body());
            if (response.statusCode() >= 300 || !json.hasNonNull("id")) {
                String msg = json.path("error").path("description").asText("Razorpay refund failed");
                throw BusinessException.serviceUnavailable("RAZORPAY_REFUND_ERROR", msg);
            }
            return json.get("id").asText();
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Razorpay refund error", ex);
            throw BusinessException.serviceUnavailable("RAZORPAY_REFUND_UNAVAILABLE",
                    "Could not reach Razorpay API to execute refund");
        }
    }

    private String requireKeySecret() {
        String secret = properties.razorpay().keySecret();
        if ((secret == null || secret.isBlank()) && properties.razorpay().testSandboxEnabled()) {
            return SANDBOX_KEY_SECRET;
        }
        if (secret == null || secret.isBlank()) {
            throw BusinessException.serviceUnavailable("RAZORPAY_NOT_CONFIGURED",
                    "Razorpay secret key is not configured");
        }
        return secret;
    }

    private void recordTx(Payment payment, String type, BigDecimal amount, String status,
                          String reference, String details) {
        PaymentTransaction tx = PaymentTransaction.builder()
                .payment(payment)
                .transactionType(type)
                .amount(amount)
                .status(status)
                .providerReference(reference)
                .details(details)
                .build();
        transactionRepository.save(tx);
    }
}
