package com.spotlink.notification;

import com.spotlink.core.AppProperties;
import java.util.Locale;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

@Configuration
public class PushProviderConfiguration {

    @Bean
    @ConditionalOnMissingBean(PushProvider.class)
    PushProvider pushProvider(AppProperties appProperties) {
        AppProperties.Push push = appProperties.getPush();
        if (!push.isDeliveryEnabled()) {
            return new DisabledPushProvider();
        }

        String provider = StringUtils.hasText(push.getProvider())
                ? push.getProvider().trim().toLowerCase(Locale.ROOT)
                : "none";
        return switch (provider) {
            case "safe-log" -> new SafeLoggingPushProvider(provider);
            case "apns" -> new ApnsPushProvider(push.getApns());
            case "none" -> new DisabledPushProvider();
            default -> throw new IllegalStateException("Unsupported PUSH_PROVIDER: " + provider);
        };
    }
}
