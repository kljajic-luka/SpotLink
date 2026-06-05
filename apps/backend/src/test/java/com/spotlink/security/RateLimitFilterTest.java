package com.spotlink.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.spotlink.core.AppProperties;
import com.spotlink.core.Constants;
import com.spotlink.core.OperationalMetrics;
import com.spotlink.core.RequestCorrelationFilter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RateLimitFilterTest {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private final AppProperties appProperties = new AppProperties();
    private final MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
    private final RateLimitFilter filter = new RateLimitFilter(
            appProperties,
            new RateLimitService(clock),
            objectMapper,
            new OperationalMetrics(new SimpleMeterRegistry()));

    @Test
    void blocksConfiguredEndpointWithStableErrorEnvelopeAndRetryAfter() throws Exception {
        appProperties.getRateLimit().getLogin().setPermits(2);

        MockHttpServletResponse first = performPost("/auth/login", "req-1", "reset-user@spotlink.test", "CorrectHorse123");
        MockHttpServletResponse second = performPost("/auth/login", "req-2", "reset-user@spotlink.test", "CorrectHorse123");
        MockHttpServletResponse third = performPost("/auth/login", "req-3", "reset-user@spotlink.test", "CorrectHorse123");

        assertThat(first.getStatus()).isEqualTo(200);
        assertThat(second.getStatus()).isEqualTo(200);
        assertThat(third.getStatus()).isEqualTo(429);
        assertThat(third.getHeader("Retry-After")).isEqualTo("60");
        assertThat(third.getHeader(Constants.REQUEST_ID_HEADER)).isEqualTo("req-3");

        JsonNode body = objectMapper.readTree(third.getContentAsString());
        assertThat(body.get("code").asText()).isEqualTo("RATE_LIMITED");
        assertThat(body.get("requestId").asText()).isEqualTo("req-3");
        assertThat(body.get("details").get("operation").asText()).isEqualTo("auth_login");
        assertThat(third.getContentAsString()).doesNotContain("reset-user@spotlink.test");
        assertThat(third.getContentAsString()).doesNotContain("CorrectHorse123");
    }

    @Test
    void allowsRequestsAgainAfterWindowExpires() throws Exception {
        appProperties.getRateLimit().getPasswordResetRequest().setPermits(1);

        MockHttpServletResponse first = performPost("/auth/password/reset-request", "req-1", "user@spotlink.test", "unused");
        MockHttpServletResponse second = performPost("/auth/password/reset-request", "req-2", "user@spotlink.test", "unused");
        clock.advanceSeconds(60);
        MockHttpServletResponse third = performPost("/auth/password/reset-request", "req-3", "user@spotlink.test", "unused");

        assertThat(first.getStatus()).isEqualTo(200);
        assertThat(second.getStatus()).isEqualTo(429);
        assertThat(third.getStatus()).isEqualTo(200);
    }

    @Test
    void ignoresUnlistedEndpoints() throws Exception {
        appProperties.getRateLimit().getLogin().setPermits(1);

        MockHttpServletResponse response = performPost("/locations/search", "req-public", "user@spotlink.test", "unused");

        assertThat(response.getStatus()).isEqualTo(200);
    }

    private MockHttpServletResponse performPost(
            String path,
            String requestId,
            String email,
            String password) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        request.setRemoteAddr("203.0.113.10");
        request.addHeader(Constants.REQUEST_ID_HEADER, requestId);
        request.setAttribute(RequestCorrelationFilter.REQUEST_ID_ATTRIBUTE, requestId);
        request.setContentType("application/json");
        request.setContent("""
                {"email":"%s","password":"%s"}
                """.formatted(email, password).getBytes(java.nio.charset.StandardCharsets.UTF_8));

        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void advanceSeconds(long seconds) {
            instant = instant.plusSeconds(seconds);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
