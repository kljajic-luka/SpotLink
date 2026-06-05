package com.spotlink.notification;

public class DisabledPushProvider implements PushProvider {

    @Override
    public PushDeliveryResult deliver(DeviceToken token, PushNotificationPayload payload) {
        return PushDeliveryResult.disabled("push_delivery_disabled");
    }

    @Override
    public String name() {
        return "none";
    }
}
