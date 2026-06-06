package com.spotlink.auth;

import com.spotlink.core.AppProperties;
import com.spotlink.core.OperationalMetrics;
import com.spotlink.user.RegistrationStatus;
import com.spotlink.user.User;
import com.spotlink.user.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class AuthLockoutService {

    private final AuthLockoutStateRepository lockoutStates;
    private final UserRepository users;
    private final AppProperties.AuthLockout properties;
    private final Clock clock;
    private final OperationalMetrics metrics;

    public AuthLockoutService(
            AuthLockoutStateRepository lockoutStates,
            UserRepository users,
            AppProperties appProperties,
            Clock clock,
            OperationalMetrics metrics) {
        this.lockoutStates = lockoutStates;
        this.users = users;
        this.properties = appProperties.getAuthLockout();
        this.clock = clock;
        this.metrics = metrics;
    }

    @Transactional
    public void ensureNotLocked(String identifier, AuthLockoutOperation operation) {
        if (!properties.isEnabled()) {
            return;
        }
        String identifierHash = identifierHash(identifier).orElse(null);
        if (identifierHash == null) {
            return;
        }

        Instant now = Instant.now(clock);
        cleanupExpired(now);
        lockoutStates.findByIdentifierHashForUpdate(identifierHash).ifPresent(state -> {
            if (isLocked(state, now)) {
                metrics.increment("spotlink.auth.lockout.blocked", "operation", operation.metricTag());
                throw new AuthLockoutException(state.getLockedUntil());
            }
            if (isExpiredLockout(state, now) || isExpiredWindow(state, now)) {
                state.setFailedCount(0);
                state.setFirstFailedAt(now);
                state.setLastFailedAt(now);
                state.setLockedUntil(null);
            }
        });
    }

    @Transactional
    public void recordFailure(String identifier, AuthLockoutOperation operation) {
        if (!properties.isEnabled()) {
            return;
        }
        String normalizedIdentifier = normalized(identifier).orElse(null);
        if (normalizedIdentifier == null) {
            return;
        }

        Instant now = Instant.now(clock);
        cleanupExpired(now);

        Optional<User> user = users.findByEmailIgnoreCase(normalizedIdentifier);
        if (user.isPresent() && user.get().getRegistrationStatus() != RegistrationStatus.ACTIVE) {
            metrics.increment(
                    "spotlink.auth.lockout.failed_attempt",
                    "operation", operation.metricTag(),
                    "outcome", "inactive_account");
            return;
        }

        String identifierHash = hash(normalizedIdentifier);
        AuthLockoutState state = lockoutStates.findByIdentifierHashForUpdate(identifierHash)
                .orElseGet(() -> newState(identifierHash, now));
        user.ifPresent(value -> state.setUserId(value.getId()));

        if (state.getLastFailedAt() == null || isExpiredWindow(state, now)) {
            state.setFailedCount(0);
            state.setFirstFailedAt(now);
            state.setLockedUntil(null);
        }

        state.setFailedCount(state.getFailedCount() + 1);
        state.setLastFailedAt(now);
        if (state.getFirstFailedAt() == null) {
            state.setFirstFailedAt(now);
        }

        metrics.increment(
                "spotlink.auth.lockout.failed_attempt",
                "operation", operation.metricTag(),
                "outcome", "recorded");

        if (state.getFailedCount() >= threshold()) {
            state.setLockedUntil(now.plus(lockoutDuration()));
            metrics.increment("spotlink.auth.lockout.created", "operation", operation.metricTag());
        }
        lockoutStates.save(state);
    }

    @Transactional
    public void clearAfterSuccess(String identifier, AuthLockoutOperation operation) {
        if (!properties.isEnabled()) {
            return;
        }
        String identifierHash = identifierHash(identifier).orElse(null);
        if (identifierHash == null) {
            return;
        }
        lockoutStates.findByIdentifierHashForUpdate(identifierHash).ifPresent(state -> {
            lockoutStates.delete(state);
            metrics.increment("spotlink.auth.lockout.cleared", "operation", operation.metricTag());
        });
    }

    @Transactional
    public void clearForUser(User user) {
        if (user != null) {
            lockoutStates.deleteByUserId(user.getId());
            identifierHash(user.getEmail()).ifPresent(hash -> lockoutStates.findByIdentifierHash(hash)
                    .ifPresent(lockoutStates::delete));
        }
    }

    private AuthLockoutState newState(String identifierHash, Instant now) {
        AuthLockoutState state = new AuthLockoutState();
        state.setIdentifierHash(identifierHash);
        state.setFailedCount(0);
        state.setFirstFailedAt(now);
        state.setLastFailedAt(now);
        return state;
    }

    private boolean isLocked(AuthLockoutState state, Instant now) {
        return state.getLockedUntil() != null && state.getLockedUntil().isAfter(now);
    }

    private boolean isExpiredLockout(AuthLockoutState state, Instant now) {
        return state.getLockedUntil() != null && !state.getLockedUntil().isAfter(now);
    }

    private boolean isExpiredWindow(AuthLockoutState state, Instant now) {
        return state.getLastFailedAt() != null
                && state.getLastFailedAt().plus(rollingWindow()).isBefore(now);
    }

    private void cleanupExpired(Instant now) {
        Duration retention = rollingWindow().plus(lockoutDuration());
        Instant cutoff = now.minus(retention);
        lockoutStates.deleteExpired(cutoff, now);
    }

    private int threshold() {
        return Math.max(1, properties.getFailedAttemptThreshold());
    }

    private Duration rollingWindow() {
        Duration configured = properties.getRollingWindow();
        if (configured == null || configured.isZero() || configured.isNegative()) {
            return Duration.ofMinutes(15);
        }
        return configured;
    }

    private Duration lockoutDuration() {
        Duration configured = properties.getLockoutDuration();
        if (configured == null || configured.isZero() || configured.isNegative()) {
            return Duration.ofMinutes(15);
        }
        return configured;
    }

    private Optional<String> identifierHash(String identifier) {
        return normalized(identifier).map(this::hash);
    }

    private Optional<String> normalized(String identifier) {
        if (!StringUtils.hasText(identifier)) {
            return Optional.empty();
        }
        return Optional.of(identifier.trim().toLowerCase(Locale.ROOT));
    }

    private String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available.", ex);
        }
    }
}
