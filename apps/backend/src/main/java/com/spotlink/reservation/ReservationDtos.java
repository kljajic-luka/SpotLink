package com.spotlink.reservation;

import com.spotlink.payment.PaymentDtos;
import com.spotlink.support.SupportDtos;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.Map;
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
            UUID inventoryPoolId,
            UUID holdId,
            UUID vehicleId,
            Instant startsAt,
            Instant endsAt,
            String timezone,
            String bookingCode,
            ReservationCancellationPolicy cancellationPolicy,
            Instant cancellableUntil,
            long refundEligibleCents,
            ReservationStatus status,
            PaymentMode paymentMode,
            long totalAmountCents,
            String currency,
            boolean accessInstructionsVisible,
            Instant paymentExpiresAt,
            Instant operatorConfirmationExpiresAt,
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
            PaymentMode paymentMode,
            @NotBlank String idempotencyKey
    ) {
    }

    public record CancelReservationRequest(String reason) {
    }

    public record BookingHoldDto(
            UUID id,
            UUID inventoryPoolId,
            BookingHoldStatus status,
            Instant expiresAt,
            PaymentMode paymentMode
    ) {
    }

    public record BookingEventDto(
            UUID id,
            BookingEventType eventType,
            BookingActorType actorType,
            UUID actorId,
            String notes,
            Map<String, Object> payload,
            Instant occurredAt
    ) {
    }

    public record CheckinDto(
            UUID id,
            CheckinStatus status,
            UUID operatorUserId,
            Instant checkinAt,
            Instant checkoutAt,
            String notes
    ) {
    }

    public record BookingDetailDto(
            ReservationDto reservation,
            BookingHoldDto hold,
            CheckinDto checkin,
            List<BookingEventDto> timeline,
            List<PaymentDtos.PaymentAttemptDto> paymentAttempts,
            List<PaymentDtos.RefundDto> refunds,
            List<SupportDtos.SupportTicketDto> supportCases
    ) {
    }
}
