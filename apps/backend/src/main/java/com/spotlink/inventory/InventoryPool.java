package com.spotlink.inventory;

import com.spotlink.core.AuditableEntity;
import com.spotlink.partner.ConfirmationMode;
import com.spotlink.vehicle.VehicleType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.UUID;

@Entity
@Table(name = "inventory_pools")
public class InventoryPool extends AuditableEntity {

    @Column(nullable = false)
    private UUID locationId;

    @Column
    private UUID sourceResourceId;

    @Column(nullable = false, length = 120)
    private String label;

    @Column(length = 200)
    private String allowedVehicleTypes;

    @Column(nullable = false)
    private boolean evOnly;

    @Column(precision = 5, scale = 2)
    private BigDecimal maxHeightMeters;

    @Column(precision = 5, scale = 2)
    private BigDecimal maxLengthMeters;

    @Column(nullable = false)
    private long hourlyRateCents;

    @Column
    private Long dailyRateCents;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false)
    private int baseCapacity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ConfirmationMode confirmationMode = ConfirmationMode.INSTANT;

    @Column(nullable = false)
    private boolean payOnArrivalEnabled = true;

    @Column(nullable = false)
    private boolean active = true;

    @Column(nullable = false)
    private boolean paused;

    @Column(length = 240)
    private String pauseReason;

    public UUID getLocationId() {
        return locationId;
    }

    public void setLocationId(UUID locationId) {
        this.locationId = locationId;
    }

    public UUID getSourceResourceId() {
        return sourceResourceId;
    }

    public void setSourceResourceId(UUID sourceResourceId) {
        this.sourceResourceId = sourceResourceId;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getAllowedVehicleTypes() {
        return allowedVehicleTypes;
    }

    public void setAllowedVehicleTypes(String allowedVehicleTypes) {
        this.allowedVehicleTypes = allowedVehicleTypes;
    }

    public boolean isEvOnly() {
        return evOnly;
    }

    public void setEvOnly(boolean evOnly) {
        this.evOnly = evOnly;
    }

    public BigDecimal getMaxHeightMeters() {
        return maxHeightMeters;
    }

    public void setMaxHeightMeters(BigDecimal maxHeightMeters) {
        this.maxHeightMeters = maxHeightMeters;
    }

    public BigDecimal getMaxLengthMeters() {
        return maxLengthMeters;
    }

    public void setMaxLengthMeters(BigDecimal maxLengthMeters) {
        this.maxLengthMeters = maxLengthMeters;
    }

    public long getHourlyRateCents() {
        return hourlyRateCents;
    }

    public void setHourlyRateCents(long hourlyRateCents) {
        this.hourlyRateCents = hourlyRateCents;
    }

    public Long getDailyRateCents() {
        return dailyRateCents;
    }

    public void setDailyRateCents(Long dailyRateCents) {
        this.dailyRateCents = dailyRateCents;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public int getBaseCapacity() {
        return baseCapacity;
    }

    public void setBaseCapacity(int baseCapacity) {
        this.baseCapacity = baseCapacity;
    }

    public ConfirmationMode getConfirmationMode() {
        return confirmationMode;
    }

    public void setConfirmationMode(ConfirmationMode confirmationMode) {
        this.confirmationMode = confirmationMode;
    }

    public boolean isPayOnArrivalEnabled() {
        return payOnArrivalEnabled;
    }

    public void setPayOnArrivalEnabled(boolean payOnArrivalEnabled) {
        this.payOnArrivalEnabled = payOnArrivalEnabled;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public boolean isPaused() {
        return paused;
    }

    public void setPaused(boolean paused) {
        this.paused = paused;
    }

    public String getPauseReason() {
        return pauseReason;
    }

    public void setPauseReason(String pauseReason) {
        this.pauseReason = pauseReason;
    }

    public boolean allowsVehicleType(VehicleType vehicleType) {
        if (vehicleType == null || allowedVehicleTypes == null || allowedVehicleTypes.isBlank()) {
            return true;
        }
        return Arrays.stream(allowedVehicleTypes.split(","))
                .map(String::trim)
                .anyMatch(type -> type.equals(vehicleType.name()));
    }
}