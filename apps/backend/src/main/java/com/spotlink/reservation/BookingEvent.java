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
@Table(name = "booking_events")
public class BookingEvent extends AuditableEntity {

    @Column(nullable = false)
    private UUID reservationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 48)
    private BookingEventType eventType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private BookingActorType actorType;

    @Column
    private UUID actorId;

    @Column(length = 1000)
    private String notes;

    @Column(length = 4000)
    private String payload;

    @Column(nullable = false)
    private Instant occurredAt;

    public UUID getReservationId() {
        return reservationId;
    }

    public void setReservationId(UUID reservationId) {
        this.reservationId = reservationId;
    }

    public BookingEventType getEventType() {
        return eventType;
    }

    public void setEventType(BookingEventType eventType) {
        this.eventType = eventType;
    }

    public BookingActorType getActorType() {
        return actorType;
    }

    public void setActorType(BookingActorType actorType) {
        this.actorType = actorType;
    }

    public UUID getActorId() {
        return actorId;
    }

    public void setActorId(UUID actorId) {
        this.actorId = actorId;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(Instant occurredAt) {
        this.occurredAt = occurredAt;
    }
}