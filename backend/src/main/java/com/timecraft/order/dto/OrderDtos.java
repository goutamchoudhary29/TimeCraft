package com.timecraft.order.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.timecraft.order.entity.Order;
import com.timecraft.order.entity.OrderItem;
import com.timecraft.order.entity.OrderStatus;
import com.timecraft.order.entity.OrderStatusHistory;
import com.timecraft.order.entity.PaymentMethod;
import com.timecraft.order.entity.PaymentStatus;
import com.timecraft.product.entity.Product;
import com.timecraft.product.entity.ProductVariant;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class OrderDtos {

    private OrderDtos() {
    }

    public record PlaceOrderRequest(
            @JsonProperty("addressId") @NotNull Long addressId,
            @JsonProperty("couponCode") @Size(max = 40) String couponCode,
            @JsonProperty("customerNote") @Size(max = 500) String customerNote,
            @JsonProperty("idempotencyKey") @Size(max = 64) String idempotencyKey,
            @JsonProperty("paymentMethod") PaymentMethod paymentMethod
    ) {
        @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
        public PlaceOrderRequest {
        }

        public PlaceOrderRequest(Long addressId, String couponCode, String customerNote, String idempotencyKey) {
            this(addressId, couponCode, customerNote, idempotencyKey, PaymentMethod.RAZORPAY);
        }
    }

    public record CancelOrderRequest(@JsonProperty("reason") @Size(max = 255) String reason) {
        @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
        public CancelOrderRequest {
        }
    }

    public record UpdateOrderStatusRequest(
            @JsonProperty("status") @NotNull OrderStatus status,
            @JsonProperty("note") @Size(max = 500) String note
    ) {
        @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
        public UpdateOrderStatusRequest {
        }
    }

    public record OrderLineDto(
            Long id,
            Long productId,
            Long variantId,
            String productSlug,
            String productName,
            String brandName,
            String modelName,
            String sku,
            String variantName,
            String dialColor,
            String strapMaterial,
            String strapColor,
            String movementType,
            BigDecimal caseDiameterMm,
            String caseMaterial,
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
            Long productId = null;
            Long variantId = null;
            String productSlug = null;
            String modelName = null;
            String dialColor = null;
            String strapMaterial = null;
            String strapColor = null;
            String movementType = null;
            BigDecimal caseDiameterMm = null;
            String caseMaterial = null;
            String resolvedImage = oi.getImageUrl();

            try {
                Product p = oi.getProduct();
                ProductVariant v = oi.getVariant();
                if (p != null) {
                    productId = p.getId();
                    productSlug = p.getSlug();
                    modelName = p.getModelName();
                    dialColor = p.getDialColor();
                    strapMaterial = p.getStrapMaterial();
                    movementType = p.getMovementType() != null ? p.getMovementType().name() : null;
                    caseDiameterMm = p.getCaseDiameterMm();
                    caseMaterial = p.getCaseMaterial();
                    if (resolvedImage == null || resolvedImage.isBlank()) {
                        resolvedImage = p.getPrimaryImageUrl();
                    }
                }
                if (v != null) {
                    variantId = v.getId();
                    if (v.getDialColor() != null && !v.getDialColor().isBlank()) {
                        dialColor = v.getDialColor();
                    }
                    if (v.getStrapMaterial() != null && !v.getStrapMaterial().isBlank()) {
                        strapMaterial = v.getStrapMaterial();
                    }
                    if (v.getStrapColor() != null && !v.getStrapColor().isBlank()) {
                        strapColor = v.getStrapColor();
                    }
                }
            } catch (Exception ignored) {
                // Historical order snapshot remains intact even if linked catalog entities are unavailable
            }

            return new OrderLineDto(
                    oi.getId(),
                    productId,
                    variantId,
                    productSlug,
                    oi.getProductName(),
                    oi.getBrandName(),
                    modelName,
                    oi.getSku(),
                    oi.getVariantName(),
                    dialColor,
                    strapMaterial,
                    strapColor,
                    movementType,
                    caseDiameterMm,
                    caseMaterial,
                    resolvedImage,
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
            PaymentMethod paymentMethod,
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
                    o.getPaymentStatus(), o.getPaymentMethod(), o.getCurrency(), o.getGrandTotal(), count,
                    o.getCustomerName(), o.getCustomerEmail(), o.getPlacedAt(), o.getPaymentExpiresAt());
        }
    }

    public record OrderDetailDto(
            Long id,
            String orderNumber,
            Long userId,
            OrderStatus status,
            PaymentStatus paymentStatus,
            PaymentMethod paymentMethod,
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
                    o.getPaymentMethod(),
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
