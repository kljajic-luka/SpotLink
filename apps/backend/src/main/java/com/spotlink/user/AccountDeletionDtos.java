package com.spotlink.user;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class AccountDeletionDtos {

    private AccountDeletionDtos() {
    }

    public enum AccountDeletionFulfillmentStatus {
        PROCESSED,
        ALREADY_PROCESSED,
        BLOCKED
    }

    public record AccountDeletionBlocker(
            String code,
            String message,
            long count
    ) {
    }

    public record AccountDeletionFulfillmentResponse(
            UUID ticketId,
            UUID userId,
            AccountDeletionFulfillmentStatus status,
            List<AccountDeletionBlocker> blockers,
            Instant processedAt
    ) {
    }
}
