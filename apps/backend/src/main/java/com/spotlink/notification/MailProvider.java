package com.spotlink.notification;

public interface MailProvider {

    void send(String to, String subject, String body);
}
