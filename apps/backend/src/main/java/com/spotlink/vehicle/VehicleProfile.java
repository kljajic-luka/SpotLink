package com.spotlink.vehicle;

import com.spotlink.core.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "vehicles")
public class VehicleProfile extends AuditableEntity {

    @Column(nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private VehicleType type;

    @Column(length = 100)
    private String nickname;

    @Column(length = 100)
    private String make;

    @Column(length = 100)
    private String model;

    @Column(length = 60)
    private String color;

    @Column(length = 40)
    private String licensePlate;

    @Column(precision = 5, scale = 2)
    private BigDecimal heightMeters;

    @Column(precision = 5, scale = 2)
    private BigDecimal lengthMeters;

    @Column(nullable = false)
    private boolean evCapable;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private VehicleVerificationStatus verificationStatus = VehicleVerificationStatus.UNVERIFIED;

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public VehicleType getType() {
        return type;
    }

    public void setType(VehicleType type) {
        this.type = type;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public String getMake() {
        return make;
    }

    public void setMake(String make) {
        this.make = make;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public String getLicensePlate() {
        return licensePlate;
    }

    public void setLicensePlate(String licensePlate) {
        this.licensePlate = licensePlate;
    }

    public BigDecimal getHeightMeters() {
        return heightMeters;
    }

    public void setHeightMeters(BigDecimal heightMeters) {
        this.heightMeters = heightMeters;
    }

    public BigDecimal getLengthMeters() {
        return lengthMeters;
    }

    public void setLengthMeters(BigDecimal lengthMeters) {
        this.lengthMeters = lengthMeters;
    }

    public boolean isEvCapable() {
        return evCapable;
    }

    public void setEvCapable(boolean evCapable) {
        this.evCapable = evCapable;
    }

    public VehicleVerificationStatus getVerificationStatus() {
        return verificationStatus;
    }

    public void setVerificationStatus(VehicleVerificationStatus verificationStatus) {
        this.verificationStatus = verificationStatus;
    }
}
