package com.spotlink.notification;

import com.spotlink.core.ApiPage;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping({"/notifications", "/v1/notifications"})
    ApiPage<NotificationDtos.NotificationItem> mine(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return notificationService.mine(page, size);
    }

    @GetMapping({"/notifications/unread-count", "/v1/notifications/unread-count"})
    NotificationDtos.UnreadNotificationCount unreadCount() {
        return notificationService.unreadCount();
    }

    @PostMapping({"/notifications/{notificationId}/read", "/v1/notifications/{notificationId}/read"})
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void markRead(@PathVariable UUID notificationId) {
        notificationService.markRead(notificationId);
    }

    @PostMapping({"/notifications/device-tokens", "/v1/notifications/device-tokens"})
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void registerDevice(@Valid @RequestBody NotificationDtos.RegisterDeviceTokenRequest request) {
        notificationService.registerDevice(request);
    }
}
