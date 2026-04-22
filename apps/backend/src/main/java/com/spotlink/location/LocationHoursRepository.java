package com.spotlink.location;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LocationHoursRepository extends JpaRepository<LocationHours, UUID> {

    List<LocationHours> findByLocationIdOrderByDayOfWeek(UUID locationId);

    void deleteByLocationId(UUID locationId);
}
