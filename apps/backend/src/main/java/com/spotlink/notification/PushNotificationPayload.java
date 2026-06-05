package com.spotlink.notification;

import java.util.UUID;

public record PushNotificationPayload(
        UUID notificationId,
        NotificationType type,
        String title,
        String body,
        UUID relatedEntityId
) {
    static PushNotificationPayload from(Notification notification) {
        return new PushNotificationPayload(
                notification.getId(),
                notification.getType(),
                notification.getTitle(),
                notification.getBody(),
                notification.getRelatedEntityId());
    }
}
