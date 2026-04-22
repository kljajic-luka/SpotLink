package com.spotlink.analytics;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AnalyticsService {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsService.class);

    private final AnalyticsEventRepository events;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public AnalyticsService(AnalyticsEventRepository events, ObjectMapper objectMapper, Clock clock) {
        this.events = events;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional
    public void ingest(AnalyticsDtos.AnalyticsBatch batch) {
        for (AnalyticsDtos.AnalyticsEventDto dto : batch.events()) {
            AnalyticsEvent event = new AnalyticsEvent();
            event.setEventName(dto.event());
            event.setProperties(serialize(dto.properties()));
            event.setOccurredAt(dto.timestamp() == null ? Instant.now(clock) : dto.timestamp());
            event.setUrl(dto.url());
            event.setSessionId(dto.sessionId());
            events.save(event);
        }
    }

    private String serialize(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            log.warn("Could not serialize analytics properties: {}", ex.getMessage());
            return "{}";
        }
    }
}
