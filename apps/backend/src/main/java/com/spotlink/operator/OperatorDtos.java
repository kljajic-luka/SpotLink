package com.spotlink.operator;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public final class OperatorDtos {

    private OperatorDtos() {
    }

    public record OperatorAccountDto(
            UUID id,
            String displayName,
            String legalName,
            String supportEmail,
            boolean active,
            Instant createdAt
    ) {
    }

    public record OperatorDashboardSummary(
            long activeLocations,
            long activeResources,
            long reservationsToday,
            double occupancyRate,
            long pendingSupportTickets,
            long grossRevenueCents,
            String currency
    ) {
    }

    public record OperatorResourceHealth(
            UUID resourceId,
            String label,
            boolean online,
            UUID currentReservationId,
            Instant nextReservationAt,
            String attentionRequired
    ) {
    }

    public record BookingActionRequest(
            @Size(max = 240) String reason,
            @Size(max = 1000) String notes
    ) {
    }

    public record CapacityOverrideRequest(
            @Min(0) Integer sellableCapacity,
            @Size(max = 240) String reason
    ) {
    }

    public record InventoryControlDto(
            UUID resourceId,
            UUID inventoryPoolId,
            boolean paused,
            String pauseReason,
            int baseCapacity
    ) {
    }
}
