package com.spotlink;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spotlink.analytics.AnalyticsEvent;
import com.spotlink.analytics.AnalyticsEventRepository;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
class AnalyticsPrivacyTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AnalyticsEventRepository analyticsEvents;

    @BeforeEach
    void resetAnalyticsEvents() {
        analyticsEvents.deleteAll();
    }

    @Test
    void validAllowedEventBatchReturnsAcceptedWithEmptyBodyAndRequestId() throws Exception {
        String requestId = "analytics-valid-" + UUID.randomUUID();

        MvcResult result = mockMvc.perform(post("/analytics/events")
                        .header("X-Request-Id", requestId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "events": [
                                    {
                                      "event": "screen_view",
                                      "properties": {
                                        "platform": "ios",
                                        "appVersion": "0.1.0",
                                        "screen": "search"
                                      },
                                      "timestamp": "2026-06-06T07:15:00Z",
                                      "url": "https://app.spotlink.test/search?token=unsafe",
                                      "sessionId": "ios-session-valid"
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isAccepted())
                .andExpect(header().string("X-Request-Id", requestId))
                .andReturn();

        assertThat(result.getResponse().getContentAsByteArray()).isEmpty();
        assertThat(analyticsEvents.findAll()).hasSize(1);

        AnalyticsEvent stored = analyticsEvents.findAll().getFirst();
        assertThat(stored.getEventName()).isEqualTo("screen_view");
        assertThat(stored.getSessionId()).isEqualTo("ios-session-valid");
        assertThat(stored.getUrl()).isNull();
        assertThat(stored.getProperties()).contains("\"screen\":\"search\"");
        assertThat(stored.getProperties()).doesNotContain("token=unsafe");
    }

    @Test
    void unknownEventIsRejectedAndNotPersisted() throws Exception {
        String requestId = "analytics-unknown-" + UUID.randomUUID();

        mockMvc.perform(post("/v1/analytics/events")
                        .header("X-Request-Id", requestId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "events": [
                                    {
                                      "event": "free_form_debug_dump",
                                      "properties": { "screen": "profile" },
                                      "sessionId": "ios-session-rejected"
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("X-Request-Id", requestId))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details['events[0].event']").exists());

        assertThat(analyticsEvents.findAll()).isEmpty();
    }

    @Test
    void unsafePropertyKeysAndPiiLikeValuesAreRejectedAndNotPersisted(CapturedOutput output) throws Exception {
        String sensitiveEmail = "analytics-secret-%s@spotlink.test".formatted(UUID.randomUUID());
        String rawToken = "Bearer analytics-secret-token-" + UUID.randomUUID();

        MvcResult result = mockMvc.perform(post("/analytics/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "events": [
                                    {
                                      "event": "screen_view",
                                      "properties": {
                                        "screen": "profile",
                                        "email": "%s",
                                        "context": "%s"
                                      },
                                      "sessionId": "ios-session-unsafe"
                                    }
                                  ]
                                }
                                """.formatted(sensitiveEmail, rawToken)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andReturn();

        assertThat(analyticsEvents.findAll()).isEmpty();
        assertThat(result.getResponse().getContentAsString()).doesNotContain(sensitiveEmail);
        assertThat(result.getResponse().getContentAsString()).doesNotContain(rawToken);
        assertThat(output).doesNotContain(sensitiveEmail);
        assertThat(output).doesNotContain(rawToken);
    }

    @Test
    void piiLikeAllowedPropertyValueIsRejectedAndNotPersisted() throws Exception {
        String licensePlate = "BG-1234-AA";

        MvcResult result = mockMvc.perform(post("/analytics/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "events": [
                                    {
                                      "event": "search_performed",
                                      "properties": {
                                        "screen": "search",
                                        "context": "%s"
                                      },
                                      "sessionId": "ios-session-plate"
                                    }
                                  ]
                                }
                                """.formatted(licensePlate)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andReturn();

        assertThat(analyticsEvents.findAll()).isEmpty();
        assertThat(result.getResponse().getContentAsString()).doesNotContain(licensePlate);
    }

    @Test
    void batchSizeLimitIsEnforced() throws Exception {
        StringBuilder events = new StringBuilder();
        for (int index = 0; index < 21; index++) {
            if (!events.isEmpty()) {
                events.append(',');
            }
            events.append("""
                    {
                      "event": "app_open",
                      "properties": { "platform": "ios" },
                      "sessionId": "ios-session-%d"
                    }
                    """.formatted(index));
        }

        mockMvc.perform(post("/analytics/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"events\": [%s]}".formatted(events)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        assertThat(analyticsEvents.findAll()).isEmpty();
    }

    @Test
    void propertyValueSizeLimitIsEnforced() throws Exception {
        String longScreen = "s".repeat(121);

        mockMvc.perform(post("/analytics/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "events": [
                                    {
                                      "event": "screen_view",
                                      "properties": {
                                        "screen": "%s"
                                      },
                                      "sessionId": "ios-session-long-property"
                                    }
                                  ]
                                }
                                """.formatted(longScreen)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        assertThat(analyticsEvents.findAll()).isEmpty();
    }
}
