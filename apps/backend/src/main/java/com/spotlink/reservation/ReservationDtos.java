package com.spotlink.reservation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

public final class ReservationDtos {

    private ReservationDtos() {
    }

    public record ReservationDto(
            UUID id,
            UUID customerId,
            UUID operatorId,
            UUID locationId,
            UUID resourceId,
            UUID vehicleId,
            Instant startsAt,
            Instant endsAt,
            String timezone,
            ReservationStatus status,
            long totalAmountCents,
            String currency,
            boolean accessInstructionsVisible,
            Instant paymentExpiresAt,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record ReservationQuoteRequest(
            @NotNull UUID resourceId,
            UUID vehicleId,
            @NotNull Instant startsAt,
            @NotNull Instant endsAt,
            String promoCode
    ) {
    }

    public record ReservationQuote(
            UUID resourceId,
            Instant startsAt,
            Instant endsAt,
            long subtotalCents,
            long feesCents,
            long discountCents,
            long totalAmountCents,
            String currency,
            Instant expiresAt
    ) {
    }

    public record CreateReservationRequest(
            @NotNull UUID resourceId,
            UUID vehicleId,
            @NotNull Instant startsAt,
            @NotNull Instant endsAt,
            String promoCode,
            String quoteId,
            String paymentMethodId,
            @NotBlank String idempotencyKey
    ) {
    }

    public record CancelReservationRequest(String reason) {
    }
}
