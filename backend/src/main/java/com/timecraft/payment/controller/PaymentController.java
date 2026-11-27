package com.timecraft.payment.controller;

import com.timecraft.common.dto.MessageResponse;
import com.timecraft.order.dto.OrderDtos.OrderDetailDto;
import com.timecraft.payment.service.PayuPaymentService;
import com.timecraft.payment.service.PayuPaymentService.PayuCallbackOutcome;
import com.timecraft.payment.service.PayuPaymentService.PayuInitiateRequest;
import com.timecraft.payment.service.PayuPaymentService.PayuInitiateResponse;
import com.timecraft.payment.service.PayuPaymentService.PayuVerifyRequest;
import com.timecraft.payment.service.RazorpayPaymentService;
import com.timecraft.payment.service.RazorpayPaymentService.CreatePaymentRequest;
import com.timecraft.payment.service.RazorpayPaymentService.PaymentDto;
import com.timecraft.payment.service.RazorpayPaymentService.PaymentFailureReport;
import com.timecraft.payment.service.RazorpayPaymentService.PaymentOrderResponse;
import com.timecraft.payment.service.RazorpayPaymentService.SandboxAuthorizeRequest;
import com.timecraft.payment.service.RazorpayPaymentService.VerifyPaymentRequest;
import com.timecraft.payment.service.RazorpayPaymentService.sandboxSignatureResponse;
import com.timecraft.security.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final RazorpayPaymentService paySvc;
    private final PayuPaymentService payuSvc;
    private final com.timecraft.order.service.OrderService orderSvc;

    @PostMapping("/create")
    public PaymentOrderResponse initiatePayment(@Valid @RequestBody CreatePaymentRequest body) {
        return paySvc.createRazorpayOrder(SecurityUtils.requireUserId(), body);
    }

    @PostMapping("/sandbox-authorize")
    public sandboxSignatureResponse authorizeSandbox(@Valid @RequestBody SandboxAuthorizeRequest body) {
        return paySvc.authorizeSandboxPayment(SecurityUtils.requireUserId(), body);
    }

    @PostMapping("/verify")
    public OrderDetailDto verifySignature(@Valid @RequestBody VerifyPaymentRequest body) {
        return paySvc.verifyCheckoutPayment(SecurityUtils.requireUserId(), body);
    }

    @PostMapping("/failure")
    public OrderDetailDto recordFailure(@Valid @RequestBody PaymentFailureReport body) {
        return paySvc.reportFailureOrCancellation(SecurityUtils.requireUserId(), body);
    }

    @GetMapping("/order/{orderId}")
    public List<PaymentDto> listForCustomerOrder(@PathVariable Long orderId) {
        orderSvc.findCustomerOrderEntity(SecurityUtils.requireUserId(), String.valueOf(orderId));
        return paySvc.listForOrder(orderId);
    }

    @PostMapping("/webhook")
    public MessageResponse razorpayWebhook(
            @RequestBody String rawPayload,
            @RequestHeader(value = "X-Razorpay-Signature", required = false) String signature,
            @RequestHeader(value = "X-Razorpay-Event-Id", required = false) String eventId
    ) {
        paySvc.processWebhook(rawPayload, signature, eventId);
        return MessageResponse.of("Webhook processed");
    }

    @PostMapping("/payu/initiate")
    public PayuInitiateResponse initiatePayuPayment(@Valid @RequestBody PayuInitiateRequest body,
                                                    HttpServletRequest request) {
        return payuSvc.initiatePayment(SecurityUtils.requireUserId(), body, resolveOrigin(request));
    }

    @PostMapping("/payu/verify")
    public OrderDetailDto verifyPayuPayment(@Valid @RequestBody PayuVerifyRequest body) {
        return payuSvc.verifyPaymentForCustomer(SecurityUtils.requireUserId(), body);
    }

    @RequestMapping(value = "/payu/callback", method = {RequestMethod.POST, RequestMethod.GET})
    public ResponseEntity<Void> handlePayuCallback(@RequestParam Map<String, String> params) {
        PayuCallbackOutcome outcome = payuSvc.handleBrowserCallback(params);
        String targetUrl;
        if (outcome.success() && outcome.orderNumber() != null) {
            targetUrl = "/pages/order-confirmation.html?order="
                    + URLEncoder.encode(outcome.orderNumber(), StandardCharsets.UTF_8)
                    + "&method=PAYU";
        } else if (outcome.orderNumber() != null && !outcome.orderNumber().isBlank()) {
            String reason = outcome.reason() != null ? outcome.reason() : "PayU payment was not completed";
            targetUrl = "/pages/checkout.html?order="
                    + URLEncoder.encode(outcome.orderNumber(), StandardCharsets.UTF_8)
                    + "&status=failed&gateway=PAYU&reason="
                    + URLEncoder.encode(reason, StandardCharsets.UTF_8);
        } else {
            String reason = outcome.reason() != null ? outcome.reason() : "PayU payment was not completed";
            targetUrl = "/pages/payment-result.html?status=failed&gateway=PAYU&reason="
                    + URLEncoder.encode(reason, StandardCharsets.UTF_8);
        }
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(targetUrl)).build();
    }

    private static String resolveOrigin(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String proto = request.getHeader("X-Forwarded-Proto");
        String host = request.getHeader("X-Forwarded-Host");
        if (proto != null && !proto.isBlank() && host != null && !host.isBlank()) {
            return proto.trim() + "://" + host.trim();
        }
        String scheme = request.getScheme();
        String serverName = request.getServerName();
        int port = request.getServerPort();
        if (serverName == null || serverName.isBlank()) {
            return null;
        }
        boolean standardPort = ("http".equalsIgnoreCase(scheme) && port == 80)
                || ("https".equalsIgnoreCase(scheme) && port == 443);
        return standardPort ? (scheme + "://" + serverName) : (scheme + "://" + serverName + ":" + port);
    }
}
