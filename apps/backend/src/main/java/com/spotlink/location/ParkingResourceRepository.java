package com.spotlink.location;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ParkingResourceRepository extends JpaRepository<ParkingResource, UUID> {

    List<ParkingResource> findByLocationIdAndActiveTrueOrderByLabel(UUID locationId);

    List<ParkingResource> findByLocationIdInAndActiveTrue(Collection<UUID> locationIds);

    long countByLocationIdInAndActiveTrue(Collection<UUID> locationIds);

    long countByActiveTrue();
}
