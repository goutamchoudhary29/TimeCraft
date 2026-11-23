package com.timecraft.order.entity;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Validated order state machine. Prevents invalid or backward status transitions.
 */
public enum OrderStatus {
    PENDING,
    CONFIRMED,
    PROCESSING,
    PACKED,
    SHIPPED,
    OUT_FOR_DELIVERY,
    DELIVERED,
    CANCELLED,
    RETURN_REQUESTED,
    RETURNED,
    REFUNDED,
    FAILED;

    private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED = new EnumMap<>(OrderStatus.class);

    static {
        ALLOWED.put(PENDING, EnumSet.of(CONFIRMED, CANCELLED, FAILED));
        ALLOWED.put(CONFIRMED, EnumSet.of(PROCESSING, PACKED, CANCELLED));
        ALLOWED.put(PROCESSING, EnumSet.of(PACKED, CANCELLED));
        ALLOWED.put(PACKED, EnumSet.of(SHIPPED, CANCELLED));
        ALLOWED.put(SHIPPED, EnumSet.of(OUT_FOR_DELIVERY, DELIVERED));
        ALLOWED.put(OUT_FOR_DELIVERY, EnumSet.of(DELIVERED));
        ALLOWED.put(DELIVERED, EnumSet.of(RETURN_REQUESTED));
        ALLOWED.put(RETURN_REQUESTED, EnumSet.of(RETURNED, DELIVERED));
        ALLOWED.put(RETURNED, EnumSet.of(REFUNDED));
        ALLOWED.put(CANCELLED, EnumSet.of(REFUNDED));
        ALLOWED.put(REFUNDED, EnumSet.noneOf(OrderStatus.class));
        ALLOWED.put(FAILED, EnumSet.noneOf(OrderStatus.class));
    }

    public boolean canTransitionTo(OrderStatus next) {
        if (next == null || this == next) {
            return false;
        }
        return ALLOWED.getOrDefault(this, Set.of()).contains(next);
    }

    public boolean isCustomerCancellable() {
        return this == PENDING || this == CONFIRMED || this == PROCESSING || this == PACKED;
    }
}
