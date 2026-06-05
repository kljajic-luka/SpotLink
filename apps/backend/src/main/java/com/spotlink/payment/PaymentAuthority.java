package com.spotlink.payment;

import com.spotlink.core.AppProperties;
import com.spotlink.core.ConflictException;
import com.spotlink.core.OperationalMetrics;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

@Service
public class PaymentAuthority {

    private static final Set<String> PRODUCTION_PROFILES = Set.of("prod", "production");
    private static final String PROVIDER_NONE = "NONE";
    private static final String PROVIDER_UNCONFIGURED = "UNCONFIGURED";

    private final AppProperties appProperties;
    private final PaymentProvider paymentProvider;
    private final Environment environment;
    private final OperationalMetrics metrics;

    public PaymentAuthority(
            AppProperties appProperties,
            PaymentProvider paymentProvider,
            Environment environment,
            OperationalMetrics metrics) {
        this.appProperties = appProperties;
        this.paymentProvider = paymentProvider;
        this.environment = environment;
        this.metrics = metrics;
    }

    public PaymentDtos.PaymentCapabilitiesDto capabilities() {
        boolean onlinePaymentsEnabled = onlinePaymentsEnabled();
        PaymentProvider.PaymentProviderOperations operations = onlinePaymentsEnabled
                ? paymentProvider.operations()
                : disabledOperations();
        return new PaymentDtos.PaymentCapabilitiesDto(
                onlinePaymentsEnabled,
                activeProviderName(onlinePaymentsEnabled),
                onlinePaymentsEnabled && paymentProvider.mockProvider(),
                mockPaymentMethodsAllowed(),
                new PaymentDtos.PaymentOperationCapabilitiesDto(
                        operations.authorize(),
                        operations.capture(),
                        operations.cancel(),
                        operations.refund(),
                        operations.webhook(),
                        operations.reconciliation()));
    }

    public boolean onlinePaymentsEnabled() {
        if (!appProperties.getPayment().isOnlineEnabled()) {
            return false;
        }
        if (providerConfiguredAsMock()) {
            return mockPaymentAllowed();
        }
        return false;
    }

    public boolean mockPaymentMethodsAllowed() {
        return onlinePaymentsEnabled() && paymentProvider.mockProvider() && mockPaymentAllowed();
    }

    public void requireOnlinePaymentsEnabled() {
        if (!onlinePaymentsEnabled()) {
            metrics.increment(
                    "spotlink.payment.authority.disabled",
                    "provider", activeProviderName(false));
            throw new ConflictException(
                    "ONLINE_PAYMENTS_DISABLED",
                    "Online payments are disabled because no configured payment provider is available.");
        }
    }

    public String activeProviderName() {
        return activeProviderName(onlinePaymentsEnabled());
    }

    private String activeProviderName(boolean onlinePaymentsEnabled) {
        if (onlinePaymentsEnabled) {
            return paymentProvider.name();
        }
        String configuredProvider = configuredProvider();
        return configuredProvider.isBlank() || "none".equals(configuredProvider)
                ? PROVIDER_NONE
                : PROVIDER_UNCONFIGURED;
    }

    private boolean providerConfiguredAsMock() {
        return "mock".equals(configuredProvider()) && paymentProvider.mockProvider();
    }

    private boolean mockPaymentAllowed() {
        return appProperties.isMockPaymentEnabled() && !hasProductionProfile();
    }

    private boolean hasProductionProfile() {
        return Arrays.stream(environment.getActiveProfiles())
                .map(profile -> profile.toLowerCase(Locale.ROOT))
                .anyMatch(PRODUCTION_PROFILES::contains);
    }

    private String configuredProvider() {
        String provider = appProperties.getPayment().getProvider();
        return provider == null ? "" : provider.trim().toLowerCase(Locale.ROOT);
    }

    private PaymentProvider.PaymentProviderOperations disabledOperations() {
        return new PaymentProvider.PaymentProviderOperations(false, false, false, false, false, false);
    }
}
