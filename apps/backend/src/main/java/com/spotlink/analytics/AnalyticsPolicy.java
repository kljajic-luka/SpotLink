package com.spotlink.analytics;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spotlink.core.ValidationException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class AnalyticsPolicy {

    static final int MAX_EVENTS_PER_BATCH = 20;
    static final int MAX_PROPERTIES_PER_EVENT = 20;
    static final int MAX_PROPERTY_KEY_LENGTH = 40;
    static final int MAX_STRING_VALUE_LENGTH = 120;
    static final int MAX_SERIALIZED_PROPERTIES_LENGTH = 2000;

    private static final Set<String> ALLOWED_EVENTS = Set.of(
            "app_open",
            "screen_view",
            "login",
            "logout",
            "registration_started",
            "registration_completed",
            "search_performed",
            "reservation_quote_requested",
            "reservation_flow_started",
            "reservation_created",
            "reservation_create_failed",
            "payment_intent_created",
            "payment_unavailable",
            "support_ticket_created",
            "account_deletion_requested",
            "notification_preferences_updated",
            "profile_updated",
            "error");

    private static final Set<String> ALLOWED_PROPERTY_KEYS = Set.of(
            "platform",
            "appVersion",
            "appBuild",
            "environment",
            "screen",
            "context",
            "source",
            "flow",
            "type",
            "result",
            "status",
            "reason",
            "category",
            "provider",
            "registrationType",
            "paymentMode",
            "reservationStatus",
            "notificationType",
            "errorName");

    private static final Set<String> UNSAFE_KEY_FRAGMENTS = Set.of(
            "email",
            "phone",
            "firstname",
            "lastname",
            "fullname",
            "licenseplate",
            "plate",
            "token",
            "authorization",
            "bearer",
            "password",
            "secret",
            "address",
            "latitude",
            "longitude",
            "coordinate",
            "card",
            "pan",
            "cvv",
            "paymentmethod",
            "apns",
            "description",
            "message");

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern PHONE_PATTERN = Pattern.compile("\\+?\\d[\\d\\s().-]{7,}\\d");
    private static final Pattern BEARER_PATTERN = Pattern.compile("(?i)\\bbearer\\s+[a-z0-9._~-]+");
    private static final Pattern TOKEN_PATTERN = Pattern.compile("(?i)(sl_reset_|refresh[_-]?token|access[_-]?token|apns[_-]?token|eyJ[a-z0-9_-]+\\.)");
    private static final Pattern CARD_PATTERN = Pattern.compile("\\b(?:\\d[ -]*?){13,19}\\b");
    private static final Pattern LICENSE_PLATE_PATTERN = Pattern.compile("\\b[A-ZČĆŽŠĐ]{1,3}[-\\s]?\\d{2,5}[-\\s]?[A-ZČĆŽŠĐ]{1,3}\\b");
    private static final Pattern PRECISE_COORDINATES_PATTERN = Pattern.compile("\\b-?\\d{1,2}\\.\\d{4,}\\s*,\\s*-?\\d{1,3}\\.\\d{4,}\\b");

    private final ObjectMapper objectMapper;

    public AnalyticsPolicy(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public SanitizedBatch sanitize(AnalyticsDtos.AnalyticsBatch batch) {
        List<AnalyticsDtos.AnalyticsEventDto> requested = batch.events();
        if (requested.size() > MAX_EVENTS_PER_BATCH) {
            throw invalid("events", "At most %d analytics events are accepted per batch.".formatted(MAX_EVENTS_PER_BATCH));
        }
        List<SanitizedEvent> sanitized = new java.util.ArrayList<>(requested.size());
        for (int i = 0; i < requested.size(); i++) {
            sanitized.add(sanitizeEvent(requested.get(i), i));
        }
        return new SanitizedBatch(List.copyOf(sanitized));
    }

    private SanitizedEvent sanitizeEvent(AnalyticsDtos.AnalyticsEventDto dto, int index) {
        String eventName = dto.event();
        if (!ALLOWED_EVENTS.contains(eventName)) {
            throw invalid(field(index, "event"), "Analytics event is not allowed.");
        }

        Map<String, Object> properties = sanitizeProperties(dto.properties(), index);
        assertSerializedSize(properties, index);

        return new SanitizedEvent(
                eventName,
                properties.isEmpty() ? null : properties,
                dto.timestamp(),
                null,
                dto.sessionId());
    }

    private Map<String, Object> sanitizeProperties(Map<String, Object> rawProperties, int index) {
        if (rawProperties == null || rawProperties.isEmpty()) {
            return Map.of();
        }
        if (rawProperties.size() > MAX_PROPERTIES_PER_EVENT) {
            throw invalid(field(index, "properties"), "At most %d analytics properties are accepted per event.".formatted(MAX_PROPERTIES_PER_EVENT));
        }

        Map<String, Object> sanitized = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : rawProperties.entrySet()) {
            String key = entry.getKey();
            assertAllowedKey(key, index);
            Object value = sanitizeValue(key, entry.getValue(), index);
            if (value != null) {
                sanitized.put(key, value);
            }
        }
        return sanitized;
    }

    private void assertAllowedKey(String key, int index) {
        if (!StringUtils.hasText(key) || key.length() > MAX_PROPERTY_KEY_LENGTH) {
            throw invalid(field(index, "properties"), "Analytics property key is invalid.");
        }
        if (containsUnsafeKeyFragment(key) || !ALLOWED_PROPERTY_KEYS.contains(key)) {
            throw invalid(field(index, "properties." + key), "Analytics property is not allowed.");
        }
    }

    private Object sanitizeValue(String key, Object rawValue, int index) {
        if (rawValue == null) {
            return null;
        }
        if (rawValue instanceof String stringValue) {
            String trimmed = stringValue.trim();
            if (trimmed.length() > MAX_STRING_VALUE_LENGTH) {
                throw invalid(field(index, "properties." + key), "Analytics property value is too long.");
            }
            if (containsSensitiveValue(trimmed)) {
                throw invalid(field(index, "properties." + key), "Analytics property value is not allowed.");
            }
            return trimmed;
        }
        if (rawValue instanceof Boolean || rawValue instanceof Integer || rawValue instanceof Long || rawValue instanceof Double || rawValue instanceof Float) {
            return rawValue;
        }
        throw invalid(field(index, "properties." + key), "Analytics property values must be strings, numbers, booleans, or null.");
    }

    private void assertSerializedSize(Map<String, Object> properties, int index) {
        if (properties.isEmpty()) {
            return;
        }
        try {
            String serialized = objectMapper.writeValueAsString(properties);
            if (serialized.length() > MAX_SERIALIZED_PROPERTIES_LENGTH) {
                throw invalid(field(index, "properties"), "Analytics properties payload is too large.");
            }
        } catch (JsonProcessingException ex) {
            throw invalid(field(index, "properties"), "Analytics properties could not be serialized.");
        }
    }

    private boolean containsUnsafeKeyFragment(String key) {
        String normalized = key.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        return UNSAFE_KEY_FRAGMENTS.stream().anyMatch(normalized::contains);
    }

    private boolean containsSensitiveValue(String value) {
        return EMAIL_PATTERN.matcher(value).find()
                || PHONE_PATTERN.matcher(value).find()
                || BEARER_PATTERN.matcher(value).find()
                || TOKEN_PATTERN.matcher(value).find()
                || CARD_PATTERN.matcher(value).find()
                || LICENSE_PLATE_PATTERN.matcher(value).find()
                || PRECISE_COORDINATES_PATTERN.matcher(value).find();
    }

    private ValidationException invalid(String field, String message) {
        return new ValidationException(field, message);
    }

    private String field(int index, String suffix) {
        return "events[%d].%s".formatted(index, suffix);
    }

    public record SanitizedBatch(List<SanitizedEvent> events) {
    }

    public record SanitizedEvent(
            String event,
            Map<String, Object> properties,
            Instant timestamp,
            String url,
            String sessionId
    ) {
    }
}
