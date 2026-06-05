package com.spotlink.notification;

import com.eatthepath.pushy.apns.ApnsClient;
import com.eatthepath.pushy.apns.ApnsClientBuilder;
import com.eatthepath.pushy.apns.PushNotificationResponse;
import com.eatthepath.pushy.apns.auth.ApnsSigningKey;
import com.eatthepath.pushy.apns.util.SimpleApnsPushNotification;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spotlink.core.AppProperties;
import jakarta.annotation.PreDestroy;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

public class ApnsPushProvider implements PushProvider, AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(ApnsPushProvider.class);
    private static final Set<String> INVALID_TOKEN_REASONS = Set.of(
            "BadDeviceToken",
            "DeviceTokenNotForTopic",
            "Unregistered");

    private final AppProperties.Apns properties;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Duration requestTimeout;
    private volatile ApnsClient client;

    public ApnsPushProvider(AppProperties.Apns properties) {
        this.properties = properties;
        this.requestTimeout = Duration.ofSeconds(Math.max(1, properties.getRequestTimeoutSeconds()));
    }

    @Override
    public PushDeliveryResult deliver(DeviceToken token, PushNotificationPayload payload) {
        SimpleApnsPushNotification notification = new SimpleApnsPushNotification(
                token.getDeviceToken(),
                properties.getBundleId(),
                buildPayload(payload));
        try {
            PushNotificationResponse<SimpleApnsPushNotification> response = client()
                    .sendNotification(notification)
                    .get(requestTimeout.toMillis(), TimeUnit.MILLISECONDS);
            if (response.isAccepted()) {
                return PushDeliveryResult.success();
            }
            String reason = response.getRejectionReason().orElse("rejected");
            if (INVALID_TOKEN_REASONS.contains(reason)) {
                return PushDeliveryResult.permanentInvalidToken(reason);
            }
            return PushDeliveryResult.transientFailure(reason);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return PushDeliveryResult.transientFailure("interrupted");
        } catch (ExecutionException | TimeoutException | IOException ex) {
            log.warn("APNs delivery failed provider=apns tokenHash={} reason={}",
                    NotificationLogSanitizer.stableHash(token.getDeviceToken()),
                    ex.getClass().getSimpleName());
            return PushDeliveryResult.transientFailure(ex.getClass().getSimpleName());
        }
    }

    @Override
    public boolean productionReady() {
        return true;
    }

    @Override
    public String name() {
        return "apns";
    }

    @Override
    @PreDestroy
    public void close() {
        ApnsClient current = client;
        if (current != null) {
            current.close();
        }
    }

    private ApnsClient client() throws IOException {
        ApnsClient current = client;
        if (current != null) {
            return current;
        }
        synchronized (this) {
            if (client == null) {
                client = buildClient();
            }
            return client;
        }
    }

    private ApnsClient buildClient() throws IOException {
        return new ApnsClientBuilder()
                .setApnsServer(apnsHost())
                .setSigningKey(loadSigningKey())
                .build();
    }

    private String apnsHost() {
        String environment = StringUtils.hasText(properties.getEnvironment())
                ? properties.getEnvironment().trim().toLowerCase(Locale.ROOT)
                : "sandbox";
        return "production".equals(environment)
                ? ApnsClientBuilder.PRODUCTION_APNS_HOST
                : ApnsClientBuilder.DEVELOPMENT_APNS_HOST;
    }

    private ApnsSigningKey loadSigningKey() throws IOException {
        try (InputStream input = privateKeyInputStream()) {
            return ApnsSigningKey.loadFromInputStream(
                    input,
                    properties.getTeamId(),
                    properties.getKeyId());
        } catch (Exception ex) {
            throw new IOException("Unable to load APNs signing key.", ex);
        }
    }

    private InputStream privateKeyInputStream() throws IOException {
        if (StringUtils.hasText(properties.getPrivateKey())) {
            String key = properties.getPrivateKey().replace("\\n", "\n");
            return new ByteArrayInputStream(key.getBytes(StandardCharsets.UTF_8));
        }
        return Files.newInputStream(Path.of(properties.getPrivateKeyPath()));
    }

    private String buildPayload(PushNotificationPayload payload) {
        Map<String, Object> alert = new LinkedHashMap<>();
        alert.put("title", payload.title());
        alert.put("body", payload.body());

        Map<String, Object> aps = new LinkedHashMap<>();
        aps.put("alert", alert);
        aps.put("sound", "default");

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("aps", aps);
        root.put("type", payload.type().name());
        root.put("notificationId", payload.notificationId().toString());
        if (payload.relatedEntityId() != null) {
            root.put("relatedEntityId", payload.relatedEntityId().toString());
        }

        try {
            return objectMapper.writeValueAsString(root);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unable to build APNs payload.", ex);
        }
    }
}
