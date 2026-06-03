package com.spotlink.core;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

class RuntimeSafetyGuardTest {

    private static final String STRONG_SECRET = "staging-secret-with-at-least-thirty-two-bytes";

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class);

    @Test
    void devProfileAllowsLocalDefaults() {
        contextRunner
                .withPropertyValues("spring.profiles.active=dev")
                .run(context -> {
                    assertThat(context.getStartupFailure()).isNull();
                    assertThat(context).hasSingleBean(RuntimeSafetyGuard.class);
                });
    }

    @Test
    void stagingRejectsH2Database() {
        contextRunner
                .withPropertyValues(validStagingProperties())
                .withPropertyValues(
                        "spring.datasource.url=jdbc:h2:mem:spotlink",
                        "spring.datasource.username=sa",
                        "spring.datasource.password=",
                        "spring.datasource.driver-class-name=org.h2.Driver")
                .run(context -> assertStartupFailureContains(
                        context.getStartupFailure(),
                        "H2/default database configuration is not allowed"));
    }

    @Test
    void stagingRejectsDefaultJwtSecret() {
        contextRunner
                .withPropertyValues(validStagingProperties())
                .withPropertyValues("spotlink.jwt.secret=" + AppProperties.Jwt.DEFAULT_DEV_SECRET)
                .run(context -> assertStartupFailureContains(
                        context.getStartupFailure(),
                        "JWT_SECRET must not use the development/default placeholder"));
    }

    @Test
    void stagingRejectsLocalCorsOrigins() {
        contextRunner
                .withPropertyValues(validStagingProperties())
                .withPropertyValues("spotlink.cors.allowed-origins=http://localhost:4200")
                .run(context -> assertStartupFailureContains(
                        context.getStartupFailure(),
                        "Localhost CORS origins are not allowed"));
    }

    @Test
    void stagingRejectsInsecureCookies() {
        contextRunner
                .withPropertyValues(validStagingProperties())
                .withPropertyValues("spotlink.cookie.secure=false")
                .run(context -> assertStartupFailureContains(
                        context.getStartupFailure(),
                        "COOKIE_SECURE=true is required"));
    }

    @Test
    void stagingRejectsImplicitMockPaymentDefault() {
        contextRunner
                .withPropertyValues(validStagingProperties())
                .withPropertyValues("spotlink.mock-payment.enabled=true")
                .run(context -> assertThat(context.getStartupFailure()).isNull());

        contextRunner
                .withPropertyValues(validStagingPropertiesWithoutMockPayment())
                .run(context -> assertStartupFailureContains(
                        context.getStartupFailure(),
                        "MOCK_PAYMENT_ENABLED must be explicitly set for staging"));
    }

    @Test
    void productionRejectsMockPaymentEvenWhenExplicit() {
        contextRunner
                .withPropertyValues(validProductionProperties())
                .withPropertyValues("spotlink.mock-payment.enabled=true")
                .run(context -> assertStartupFailureContains(
                        context.getStartupFailure(),
                        "MOCK_PAYMENT_ENABLED=true is not allowed"));
    }

    @Test
    void stagingAllowsHardenedConfigWithExplicitMockPayment() {
        contextRunner
                .withPropertyValues(validStagingProperties())
                .run(context -> {
                    assertThat(context.getStartupFailure()).isNull();
                    assertThat(context).hasSingleBean(RuntimeSafetyGuard.class);
                });
    }

    private String[] validStagingProperties() {
        return append(validStagingPropertiesWithoutMockPayment(), "spotlink.mock-payment.enabled=true");
    }

    private String[] validStagingPropertiesWithoutMockPayment() {
        return new String[] {
                "spring.profiles.active=staging",
                "spring.datasource.url=jdbc:postgresql://staging-db:5432/spotlink",
                "spring.datasource.username=spotlink_staging",
                "spring.datasource.password=staging-password",
                "spring.datasource.driver-class-name=org.postgresql.Driver",
                "spotlink.jwt.secret=" + STRONG_SECRET,
                "spotlink.cors.allowed-origins=https://staging.spotlink.app",
                "spotlink.cookie.secure=true"
        };
    }

    private String[] validProductionProperties() {
        return new String[] {
                "spring.profiles.active=production",
                "spring.datasource.url=jdbc:postgresql://prod-db:5432/spotlink",
                "spring.datasource.username=spotlink_prod",
                "spring.datasource.password=prod-password",
                "spring.datasource.driver-class-name=org.postgresql.Driver",
                "spotlink.jwt.secret=" + STRONG_SECRET,
                "spotlink.cors.allowed-origins=https://spotlink.app",
                "spotlink.cookie.secure=true",
                "spotlink.mock-payment.enabled=false"
        };
    }

    private String[] append(String[] values, String value) {
        String[] copy = new String[values.length + 1];
        System.arraycopy(values, 0, copy, 0, values.length);
        copy[values.length] = value;
        return copy;
    }

    private void assertStartupFailureContains(Throwable failure, String message) {
        assertThat(failure).isNotNull();
        assertThat(failure).hasRootCauseInstanceOf(IllegalStateException.class);
        assertThat(failure).hasMessageContaining(message);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(AppProperties.class)
    @Import(RuntimeSafetyGuard.class)
    static class TestConfig {
    }
}
