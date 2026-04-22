package com.spotlink.security;

import com.spotlink.core.AppProperties;
import com.spotlink.user.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Izdaje i validira JWT access tokene za mobilni bearer auth.
 * Web frontend i dalje koristi cookie/session auth.
 */
@Service
public class JwtService {

    private static final String CLAIM_EMAIL = "email";
    private static final String CLAIM_ROLES = "roles";
    private static final String CLAIM_AUDIENCE = "aud";

    private final SecretKey signingKey;
    private final AppProperties.Jwt properties;

    public JwtService(AppProperties appProperties, Environment environment) {
        this.properties = appProperties.getJwt();
        String secret = properties.getSecret();
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            throw new IllegalStateException("JWT secret mora biti najmanje 32 bajta");
        }
        if (isProduction(environment) && AppProperties.Jwt.DEFAULT_DEV_SECRET.equals(secret)) {
            throw new IllegalStateException("JWT_SECRET must be configured for production profiles.");
        }
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
    }

    /** Generise JWT token za korisnika. */
    public String generateToken(UUID userId, String email, List<String> roles) {
        return issueAccessToken(userId, email, roles).token();
    }

    public AccessToken issueAccessToken(User user) {
        List<String> roles = user.getRoles().stream()
                .map(Enum::name)
                .toList();
        return issueAccessToken(user.getId(), user.getEmail(), roles);
    }

    public AccessToken issueAccessToken(UUID userId, String email, List<String> roles) {
        return issueAccessToken(userId, email, roles, Instant.now());
    }

    private AccessToken issueAccessToken(UUID userId, String email, List<String> roles, Instant now) {
        Instant expiry = now.plus(properties.getAccessTokenTtlMinutes(), ChronoUnit.MINUTES);
        String token = Jwts.builder()
                .issuer(properties.getIssuer())
                .subject(userId.toString())
                .claim(CLAIM_EMAIL, email)
                .claim(CLAIM_ROLES, roles)
                .claim(CLAIM_AUDIENCE, properties.getAudience())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(signingKey)
                .compact();
        return new AccessToken(token, now, expiry, secondsBetween(now, expiry));
    }

    /** Validira token i vraca Claims, ili baca JwtException. */
    public Claims parseAndValidate(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        validateIssuerAndAudience(claims);
        return claims;
    }

    /**
     * Vraca userId iz tokena bez bacanja izuzetka.
     * @return UUID ili null ako je token nevalidan/istekao
     */
    public UUID extractUserId(String token) {
        try {
            Claims claims = parseAndValidate(token);
            return UUID.fromString(claims.getSubject());
        } catch (JwtException | IllegalArgumentException e) {
            return null;
        }
    }

    public long expirySeconds() {
        return (long) properties.getAccessTokenTtlMinutes() * 60;
    }

    public long refreshExpirySeconds() {
        return (long) properties.getRefreshTokenTtlDays() * 24 * 3600;
    }

    @SuppressWarnings("unchecked")
    public List<String> roles(Claims claims) {
        Object value = claims.get(CLAIM_ROLES);
        if (value instanceof List<?> list) {
            return list.stream().filter(String.class::isInstance).map(String.class::cast).toList();
        }
        return List.of();
    }

    private void validateIssuerAndAudience(Claims claims) {
        if (StringUtils.hasText(properties.getIssuer()) && !Objects.equals(properties.getIssuer(), claims.getIssuer())) {
            throw new JwtException("Invalid issuer.");
        }
        if (StringUtils.hasText(properties.getAudience())
                && !audienceMatches(claims.get(CLAIM_AUDIENCE))) {
            throw new JwtException("Invalid audience.");
        }
    }

    private boolean audienceMatches(Object claim) {
        if (claim instanceof String value) {
            return Objects.equals(properties.getAudience(), value);
        }
        if (claim instanceof Iterable<?> values) {
            for (Object value : values) {
                if (Objects.equals(properties.getAudience(), String.valueOf(value))) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isProduction(Environment environment) {
        for (String profile : environment.getActiveProfiles()) {
            String normalized = profile.toLowerCase();
            if ("prod".equals(normalized) || "production".equals(normalized)) {
                return true;
            }
        }
        return false;
    }

    private long secondsBetween(Instant start, Instant end) {
        return Math.max(0, end.getEpochSecond() - start.getEpochSecond());
    }

    public record AccessToken(String token, Instant issuedAt, Instant expiresAt, long expiresInSeconds) {
    }
}
