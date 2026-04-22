package com.spotlink.operator;

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
}
