package com.spotlink.payment;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.spotlink.reservation.PaymentMode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class PaymentDtos {

    private PaymentDtos() {
    }

    public record PaymentMethodDto(
            String id,
            String brand,
            String last4,
            Integer expMonth,
            Integer expYear,
            @JsonProperty("default") boolean defaultMethod
    ) {
    }

    public record PaymentIntentDto(
            UUID id,
            UUID reservationId,
            long amountCents,
            String currency,
            PaymentStatus status,
            String redirectUrl,
            String clientSecret
    ) {
    }

    public record CreatePaymentIntentRequest(
            @NotNull UUID reservationId,
            String paymentMethodId,
            @NotBlank String idempotencyKey
    ) {
    }

    public record PaymentProviderResult(
            PaymentStatus status,
            UUID paymentIntentId,
            String redirectUrl,
            String message
    ) {
    }

    public record PaymentProviderEventDto(
            UUID id,
            String provider,
            String externalEventId,
            String eventType,
            PaymentProviderEventStatus status,
            Instant processedAt
    ) {
    }

    public record PaymentAttemptDto(
            UUID id,
            UUID reservationId,
            String provider,
            PaymentAttemptStatus status,
            PaymentMode paymentMode,
            long amountCents,
            String currency,
            String providerReference,
            String failureCode,
            String failureMessage,
            Instant lastTransitionAt,
            List<PaymentProviderEventDto> providerEvents
    ) {
    }

    public record RefundDto(
            UUID id,
            UUID reservationId,
            UUID paymentAttemptId,
            long amountCents,
            String currency,
            RefundStatus status,
            String reason,
            String providerReference,
            UUID markedByUserId,
            Instant markedAt
    ) {
    }
}
