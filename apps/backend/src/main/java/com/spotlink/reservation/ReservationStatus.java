package com.spotlink.reservation;

public enum ReservationStatus {
    DRAFT,
    PENDING_PAYMENT,
    PENDING_OPERATOR_CONFIRMATION,
    CONFIRMED,
    ACTIVE,
    COMPLETED,
    CANCELLED,
    REJECTED,
    EXPIRED,
    DISPUTED,
    NO_SHOW
}
