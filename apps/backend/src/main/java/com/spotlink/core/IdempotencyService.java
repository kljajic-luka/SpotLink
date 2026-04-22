package com.spotlink.core;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IdempotencyService {

    private static final Pattern SAFE_KEY = Pattern.compile("^[A-Za-z0-9._:-]{8,160}$");

    private final IdempotencyRecordRepository records;
    private final Clock clock;

    public IdempotencyService(IdempotencyRecordRepository records, Clock clock) {
        this.records = records;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Optional<IdempotencyRecord> find(UUID userId, String scope, String key) {
        validateKey(key);
        return records.findByUserIdAndScopeAndIdempotencyKey(userId, scope, key);
    }

    @Transactional
    public IdempotencyRecord begin(UUID userId, String scope, String key) {
        validateKey(key);
        return records.findByUserIdAndScopeAndIdempotencyKey(userId, scope, key)
                .orElseGet(() -> {
                    IdempotencyRecord record = new IdempotencyRecord();
                    record.setUserId(userId);
                    record.setScope(scope);
                    record.setIdempotencyKey(key);
                    record.setStatus(IdempotencyStatus.PROCESSING);
                    record.setExpiresAt(Instant.now(clock).plus(24, ChronoUnit.HOURS));
                    return records.save(record);
                });
    }

    @Transactional
    public void complete(IdempotencyRecord record, int status, String responseBody) {
        record.setStatus(IdempotencyStatus.COMPLETED);
        record.setResponseStatus(status);
        record.setResponseBody(responseBody);
    }

    @Transactional
    public void fail(IdempotencyRecord record, int status, String message) {
        record.setStatus(IdempotencyStatus.FAILED);
        record.setResponseStatus(status);
        record.setErrorMessage(message);
    }

    private void validateKey(String key) {
        if (key == null || !SAFE_KEY.matcher(key).matches()) {
            throw new ConflictException("INVALID_IDEMPOTENCY_KEY", "A valid idempotency key is required.");
        }
    }
}
