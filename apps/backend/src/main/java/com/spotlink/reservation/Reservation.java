package com.spotlink.reservation;

import com.spotlink.core.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "reservations")
public class Reservation extends AuditableEntity {

    @Column(nullable = false)
    private UUID customerId;

    @Column(nullable = false)
    private UUID operatorId;

    @Column(nullable = false)
    private UUID locationId;

    @Column(nullable = false)
    private UUID resourceId;

    @Column
    private UUID inventoryPoolId;

    @Column
    private UUID holdId;

    @Column
    private UUID vehicleId;

    @Column(nullable = false)
    private Instant startsAt;

    @Column(nullable = false)
    private Instant endsAt;

    @Column(nullable = false, length = 80)
    private String timezone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ReservationStatus status = ReservationStatus.PENDING_PAYMENT;

    @Column(nullable = false)
    private long totalAmountCents;

    @Column(nullable = false, length = 3)
    private String currency = "USD";

    @Column(nullable = false)
    private boolean accessInstructionsVisible;

    @Column
    private Instant paymentExpiresAt;

    @Column(length = 160)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PaymentMode paymentMode = PaymentMode.ONLINE;

    public UUID getCustomerId() {
        return customerId;
    }

    public void setCustomerId(UUID customerId) {
        this.customerId = customerId;
    }

    public UUID getOperatorId() {
        return operatorId;
    }

    public void setOperatorId(UUID operatorId) {
        this.operatorId = operatorId;
    }

    public UUID getLocationId() {
        return locationId;
    }

    public void setLocationId(UUID locationId) {
        this.locationId = locationId;
    }

    public UUID getResourceId() {
        return resourceId;
    }

    public void setResourceId(UUID resourceId) {
        this.resourceId = resourceId;
    }

    public UUID getInventoryPoolId() {
        return inventoryPoolId;
    }

    public void setInventoryPoolId(UUID inventoryPoolId) {
        this.inventoryPoolId = inventoryPoolId;
    }

    public UUID getHoldId() {
        return holdId;
    }

    public void setHoldId(UUID holdId) {
        this.holdId = holdId;
    }

    public UUID getVehicleId() {
        return vehicleId;
    }

    public void setVehicleId(UUID vehicleId) {
        this.vehicleId = vehicleId;
    }

    public Instant getStartsAt() {
        return startsAt;
    }

    public void setStartsAt(Instant startsAt) {
        this.startsAt = startsAt;
    }

    public Instant getEndsAt() {
        return endsAt;
    }

    public void setEndsAt(Instant endsAt) {
        this.endsAt = endsAt;
    }

    public String getTimezone() {
        return timezone;
    }

    public void setTimezone(String timezone) {
        this.timezone = timezone;
    }

    public ReservationStatus getStatus() {
        return status;
    }

    public void setStatus(ReservationStatus status) {
        this.status = status;
    }

    public long getTotalAmountCents() {
        return totalAmountCents;
    }

    public void setTotalAmountCents(long totalAmountCents) {
        this.totalAmountCents = totalAmountCents;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public boolean isAccessInstructionsVisible() {
        return accessInstructionsVisible;
    }

    public void setAccessInstructionsVisible(boolean accessInstructionsVisible) {
        this.accessInstructionsVisible = accessInstructionsVisible;
    }

    public Instant getPaymentExpiresAt() {
        return paymentExpiresAt;
    }

    public void setPaymentExpiresAt(Instant paymentExpiresAt) {
        this.paymentExpiresAt = paymentExpiresAt;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public PaymentMode getPaymentMode() {
        return paymentMode;
    }

    public void setPaymentMode(PaymentMode paymentMode) {
        this.paymentMode = paymentMode;
    }
}
