package com.spotlink.core;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

    private final Clock clock;

    public HealthController(Clock clock) {
        this.clock = clock;
    }

    @GetMapping({"/health", "/v1/health"})
    Map<String, Object> health() {
        return Map.of(
                "status", "UP",
                "service", "spotlink-backend",
                "timestamp", Instant.now(clock));
    }
}
