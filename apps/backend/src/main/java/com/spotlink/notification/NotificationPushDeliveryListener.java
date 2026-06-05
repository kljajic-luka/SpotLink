package com.spotlink.notification;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class NotificationPushDeliveryListener {

    private final NotificationPushDeliveryService deliveryService;

    public NotificationPushDeliveryListener(NotificationPushDeliveryService deliveryService) {
        this.deliveryService = deliveryService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onNotificationCreated(NotificationCreatedEvent event) {
        deliveryService.deliver(event.notificationId());
    }
}
