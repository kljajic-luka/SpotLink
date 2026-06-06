package com.spotlink.core;

import static org.assertj.core.api.Assertions.assertThat;

import com.spotlink.notification.MailProviderConfiguration;
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
    void stagingRejectsPasswordResetDeliveryWithoutProductionReadyProvider() {
        contextRunner
                .withPropertyValues(validStagingProperties())
                .withPropertyValues(
                        "spotlink.password-reset.delivery-enabled=true",
                        "spotlink.mail.provider=safe-log")
                .run(context -> assertStartupFailureContains(
                        context.getStartupFailure(),
                        "PASSWORD_RESET_DELIVERY_ENABLED=true requires a production-ready MailProvider"));
    }

    @Test
    void stagingRejectsIncompleteSmtpWhenPasswordResetDeliveryEnabled() {
        contextRunner
                .withPropertyValues(validStagingProperties())
                .withPropertyValues(
                        "spotlink.password-reset.delivery-enabled=true",
                        "spotlink.mail.provider=smtp",
                        "spotlink.mail.smtp.host=smtp.example.net",
                        "spotlink.mail.smtp.port=587",
                        "spotlink.mail.smtp.from=no-reply@spotlink.app",
                        "spotlink.mail.smtp.starttls-enabled=true",
                        "spotlink.mail.smtp.auth-enabled=true",
                        "spotlink.mail.smtp.username=spotlink")
                .run(context -> assertStartupFailureContains(
                        context.getStartupFailure(),
                        "PASSWORD_RESET_DELIVERY_ENABLED=true requires a production-ready MailProvider"));
    }

    @Test
    void stagingAcceptsCompleteSmtpWhenPasswordResetDeliveryEnabled() {
        contextRunner
                .withPropertyValues(validStagingProperties())
                .withPropertyValues(completeSmtpProperties())
                .run(context -> {
                    assertThat(context.getStartupFailure()).isNull();
                    assertThat(context).hasSingleBean(RuntimeSafetyGuard.class);
                });
    }

    @Test
    void productionAcceptsCompleteSmtpWhenPasswordResetDeliveryEnabled() {
        contextRunner
                .withPropertyValues(validProductionProperties())
                .withPropertyValues(completeSmtpProperties())
                .run(context -> {
                    assertThat(context.getStartupFailure()).isNull();
                    assertThat(context).hasSingleBean(RuntimeSafetyGuard.class);
                });
    }

    @Test
    void stagingRequiresExplicitPushDeliveryPolicy() {
        contextRunner
                .withPropertyValues(append(validStagingPropertiesWithoutPushDelivery(), "spotlink.mock-payment.enabled=true"))
                .run(context -> assertStartupFailureContains(
                        context.getStartupFailure(),
                        "PUSH_DELIVERY_ENABLED must be explicitly set"));
    }

    @Test
    void stagingRejectsEnabledPushWithoutApnsProvider() {
        contextRunner
                .withPropertyValues(validStagingProperties())
                .withPropertyValues(
                        "spotlink.push.delivery-enabled=true",
                        "spotlink.push.provider=safe-log")
                .run(context -> assertStartupFailureContains(
                        context.getStartupFailure(),
                        "PUSH_DELIVERY_ENABLED=true requires PUSH_PROVIDER=apns"));
    }

    @Test
    void stagingRejectsIncompleteApnsConfigWhenPushEnabled() {
        contextRunner
                .withPropertyValues(validStagingProperties())
                .withPropertyValues(
                        "spotlink.push.delivery-enabled=true",
                        "spotlink.push.provider=apns",
                        "spotlink.push.apns.environment=sandbox")
                .run(context -> assertStartupFailureContains(
                        context.getStartupFailure(),
                        "APNS_BUNDLE_ID is required"));
    }

    @Test
    void productionRejectsSandboxApnsEnvironment() {
        contextRunner
                .withPropertyValues(validProductionProperties())
                .withPropertyValues(completeApnsProperties("sandbox"))
                .run(context -> assertStartupFailureContains(
                        context.getStartupFailure(),
                        "APNS_ENVIRONMENT=sandbox is not allowed"));
    }

    @Test
    void stagingAllowsCompleteApnsConfigWhenPushEnabled() {
        contextRunner
                .withPropertyValues(validStagingProperties())
                .withPropertyValues(completeApnsProperties("sandbox"))
                .run(context -> {
                    assertThat(context.getStartupFailure()).isNull();
                    assertThat(context).hasSingleBean(RuntimeSafetyGuard.class);
                });
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

    private String[] validStagingPropertiesWithoutPushDelivery() {
        return new String[] {
                "spring.profiles.active=staging",
                "spring.datasource.url=jdbc:postgresql://staging-db:5432/spotlink",
                "spring.datasource.username=spotlink_staging",
                "spring.datasource.password=staging-password",
                "spring.datasource.driver-class-name=org.postgresql.Driver",
                "spotlink.jwt.secret=" + STRONG_SECRET,
                "spotlink.cors.allowed-origins=https://staging.spotlink.app",
                "spotlink.cookie.secure=true",
                "spotlink.password-reset.delivery-enabled=false"
        };
    }

    private String[] validStagingPropertiesWithoutMockPayment() {
        return append(validStagingPropertiesWithoutPushDelivery(), "spotlink.push.delivery-enabled=false");
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
                "spotlink.mock-payment.enabled=false",
                "spotlink.password-reset.delivery-enabled=false",
                "spotlink.push.delivery-enabled=false"
        };
    }

    private String[] completeApnsProperties(String apnsEnvironment) {
        return new String[] {
                "spotlink.push.delivery-enabled=true",
                "spotlink.push.provider=apns",
                "spotlink.push.apns.environment=" + apnsEnvironment,
                "spotlink.push.apns.bundle-id=com.spotlink.app.staging",
                "spotlink.push.apns.team-id=TEAMID1234",
                "spotlink.push.apns.key-id=KEYID12345",
                "spotlink.push.apns.private-key-path=/run/secrets/apns/apns-auth-key-placeholder"
        };
    }

    private String[] completeSmtpProperties() {
        return new String[] {
                "spotlink.password-reset.delivery-enabled=true",
                "spotlink.mail.provider=smtp",
                "spotlink.mail.smtp.host=smtp.example.net",
                "spotlink.mail.smtp.port=587",
                "spotlink.mail.smtp.from=no-reply@spotlink.app",
                "spotlink.mail.smtp.starttls-enabled=true",
                "spotlink.mail.smtp.auth-enabled=true",
                "spotlink.mail.smtp.username=spotlink-sender",
                "spotlink.mail.smtp.password=smtp-password-for-tests",
                "spotlink.mail.smtp.connection-timeout-ms=5000",
                "spotlink.mail.smtp.read-timeout-ms=5000",
                "spotlink.mail.smtp.write-timeout-ms=5000"
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
    @Import({RuntimeSafetyGuard.class, MailProviderConfiguration.class})
    static class TestConfig {
    }
}
