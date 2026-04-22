package com.spotlink.location;

import com.spotlink.core.AuditableEntity;
import com.spotlink.partner.ConfirmationMode;
import com.spotlink.vehicle.VehicleType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "parking_resources")
public class ParkingResource extends AuditableEntity {

    @Column(nullable = false)
    private UUID locationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ParkingResourceType type = ParkingResourceType.PARKING_SPOT;

    @Column(nullable = false, length = 100)
    private String label;

    @Column(length = 40)
    private String floor;

    @Column(length = 40)
    private String bayNumber;

    @Column(precision = 5, scale = 2)
    private BigDecimal maxHeightMeters;

    @Column(precision = 5, scale = 2)
    private BigDecimal maxLengthMeters;

    @Column(length = 200)
    private String allowedVehicleTypes;

    @Column(nullable = false)
    private boolean evOnly;

    @Column(nullable = false)
    private long hourlyRateCents;

    @Column
    private Long dailyRateCents;

    @Column(nullable = false, length = 3)
    private String currency = "USD";

    @Column(nullable = false)
    private boolean instantReserve = true;

    @Column(nullable = false)
    private boolean active = true;

    @Column(nullable = false)
    private int capacity = 1;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ConfirmationMode confirmationMode = ConfirmationMode.INSTANT;

    public UUID getLocationId() {
        return locationId;
    }

    public void setLocationId(UUID locationId) {
        this.locationId = locationId;
    }

    public ParkingResourceType getType() {
        return type;
    }

    public void setType(ParkingResourceType type) {
        this.type = type;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getFloor() {
        return floor;
    }

    public void setFloor(String floor) {
        this.floor = floor;
    }

    public String getBayNumber() {
        return bayNumber;
    }

    public void setBayNumber(String bayNumber) {
        this.bayNumber = bayNumber;
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

    public boolean isInstantReserve() {
        return instantReserve;
    }

    public void setInstantReserve(boolean instantReserve) {
        this.instantReserve = instantReserve;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public int getCapacity() {
        return capacity;
    }

    public void setCapacity(int capacity) {
        this.capacity = capacity;
    }

    public ConfirmationMode getConfirmationMode() {
        return confirmationMode;
    }

    public void setConfirmationMode(ConfirmationMode confirmationMode) {
        this.confirmationMode = confirmationMode;
    }

    public boolean allowsVehicleType(VehicleType vehicleType) {
        if (allowedVehicleTypes == null || allowedVehicleTypes.isBlank()) {
            return true;
        }
        return java.util.Arrays.stream(allowedVehicleTypes.split(","))
                .map(String::trim)
                .anyMatch(type -> type.equals(vehicleType.name()));
    }
}
