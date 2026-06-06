package com.spotlink.notification;

import com.spotlink.core.OperationalMetrics;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationPushDeliveryService {

    private static final Logger log = LoggerFactory.getLogger(NotificationPushDeliveryService.class);

    private final NotificationRepository notifications;
    private final DeviceTokenRepository deviceTokens;
    private final PushProvider pushProvider;
    private final NotificationPreferencePolicy preferencePolicy;
    private final OperationalMetrics metrics;

    public NotificationPushDeliveryService(
            NotificationRepository notifications,
            DeviceTokenRepository deviceTokens,
            PushProvider pushProvider,
            NotificationPreferencePolicy preferencePolicy,
            OperationalMetrics metrics) {
        this.notifications = notifications;
        this.deviceTokens = deviceTokens;
        this.pushProvider = pushProvider;
        this.preferencePolicy = preferencePolicy;
        this.metrics = metrics;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void deliver(UUID notificationId) {
        Notification notification = notifications.findById(notificationId).orElse(null);
        if (notification == null) {
            return;
        }

        NotificationPreferencePolicy.PreferenceDecision preference = preferencePolicy.pushAllowed(
                notification.getUserId(),
                notification.getType());
        if (!preference.allowed()) {
            increment("preference_skipped", safeReason(preference.reason()));
            return;
        }

        List<DeviceToken> tokens = deviceTokens.findByUserIdAndPlatformAndActiveTrue(
                notification.getUserId(),
                DevicePlatform.IOS);
        if (tokens.isEmpty()) {
            return;
        }

        PushNotificationPayload payload = PushNotificationPayload.from(notification);
        for (DeviceToken token : tokens) {
            deliverToToken(token, payload);
        }
    }

    private void deliverToToken(DeviceToken token, PushNotificationPayload payload) {
        increment("attempted");
        PushDeliveryResult result;
        try {
            result = pushProvider.deliver(token, payload);
        } catch (RuntimeException ex) {
            log.warn(
                    "Push delivery provider threw provider={} tokenHash={} notificationId={} reason={}",
                    pushProvider.name(),
                    NotificationLogSanitizer.stableHash(token.getDeviceToken()),
                    payload.notificationId(),
                    ex.getClass().getSimpleName());
            increment("failed", "provider_exception");
            return;
        }

        switch (result.outcome()) {
            case SUCCESS -> increment("succeeded");
            case PERMANENT_INVALID_TOKEN -> {
                token.setActive(false);
                increment("invalid_token", safeReason(result.reason()));
                increment("failed", "invalid_token");
            }
            case TRANSIENT_FAILURE -> increment("failed", safeReason(result.reason()));
            case DISABLED -> increment("disabled", "push_delivery_disabled");
        }
    }

    private void increment(String outcome) {
        increment(outcome, "none");
    }

    private void increment(String outcome, String reason) {
        metrics.increment(
                "spotlink.push.delivery",
                "provider", pushProvider.name(),
                "outcome", outcome,
                "reason", reason);
    }

    private String safeReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return "unknown";
        }
        return reason.replaceAll("[^A-Za-z0-9_.-]", "_");
    }
}
