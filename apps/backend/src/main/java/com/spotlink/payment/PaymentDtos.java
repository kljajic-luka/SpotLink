package com.spotlink.payment;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
}
