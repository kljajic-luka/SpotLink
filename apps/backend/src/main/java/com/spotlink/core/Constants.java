package com.spotlink.core;

public final class Constants {

    public static final String API_PREFIX = "/api";
    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    public static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
    public static final String IDEMPOTENCY_HEADER = "X-Idempotency-Key";
    public static final String XSRF_COOKIE = "XSRF-TOKEN";
    public static final String XSRF_HEADER = "X-XSRF-TOKEN";

    private Constants() {
    }
}
