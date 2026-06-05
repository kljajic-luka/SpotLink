package com.spotlink.notification;

public interface PushProvider {

    PushDeliveryResult deliver(DeviceToken token, PushNotificationPayload payload);

    default boolean productionReady() {
        return false;
    }

    default String name() {
        return getClass().getSimpleName();
    }
}
