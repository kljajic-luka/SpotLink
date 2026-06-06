package com.spotlink.notification;

import com.spotlink.user.UserPreferences;
import com.spotlink.user.UserPreferencesRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class NotificationPreferencePolicy {

    private final UserPreferencesRepository preferences;

    public NotificationPreferencePolicy(UserPreferencesRepository preferences) {
        this.preferences = preferences;
    }

    public PreferenceDecision pushAllowed(UUID userId, NotificationType type) {
        UserPreferences prefs = preferences.findByUserId(userId).orElse(null);
        return switch (type) {
            case RESERVATION_CONFIRMED, RESERVATION_CANCELLED, ACCESS_INSTRUCTIONS_READY ->
                    decision(alertEnabled(prefs, PreferenceCategory.RESERVATION), "reservation_alerts_disabled");
            case PAYMENT_ACTION_REQUIRED ->
                    decision(alertEnabled(prefs, PreferenceCategory.PAYMENT), "payment_alerts_disabled");
            // Operator alerts are currently operational/support messages until a separate operator-alert preference exists.
            case SUPPORT_REPLY, OPERATOR_ALERT ->
                    decision(alertEnabled(prefs, PreferenceCategory.SUPPORT), "support_alerts_disabled");
            case SYSTEM -> PreferenceDecision.allow();
        };
    }

    private PreferenceDecision decision(boolean allowed, String disabledReason) {
        return allowed ? PreferenceDecision.allow() : PreferenceDecision.skipped(disabledReason);
    }

    private boolean alertEnabled(UserPreferences prefs, PreferenceCategory category) {
        if (prefs == null) {
            return true;
        }
        return switch (category) {
            case RESERVATION -> prefs.isReservationAlerts();
            case PAYMENT -> prefs.isPaymentAlerts();
            case SUPPORT -> prefs.isSupportAlerts();
        };
    }

    private enum PreferenceCategory {
        RESERVATION,
        PAYMENT,
        SUPPORT
    }

    public record PreferenceDecision(boolean allowed, String reason) {
        static PreferenceDecision allow() {
            return new PreferenceDecision(true, "allowed");
        }

        static PreferenceDecision skipped(String reason) {
            return new PreferenceDecision(false, reason);
        }
    }
}
