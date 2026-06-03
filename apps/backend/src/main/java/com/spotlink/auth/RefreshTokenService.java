package com.spotlink.auth;

import com.spotlink.core.AppProperties;
import com.spotlink.user.RegistrationStatus;
import com.spotlink.user.User;
import com.spotlink.user.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class RefreshTokenService {

    private static final int TOKEN_BYTES = 48;

    private final RefreshTokenRepository refreshTokens;
    private final UserRepository users;
    private final AppProperties appProperties;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();

    public RefreshTokenService(
            RefreshTokenRepository refreshTokens,
            UserRepository users,
            AppProperties appProperties,
            Clock clock) {
        this.refreshTokens = refreshTokens;
        this.users = users;
        this.appProperties = appProperties;
        this.clock = clock;
    }

    @Transactional
    public IssuedRefreshToken issue(User user, String deviceId, String userAgent) {
        if (user.getRegistrationStatus() != RegistrationStatus.ACTIVE) {
            throw new BadCredentialsException("Refresh token user is not active.");
        }
        Instant issuedAt = Instant.now(clock);
        Instant expiresAt = issuedAt.plus(appProperties.getJwt().getRefreshTokenTtlDays(), ChronoUnit.DAYS);
        String rawToken = generateToken();

        RefreshToken token = new RefreshToken();
        token.setUserId(user.getId());
        token.setTokenHash(hash(rawToken));
        token.setDeviceId(truncate(blankToNull(deviceId), 160));
        token.setUserAgent(truncate(blankToNull(userAgent), 500));
        token.setIssuedAt(issuedAt);
        token.setExpiresAt(expiresAt);
        RefreshToken saved = refreshTokens.save(token);
        return new IssuedRefreshToken(rawToken, saved);
    }

    @Transactional
    public RotationResult rotate(String rawRefreshToken, String deviceId, String userAgent) {
        // Koristimo findByTokenHashForUpdate (PESSIMISTIC_WRITE) da blokiramo red
        // za azuriranje. Ako dva konkurentna poziva donesu isti token, drugi ce
        // cekati na bazi dok prvi ne komituje; potom ce naci revokedAt != null
        // i aktivirati theft detection (revokeAllForUser).
        if (!org.springframework.util.StringUtils.hasText(rawRefreshToken)) {
            throw new org.springframework.security.authentication.BadCredentialsException("Refresh token is required.");
        }
        RefreshToken existing = refreshTokens.findByTokenHashForUpdate(hash(rawRefreshToken))
                .orElseThrow(() -> new org.springframework.security.authentication.BadCredentialsException("Refresh token is invalid."));
        Instant now = Instant.now(clock);
        if (existing.getRevokedAt() != null) {
            revokeAllForUser(existing.getUserId(), now);
            throw new BadCredentialsException("Refresh token has been revoked.");
        }
        if (!existing.getExpiresAt().isAfter(now)) {
            existing.setRevokedAt(now);
            throw new BadCredentialsException("Refresh token has expired.");
        }

        User user = users.findById(existing.getUserId())
                .orElseThrow(() -> new BadCredentialsException("Refresh token user was not found."));
        if (user.getRegistrationStatus() != RegistrationStatus.ACTIVE) {
            revokeAllForUser(existing.getUserId(), now);
            throw new BadCredentialsException("Refresh token user is not active.");
        }
        IssuedRefreshToken replacement = issue(user, deviceId, userAgent);
        existing.setRevokedAt(now);
        existing.setReplacedByTokenId(replacement.entity().getId());
        return new RotationResult(user, replacement);
    }

    @Transactional
    public void revoke(String rawRefreshToken) {
        RefreshToken existing = findKnownToken(rawRefreshToken);
        if (existing.getRevokedAt() == null) {
            existing.setRevokedAt(Instant.now(clock));
        }
    }

    @Transactional
    public void revokeAllForUser(UUID userId) {
        revokeAllForUser(userId, Instant.now(clock));
    }

    private void revokeAllForUser(UUID userId, Instant now) {
        for (RefreshToken token : refreshTokens.findByUserIdAndRevokedAtIsNullAndExpiresAtAfter(userId, now)) {
            token.setRevokedAt(now);
        }
    }

    private RefreshToken findKnownToken(String rawRefreshToken) {
        if (!StringUtils.hasText(rawRefreshToken)) {
            throw new BadCredentialsException("Refresh token is required.");
        }
        return refreshTokens.findByTokenHash(hash(rawRefreshToken))
                .orElseThrow(() -> new BadCredentialsException("Refresh token is invalid."));
    }

    private String generateToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return "sl_refresh_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available.", ex);
        }
    }

    private String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    public record IssuedRefreshToken(String rawToken, RefreshToken entity) {
    }

    public record RotationResult(User user, IssuedRefreshToken refreshToken) {
    }
}
