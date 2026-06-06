package com.spotlink;

import static org.assertj.core.api.Assertions.assertThat;

import com.spotlink.core.AppProperties;
import com.spotlink.notification.MailProvider;
import com.spotlink.notification.MailProviderConfiguration;
import com.spotlink.notification.SafeLoggingMailProvider;
import com.spotlink.notification.SmtpMailProvider;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

class MailProviderConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class);

    @Test
    void smtpProviderIsSelectedWhenConfigured() {
        contextRunner
                .withPropertyValues(completeSmtpProperties())
                .run(context -> {
                    assertThat(context).hasSingleBean(MailProvider.class);
                    MailProvider provider = context.getBean(MailProvider.class);
                    assertThat(provider).isInstanceOf(SmtpMailProvider.class);
                    assertThat(provider.name()).isEqualTo("smtp");
                    assertThat(provider.productionReady()).isTrue();
                });
    }

    @Test
    void safeLogProviderIsNeverProductionReady() {
        contextRunner
                .withPropertyValues("spotlink.mail.provider=safe-log")
                .run(context -> {
                    MailProvider provider = context.getBean(MailProvider.class);
                    assertThat(provider).isInstanceOf(SafeLoggingMailProvider.class);
                    assertThat(provider.productionReady()).isFalse();
                });
    }

    @Test
    void incompleteSmtpProviderIsNotProductionReady() {
        contextRunner
                .withPropertyValues(
                        "spotlink.mail.provider=smtp",
                        "spotlink.mail.smtp.host=smtp.example.net",
                        "spotlink.mail.smtp.from=no-reply@spotlink.app",
                        "spotlink.mail.smtp.auth-enabled=true",
                        "spotlink.mail.smtp.username=spotlink")
                .run(context -> {
                    MailProvider provider = context.getBean(MailProvider.class);
                    assertThat(provider).isInstanceOf(SmtpMailProvider.class);
                    assertThat(provider.productionReady()).isFalse();
                });
    }

    private String[] completeSmtpProperties() {
        return new String[] {
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

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(AppProperties.class)
    @Import(MailProviderConfiguration.class)
    static class TestConfig {
    }
}
