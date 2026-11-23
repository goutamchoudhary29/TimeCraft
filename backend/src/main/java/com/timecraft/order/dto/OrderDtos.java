package com.timecraft.order.dto;

import com.timecraft.order.entity.Order;
import com.timecraft.order.entity.OrderItem;
import com.timecraft.order.entity.OrderStatus;
import com.timecraft.order.entity.OrderStatusHistory;
import com.timecraft.order.entity.PaymentStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class OrderDtos {

    private OrderDtos() {
    }

    public record PlaceOrderRequest(
            @NotNull Long addressId,
            @Size(max = 40) String couponCode,
            @Size(max = 500) String customerNote,
            @Size(max = 64) String idempotencyKey
    ) {
    }

    public record CancelOrderRequest(@Size(max = 255) String reason) {
    }

    public record UpdateOrderStatusRequest(
            @NotNull OrderStatus status,
            @Size(max = 500) String note
    ) {
    }

    public record OrderLineDto(
            Long id,
            Long productId,
            Long variantId,
            String productName,
            String brandName,
            String sku,
            String variantName,
            String imageUrl,
            BigDecimal unitPrice,
            int quantity,
            BigDecimal lineSubtotal,
            BigDecimal discountAmount,
            BigDecimal taxRate,
            BigDecimal taxAmount,
            BigDecimal lineTotal,
            int returnedQuantity,
            int returnableQuantity
    ) {
        public static OrderLineDto from(OrderItem oi) {
            return new OrderLineDto(
                    oi.getId(),
                    oi.getProduct() != null ? oi.getProduct().getId() : null,
                    oi.getVariant() != null ? oi.getVariant().getId() : null,
                    oi.getProductName(),
                    oi.getBrandName(),
                    oi.getSku(),
                    oi.getVariantName(),
                    oi.getImageUrl(),
                    oi.getUnitPrice(),
                    oi.getQuantity(),
                    oi.getLineSubtotal(),
                    oi.getDiscountAmount(),
                    oi.getTaxRate(),
                    oi.getTaxAmount(),
                    oi.getLineTotal(),
                    oi.getReturnedQuantity(),
                    oi.getReturnableQuantity()
            );
        }
    }

    public record StatusTimelineDto(
            OrderStatus fromStatus,
            OrderStatus toStatus,
            String note,
            String changedByType,
            Instant createdAt
    ) {
        public static StatusTimelineDto from(OrderStatusHistory h) {
            return new StatusTimelineDto(h.getFromStatus(), h.getToStatus(),
                    h.getNote(), h.getChangedByType(), h.getCreatedAt());
        }
    }

    public record OrderSummaryDto(
            Long id,
            String orderNumber,
            OrderStatus status,
            PaymentStatus paymentStatus,
            String currency,
            BigDecimal grandTotal,
            int itemCount,
            String customerName,
            String customerEmail,
            Instant placedAt,
            Instant paymentExpiresAt
    ) {
        public static OrderSummaryDto from(Order o) {
            int count = o.getItems().stream().mapToInt(OrderItem::getQuantity).sum();
            return new OrderSummaryDto(o.getId(), o.getOrderNumber(), o.getStatus(),
                    o.getPaymentStatus(), o.getCurrency(), o.getGrandTotal(), count,
                    o.getCustomerName(), o.getCustomerEmail(), o.getPlacedAt(), o.getPaymentExpiresAt());
        }
    }

    public record OrderDetailDto(
            Long id,
            String orderNumber,
            Long userId,
            OrderStatus status,
            PaymentStatus paymentStatus,
            String currency,
            BigDecimal subtotal,
            BigDecimal discountTotal,
            BigDecimal taxTotal,
            BigDecimal shippingTotal,
            BigDecimal grandTotal,
            boolean taxInclusive,
            String couponCode,
            String customerEmail,
            String customerName,
            String shipFullName,
            String shipPhone,
            String shipLine1,
            String shipLine2,
            String shipLandmark,
            String shipCity,
            String shipState,
            String shipPostalCode,
            String shipCountry,
            String customerNote,
            Instant paymentExpiresAt,
            Instant placedAt,
            Instant confirmedAt,
            Instant deliveredAt,
            Instant cancelledAt,
            String cancelReason,
            boolean cancellable,
            List<OrderLineDto> items,
            List<StatusTimelineDto> statusHistory
    ) {
        public static OrderDetailDto from(Order o) {
            return new OrderDetailDto(
                    o.getId(),
                    o.getOrderNumber(),
                    o.getUser().getId(),
                    o.getStatus(),
                    o.getPaymentStatus(),
                    o.getCurrency(),
                    o.getSubtotal(),
                    o.getDiscountTotal(),
                    o.getTaxTotal(),
                    o.getShippingTotal(),
                    o.getGrandTotal(),
                    o.isTaxInclusive(),
                    o.getCouponCode(),
                    o.getCustomerEmail(),
                    o.getCustomerName(),
                    o.getShipFullName(),
                    o.getShipPhone(),
                    o.getShipLine1(),
                    o.getShipLine2(),
                    o.getShipLandmark(),
                    o.getShipCity(),
                    o.getShipState(),
                    o.getShipPostalCode(),
                    o.getShipCountry(),
                    o.getCustomerNote(),
                    o.getPaymentExpiresAt(),
                    o.getPlacedAt(),
                    o.getConfirmedAt(),
                    o.getDeliveredAt(),
                    o.getCancelledAt(),
                    o.getCancelReason(),
                    o.getStatus().isCustomerCancellable(),
                    o.getItems().stream().map(OrderLineDto::from).toList(),
                    o.getStatusHistory().stream().map(StatusTimelineDto::from).toList()
            );
        }
    }
}
