package com.spotlink.notification;

import com.spotlink.core.AppProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.util.StringUtils;

public class SmtpMailProvider implements MailProvider {

    private static final Logger log = LoggerFactory.getLogger(SmtpMailProvider.class);

    private final AppProperties.Mail mailProperties;
    private final JavaMailSender mailSender;

    public SmtpMailProvider(AppProperties.Mail mailProperties, JavaMailSender mailSender) {
        this.mailProperties = mailProperties;
        this.mailSender = mailSender;
    }

    @Override
    public void send(String to, String subject, String body) {
        if (!productionReady()) {
            throw new IllegalStateException("SMTP mail provider is not fully configured.");
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(mailProperties.getSmtp().getFrom());
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);
        mailSender.send(message);

        log.info("Mail delivery queued provider=smtp recipientHash={}", stableHash(to));
    }

    @Override
    public boolean productionReady() {
        AppProperties.Smtp smtp = mailProperties.getSmtp();
        return safeText(smtp.getHost())
                && smtp.getPort() > 0
                && safeText(smtp.getFrom())
                && smtp.getConnectionTimeoutMs() > 0
                && smtp.getReadTimeoutMs() > 0
                && smtp.getWriteTimeoutMs() > 0
                && smtp.isStarttlsEnabled()
                && (!smtp.isAuthEnabled() || (safeText(smtp.getUsername()) && safeText(smtp.getPassword())));
    }

    @Override
    public String name() {
        return "smtp";
    }

    private boolean safeText(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return !normalized.startsWith("<")
                && !normalized.contains("placeholder")
                && !normalized.contains("change-me")
                && !normalized.contains("secret-store");
    }

    private String stableHash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(
                    String.valueOf(value).toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available.", ex);
        }
    }
}
