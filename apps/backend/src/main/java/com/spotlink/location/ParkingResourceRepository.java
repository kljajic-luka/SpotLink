package com.spotlink.location;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ParkingResourceRepository extends JpaRepository<ParkingResource, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from ParkingResource r where r.id = :resourceId")
    java.util.Optional<ParkingResource> findByIdForUpdate(@Param("resourceId") UUID resourceId);

    List<ParkingResource> findByLocationIdAndActiveTrueOrderByLabel(UUID locationId);

    List<ParkingResource> findByLocationIdInAndActiveTrue(Collection<UUID> locationIds);

    long countByLocationIdInAndActiveTrue(Collection<UUID> locationIds);

    long countByActiveTrue();
}
