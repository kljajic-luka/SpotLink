package com.spotlink.core;

import java.util.Map;

public class ValidationException extends RuntimeException {

    private final Map<String, String> fields;

    public ValidationException(String field, String message) {
        super(message);
        this.fields = Map.of(field, message);
    }

    public Map<String, String> getFields() {
        return fields;
    }
}
