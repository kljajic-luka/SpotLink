package com.spotlink.location;

import com.spotlink.core.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "location_hours")
public class LocationHours extends AuditableEntity {

    @Column(nullable = false)
    private UUID locationId;

    // Dan u nedelji: MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY, SUNDAY
    @Column(nullable = false, length = 9)
    private String dayOfWeek;

    // Vreme otvaranja u formatu HH:mm (lokalno vreme lokacije)
    @Column(nullable = false, length = 5)
    private String openTime;

    // Vreme zatvaranja u formatu HH:mm (lokalno vreme lokacije)
    @Column(nullable = false, length = 5)
    private String closeTime;

    public UUID getLocationId() { return locationId; }
    public void setLocationId(UUID locationId) { this.locationId = locationId; }

    public String getDayOfWeek() { return dayOfWeek; }
    public void setDayOfWeek(String dayOfWeek) { this.dayOfWeek = dayOfWeek; }

    public String getOpenTime() { return openTime; }
    public void setOpenTime(String openTime) { this.openTime = openTime; }

    public String getCloseTime() { return closeTime; }
    public void setCloseTime(String closeTime) { this.closeTime = closeTime; }
}
