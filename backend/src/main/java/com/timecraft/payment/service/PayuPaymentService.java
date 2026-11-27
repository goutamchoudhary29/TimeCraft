package com.timecraft.payment.service;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.timecraft.common.util.Money;
import com.timecraft.common.util.TokenUtils;
import com.timecraft.config.AppProperties;
import com.timecraft.config.PayuProperties;
import com.timecraft.exception.BusinessException;
import com.timecraft.exception.ResourceNotFoundException;
import com.timecraft.order.dto.OrderDtos.OrderDetailDto;
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
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/**
 * PayU Hosted Checkout payment initiation and server-side SHA-512 reverse-hash verification.
 * Merchant salt never leaves the backend, and all monetary totals use {@link BigDecimal}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PayuPaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentTransactionRepository transactionRepository;
    private final OrderRepository orderRepository;
    private final OrderService orderService;
    private final PayuProperties payuProperties;
    private final AppProperties appProperties;

    public record PayuInitiateRequest(
            @JsonProperty("orderId") @NotNull Long orderId,
            @JsonProperty("retry") Boolean retry
    ) {
        @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
        public PayuInitiateRequest {
        }
    }

    public record PayuInitiateResponse(
            Long paymentId,
            Long orderId,
            String orderNumber,
            String actionUrl,
            String key,
            String txnid,
            BigDecimal amount,
            String amountFormatted,
            String currency,
            String productinfo,
            String firstname,
            String email,
            String phone,
            String udf1,
            String udf2,
            String udf3,
            String udf4,
            String udf5,
            String surl,
            String furl,
            String hash
    ) {
    }

    public record PayuVerifyRequest(
            @JsonProperty("txnid") @NotBlank String txnid,
            @JsonProperty("status") @NotBlank String status,
            @JsonProperty("mihpayid") String mihpayid,
            @JsonProperty("mode") String mode,
            @JsonProperty("amount") @NotBlank String amount,
            @JsonProperty("productinfo") @NotBlank String productinfo,
            @JsonProperty("firstname") @NotBlank String firstname,
            @JsonProperty("email") @NotBlank String email,
            @JsonProperty("udf1") String udf1,
            @JsonProperty("udf2") String udf2,
            @JsonProperty("udf3") String udf3,
            @JsonProperty("udf4") String udf4,
            @JsonProperty("udf5") String udf5,
            @JsonProperty("additionalCharges") String additionalCharges,
            @JsonProperty("errorMessage") String errorMessage,
            @JsonProperty("hash") @NotBlank String hash
    ) {
        @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
        public PayuVerifyRequest {
        }
    }

    public record PayuCallbackOutcome(
            boolean success,
            String orderNumber,
            String reason
    ) {
    }

    @Transactional
    public PayuInitiateResponse initiatePayment(Long userId, PayuInitiateRequest req, String requestBaseUrl) {
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

        if (order.getPaymentMethod() != PaymentMethod.PAYU) {
            order.setPaymentMethod(PaymentMethod.PAYU);
            order = orderRepository.save(order);
        }

        // Cancel any open CREATED attempts from another gateway or when explicitly retrying
        List<Payment> existingAttempts = paymentRepository.findByOrderIdOrderByCreatedAtDesc(order.getId());
        Payment reusablePayuAttempt = null;
        for (Payment attempt : existingAttempts) {
            if (attempt.getStatus() == PaymentAttemptStatus.CREATED) {
                if (Boolean.TRUE.equals(req.retry()) || !"PAYU".equalsIgnoreCase(attempt.getProvider())) {
                    attempt.setStatus(PaymentAttemptStatus.CANCELLED);
                    attempt.setFailureReason("Superseded by PayU payment initiation");
                    paymentRepository.save(attempt);
                    recordTx(attempt, "PAYMENT_SUPERSEDED", attempt.getAmount(), "CANCELLED",
                            attempt.getProviderOrderId(), "Superseded by fresh PayU payment attempt");
                } else if (reusablePayuAttempt == null) {
                    reusablePayuAttempt = attempt;
                }
            }
        }

        BigDecimal exactAmount = Money.of(order.getGrandTotal());
        String amountStr = exactAmount.toPlainString();
        String key = payuProperties.effectiveMerchantKey();
        String salt = payuProperties.effectiveMerchantSalt();
        String actionUrl = payuProperties.effectiveActionUrl();

        Payment payment;
        if (reusablePayuAttempt != null) {
            payment = reusablePayuAttempt;
        } else {
            String txnid = "PYU" + order.getId() + "T" + TokenUtils.randomAlphanumeric(10).toUpperCase();
            payment = Payment.builder()
                    .order(order)
                    .provider("PAYU")
                    .providerOrderId(txnid)
                    .amount(exactAmount)
                    .currency("INR")
                    .method("PAYU")
                    .status(PaymentAttemptStatus.CREATED)
                    .build();
            payment = paymentRepository.save(payment);
            recordTx(payment, "PAYU_ORDER_CREATED", exactAmount, "CREATED", txnid,
                    "PayU payment initiated for " + order.getOrderNumber());
        }

        String txnid = payment.getProviderOrderId();
        String productInfo = sanitizeField("TimeCraft Order " + order.getOrderNumber(), "TimeCraft Watch");
        String firstName = extractFirstName(order.getCustomerName());
        String email = sanitizeField(order.getCustomerEmail(), "collector@timecraft.example");
        String phone = RazorpayPaymentService.sanitizeIndianPhone(order.getShipPhone());
        String udf1 = sanitizeField(order.getOrderNumber(), "");
        String udf2 = "";
        String udf3 = "";
        String udf4 = "";
        String udf5 = "";

        String baseUrl = (requestBaseUrl != null && !requestBaseUrl.isBlank())
                ? trimTrailingSlash(requestBaseUrl)
                : trimTrailingSlash(appProperties.frontendBaseUrl());
        String callbackUrl = baseUrl + "/api/v1/payments/payu/callback";

        String hash = computeRequestHash(
                key, txnid, amountStr, productInfo, firstName, email,
                udf1, udf2, udf3, udf4, udf5, salt
        );

        return new PayuInitiateResponse(
                payment.getId(),
                order.getId(),
                order.getOrderNumber(),
                actionUrl,
                key,
                txnid,
                exactAmount,
                amountStr,
                "INR",
                productInfo,
                firstName,
                email,
                phone,
                udf1,
                udf2,
                udf3,
                udf4,
                udf5,
                callbackUrl,
                callbackUrl,
                hash
        );
    }

    @Transactional
    public OrderDetailDto verifyPaymentForCustomer(Long userId, PayuVerifyRequest req) {
        Payment payment = paymentRepository.findByProviderOrderId(req.txnid().trim())
                .orElseThrow(() -> new ResourceNotFoundException("PayU transaction", req.txnid()));
        Order order = orderService.findCustomerOrderEntity(userId, String.valueOf(payment.getOrder().getId()));

        PayuCallbackOutcome outcome = processVerifiedResponse(
                payment,
                order,
                req.status(),
                req.mihpayid(),
                req.mode(),
                req.amount(),
                req.productinfo(),
                req.firstname(),
                req.email(),
                nullToEmpty(req.udf1()),
                nullToEmpty(req.udf2()),
                nullToEmpty(req.udf3()),
                nullToEmpty(req.udf4()),
                nullToEmpty(req.udf5()),
                req.additionalCharges(),
                req.errorMessage(),
                null,
                req.hash(),
                true
        );

        Order refreshed = orderRepository.findById(order.getId()).orElse(order);
        if (!outcome.success() && !"success".equalsIgnoreCase(req.status())) {
            return OrderDetailDto.from(refreshed);
        }
        return OrderDetailDto.from(refreshed);
    }

    @Transactional
    public PayuCallbackOutcome handleBrowserCallback(Map<String, String> params) {
        String txnid = trimOrNull(params.get("txnid"));
        String udf1OrderNo = trimOrNull(params.get("udf1"));
        if (txnid == null) {
            log.warn("PayU callback received without txnid (udf1={})", udf1OrderNo);
            return new PayuCallbackOutcome(false, udf1OrderNo, "Missing PayU transaction reference");
        }

        Payment payment = paymentRepository.findByProviderOrderId(txnid).orElse(null);
        if (payment == null) {
            log.warn("PayU callback received for unknown txnid={}", txnid);
            return new PayuCallbackOutcome(false, udf1OrderNo, "Unknown PayU transaction reference");
        }

        Order order = payment.getOrder();
        order.getItems().size();

        String errorReason = firstNonBlank(
                params.get("error_Message"),
                params.get("field9"),
                params.get("error"),
                "PayU payment was not completed"
        );

        return processVerifiedResponse(
                payment,
                order,
                nullToEmpty(params.get("status")),
                params.get("mihpayid"),
                params.get("mode"),
                nullToEmpty(params.get("amount")),
                nullToEmpty(params.get("productinfo")),
                nullToEmpty(params.get("firstname")),
                nullToEmpty(params.get("email")),
                nullToEmpty(params.get("udf1")),
                nullToEmpty(params.get("udf2")),
                nullToEmpty(params.get("udf3")),
                nullToEmpty(params.get("udf4")),
                nullToEmpty(params.get("udf5")),
                params.get("additionalCharges"),
                errorReason,
                params.get("unmappedstatus"),
                nullToEmpty(params.get("hash")),
                false
        );
    }

    private PayuCallbackOutcome processVerifiedResponse(
            Payment payment,
            Order order,
            String status,
            String mihpayid,
            String mode,
            String amountRaw,
            String productinfo,
            String firstname,
            String email,
            String udf1,
            String udf2,
            String udf3,
            String udf4,
            String udf5,
            String additionalCharges,
            String errorMessage,
            String unmappedStatus,
            String receivedHash,
            boolean throwOnInvalidSignature
    ) {
        if (payment.getStatus() == PaymentAttemptStatus.CAPTURED && order.getPaymentStatus() == PaymentStatus.PAID) {
            return new PayuCallbackOutcome(true, order.getOrderNumber(), null);
        }

        String key = payuProperties.effectiveMerchantKey();
        String salt = payuProperties.effectiveMerchantSalt();
        String txnid = payment.getProviderOrderId();

        String expectedHash = computeResponseHash(
                salt, status, udf5, udf4, udf3, udf2, udf1,
                email, firstname, productinfo, amountRaw, txnid, key, additionalCharges
        );

        boolean hashValid = receivedHash != null
                && !receivedHash.isBlank()
                && TokenUtils.constantTimeEquals(expectedHash, receivedHash.trim().toLowerCase());

        if (!hashValid) {
            log.warn("PayU response hash mismatch for order={} txnid={}", order.getOrderNumber(), txnid);
            payment.setStatus(PaymentAttemptStatus.FAILED);
            payment.setFailureReason("PayU hash verification failed");
            paymentRepository.save(payment);
            recordTx(payment, "PAYU_HASH_MISMATCH", payment.getAmount(), "FAILED", mihpayid,
                    "PayU SHA-512 reverse hash verification failed");
            orderService.recordPaymentAttemptIncomplete(order,
                    "PayU response signature verification failed", false, order.getUser().getId());
            if (throwOnInvalidSignature) {
                throw BusinessException.badRequest("INVALID_PAYMENT_SIGNATURE",
                        "PayU response hash verification failed");
            }
            return new PayuCallbackOutcome(false, order.getOrderNumber(),
                    "PayU response signature could not be verified. Please retry payment.");
        }

        BigDecimal callbackAmount;
        try {
            callbackAmount = Money.of(new BigDecimal(amountRaw.trim()));
        } catch (Exception ex) {
            callbackAmount = BigDecimal.valueOf(-1);
        }
        if (callbackAmount.compareTo(Money.of(payment.getAmount())) != 0) {
            log.warn("PayU amount mismatch for order={} expected={} got={}",
                    order.getOrderNumber(), payment.getAmount(), amountRaw);
            payment.setStatus(PaymentAttemptStatus.FAILED);
            payment.setFailureReason("PayU callback amount mismatch");
            paymentRepository.save(payment);
            recordTx(payment, "PAYU_AMOUNT_MISMATCH", payment.getAmount(), "FAILED", mihpayid,
                    "Expected " + payment.getAmount() + " but received " + amountRaw);
            if (throwOnInvalidSignature) {
                throw BusinessException.badRequest("PAYMENT_AMOUNT_MISMATCH",
                        "PayU payment amount did not match order total");
            }
            return new PayuCallbackOutcome(false, order.getOrderNumber(),
                    "Payment amount mismatch detected. Order remains pending.");
        }

        if ("success".equalsIgnoreCase(status != null ? status.trim() : "")) {
            String providerPayId = (mihpayid != null && !mihpayid.isBlank())
                    ? mihpayid.trim()
                    : ("payu_pay_" + txnid);
            if (payment.getProviderPaymentId() == null
                    && paymentRepository.findByProviderPaymentId(providerPayId).isEmpty()) {
                payment.setProviderPaymentId(providerPayId);
            }
            String cleanMode = (mode != null && !mode.isBlank()) ? ("PAYU_" + mode.trim().toUpperCase()) : "PAYU";
            payment.setMethod(cleanMode.length() > 30 ? "PAYU" : cleanMode);
            payment.setStatus(PaymentAttemptStatus.CAPTURED);
            payment.setPaidAt(Instant.now());
            paymentRepository.save(payment);
            recordTx(payment, "PAYU_CAPTURED", payment.getAmount(), "CAPTURED",
                    providerPayId, "Verified PayU SHA-512 reverse hash");

            if (order.getPaymentMethod() != PaymentMethod.PAYU) {
                order.setPaymentMethod(PaymentMethod.PAYU);
                orderRepository.save(order);
            }
            Order confirmed = orderService.confirmOrderAfterVerifiedPayment(order.getId(), providerPayId);
            return new PayuCallbackOutcome(true, confirmed.getOrderNumber(), null);
        }

        String cleanReason = firstNonBlank(errorMessage, "PayU payment attempt was not completed");
        boolean cancelled = "userCancelled".equalsIgnoreCase(nullToEmpty(unmappedStatus))
                || cleanReason.toLowerCase().contains("cancel");

        if (mihpayid != null && !mihpayid.isBlank() && payment.getProviderPaymentId() == null) {
            String pid = mihpayid.trim();
            if (paymentRepository.findByProviderPaymentId(pid).isEmpty()) {
                payment.setProviderPaymentId(pid);
            }
        }
        payment.setStatus(cancelled ? PaymentAttemptStatus.CANCELLED : PaymentAttemptStatus.FAILED);
        payment.setFailureReason(cleanReason.length() > 500 ? cleanReason.substring(0, 500) : cleanReason);
        paymentRepository.save(payment);
        recordTx(payment, cancelled ? "PAYU_CANCELLED" : "PAYU_FAILED",
                payment.getAmount(), payment.getStatus().name(), mihpayid, cleanReason);

        orderService.recordPaymentAttemptIncomplete(order, cleanReason, cancelled, order.getUser().getId());
        return new PayuCallbackOutcome(false, order.getOrderNumber(), cleanReason);
    }

    /**
     * PayU forward hash:
     * {@code sha512(key|txnid|amount|productinfo|firstname|email|udf1|udf2|udf3|udf4|udf5||||||SALT)}
     */
    public static String computeRequestHash(
            String key,
            String txnid,
            String amount,
            String productinfo,
            String firstname,
            String email,
            String udf1,
            String udf2,
            String udf3,
            String udf4,
            String udf5,
            String salt
    ) {
        String raw = String.join("|",
                nullToEmpty(key),
                nullToEmpty(txnid),
                nullToEmpty(amount),
                nullToEmpty(productinfo),
                nullToEmpty(firstname),
                nullToEmpty(email),
                nullToEmpty(udf1),
                nullToEmpty(udf2),
                nullToEmpty(udf3),
                nullToEmpty(udf4),
                nullToEmpty(udf5),
                "", "", "", "", "",
                nullToEmpty(salt)
        );
        return sha512Hex(raw);
    }

    /**
     * PayU reverse response hash:
     * {@code sha512([additionalCharges|]SALT|status||||||udf5|udf4|udf3|udf2|udf1|email|firstname|productinfo|amount|txnid|key)}
     */
    public static String computeResponseHash(
            String salt,
            String status,
            String udf5,
            String udf4,
            String udf3,
            String udf2,
            String udf1,
            String email,
            String firstname,
            String productinfo,
            String amount,
            String txnid,
            String key,
            String additionalCharges
    ) {
        String core = String.join("|",
                nullToEmpty(salt),
                nullToEmpty(status),
                "", "", "", "", "",
                nullToEmpty(udf5),
                nullToEmpty(udf4),
                nullToEmpty(udf3),
                nullToEmpty(udf2),
                nullToEmpty(udf1),
                nullToEmpty(email),
                nullToEmpty(firstname),
                nullToEmpty(productinfo),
                nullToEmpty(amount),
                nullToEmpty(txnid),
                nullToEmpty(key)
        );
        String full = (additionalCharges != null && !additionalCharges.isBlank())
                ? (additionalCharges.trim() + "|" + core)
                : core;
        return sha512Hex(full);
    }

    public static String sha512Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-512");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to compute SHA-512 hash", ex);
        }
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

    private static String extractFirstName(String fullName) {
        String cleaned = sanitizeField(fullName, "Collector");
        int space = cleaned.indexOf(' ');
        String first = space > 0 ? cleaned.substring(0, space) : cleaned;
        return first.length() > 60 ? first.substring(0, 60) : first;
    }

    private static String sanitizeField(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        String cleaned = value.replace("|", " ").trim();
        return cleaned.isEmpty() ? fallback : cleaned;
    }

    private static String trimTrailingSlash(String url) {
        if (url == null) {
            return "http://localhost:8080";
        }
        String u = url.trim();
        while (u.endsWith("/")) {
            u = u.substring(0, u.length() - 1);
        }
        return u;
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s.trim();
    }

    private static String trimOrNull(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        return s.trim();
    }

    private static String firstNonBlank(String... candidates) {
        for (String c : candidates) {
            if (c != null && !c.isBlank()) {
                return c.trim();
            }
        }
        return null;
    }
}
