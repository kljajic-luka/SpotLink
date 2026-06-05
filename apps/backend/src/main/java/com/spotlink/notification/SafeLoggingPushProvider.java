package com.spotlink.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

public class SafeLoggingPushProvider implements PushProvider {

    private static final Logger log = LoggerFactory.getLogger(SafeLoggingPushProvider.class);

    private final String providerName;

    public SafeLoggingPushProvider(String providerName) {
        this.providerName = StringUtils.hasText(providerName) ? providerName.trim() : "safe-log";
    }

    @Override
    public PushDeliveryResult deliver(DeviceToken token, PushNotificationPayload payload) {
        log.info(
                "Push delivery captured provider={} tokenHash={} notificationId={} type={}",
                providerName,
                NotificationLogSanitizer.stableHash(token.getDeviceToken()),
                payload.notificationId(),
                payload.type());
        return PushDeliveryResult.success();
    }

    @Override
    public String name() {
        return providerName;
    }
}
