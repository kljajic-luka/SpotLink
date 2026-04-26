package com.spotlink.admin;

import jakarta.validation.constraints.Size;
import com.spotlink.user.RegistrationStatus;
import com.spotlink.user.UserRole;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class AdminDtos {

    private AdminDtos() {
    }

    public record AdminDashboardSummary(
            long users,
            long operators,
            long activeReservations,
            long openSupportTickets,
            long grossMarketplaceVolumeCents,
            String currency
    ) {
    }

    public record AdminAuditEvent(
            UUID id,
            UUID actorUserId,
            String action,
            String resourceType,
            String resourceId,
            Instant createdAt,
            Map<String, Object> metadata
    ) {
    }

    public record AdminUserSummary(
            UUID id,
            String email,
            String name,
            List<UserRole> roles,
            RegistrationStatus status,
            Instant createdAt
    ) {
    }

    public record AdminActionRequest(@Size(max = 240) String reason) {
    }

    public record RefundMarkerRequest(Long amountCents, @Size(max = 240) String reason) {
    }

    public record PauseOperationResult(
            UUID targetId,
            int affectedPools,
            String reason
    ) {
    }
}
