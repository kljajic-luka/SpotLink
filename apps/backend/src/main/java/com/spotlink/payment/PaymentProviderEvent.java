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
@Table(name = "payment_provider_events")
public class PaymentProviderEvent extends AuditableEntity {

    @Column
    private UUID paymentAttemptId;

    @Column(nullable = false, length = 64)
    private String provider;

    @Column(nullable = false, length = 160)
    private String externalEventId;

    @Column(nullable = false, length = 120)
    private String eventType;

    @Column(length = 4000)
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PaymentProviderEventStatus status = PaymentProviderEventStatus.RECEIVED;

    @Column
    private Instant processedAt;

    public UUID getPaymentAttemptId() {
        return paymentAttemptId;
    }

    public void setPaymentAttemptId(UUID paymentAttemptId) {
        this.paymentAttemptId = paymentAttemptId;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getExternalEventId() {
        return externalEventId;
    }

    public void setExternalEventId(String externalEventId) {
        this.externalEventId = externalEventId;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public PaymentProviderEventStatus getStatus() {
        return status;
    }

    public void setStatus(PaymentProviderEventStatus status) {
        this.status = status;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }

    public void setProcessedAt(Instant processedAt) {
        this.processedAt = processedAt;
    }
}