package com.spotlink.notification;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public final class NotificationDtos {

    private NotificationDtos() {
    }

    public record NotificationItem(
            UUID id,
            NotificationType type,
            String title,
            String body,
            UUID relatedEntityId,
            boolean read,
            Instant createdAt
    ) {
    }

    public record RegisterDeviceTokenRequest(
            @NotBlank @Size(max = 500) String deviceToken,
            @NotNull DevicePlatform platform
    ) {
    }

    public record UnregisterDeviceTokenRequest(
            @NotBlank @Size(max = 500) String deviceToken,
            @NotNull DevicePlatform platform
    ) {
    }

    public record UnreadNotificationCount(long count) {
    }
}
