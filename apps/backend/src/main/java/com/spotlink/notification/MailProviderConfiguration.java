package com.spotlink.notification;

import com.spotlink.core.AppProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.util.StringUtils;

@Configuration
public class MailProviderConfiguration {

    @Bean
    @ConditionalOnMissingBean(MailProvider.class)
    MailProvider mailProvider(AppProperties appProperties) {
        String provider = normalized(appProperties.getMail().getProvider());
        return switch (provider) {
            case "none", "safe-log" -> new SafeLoggingMailProvider(provider);
            case "smtp" -> new SmtpMailProvider(appProperties.getMail(), smtpSender(appProperties.getMail().getSmtp()));
            default -> throw new IllegalStateException("MAIL_PROVIDER must be one of none, safe-log, or smtp.");
        };
    }

    private JavaMailSender smtpSender(AppProperties.Smtp smtp) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(smtp.getHost());
        sender.setPort(smtp.getPort());
        sender.setUsername(smtp.getUsername());
        sender.setPassword(smtp.getPassword());
        sender.getJavaMailProperties().put("mail.smtp.auth", Boolean.toString(smtp.isAuthEnabled()));
        sender.getJavaMailProperties().put("mail.smtp.starttls.enable", Boolean.toString(smtp.isStarttlsEnabled()));
        sender.getJavaMailProperties().put("mail.smtp.connectiontimeout", Integer.toString(smtp.getConnectionTimeoutMs()));
        sender.getJavaMailProperties().put("mail.smtp.timeout", Integer.toString(smtp.getReadTimeoutMs()));
        sender.getJavaMailProperties().put("mail.smtp.writetimeout", Integer.toString(smtp.getWriteTimeoutMs()));
        return sender;
    }

    private String normalized(String value) {
        return StringUtils.hasText(value) ? value.trim().toLowerCase(java.util.Locale.ROOT) : "none";
    }
}
