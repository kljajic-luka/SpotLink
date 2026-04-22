package com.spotlink.analytics;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class AnalyticsDtos {

    private AnalyticsDtos() {
    }

    public record AnalyticsEventDto(
            @NotBlank @Size(max = 160) String event,
            Map<String, Object> properties,
            Instant timestamp,
            @Size(max = 1000) String url,
            @NotBlank @Size(max = 120) String sessionId
    ) {
    }

    public record AnalyticsBatch(
            @Valid @NotEmpty List<AnalyticsEventDto> events
    ) {
    }

    public record WebVitalMetric(
            @NotBlank String name,
            double value,
            String rating
    ) {
    }
}
