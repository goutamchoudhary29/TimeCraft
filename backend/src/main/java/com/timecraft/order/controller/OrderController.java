package com.timecraft.order.controller;

import com.timecraft.common.dto.PageResponse;
import com.timecraft.order.dto.OrderDtos.CancelOrderRequest;
import com.timecraft.order.dto.OrderDtos.OrderDetailDto;
import com.timecraft.order.dto.OrderDtos.OrderSummaryDto;
import com.timecraft.order.dto.OrderDtos.PlaceOrderRequest;
import com.timecraft.order.entity.Order;
import com.timecraft.order.service.InvoicePdfService;
import com.timecraft.order.service.OrderService;
import com.timecraft.payment.service.RazorpayPaymentService;
import com.timecraft.security.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderSvc;
    private final InvoicePdfService invoiceSvc;
    private final RazorpayPaymentService paymentSvc;

    @PostMapping
    public ResponseEntity<OrderDetailDto> createOrder(
            @Valid @RequestBody PlaceOrderRequest body,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempHeader
    ) {
        OrderDetailDto created = orderSvc.placeOrder(SecurityUtils.requireUserId(), body, idempHeader);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping
    public PageResponse<OrderSummaryDto> myOrders(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return orderSvc.listCustomerOrders(
                SecurityUtils.requireUserId(),
                PageRequest.of(Math.max(0, page), Math.min(50, Math.max(1, size)))
        );
    }

    @GetMapping("/{idOrNumber}")
    public OrderDetailDto orderDetails(@PathVariable String idOrNumber) {
        return orderSvc.getCustomerOrder(SecurityUtils.requireUserId(), idOrNumber);
    }

    @PostMapping("/{id}/cancel")
    public OrderDetailDto cancelMyOrder(@PathVariable Long id,
                                        @RequestBody(required = false) CancelOrderRequest body) {
        return orderSvc.cancelByCustomer(SecurityUtils.requireUserId(), id,
                body != null ? body.reason() : null);
    }

    @PostMapping("/{idOrNumber}/switch-to-cod")
    public OrderDetailDto switchOrderToCod(@PathVariable String idOrNumber) {
        return paymentSvc.switchOrderToCod(SecurityUtils.requireUserId(), idOrNumber);
    }

    @GetMapping(value = "/{idOrNumber}/invoice", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> downloadInvoice(@PathVariable String idOrNumber) {
        Order order = orderSvc.findCustomerOrderEntity(SecurityUtils.requireUserId(), idOrNumber);
        byte[] pdf = invoiceSvc.generateInvoice(order);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename("TimeCraft-Invoice-" + order.getOrderNumber() + ".pdf")
                .build());
        return new ResponseEntity<>(pdf, headers, HttpStatus.OK);
    }
}
