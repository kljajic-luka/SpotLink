package com.spotlink.inventory;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InventoryPoolRepository extends JpaRepository<InventoryPool, UUID> {

    Optional<InventoryPool> findBySourceResourceId(UUID sourceResourceId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from InventoryPool p where p.id = :poolId")
    Optional<InventoryPool> findByIdForUpdate(@Param("poolId") UUID poolId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from InventoryPool p where p.sourceResourceId = :resourceId")
    Optional<InventoryPool> findBySourceResourceIdForUpdate(@Param("resourceId") UUID resourceId);

    List<InventoryPool> findByLocationIdAndActiveTrueOrderByLabel(UUID locationId);

    List<InventoryPool> findByLocationIdInAndActiveTrue(Collection<UUID> locationIds);
}