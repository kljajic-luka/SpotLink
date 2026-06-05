package com.spotlink.notification;

public interface MailProvider {

    void send(String to, String subject, String body);

    default boolean productionReady() {
        return false;
    }

    default String name() {
        return getClass().getSimpleName();
    }
}
