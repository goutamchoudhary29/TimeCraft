package com.timecraft.order.service;

import com.timecraft.address.entity.CustomerAddress;
import com.timecraft.address.service.AddressService;
import com.timecraft.admin.service.StoreConfigService;
import com.timecraft.audit.service.AuditLogService;
import com.timecraft.authentication.entity.AccountStatus;
import com.timecraft.authentication.entity.User;
import com.timecraft.authentication.repository.UserRepository;
import com.timecraft.cart.entity.Cart;
import com.timecraft.cart.entity.CartItem;
import com.timecraft.cart.service.CartService;
import com.timecraft.cart.service.PriceBreakdownCalculator;
import com.timecraft.cart.service.PriceBreakdownCalculator.TotalsBreakdown;
import com.timecraft.common.dto.PageResponse;
import com.timecraft.common.util.Money;
import com.timecraft.common.util.ReferenceNumberGenerator;
import com.timecraft.coupon.entity.Coupon;
import com.timecraft.coupon.service.CouponService;
import com.timecraft.coupon.service.CouponService.CouponEvaluation;
import com.timecraft.exception.BusinessException;
import com.timecraft.exception.ResourceNotFoundException;
import com.timecraft.inventory.entity.StockReservationStatus;
import com.timecraft.inventory.service.InventoryService;
import com.timecraft.notification.service.NotificationService;
import com.timecraft.order.dto.OrderDtos.OrderDetailDto;
import com.timecraft.order.dto.OrderDtos.OrderSummaryDto;
import com.timecraft.order.dto.OrderDtos.PlaceOrderRequest;
import com.timecraft.order.entity.Order;
import com.timecraft.order.entity.OrderItem;
import com.timecraft.order.entity.OrderStatus;
import com.timecraft.order.entity.OrderStatusHistory;
import com.timecraft.order.entity.PaymentMethod;
import com.timecraft.order.entity.PaymentStatus;
import com.timecraft.order.repository.OrderRepository;
import com.timecraft.product.entity.Product;
import com.timecraft.product.entity.ProductStatus;
import com.timecraft.product.entity.ProductVariant;
import com.timecraft.product.repository.ProductRepository;
import com.timecraft.security.SecurityUtils;
import com.timecraft.security.TimeCraftPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Handles checkout validation, immutable historical order snapshots, stock reservation
 * locking, coupon reservation, validated status transitions and expired reservation cleanup.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final AddressService addressService;
    private final CartService cartService;
    private final CouponService couponService;
    private final InventoryService inventoryService;
    private final PriceBreakdownCalculator calculator;
    private final StoreConfigService storeConfigService;
    private final NotificationService notificationService;
    private final AuditLogService auditLogService;

    @Transactional
    public OrderDetailDto placeOrder(Long userId, PlaceOrderRequest req, String headerIdempotencyKey) {
        String idempotencyKey = normalizeKey(headerIdempotencyKey != null ? headerIdempotencyKey : req.idempotencyKey());
        if (idempotencyKey != null) {
            var existing = orderRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey);
            if (existing.isPresent()) {
                return OrderDetailDto.from(existing.get());
            }
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
        if (user.getAccountStatus() != AccountStatus.ACTIVE) {
            throw BusinessException.forbidden("Your account is not active");
        }
        if (!user.isEmailVerified()) {
            throw BusinessException.forbidden("Please verify your email address with the 6-digit OTP before placing an order.");
        }

        PaymentMethod paymentMethod = req.paymentMethod() != null ? req.paymentMethod() : PaymentMethod.RAZORPAY;

        CustomerAddress address = addressService.requireOwnedEntity(userId, req.addressId());
        Cart cart = cartService.requireNonEmptyUserCart(userId);
        int maxPerItem = storeConfigService.getInt("checkout.max_quantity_per_item", 5);
        int reservationMinutes = storeConfigService.getInt("checkout.reservation_minutes", 15);
        Duration reservationTtl = Duration.ofMinutes(Math.max(5, reservationMinutes));

        // Sort cart items by variantId before locking inventory rows to prevent deadlocks.
        List<CartItem> sortedCartItems = new ArrayList<>(cart.getItems());
        sortedCartItems.sort(Comparator.comparing(i -> i.getVariant().getId()));

        BigDecimal rawSubtotal = Money.ZERO;
        for (CartItem ci : sortedCartItems) {
            ProductVariant v = ci.getVariant();
            Product p = v.getProduct();
            if (v.isDeleted() || !v.isActive() || p.isDeleted() || p.getStatus() != ProductStatus.ACTIVE) {
                throw BusinessException.badRequest("PRODUCT_UNAVAILABLE",
                        p.getName() + " (" + v.getName() + ") is no longer available");
            }
            if (ci.getQuantity() <= 0 || ci.getQuantity() > maxPerItem) {
                throw BusinessException.badRequest("INVALID_QUANTITY",
                        "Invalid quantity for " + p.getName());
            }
            BigDecimal unit = Money.of(v.effectiveSellingPrice());
            rawSubtotal = rawSubtotal.add(unit.multiply(BigDecimal.valueOf(ci.getQuantity())));
        }
        rawSubtotal = Money.of(rawSubtotal);

        String couponCodeToUse = req.couponCode() != null && !req.couponCode().isBlank()
                ? req.couponCode().trim()
                : (cart.getCoupon() != null ? cart.getCoupon().getCode() : null);

        CouponEvaluation couponEvaluation = null;
        BigDecimal discountTotal = Money.ZERO;
        if (couponCodeToUse != null) {
            couponEvaluation = couponService.evaluateForOrderLocking(couponCodeToUse, rawSubtotal, userId);
            discountTotal = couponEvaluation.discountAmount();
        }

        TotalsBreakdown totals = calculator.calculate(rawSubtotal, discountTotal);
        Instant now = Instant.now();

        if (paymentMethod == PaymentMethod.COD) {
            boolean codEnabled = storeConfigService.getBoolean("cod_enabled", true);
            if (!codEnabled) {
                throw BusinessException.badRequest("COD_DISABLED",
                        "Cash on Delivery is currently disabled. Please pay online via Razorpay.");
            }
            BigDecimal codMax = storeConfigService.getDecimal("cod_max_amount", "50000.00");
            if (totals.grandTotal().compareTo(codMax) > 0) {
                throw BusinessException.badRequest("COD_LIMIT_EXCEEDED",
                        "Cash on Delivery is available only for orders up to ₹" + codMax.toPlainString() + ". Please pay online via Razorpay.");
            }
        }

        boolean isCod = paymentMethod == PaymentMethod.COD;

        Order order = Order.builder()
                .orderNumber(ReferenceNumberGenerator.next("TC"))
                .user(user)
                .status(isCod ? OrderStatus.CONFIRMED : OrderStatus.PENDING)
                .paymentStatus(isCod ? PaymentStatus.PENDING_COD : PaymentStatus.PENDING)
                .paymentMethod(paymentMethod)
                .currency(totals.currency())
                .subtotal(totals.subtotal())
                .discountTotal(totals.discountTotal())
                .taxTotal(totals.taxTotal())
                .shippingTotal(totals.shippingTotal())
                .grandTotal(totals.grandTotal())
                .taxInclusive(totals.taxInclusive())
                .coupon(couponEvaluation != null ? couponEvaluation.coupon() : null)
                .couponCode(couponEvaluation != null ? couponEvaluation.coupon().getCode() : null)
                .customerEmail(user.getEmail())
                .customerName(user.getFullName())
                .shipFullName(address.getFullName())
                .shipPhone(address.getPhone())
                .shipLine1(address.getLine1())
                .shipLine2(address.getLine2())
                .shipLandmark(address.getLandmark())
                .shipCity(address.getCity())
                .shipState(address.getState())
                .shipPostalCode(address.getPostalCode())
                .shipCountry(address.getCountry())
                .customerNote(req.customerNote())
                .idempotencyKey(idempotencyKey)
                .paymentExpiresAt(isCod ? null : now.plus(reservationTtl))
                .placedAt(now)
                .confirmedAt(isCod ? now : null)
                .build();

        // Build immutable historical snapshots for each ordered item
        BigDecimal allocatedDiscount = Money.ZERO;
        for (int idx = 0; idx < sortedCartItems.size(); idx++) {
            CartItem ci = sortedCartItems.get(idx);
            ProductVariant v = ci.getVariant();
            Product p = v.getProduct();
            BigDecimal unit = Money.of(v.effectiveSellingPrice());
            BigDecimal lineSubtotal = Money.of(unit.multiply(BigDecimal.valueOf(ci.getQuantity())));

            BigDecimal lineDiscount = Money.ZERO;
            if (totals.discountTotal().signum() > 0 && totals.subtotal().signum() > 0) {
                if (idx == sortedCartItems.size() - 1) {
                    lineDiscount = Money.max(Money.ZERO, totals.discountTotal().subtract(allocatedDiscount));
                } else {
                    lineDiscount = lineSubtotal.multiply(totals.discountTotal())
                            .divide(totals.subtotal(), 2, RoundingMode.HALF_UP);
                    allocatedDiscount = allocatedDiscount.add(lineDiscount);
                }
            }
            BigDecimal taxableBase = Money.max(Money.ZERO, lineSubtotal.subtract(lineDiscount));
            BigDecimal lineTax = totals.taxInclusive()
                    ? taxableBase.multiply(totals.taxRatePercent())
                            .divide(Money.HUNDRED.add(totals.taxRatePercent()), 2, RoundingMode.HALF_UP)
                    : Money.percentOf(taxableBase, totals.taxRatePercent());
            BigDecimal lineTotal = totals.taxInclusive() ? taxableBase : taxableBase.add(lineTax);

            order.getItems().add(OrderItem.builder()
                    .order(order)
                    .product(p)
                    .variant(v)
                    .productName(p.getName())
                    .brandName(p.getBrand().getName())
                    .sku(v.getSku())
                    .variantName(v.getName())
                    .imageUrl(p.getPrimaryImageUrl())
                    .unitPrice(unit)
                    .quantity(ci.getQuantity())
                    .lineSubtotal(lineSubtotal)
                    .discountAmount(lineDiscount)
                    .taxRate(totals.taxRatePercent())
                    .taxAmount(Money.of(lineTax))
                    .lineTotal(Money.of(lineTotal))
                    .returnedQuantity(0)
                    .build());
        }

        order.getStatusHistory().add(OrderStatusHistory.builder()
                .order(order)
                .fromStatus(null)
                .toStatus(isCod ? OrderStatus.CONFIRMED : OrderStatus.PENDING)
                .note(isCod
                        ? "Order confirmed via Cash on Delivery (payment pending on delivery)"
                        : "Order created; awaiting online payment verification")
                .changedByType("CUSTOMER")
                .changedByUserId(userId)
                .build());

        order = orderRepository.save(order);

        // Lock and reserve stock per variant
        for (CartItem ci : sortedCartItems) {
            inventoryService.reserveForOrder(order, ci.getVariant(), ci.getQuantity(), reservationTtl);
        }

        // Reserve coupon usage if applied
        if (couponEvaluation != null) {
            couponService.reserveForOrder(couponEvaluation.coupon(), user, order, couponEvaluation.discountAmount());
        }

        if (isCod) {
            // COD orders commit stock & coupon immediately
            inventoryService.commitReservationsForOrder(order);
            couponService.markRedeemedForOrder(order.getId());
            for (OrderItem item : order.getItems()) {
                if (item.getProduct() != null) {
                    Product p = item.getProduct();
                    p.setSoldCount(p.getSoldCount() + item.getQuantity());
                    productRepository.save(p);
                }
            }
            notificationService.notifyOrderConfirmed(order);
        }

        cartService.clearUserCart(userId);
        return OrderDetailDto.from(order);
    }

    @Transactional
    public Order markCodPaymentCollectedByAdmin(Long orderId, String note) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderId));
        if (order.getPaymentMethod() != PaymentMethod.COD) {
            throw BusinessException.badRequest("NOT_COD_ORDER", "Only Cash on Delivery orders can be marked COD paid");
        }
        if (order.getPaymentStatus() == PaymentStatus.PAID) {
            return order;
        }
        Long adminId = SecurityUtils.currentPrincipal().map(TimeCraftPrincipal::getId).orElse(null);
        String adminEmail = SecurityUtils.currentPrincipal().map(TimeCraftPrincipal::getEmail).orElse("admin");

        order.setPaymentStatus(PaymentStatus.PAID);
        order.getStatusHistory().add(OrderStatusHistory.builder()
                .order(order)
                .fromStatus(order.getStatus())
                .toStatus(order.getStatus())
                .note("COD payment collected & marked PAID by admin" + (note != null && !note.isBlank() ? ": " + note : ""))
                .changedByType("ADMIN")
                .changedByUserId(adminId)
                .build());
        order = orderRepository.save(order);

        auditLogService.log(adminId, adminEmail, "COD_PAYMENT_COLLECTED", "ORDER",
                String.valueOf(order.getId()),
                "Marked COD order " + order.getOrderNumber() + " as PAID (₹" + order.getGrandTotal() + ")", null);
        return order;
    }

    @CacheEvict(value = {"productDetails", "homeContent"}, allEntries = true)
    @Transactional
    public Order confirmOrderAfterVerifiedPayment(Long orderId, String paymentReference) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderId));
        if (order.getPaymentStatus() == PaymentStatus.PAID && order.getStatus() != OrderStatus.PENDING) {
            return order; // idempotent
        }

        inventoryService.commitReservationsForOrder(order);
        couponService.markRedeemedForOrder(order.getId());

        OrderStatus prev = order.getStatus();
        order.setPaymentStatus(PaymentStatus.PAID);
        order.setStatus(OrderStatus.CONFIRMED);
        order.setConfirmedAt(Instant.now());
        order.getStatusHistory().add(OrderStatusHistory.builder()
                .order(order)
                .fromStatus(prev)
                .toStatus(OrderStatus.CONFIRMED)
                .note("Payment verified (" + paymentReference + ")")
                .changedByType("SYSTEM")
                .build());

        for (OrderItem item : order.getItems()) {
            if (item.getProduct() != null) {
                Product p = item.getProduct();
                p.setSoldCount(p.getSoldCount() + item.getQuantity());
                productRepository.save(p);
            }
        }

        order = orderRepository.save(order);
        notificationService.notifyOrderConfirmed(order);
        return order;
    }

    @Transactional
    public void handlePaymentFailedOrExpired(Order order, String reason, boolean terminal) {
        if (order.getPaymentStatus() == PaymentStatus.PAID) {
            return;
        }
        if (terminal && order.getStatus() == OrderStatus.PENDING) {
            inventoryService.releaseReservationsForOrder(order, StockReservationStatus.EXPIRED, reason);
            couponService.releaseForOrder(order.getId());
            order.setPaymentStatus(PaymentStatus.FAILED);
            order.setStatus(OrderStatus.FAILED);
            order.getStatusHistory().add(OrderStatusHistory.builder()
                    .order(order)
                    .fromStatus(OrderStatus.PENDING)
                    .toStatus(OrderStatus.FAILED)
                    .note(reason)
                    .changedByType("SYSTEM")
                    .build());
            orderRepository.save(order);
        }
        notificationService.notifyPaymentFailed(order, reason);
    }

    @CacheEvict(value = {"productDetails", "homeContent"}, allEntries = true)
    @Transactional
    public OrderDetailDto cancelByCustomer(Long userId, Long orderId, String reason) {
        Order order = orderRepository.findByIdAndUserId(orderId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderId));
        if (!order.getStatus().isCustomerCancellable()) {
            throw BusinessException.badRequest("ORDER_NOT_CANCELLABLE",
                    "Orders in status " + order.getStatus() + " cannot be cancelled directly");
        }
        return performCancellation(order, reason != null && !reason.isBlank() ? reason.trim() : "Cancelled by customer",
                "CUSTOMER", userId);
    }

    @CacheEvict(value = {"productDetails", "homeContent"}, allEntries = true)
    @Transactional
    public OrderDetailDto updateStatusByAdmin(Long orderId, OrderStatus nextStatus, String note) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderId));
        Long adminId = SecurityUtils.currentPrincipal().map(TimeCraftPrincipal::getId).orElse(null);

        if (nextStatus == OrderStatus.CANCELLED) {
            return performCancellation(order, note != null ? note : "Cancelled by administrator", "ADMIN", adminId);
        }
        if (!order.getStatus().canTransitionTo(nextStatus)) {
            throw BusinessException.badRequest("INVALID_ORDER_STATE_TRANSITION",
                    "Cannot transition order from " + order.getStatus() + " to " + nextStatus);
        }
        if ((nextStatus == OrderStatus.PROCESSING || nextStatus == OrderStatus.PACKED || nextStatus == OrderStatus.SHIPPED)
                && order.getPaymentStatus() != PaymentStatus.PAID) {
            throw BusinessException.badRequest("ORDER_UNPAID",
                    "Order cannot move to " + nextStatus + " before payment is verified");
        }

        OrderStatus prev = order.getStatus();
        order.setStatus(nextStatus);
        if (nextStatus == OrderStatus.DELIVERED && order.getDeliveredAt() == null) {
            order.setDeliveredAt(Instant.now());
        }
        order.getStatusHistory().add(OrderStatusHistory.builder()
                .order(order)
                .fromStatus(prev)
                .toStatus(nextStatus)
                .note(note != null && !note.isBlank() ? note.trim() : "Status updated to " + nextStatus)
                .changedByType("ADMIN")
                .changedByUserId(adminId)
                .build());
        order = orderRepository.save(order);

        auditLogService.logCurrentActor("ORDER_STATUS_UPDATED", "ORDER", order.getId(),
                "Order " + order.getOrderNumber() + " " + prev + " -> " + nextStatus);
        notificationService.notifyOrderStatusChanged(order, "Order " + nextStatus.name().replace('_', ' '),
                "Your order " + order.getOrderNumber() + " is now " + nextStatus.name().replace('_', ' ') + ".");
        return OrderDetailDto.from(order);
    }

    @Transactional
    public void transitionFromSystem(Order order, OrderStatus nextStatus, String note) {
        OrderStatus prev = order.getStatus();
        if (prev == nextStatus) {
            return;
        }
        if (!prev.canTransitionTo(nextStatus)) {
            throw BusinessException.badRequest("INVALID_ORDER_STATE_TRANSITION",
                    "Cannot transition order from " + prev + " to " + nextStatus);
        }
        order.setStatus(nextStatus);
        if (nextStatus == OrderStatus.DELIVERED && order.getDeliveredAt() == null) {
            order.setDeliveredAt(Instant.now());
        }
        order.getStatusHistory().add(OrderStatusHistory.builder()
                .order(order)
                .fromStatus(prev)
                .toStatus(nextStatus)
                .note(note)
                .changedByType("SYSTEM")
                .build());
        orderRepository.save(order);
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderSummaryDto> listCustomerOrders(Long userId, Pageable pageable) {
        return PageResponse.of(orderRepository.findByUserIdOrderByPlacedAtDesc(userId, pageable), OrderSummaryDto::from);
    }

    @Transactional(readOnly = true)
    public OrderDetailDto getCustomerOrder(Long userId, String idOrNumber) {
        Order order = findCustomerOrderEntity(userId, idOrNumber);
        return OrderDetailDto.from(order);
    }

    @Transactional(readOnly = true)
    public Order findCustomerOrderEntity(Long userId, String idOrNumber) {
        Order order;
        if (idOrNumber.chars().allMatch(Character::isDigit)) {
            order = orderRepository.findByIdAndUserId(Long.valueOf(idOrNumber), userId)
                    .orElseThrow(() -> new ResourceNotFoundException("Order", idOrNumber));
        } else {
            order = orderRepository.findByOrderNumberAndUserId(idOrNumber, userId)
                    .orElseThrow(() -> new ResourceNotFoundException("Order", idOrNumber));
        }
        order.getItems().size();
        return order;
    }

    @Transactional(readOnly = true)
    public Order requireOrderEntity(Long orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderId));
    }

    @Transactional(readOnly = true)
    public Order getAdminOrderEntity(Long orderId) {
        Order order = requireOrderEntity(orderId);
        order.getItems().size();
        return order;
    }

    @Transactional(readOnly = true)
    public OrderDetailDto getAdminOrder(Long orderId) {
        return OrderDetailDto.from(requireOrderEntity(orderId));
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderSummaryDto> searchAdminOrders(String search, OrderStatus status,
                                                           PaymentStatus paymentStatus, Pageable pageable) {
        String q = search != null && !search.isBlank() ? search.trim() : null;
        return PageResponse.of(orderRepository.searchAdmin(q, status, paymentStatus, pageable), OrderSummaryDto::from);
    }

    /**
     * Periodically releases stock reservations for unpaid PENDING orders whose payment window elapsed.
     */
    @Scheduled(fixedDelay = 60_000)
    @Transactional
    public void releaseExpiredUnpaidOrders() {
        List<Order> expired = orderRepository.findByStatusAndPaymentStatusAndPaymentExpiresAtBefore(
                OrderStatus.PENDING, PaymentStatus.PENDING, Instant.now());
        for (Order order : expired) {
            try {
                handlePaymentFailedOrExpired(order, "Payment reservation window expired", true);
            } catch (Exception ex) {
                log.warn("Error expiring unpaid order {}: {}", order.getOrderNumber(), ex.getMessage());
            }
        }
    }

    private OrderDetailDto performCancellation(Order order, String reason, String actorType, Long actorId) {
        if (!order.getStatus().canTransitionTo(OrderStatus.CANCELLED)) {
            throw BusinessException.badRequest("INVALID_ORDER_STATE_TRANSITION",
                    "Cannot cancel order in status " + order.getStatus());
        }
        OrderStatus prev = order.getStatus();
        if (order.getPaymentStatus() == PaymentStatus.PAID) {
            inventoryService.restoreCommittedStockForOrder(order, "Order cancelled (" + order.getOrderNumber() + ")");
        } else {
            inventoryService.releaseReservationsForOrder(order, StockReservationStatus.RELEASED,
                    "Order cancelled (" + order.getOrderNumber() + ")");
        }
        couponService.releaseForOrder(order.getId());

        order.setStatus(OrderStatus.CANCELLED);
        order.setCancelledAt(Instant.now());
        order.setCancelReason(reason);
        order.getStatusHistory().add(OrderStatusHistory.builder()
                .order(order)
                .fromStatus(prev)
                .toStatus(OrderStatus.CANCELLED)
                .note(reason)
                .changedByType(actorType)
                .changedByUserId(actorId)
                .build());
        order = orderRepository.save(order);

        if ("ADMIN".equals(actorType)) {
            auditLogService.logCurrentActor("ORDER_CANCELLED", "ORDER", order.getId(),
                    "Cancelled order " + order.getOrderNumber() + ": " + reason);
        }
        notificationService.notifyOrderStatusChanged(order, "Order Cancelled",
                "Your order " + order.getOrderNumber() + " has been cancelled. Reason: " + reason);
        return OrderDetailDto.from(order);
    }

    private static String normalizeKey(String k) {
        if (k == null || k.isBlank()) {
            return null;
        }
        String trimmed = k.trim();
        return trimmed.length() > 64 ? trimmed.substring(0, 64) : trimmed;
    }
}
