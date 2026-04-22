package com.spotlink.support;

import com.spotlink.core.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "support_tickets")
public class SupportTicket extends AuditableEntity {

    @Column(nullable = false)
    private UUID requesterUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private SupportTicketCategory category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private SupportTicketStatus status = SupportTicketStatus.OPEN;

    @Column(nullable = false, length = 180)
    private String subject;

    @Column
    private UUID reservationId;

    @Column
    private UUID locationId;

    public UUID getRequesterUserId() {
        return requesterUserId;
    }

    public void setRequesterUserId(UUID requesterUserId) {
        this.requesterUserId = requesterUserId;
    }

    public SupportTicketCategory getCategory() {
        return category;
    }

    public void setCategory(SupportTicketCategory category) {
        this.category = category;
    }

    public SupportTicketStatus getStatus() {
        return status;
    }

    public void setStatus(SupportTicketStatus status) {
        this.status = status;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public UUID getReservationId() {
        return reservationId;
    }

    public void setReservationId(UUID reservationId) {
        this.reservationId = reservationId;
    }

    public UUID getLocationId() {
        return locationId;
    }

    public void setLocationId(UUID locationId) {
        this.locationId = locationId;
    }
}
