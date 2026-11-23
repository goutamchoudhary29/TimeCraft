package com.timecraft.payment.entity;

public enum PaymentAttemptStatus {
    CREATED,
    CAPTURED,
    FAILED,
    CANCELLED,
    PARTIALLY_REFUNDED,
    REFUNDED
}
