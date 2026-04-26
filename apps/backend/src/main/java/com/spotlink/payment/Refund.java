package com.spotlink.payment;

import com.spotlink.core.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "refunds")
public class Refund extends AuditableEntity {

    @Column(nullable = false)
    private UUID reservationId;

    @Column
    private UUID paymentAttemptId;

    @Column(nullable = false)
    private long amountCents;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private RefundStatus status = RefundStatus.MARKED;

    @Column(length = 240)
    private String reason;

    @Column(length = 160)
    private String providerReference;

    @Column
    private UUID markedByUserId;

    @Column(nullable = false)
    private Instant markedAt;

    public UUID getReservationId() {
        return reservationId;
    }

    public void setReservationId(UUID reservationId) {
        this.reservationId = reservationId;
    }

    public UUID getPaymentAttemptId() {
        return paymentAttemptId;
    }

    public void setPaymentAttemptId(UUID paymentAttemptId) {
        this.paymentAttemptId = paymentAttemptId;
    }

    public long getAmountCents() {
        return amountCents;
    }

    public void setAmountCents(long amountCents) {
        this.amountCents = amountCents;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public RefundStatus getStatus() {
        return status;
    }

    public void setStatus(RefundStatus status) {
        this.status = status;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getProviderReference() {
        return providerReference;
    }

    public void setProviderReference(String providerReference) {
        this.providerReference = providerReference;
    }

    public UUID getMarkedByUserId() {
        return markedByUserId;
    }

    public void setMarkedByUserId(UUID markedByUserId) {
        this.markedByUserId = markedByUserId;
    }

    public Instant getMarkedAt() {
        return markedAt;
    }

    public void setMarkedAt(Instant markedAt) {
        this.markedAt = markedAt;
    }
}