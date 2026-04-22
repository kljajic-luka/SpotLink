package com.spotlink.payment;

import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class MockPaymentProvider implements PaymentProvider {

    @Override
    public String name() {
        return "MOCK";
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
}
