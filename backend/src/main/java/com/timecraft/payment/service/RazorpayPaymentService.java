package com.timecraft.payment.service;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.razorpay.RazorpayClient;
import com.razorpay.Utils;
import org.json.JSONObject;
import java.math.RoundingMode;
import com.timecraft.common.dto.PageResponse;
import com.timecraft.common.util.Money;
import com.timecraft.common.util.TokenUtils;
import com.timecraft.config.AppProperties;
import com.timecraft.exception.BusinessException;
import com.timecraft.exception.ResourceNotFoundException;
import com.timecraft.order.dto.OrderDtos.OrderDetailDto;
import com.timecraft.order.entity.Order;
import com.timecraft.order.entity.OrderStatus;
import com.timecraft.order.entity.PaymentMethod;
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
    private final PaymentAttemptHelper attemptHelper;
    private final AppProperties properties;
    private final ObjectMapper objectMapper;
    private volatile HttpClient httpClient;

    private HttpClient httpClient() {
        HttpClient c = this.httpClient;
        if (c == null) {
            c = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
            this.httpClient = c;
        }
        return c;
    }

    private static final String SANDBOX_KEY_ID = "rzp_test_sandbox_timecraft";
    private static final String SANDBOX_KEY_SECRET = "rzp_test_sandbox_secret_key_timecraft_2026";

    public record CreatePaymentRequest(
            @JsonProperty("orderId") @NotNull Long orderId,
            @JsonProperty("retry") Boolean retry
    ) {
        @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
        public CreatePaymentRequest {
        }
    }

    public record SandboxAuthorizeRequest(
            @JsonProperty("orderId") @NotNull Long orderId,
            @JsonProperty("razorpayOrderId") @NotBlank String razorpayOrderId,
            @JsonProperty("paymentMethod") String paymentMethod
    ) {
        @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
        public SandboxAuthorizeRequest {
        }
    }

    public record sandboxSignatureResponse(
            String razorpayOrderId,
            String razorpayPaymentId,
            String razorpaySignature,
            String paymentMethod
    ) {
    }

    public record VerifyPaymentRequest(
            @JsonProperty("orderId") Long orderId,
            @JsonProperty("razorpayOrderId") @NotBlank String razorpayOrderId,
            @JsonProperty("razorpayPaymentId") @NotBlank String razorpayPaymentId,
            @JsonProperty("razorpaySignature") @NotBlank String razorpaySignature,
            @JsonProperty("paymentMethod") String paymentMethod
    ) {
        @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
        public VerifyPaymentRequest {
        }
    }

    public record PaymentFailureReport(
            @JsonProperty("orderId") @NotNull Long orderId,
            @JsonProperty("razorpayOrderId") String razorpayOrderId,
            @JsonProperty("razorpayPaymentId") String razorpayPaymentId,
            @JsonProperty("reason") String reason,
            @JsonProperty("cancelled") boolean cancelled
    ) {
        @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
        public PaymentFailureReport {
        }
    }

    public record PaymentOrderResponse(
            Long paymentId,
            Long orderId,
            Long internalOrderId,
            String orderNumber,
            String keyId,
            String razorpayKeyId,
            String razorpayOrderId,
            long amountInPaise,
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
        Order order = attemptHelper.loadPayableCustomerOrder(userId, req.orderId(), PaymentMethod.RAZORPAY);
        java.util.Optional<Payment> existingCreated = attemptHelper
                .reconcileOpenAttempts(order.getId(), "RAZORPAY", Boolean.TRUE.equals(req.retry()));
        var cfg = properties.razorpay();
        boolean useSandbox = !cfg.configured() && cfg.testSandboxEnabled();

        long amountPaise = toPaiseExact(order.getGrandTotal());
        String keyId = useSandbox ? SANDBOX_KEY_ID : cfg.keyId().trim();
        String sanitizedPhone = sanitizeIndianPhone(order.getShipPhone());

        if (existingCreated.isPresent() && (cfg.configured() || useSandbox)) {
            Payment p = existingCreated.get();
            return new PaymentOrderResponse(
                    p.getId(),
                    order.getId(),
                    order.getId(),
                    order.getOrderNumber(),
                    keyId,
                    keyId,
                    p.getProviderOrderId(),
                    amountPaise,
                    amountPaise,
                    order.getGrandTotal(),
                    "INR",
                    order.getCustomerName(),
                    order.getCustomerEmail(),
                    sanitizedPhone,
                    useSandbox
            );
        }

        if (!cfg.configured() && !useSandbox) {
            throw BusinessException.serviceUnavailable("RAZORPAY_NOT_CONFIGURED",
                    "Razorpay credentials (RAZORPAY_KEY_ID and RAZORPAY_KEY_SECRET) are not configured. "
                            + "Set your Razorpay Test Mode keys in the environment as documented in README.md.");
        }

        String providerOrderId = useSandbox
                ? "order_test_" + TokenUtils.randomAlphanumeric(14)
                : callRazorpayCreateOrder(order, amountPaise);

        Payment payment = Payment.builder()
                .order(order)
                .provider("RAZORPAY")
                .providerOrderId(providerOrderId)
                .amount(order.getGrandTotal())
                .currency("INR")
                .status(PaymentAttemptStatus.CREATED)
                .build();
        payment = paymentRepository.save(payment);
        recordTx(payment, "ORDER_CREATED", order.getGrandTotal(), "CREATED", providerOrderId,
                (useSandbox ? "[TEST SANDBOX] " : "") + "Razorpay order created for " + order.getOrderNumber());

        return new PaymentOrderResponse(
                payment.getId(),
                order.getId(),
                order.getId(),
                order.getOrderNumber(),
                keyId,
                keyId,
                providerOrderId,
                amountPaise,
                amountPaise,
                order.getGrandTotal(),
                "INR",
                order.getCustomerName(),
                order.getCustomerEmail(),
                sanitizedPhone,
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
        String rzpOrderId = req.razorpayOrderId().trim();
        String rzpPaymentId = req.razorpayPaymentId().trim();
        String rzpSignature = req.razorpaySignature().trim();

        Payment payment = paymentRepository.findByProviderOrderId(rzpOrderId)
                .orElseThrow(() -> {
                    log.warn("Payment verification failed: unknown razorpayOrderId={} for userId={}", rzpOrderId, userId);
                    return new ResourceNotFoundException("Payment order", rzpOrderId);
                });

        Order order = req.orderId() != null
                ? orderService.findCustomerOrderEntity(userId, String.valueOf(req.orderId()))
                : orderService.findCustomerOrderEntity(userId, String.valueOf(payment.getOrder().getId()));

        if (!payment.getOrder().getId().equals(order.getId())) {
            log.warn("Payment verification ownership mismatch: orderId={}, paymentOrderId={}, userId={}",
                    order.getId(), payment.getOrder().getId(), userId);
            throw BusinessException.forbidden("Payment order does not belong to this order");
        }
        if (payment.getStatus() == PaymentAttemptStatus.CAPTURED && order.getPaymentStatus() == PaymentStatus.PAID) {
            return OrderDetailDto.from(order); // idempotent duplicate verification call
        }

        String secret = requireKeySecret().trim();
        boolean signatureValid;
        try {
            JSONObject attributes = new JSONObject();
            attributes.put("razorpay_order_id", rzpOrderId);
            attributes.put("razorpay_payment_id", rzpPaymentId);
            attributes.put("razorpay_signature", rzpSignature);
            signatureValid = Utils.verifyPaymentSignature(attributes, secret);
        } catch (Exception ex) {
            log.warn("Razorpay SDK signature verification threw exception for order={} rzpOrderId={}: {}",
                    order.getOrderNumber(), rzpOrderId, ex.getMessage());
            signatureValid = false;
        }

        if (!signatureValid) {
            log.warn("Razorpay signature mismatch for order={} (orderId={}, rzpOrderId={}, rzpPaymentId={})",
                    order.getOrderNumber(), order.getId(), rzpOrderId, rzpPaymentId);
            payment.setStatus(PaymentAttemptStatus.FAILED);
            payment.setFailureReason("Signature verification failed");
            paymentRepository.save(payment);
            recordTx(payment, "SIGNATURE_MISMATCH", payment.getAmount(), "FAILED", rzpPaymentId,
                    "Checkout HMAC signature mismatch");
            throw BusinessException.badRequest("INVALID_PAYMENT_SIGNATURE",
                    "Payment signature verification failed. Please verify RAZORPAY_KEY_SECRET matches your Razorpay dashboard.");
        }

        payment.setProviderPaymentId(rzpPaymentId);
        payment.setMethod(req.paymentMethod() != null ? req.paymentMethod() : "RAZORPAY");
        payment.setStatus(PaymentAttemptStatus.CAPTURED);
        payment.setPaidAt(Instant.now());
        paymentRepository.save(payment);
        recordTx(payment, "PAYMENT_CAPTURED", payment.getAmount(), "CAPTURED",
                rzpPaymentId, "Verified checkout signature via Razorpay SDK");

        Order confirmed = orderService.confirmOrderAfterVerifiedPayment(order.getId(), rzpPaymentId);
        return OrderDetailDto.from(confirmed);
    }

    @Transactional
    public OrderDetailDto reportFailureOrCancellation(Long userId, PaymentFailureReport req) {
        Order order = orderService.findCustomerOrderEntity(userId, String.valueOf(req.orderId()));
        String cleanReason = (req.reason() != null && !req.reason().isBlank())
                ? req.reason().trim()
                : (req.cancelled() ? "Payment modal was closed before completion" : "Payment attempt was not completed");
        if (req.razorpayOrderId() != null && !req.razorpayOrderId().isBlank()) {
            paymentRepository.findByProviderOrderId(req.razorpayOrderId().trim()).ifPresent(p -> {
                if (p.getOrder().getId().equals(order.getId()) && p.getStatus() == PaymentAttemptStatus.CREATED) {
                    if (req.razorpayPaymentId() != null && !req.razorpayPaymentId().isBlank()) {
                        String pid = req.razorpayPaymentId().trim();
                        if (paymentRepository.findByProviderPaymentId(pid).isEmpty()) {
                            p.setProviderPaymentId(pid);
                        }
                    }
                    p.setStatus(req.cancelled() ? PaymentAttemptStatus.CANCELLED : PaymentAttemptStatus.FAILED);
                    p.setFailureReason(cleanReason);
                    paymentRepository.save(p);
                    recordTx(p, req.cancelled() ? "PAYMENT_CANCELLED" : "PAYMENT_FAILED",
                            p.getAmount(), p.getStatus().name(), req.razorpayPaymentId(), cleanReason);
                }
            });
        }
        return orderService.recordPaymentAttemptIncomplete(order, cleanReason, req.cancelled(), userId);
    }

    @Transactional
    public OrderDetailDto switchOrderToCod(Long userId, String idOrNumber) {
        OrderDetailDto updated = orderService.switchPendingOrderToCod(userId, idOrNumber);
        List<Payment> attempts = paymentRepository.findByOrderIdOrderByCreatedAtDesc(updated.id());
        for (Payment p : attempts) {
            if (p.getStatus() == PaymentAttemptStatus.CREATED) {
                p.setStatus(PaymentAttemptStatus.CANCELLED);
                p.setFailureReason("Customer switched order to Cash on Delivery");
                paymentRepository.save(p);
                recordTx(p, "SWITCHED_TO_COD", p.getAmount(), "CANCELLED",
                        p.getProviderOrderId(), "Order switched from online payment to Cash on Delivery");
            }
        }
        return updated;
    }

    @Transactional
    public PaymentDto recordCodPaymentCollected(Long orderId, String note) {
        Order order = orderService.markCodPaymentCollectedByAdmin(orderId, note);
        String ref = "cod_" + order.getOrderNumber();
        Payment payment = paymentRepository.findByProviderOrderId(ref).orElseGet(() -> Payment.builder()
                .order(order)
                .provider("COD")
                .providerOrderId(ref)
                .providerPaymentId("cod_pay_" + order.getOrderNumber())
                .amount(order.getGrandTotal())
                .currency(order.getCurrency())
                .method("COD")
                .status(PaymentAttemptStatus.CAPTURED)
                .paidAt(Instant.now())
                .build());
        payment.setStatus(PaymentAttemptStatus.CAPTURED);
        if (payment.getPaidAt() == null) {
            payment.setPaidAt(Instant.now());
        }
        payment = paymentRepository.save(payment);
        recordTx(payment, "COD_COLLECTED", order.getGrandTotal(), "CAPTURED",
                payment.getProviderPaymentId(),
                note != null && !note.isBlank() ? note : "Cash on Delivery collected and verified by administrator");
        return PaymentDto.from(payment);
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
            RazorpayClient client = new RazorpayClient(cfg.keyId().trim(), cfg.keySecret().trim());
            JSONObject orderRequest = new JSONObject();
            orderRequest.put("amount", amountPaise);
            orderRequest.put("currency", "INR");
            orderRequest.put("receipt", order.getOrderNumber());
            orderRequest.put("payment_capture", 1);
            JSONObject notes = new JSONObject();
            notes.put("orderNumber", order.getOrderNumber());
            notes.put("customerEmail", order.getCustomerEmail());
            orderRequest.put("notes", notes);

            com.razorpay.Order razorpayOrder = client.orders.create(orderRequest);
            String rzpOrderId = razorpayOrder.get("id");
            if (rzpOrderId == null || rzpOrderId.isBlank()) {
                throw BusinessException.serviceUnavailable("RAZORPAY_API_ERROR", "Razorpay did not return an order id");
            }
            return rzpOrderId;
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Razorpay order creation failed for order={}: {}", order.getOrderNumber(), ex.getMessage());
            throw BusinessException.serviceUnavailable("RAZORPAY_UNAVAILABLE",
                    "Could not create Razorpay order: " + ex.getMessage());
        }
    }

    public static long toPaiseExact(BigDecimal inrAmount) {
        return inrAmount.multiply(BigDecimal.valueOf(100))
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact();
    }

    public static String sanitizeIndianPhone(String rawPhone) {
        if (rawPhone == null || rawPhone.isBlank()) {
            return "9876543210";
        }
        String digits = rawPhone.replaceAll("\\D", "");
        if (digits.length() > 10 && digits.startsWith("91")) {
            digits = digits.substring(digits.length() - 10);
        } else if (digits.length() > 10 && digits.startsWith("0")) {
            digits = digits.substring(digits.length() - 10);
        }
        if (digits.length() == 10 && digits.matches("^[6-9]\\d{9}$")) {
            return digits;
        }
        return "9876543210";
    }

    public String callRazorpayRefund(Payment payment, BigDecimal amount, String refundReference) {
        if ("PAYU".equalsIgnoreCase(payment.getProvider())) {
            return "payu_rfnd_" + TokenUtils.randomAlphanumeric(12);
        }
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
            HttpResponse<String> response = httpClient().send(request, HttpResponse.BodyHandlers.ofString());
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
        return secret.trim();
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
