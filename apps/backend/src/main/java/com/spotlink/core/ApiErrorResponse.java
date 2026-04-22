package com.spotlink.core;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiErrorResponse(
        int status,
        String code,
        String message,
        String requestId,
        Map<String, ?> details,
        Instant timestamp,
        String path
) {
    public static ApiErrorResponse of(
            int status,
            String code,
            String message,
            String requestId,
            Map<String, ?> details,
            String path) {
        return new ApiErrorResponse(status, code, message, requestId, details, Instant.now(), path);
    }
}
