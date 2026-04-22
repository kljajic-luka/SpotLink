package com.spotlink.payment;

public interface PaymentProvider {

    String name();

    ProviderResult authorize(ProviderRequest request);

    record ProviderRequest(
            String providerIntentId,
            long amountCents,
            String currency,
            String paymentMethodId,
            String idempotencyKey
    ) {
    }

    record ProviderResult(
            PaymentStatus status,
            String providerReference,
            String redirectUrl,
            String message
    ) {
    }
}
