package com.spotlink.auth;

public enum AuthLockoutOperation {
    LOGIN("login"),
    MOBILE_TOKEN("mobile_token");

    private final String metricTag;

    AuthLockoutOperation(String metricTag) {
        this.metricTag = metricTag;
    }

    public String metricTag() {
        return metricTag;
    }
}
