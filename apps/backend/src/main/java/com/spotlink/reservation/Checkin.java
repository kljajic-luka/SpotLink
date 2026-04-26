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
@Table(name = "checkins")
public class Checkin extends AuditableEntity {

    @Column(nullable = false)
    private UUID reservationId;

    @Column(nullable = false)
    private UUID operatorUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private CheckinStatus status = CheckinStatus.CHECKED_IN;

    @Column(nullable = false)
    private Instant checkinAt;

    @Column
    private Instant checkoutAt;

    @Column(length = 1000)
    private String notes;

    public UUID getReservationId() {
        return reservationId;
    }

    public void setReservationId(UUID reservationId) {
        this.reservationId = reservationId;
    }

    public UUID getOperatorUserId() {
        return operatorUserId;
    }

    public void setOperatorUserId(UUID operatorUserId) {
        this.operatorUserId = operatorUserId;
    }

    public CheckinStatus getStatus() {
        return status;
    }

    public void setStatus(CheckinStatus status) {
        this.status = status;
    }

    public Instant getCheckinAt() {
        return checkinAt;
    }

    public void setCheckinAt(Instant checkinAt) {
        this.checkinAt = checkinAt;
    }

    public Instant getCheckoutAt() {
        return checkoutAt;
    }

    public void setCheckoutAt(Instant checkoutAt) {
        this.checkoutAt = checkoutAt;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}