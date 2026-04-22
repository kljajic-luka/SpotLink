package com.spotlink.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class MockNotificationProvider implements NotificationProvider {

    private static final Logger log = LoggerFactory.getLogger(MockNotificationProvider.class);

    @Override
    public void deliver(Notification notification) {
        log.info("Mock notification queued userId={} type={}", notification.getUserId(), notification.getType());
    }
}
