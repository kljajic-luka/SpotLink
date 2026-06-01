package com.spotlink.payment;

import static org.assertj.core.api.Assertions.assertThat;

import com.spotlink.core.AppProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

class MockPaymentProductionGuardTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class);

    @ParameterizedTest
    @ValueSource(strings = {"prod", "production"})
    void productionProfileRejectsMockPaymentEnabled(String profile) {
        contextRunner
                .withPropertyValues(
                        "spring.profiles.active=%s".formatted(profile),
                        "spotlink.mock-payment.enabled=true")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseInstanceOf(IllegalStateException.class)
                            .hasMessageContaining("spotlink.mock-payment.enabled=true is not allowed");
                });
    }

    @Test
    void nonProductionProfileAllowsMockPaymentEnabled() {
        contextRunner
                .withPropertyValues(
                        "spring.profiles.active=test",
                        "spotlink.mock-payment.enabled=true")
                .run(context -> {
                    assertThat(context.getStartupFailure()).isNull();
                    assertThat(context).hasSingleBean(MockPaymentProductionGuard.class);
                });
    }

    @Test
    void productionProfileAllowsMockPaymentDisabled() {
        contextRunner
                .withPropertyValues(
                        "spring.profiles.active=prod",
                        "spotlink.mock-payment.enabled=false")
                .run(context -> {
                    assertThat(context.getStartupFailure()).isNull();
                    assertThat(context).hasSingleBean(MockPaymentProductionGuard.class);
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(AppProperties.class)
    @Import(MockPaymentProductionGuard.class)
    static class TestConfig {
    }
}
