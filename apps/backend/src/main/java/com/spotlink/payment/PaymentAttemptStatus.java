package com.spotlink.payment;

public enum PaymentAttemptStatus {
    PENDING,
    REQUIRES_ACTION,
    AUTHORIZED,
    FAILED,
    CANCELLED,
    REFUND_MARKED
}