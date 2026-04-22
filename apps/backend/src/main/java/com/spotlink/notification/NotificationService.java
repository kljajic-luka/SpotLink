package com.spotlink.notification;

import com.spotlink.core.ApiPage;
import com.spotlink.core.NotFoundException;
import com.spotlink.security.CurrentUserService;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService {

    private final NotificationRepository notifications;
    private final DeviceTokenRepository deviceTokens;
    private final NotificationProvider provider;
    private final CurrentUserService currentUser;

    public NotificationService(
            NotificationRepository notifications,
            DeviceTokenRepository deviceTokens,
            NotificationProvider provider,
            CurrentUserService currentUser) {
        this.notifications = notifications;
        this.deviceTokens = deviceTokens;
        this.provider = provider;
        this.currentUser = currentUser;
    }

    @Transactional(readOnly = true)
    public ApiPage<NotificationDtos.NotificationItem> mine(int page, int size) {
        return ApiPage.from(notifications.findByUserIdOrderByCreatedAtDesc(
                currentUser.userId(),
                PageRequest.of(page, Math.min(size, 100))).map(this::toDto));
    }

    @Transactional(readOnly = true)
    public NotificationDtos.UnreadNotificationCount unreadCount() {
        return new NotificationDtos.UnreadNotificationCount(notifications.countByUserIdAndReadFalse(currentUser.userId()));
    }

    @Transactional
    public void markRead(UUID notificationId) {
        Notification notification = notifications.findById(notificationId)
                .orElseThrow(() -> new NotFoundException("Notification was not found."));
        if (!notification.getUserId().equals(currentUser.userId())) {
            throw new AccessDeniedException("Notification does not belong to the current user.");
        }
        notification.setRead(true);
    }

    @Transactional
    public void registerDevice(NotificationDtos.RegisterDeviceTokenRequest request) {
        DeviceToken token = deviceTokens.findByDeviceToken(request.deviceToken()).orElseGet(DeviceToken::new);
        token.setUserId(currentUser.userId());
        token.setDeviceToken(request.deviceToken());
        token.setPlatform(request.platform());
        token.setActive(true);
        deviceTokens.save(token);
    }

    @Transactional
    public Notification create(UUID userId, NotificationType type, String title, String body, UUID relatedEntityId) {
        Notification notification = new Notification();
        notification.setUserId(userId);
        notification.setType(type);
        notification.setTitle(title);
        notification.setBody(body);
        notification.setRelatedEntityId(relatedEntityId);
        Notification saved = notifications.save(notification);
        provider.deliver(saved);
        return saved;
    }

    private NotificationDtos.NotificationItem toDto(Notification notification) {
        return new NotificationDtos.NotificationItem(
                notification.getId(),
                notification.getType(),
                notification.getTitle(),
                notification.getBody(),
                notification.getRelatedEntityId(),
                notification.isRead(),
                notification.getCreatedAt());
    }
}
