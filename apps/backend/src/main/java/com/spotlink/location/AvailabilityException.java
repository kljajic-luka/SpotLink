package com.spotlink.location;

import com.spotlink.core.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "availability_exceptions")
public class AvailabilityException extends AuditableEntity {

    @Column(nullable = false)
    private UUID locationId;

    @Column(length = 180)
    private String label;

    @Column(nullable = false)
    private Instant startsAt;

    @Column(nullable = false)
    private Instant endsAt;

    public UUID getLocationId() { return locationId; }
    public void setLocationId(UUID locationId) { this.locationId = locationId; }

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }

    public Instant getStartsAt() { return startsAt; }
    public void setStartsAt(Instant startsAt) { this.startsAt = startsAt; }

    public Instant getEndsAt() { return endsAt; }
    public void setEndsAt(Instant endsAt) { this.endsAt = endsAt; }
}
