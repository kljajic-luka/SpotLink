package com.spotlink.payment;

import com.spotlink.core.AppProperties;
import java.util.Locale;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class MockPaymentProductionGuard implements InitializingBean {

    private final AppProperties appProperties;
    private final Environment environment;

    public MockPaymentProductionGuard(AppProperties appProperties, Environment environment) {
        this.appProperties = appProperties;
        this.environment = environment;
    }

    @Override
    public void afterPropertiesSet() {
        if (appProperties.isMockPaymentEnabled() && isProductionProfile()) {
            throw new IllegalStateException(
                    "spotlink.mock-payment.enabled=true is not allowed with active profile prod/production. "
                            + "Set MOCK_PAYMENT_ENABLED=false before starting production.");
        }
    }

    private boolean isProductionProfile() {
        for (String profile : environment.getActiveProfiles()) {
            String normalized = profile.toLowerCase(Locale.ROOT);
            if ("prod".equals(normalized) || "production".equals(normalized)) {
                return true;
            }
        }
        return false;
    }
}
