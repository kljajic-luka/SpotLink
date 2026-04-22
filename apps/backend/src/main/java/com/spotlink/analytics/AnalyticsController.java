package com.spotlink.analytics;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    public AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @PostMapping({"/analytics/events", "/v1/analytics/events"})
    @ResponseStatus(HttpStatus.ACCEPTED)
    void ingest(@Valid @RequestBody AnalyticsDtos.AnalyticsBatch batch) {
        analyticsService.ingest(batch);
    }
}
