package com.spotlink.location;

import com.spotlink.core.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "parking_locations")
public class ParkingLocation extends AuditableEntity {

    @Column(nullable = false)
    private UUID operatorId;

    @Column(nullable = false, length = 180)
    private String name;

    @Embedded
    private Address address = new Address();

    @Embedded
    private GeoCoordinates coordinates = new GeoCoordinates();

    @Column(nullable = false, length = 80)
    private String timezone = "UTC";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ParkingAccessType accessType = ParkingAccessType.SELF_PARK;

    @Column(length = 1000)
    private String publicNotes;

    @Column(nullable = false)
    private boolean active = true;

    public UUID getOperatorId() {
        return operatorId;
    }

    public void setOperatorId(UUID operatorId) {
        this.operatorId = operatorId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Address getAddress() {
        return address;
    }

    public void setAddress(Address address) {
        this.address = address;
    }

    public GeoCoordinates getCoordinates() {
        return coordinates;
    }

    public void setCoordinates(GeoCoordinates coordinates) {
        this.coordinates = coordinates;
    }

    public String getTimezone() {
        return timezone;
    }

    public void setTimezone(String timezone) {
        this.timezone = timezone;
    }

    public ParkingAccessType getAccessType() {
        return accessType;
    }

    public void setAccessType(ParkingAccessType accessType) {
        this.accessType = accessType;
    }

    public String getPublicNotes() {
        return publicNotes;
    }

    public void setPublicNotes(String publicNotes) {
        this.publicNotes = publicNotes;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
