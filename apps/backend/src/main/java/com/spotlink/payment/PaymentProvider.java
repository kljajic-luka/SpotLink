package com.spotlink.payment;

public interface PaymentProvider {

    String name();

    default boolean mockProvider() {
        return false;
    }

    PaymentProviderOperations operations();

    ProviderResult authorize(ProviderRequest request);

    ProviderResult capture(ProviderRequest request);

    ProviderResult cancel(ProviderRequest request);

    RefundResult refund(RefundRequest request);

    ProviderEventResult handleWebhook(ProviderWebhookRequest request);

    ProviderEventResult reconcile(ProviderReconciliationRequest request);

    record PaymentProviderOperations(
            boolean authorize,
            boolean capture,
            boolean cancel,
            boolean refund,
            boolean webhook,
            boolean reconciliation
    ) {
    }

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

    record RefundRequest(
            String providerReference,
            long amountCents,
            String currency,
            String reason,
            String idempotencyKey
    ) {
    }

    record RefundResult(
            RefundStatus status,
            String providerReference,
            String message
    ) {
    }

    record ProviderWebhookRequest(
            String eventId,
            String eventType,
            String payload
    ) {
    }

    record ProviderReconciliationRequest(
            String providerReference,
            String cursor
    ) {
    }

    record ProviderEventResult(
            PaymentProviderEventStatus status,
            String externalEventId,
            String eventType,
            String message
    ) {
    }
}
