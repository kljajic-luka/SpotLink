package com.spotlink.support;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public final class SupportDtos {

    private SupportDtos() {
    }

    public record SupportTicketDto(
            UUID id,
            SupportTicketCategory category,
            SupportTicketStatus status,
            String subject,
            UUID reservationId,
            UUID locationId,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record SupportMessageDto(
            UUID id,
            UUID ticketId,
            UUID senderUserId,
            String senderName,
            String body,
            String attachmentUrl,
            Instant createdAt
    ) {
    }

    public record CreateSupportTicketRequest(
            @NotNull SupportTicketCategory category,
            @NotBlank @Size(max = 180) String subject,
            @NotBlank @Size(max = 4000) String body,
            UUID reservationId,
            UUID locationId
    ) {
    }

    public record CreateMessageRequest(
            @NotBlank @Size(max = 4000) String body
    ) {
    }
}
