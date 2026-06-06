package com.spotlink.auth;

import com.spotlink.core.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "auth_lockout_states",
        indexes = {
                @Index(name = "idx_auth_lockout_states_user", columnList = "user_id"),
                @Index(name = "idx_auth_lockout_states_expiry", columnList = "last_failed_at, locked_until")
        }
)
public class AuthLockoutState extends AuditableEntity {

    @Column(name = "identifier_hash", nullable = false, unique = true, length = 128)
    private String identifierHash;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "failed_count", nullable = false)
    private int failedCount;

    @Column(name = "first_failed_at", nullable = false)
    private Instant firstFailedAt;

    @Column(name = "last_failed_at", nullable = false)
    private Instant lastFailedAt;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    public String getIdentifierHash() {
        return identifierHash;
    }

    public void setIdentifierHash(String identifierHash) {
        this.identifierHash = identifierHash;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public int getFailedCount() {
        return failedCount;
    }

    public void setFailedCount(int failedCount) {
        this.failedCount = failedCount;
    }

    public Instant getFirstFailedAt() {
        return firstFailedAt;
    }

    public void setFirstFailedAt(Instant firstFailedAt) {
        this.firstFailedAt = firstFailedAt;
    }

    public Instant getLastFailedAt() {
        return lastFailedAt;
    }

    public void setLastFailedAt(Instant lastFailedAt) {
        this.lastFailedAt = lastFailedAt;
    }

    public Instant getLockedUntil() {
        return lockedUntil;
    }

    public void setLockedUntil(Instant lockedUntil) {
        this.lockedUntil = lockedUntil;
    }
}
