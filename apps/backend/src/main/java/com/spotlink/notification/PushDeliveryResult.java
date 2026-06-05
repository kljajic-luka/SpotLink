package com.spotlink.notification;

public record PushDeliveryResult(
        PushDeliveryOutcome outcome,
        String reason
) {
    public static PushDeliveryResult success() {
        return new PushDeliveryResult(PushDeliveryOutcome.SUCCESS, null);
    }

    public static PushDeliveryResult permanentInvalidToken(String reason) {
        return new PushDeliveryResult(PushDeliveryOutcome.PERMANENT_INVALID_TOKEN, reason);
    }

    public static PushDeliveryResult transientFailure(String reason) {
        return new PushDeliveryResult(PushDeliveryOutcome.TRANSIENT_FAILURE, reason);
    }

    public static PushDeliveryResult disabled(String reason) {
        return new PushDeliveryResult(PushDeliveryOutcome.DISABLED, reason);
    }
}
