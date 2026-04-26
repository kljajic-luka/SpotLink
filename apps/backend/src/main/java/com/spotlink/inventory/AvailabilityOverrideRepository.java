package com.spotlink.inventory;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AvailabilityOverrideRepository extends JpaRepository<AvailabilityOverride, UUID> {

    @Query("""
            select o from AvailabilityOverride o
            where o.inventoryPoolId in :poolIds
              and o.active = true
              and o.startsAt < :endsAt
              and o.endsAt > :startsAt
            order by o.startsAt asc
            """)
    List<AvailabilityOverride> findActiveOverlaps(
            @Param("poolIds") Collection<UUID> poolIds,
            @Param("startsAt") Instant startsAt,
            @Param("endsAt") Instant endsAt);

    @Query("""
            select o from AvailabilityOverride o
            where o.inventoryPoolId = :poolId
              and o.active = true
              and o.startsAt < :endsAt
              and o.endsAt > :startsAt
            order by o.startsAt asc
            """)
    List<AvailabilityOverride> findActiveOverlaps(
            @Param("poolId") UUID poolId,
            @Param("startsAt") Instant startsAt,
            @Param("endsAt") Instant endsAt);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update AvailabilityOverride o
            set o.active = false
            where o.inventoryPoolId = :poolId
              and o.active = true
              and o.overrideType = com.spotlink.inventory.AvailabilityOverrideType.PAUSE
            """)
    int deactivateActivePauses(@Param("poolId") UUID poolId);
}