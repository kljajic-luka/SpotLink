package com.spotlink.notification;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

public class SafeLoggingMailProvider implements MailProvider {

    private static final Logger log = LoggerFactory.getLogger(SafeLoggingMailProvider.class);

    private final String providerName;

    public SafeLoggingMailProvider(String providerName) {
        this.providerName = StringUtils.hasText(providerName) ? providerName.trim() : "safe-log";
    }

    @Override
    public void send(String to, String subject, String body) {
        if ("none".equalsIgnoreCase(providerName)) {
            throw new IllegalStateException("Mail provider is disabled.");
        }
        log.info(
                "Mail delivery captured provider={} recipientHash={}",
                providerName,
                stableHash(to));
    }

    @Override
    public boolean productionReady() {
        return false;
    }

    @Override
    public String name() {
        return providerName;
    }

    private String stableHash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(
                    String.valueOf(value).toLowerCase().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available.", ex);
        }
    }
}
