package com.timecraft.payment.controller;

import com.timecraft.common.dto.MessageResponse;
import com.timecraft.order.dto.OrderDtos.OrderDetailDto;
import com.timecraft.payment.service.RazorpayPaymentService;
import com.timecraft.payment.service.RazorpayPaymentService.CreatePaymentRequest;
import com.timecraft.payment.service.RazorpayPaymentService.PaymentDto;
import com.timecraft.payment.service.RazorpayPaymentService.PaymentFailureReport;
import com.timecraft.payment.service.RazorpayPaymentService.PaymentOrderResponse;
import com.timecraft.payment.service.RazorpayPaymentService.SandboxAuthorizeRequest;
import com.timecraft.payment.service.RazorpayPaymentService.VerifyPaymentRequest;
import com.timecraft.payment.service.RazorpayPaymentService.sandboxSignatureResponse;
import com.timecraft.security.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final RazorpayPaymentService paySvc;
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
}
