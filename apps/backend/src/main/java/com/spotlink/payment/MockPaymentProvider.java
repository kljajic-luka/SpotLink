package com.spotlink.payment;

import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class MockPaymentProvider implements PaymentProvider {

    private static final PaymentProviderOperations OPERATIONS = new PaymentProviderOperations(
            true,
            true,
            true,
            true,
            false,
            false);

    @Override
    public String name() {
        return "MOCK";
    }

    @Override
    public boolean mockProvider() {
        return true;
    }

    @Override
    public PaymentProviderOperations operations() {
        return OPERATIONS;
    }

    @Override
    public ProviderResult authorize(ProviderRequest request) {
        String method = request.paymentMethodId();
        if ("pm_card_sca_required".equals(method)) {
            return new ProviderResult(
                    PaymentStatus.REQUIRES_ACTION,
                    null,
                    "https://payments.spotlink.local/mock-acs/" + request.providerIntentId(),
                    "Additional cardholder action is required.");
        }
        if ("pm_card_declined".equals(method)) {
            return new ProviderResult(PaymentStatus.FAILED, null, null, "Card was declined.");
        }
        return new ProviderResult(PaymentStatus.AUTHORIZED, "mock_auth_" + UUID.randomUUID(), null, "Authorized");
    }

    @Override
    public ProviderResult capture(ProviderRequest request) {
        return new ProviderResult(PaymentStatus.CAPTURED, "mock_capture_" + UUID.randomUUID(), null, "Captured");
    }

    @Override
    public ProviderResult cancel(ProviderRequest request) {
        return new ProviderResult(PaymentStatus.CANCELLED, "mock_cancel_" + request.providerIntentId(), null, "Cancelled");
    }

    @Override
    public RefundResult refund(RefundRequest request) {
        return new RefundResult(RefundStatus.PROCESSED, "mock_refund_" + UUID.randomUUID(), "Refund processed");
    }

    @Override
    public ProviderEventResult handleWebhook(ProviderWebhookRequest request) {
        return new ProviderEventResult(
                PaymentProviderEventStatus.PROCESSED,
                request.eventId(),
                request.eventType(),
                "Mock webhook accepted");
    }

    @Override
    public ProviderEventResult reconcile(ProviderReconciliationRequest request) {
        return new ProviderEventResult(
                PaymentProviderEventStatus.PROCESSED,
                request.providerReference(),
                "MOCK_RECONCILIATION",
                "Mock reconciliation accepted");
    }
}
