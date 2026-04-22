package com.spotlink.payment;

public enum PaymentStatus {
    REQUIRES_METHOD,
    REQUIRES_ACTION,
    AUTHORIZED,
    CAPTURED,
    FAILED,
    REFUNDED,
    CANCELLED
}
