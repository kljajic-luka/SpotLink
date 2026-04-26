package com.spotlink.inventory;

import com.spotlink.core.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "availability_overrides")
public class AvailabilityOverride extends AuditableEntity {

    @Column(nullable = false)
    private UUID inventoryPoolId;

    @Column
    private UUID actorUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AvailabilityOverrideType overrideType;

    @Column(nullable = false)
    private Instant startsAt;

    @Column(nullable = false)
    private Instant endsAt;

    @Column
    private Integer sellableCapacity;

    @Column(length = 240)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AvailabilityOverrideSource source;

    @Column(nullable = false)
    private boolean active = true;

    public UUID getInventoryPoolId() {
        return inventoryPoolId;
    }

    public void setInventoryPoolId(UUID inventoryPoolId) {
        this.inventoryPoolId = inventoryPoolId;
    }

    public UUID getActorUserId() {
        return actorUserId;
    }

    public void setActorUserId(UUID actorUserId) {
        this.actorUserId = actorUserId;
    }

    public AvailabilityOverrideType getOverrideType() {
        return overrideType;
    }

    public void setOverrideType(AvailabilityOverrideType overrideType) {
        this.overrideType = overrideType;
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

    public Integer getSellableCapacity() {
        return sellableCapacity;
    }

    public void setSellableCapacity(Integer sellableCapacity) {
        this.sellableCapacity = sellableCapacity;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public AvailabilityOverrideSource getSource() {
        return source;
    }

    public void setSource(AvailabilityOverrideSource source) {
        this.source = source;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}