package com.spotlink.security;

import com.spotlink.core.AppProperties;
import java.time.Clock;
import java.time.Duration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class RateLimitService {

    private static final long MIN_WINDOW_MILLIS = 1000L;

    private final Clock clock;
    private final Map<String, WindowCounter> counters = new HashMap<>();

    public RateLimitService(Clock clock) {
        this.clock = clock;
    }

    public synchronized RateLimitDecision consume(
            String bucket,
            String subject,
            AppProperties.RateLimit.Rule rule) {
        if (rule == null || !rule.isEnabled()) {
            return RateLimitDecision.allowed(Integer.MAX_VALUE, Integer.MAX_VALUE);
        }

        int limit = Math.max(1, rule.getPermits());
        long windowMillis = windowMillis(rule.getWindow());
        long nowMillis = clock.millis();
        String key = bucket + ":" + subject;
        WindowCounter counter = counters.get(key);

        if (counter == null || counter.windowStartedAtMillis + windowMillis <= nowMillis) {
            counter = new WindowCounter(nowMillis, 0);
            counters.put(key, counter);
            pruneExpired(nowMillis, windowMillis);
        }

        counter.count++;
        if (counter.count <= limit) {
            return RateLimitDecision.allowed(limit, Math.max(0, limit - counter.count));
        }

        long retryAfterMillis = counter.windowStartedAtMillis + windowMillis - nowMillis;
        long retryAfterSeconds = Math.max(1L, (retryAfterMillis + 999L) / 1000L);
        return RateLimitDecision.blocked(limit, retryAfterSeconds);
    }

    private long windowMillis(Duration duration) {
        if (duration == null || duration.isNegative() || duration.isZero()) {
            return MIN_WINDOW_MILLIS;
        }
        return Math.max(MIN_WINDOW_MILLIS, duration.toMillis());
    }

    private void pruneExpired(long nowMillis, long currentWindowMillis) {
        Iterator<Map.Entry<String, WindowCounter>> iterator = counters.entrySet().iterator();
        while (iterator.hasNext()) {
            WindowCounter counter = iterator.next().getValue();
            if (counter.windowStartedAtMillis + currentWindowMillis <= nowMillis) {
                iterator.remove();
            }
        }
    }

    private static final class WindowCounter {
        private final long windowStartedAtMillis;
        private int count;

        private WindowCounter(long windowStartedAtMillis, int count) {
            this.windowStartedAtMillis = windowStartedAtMillis;
            this.count = count;
        }
    }
}
