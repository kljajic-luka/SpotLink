package com.spotlink.notification;

public interface PushProvider {

    void push(DeviceToken token, String title, String body);
}
