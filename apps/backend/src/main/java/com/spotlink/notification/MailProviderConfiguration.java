package com.spotlink.notification;

import com.spotlink.core.AppProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MailProviderConfiguration {

    @Bean
    @ConditionalOnMissingBean(MailProvider.class)
    MailProvider mailProvider(AppProperties appProperties) {
        return new SafeLoggingMailProvider(appProperties.getMail().getProvider());
    }
}
