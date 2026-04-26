package com.spotlink.inventory;

import com.spotlink.core.NotFoundException;
import com.spotlink.location.ParkingResource;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventoryPoolService {

    private final InventoryPoolRepository inventoryPools;
    private final AvailabilityOverrideRepository availabilityOverrides;
    private final Clock clock;

    public InventoryPoolService(
            InventoryPoolRepository inventoryPools,
            AvailabilityOverrideRepository availabilityOverrides,
            Clock clock) {
        this.inventoryPools = inventoryPools;
        this.availabilityOverrides = availabilityOverrides;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public InventoryPool requireByResourceId(UUID resourceId) {
        return inventoryPools.findBySourceResourceId(resourceId)
                .orElseThrow(() -> new NotFoundException("Inventory pool was not found."));
    }

    @Transactional(readOnly = true)
    public InventoryPool require(UUID poolId) {
        return inventoryPools.findById(poolId)
                .orElseThrow(() -> new NotFoundException("Inventory pool was not found."));
    }

    @Transactional
    public InventoryPool requireByResourceIdForUpdate(UUID resourceId) {
        return inventoryPools.findBySourceResourceIdForUpdate(resourceId)
                .orElseThrow(() -> new NotFoundException("Inventory pool was not found."));
    }

    @Transactional
    public InventoryPool syncFromResource(ParkingResource resource) {
        InventoryPool pool = inventoryPools.findBySourceResourceId(resource.getId()).orElseGet(InventoryPool::new);
        pool.setLocationId(resource.getLocationId());
        pool.setSourceResourceId(resource.getId());
        pool.setLabel(resource.getLabel());
        pool.setAllowedVehicleTypes(resource.getAllowedVehicleTypes());
        pool.setEvOnly(resource.isEvOnly());
        pool.setMaxHeightMeters(resource.getMaxHeightMeters());
        pool.setMaxLengthMeters(resource.getMaxLengthMeters());
        pool.setHourlyRateCents(resource.getHourlyRateCents());
        pool.setDailyRateCents(resource.getDailyRateCents());
        pool.setCurrency(resource.getCurrency());
        pool.setBaseCapacity(resource.getCapacity());
        pool.setConfirmationMode(resource.getConfirmationMode());
        pool.setActive(resource.isActive());
        return inventoryPools.save(pool);
    }

    @Transactional(readOnly = true)
    public List<InventoryPool> findByLocationIds(Collection<UUID> locationIds) {
        return locationIds.isEmpty() ? List.of() : inventoryPools.findByLocationIdInAndActiveTrue(locationIds);
    }

    @Transactional(readOnly = true)
    public AvailabilityDecision availabilityForWindow(InventoryPool pool, Instant startsAt, Instant endsAt) {
        if (!pool.isActive()) {
            return new AvailabilityDecision(0, true, "Pool is inactive");
        }
        int capacity = pool.getBaseCapacity();
        String pauseReason = pool.isPaused() ? pool.getPauseReason() : null;
        boolean paused = pool.isPaused();
        for (AvailabilityOverride override : availabilityOverrides.findActiveOverlaps(pool.getId(), startsAt, endsAt)) {
            if (override.getOverrideType() == AvailabilityOverrideType.PAUSE) {
                paused = true;
                pauseReason = override.getReason();
                capacity = 0;
                break;
            }
            if (override.getOverrideType() == AvailabilityOverrideType.CAPACITY_CAP && override.getSellableCapacity() != null) {
                capacity = Math.min(capacity, override.getSellableCapacity());
            }
        }
        return new AvailabilityDecision(Math.max(capacity, 0), paused, pauseReason);
    }

    @Transactional
    public AvailabilityOverride pause(InventoryPool pool, UUID actorUserId, AvailabilityOverrideSource source, String reason) {
        Instant now = Instant.now(clock);
        pool.setPaused(true);
        pool.setPauseReason(reason);
        inventoryPools.save(pool);
        AvailabilityOverride override = new AvailabilityOverride();
        override.setInventoryPoolId(pool.getId());
        override.setActorUserId(actorUserId);
        override.setOverrideType(AvailabilityOverrideType.PAUSE);
        override.setStartsAt(now);
        override.setEndsAt(now.plus(3650, ChronoUnit.DAYS));
        override.setReason(reason);
        override.setSource(source);
        override.setActive(true);
        return availabilityOverrides.save(override);
    }

    @Transactional
    public void unpause(InventoryPool pool) {
        availabilityOverrides.deactivateActivePauses(pool.getId());
        pool.setPaused(false);
        pool.setPauseReason(null);
        inventoryPools.save(pool);
    }

    @Transactional
    public AvailabilityOverride capCapacity(
            InventoryPool pool,
            UUID actorUserId,
            AvailabilityOverrideSource source,
            Integer sellableCapacity,
            String reason) {
        AvailabilityOverride override = new AvailabilityOverride();
        override.setInventoryPoolId(pool.getId());
        override.setActorUserId(actorUserId);
        override.setOverrideType(AvailabilityOverrideType.CAPACITY_CAP);
        override.setStartsAt(Instant.now(clock));
        override.setEndsAt(Instant.now(clock).plus(3650, ChronoUnit.DAYS));
        override.setSellableCapacity(sellableCapacity);
        override.setReason(reason);
        override.setSource(source);
        override.setActive(true);
        return availabilityOverrides.save(override);
    }

    public record AvailabilityDecision(int sellableCapacity, boolean paused, String reason) {
    }
}