package com.spotlink.core;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "spotlink")
public class AppProperties {

    private final Cors cors = new Cors();
    private final Cookie cookie = new Cookie();
    private final Jwt jwt = new Jwt();
    private String defaultCurrency = "USD";
    private int quoteTtlMinutes = 15;
    private boolean mockPaymentEnabled = true;

    public Cors getCors() {
        return cors;
    }

    public Cookie getCookie() {
        return cookie;
    }

    public Jwt getJwt() {
        return jwt;
    }

    public String getDefaultCurrency() {
        return defaultCurrency;
    }

    public void setDefaultCurrency(String defaultCurrency) {
        this.defaultCurrency = defaultCurrency;
    }

    public int getQuoteTtlMinutes() {
        return quoteTtlMinutes;
    }

    public void setQuoteTtlMinutes(int quoteTtlMinutes) {
        this.quoteTtlMinutes = quoteTtlMinutes;
    }

    public boolean isMockPaymentEnabled() {
        return mockPaymentEnabled;
    }

    public void setMockPaymentEnabled(boolean mockPaymentEnabled) {
        this.mockPaymentEnabled = mockPaymentEnabled;
    }

    public static class Cors {
        private List<String> allowedOrigins = new ArrayList<>(List.of("http://localhost:4200"));

        public List<String> getAllowedOrigins() {
            return allowedOrigins;
        }

        public void setAllowedOrigins(List<String> allowedOrigins) {
            this.allowedOrigins = allowedOrigins;
        }
    }

    public static class Cookie {
        private boolean secure;
        private String domain = "";
        private String sameSite = "Lax";
        private int sessionTtlHours = 12;

        public boolean isSecure() {
            return secure;
        }

        public void setSecure(boolean secure) {
            this.secure = secure;
        }

        public String getDomain() {
            return domain;
        }

        public void setDomain(String domain) {
            this.domain = domain;
        }

        public String getSameSite() {
            return sameSite;
        }

        public void setSameSite(String sameSite) {
            this.sameSite = sameSite;
        }

        public int getSessionTtlHours() {
            return sessionTtlHours;
        }

        public void setSessionTtlHours(int sessionTtlHours) {
            this.sessionTtlHours = sessionTtlHours;
        }
    }

    /**
     * JWT konfiguracija za mobilni bearer token auth.
     * Secret je sirovi UTF-8 string od najmanje 32 bajta (256 bita).
     * JwtService ga koristi direktno kao HMAC kljuc (Keys.hmacShaKeyFor(secret.getBytes(UTF_8))).
     * NE treba base64 enkodiranje – proslediti sirovi string kroz JWT_SECRET env varijablu.
     */
    public static class Jwt {
        public static final String DEFAULT_DEV_SECRET = "c3BvdGxpbmstZGV2LXNlY3JldC1rZXktbXVzdC1iZS1hdC1sZWFzdC0yNTYtYml0cy1sb25n";

        // 512-bitni dev secret – zameniti environment varijablom na produkciji
        private String secret = DEFAULT_DEV_SECRET;
        private int expiryDays = 7;
        private int accessTokenTtlMinutes = 15;
        private int refreshTokenTtlDays = 30;
        private String issuer = "spotlink";
        private String audience = "spotlink-mobile";

        public String getSecret() {
            return secret;
        }

        public void setSecret(String secret) {
            this.secret = secret;
        }

        public int getExpiryDays() {
            return expiryDays;
        }

        public void setExpiryDays(int expiryDays) {
            this.expiryDays = expiryDays;
        }

        public int getAccessTokenTtlMinutes() {
            return accessTokenTtlMinutes;
        }

        public void setAccessTokenTtlMinutes(int accessTokenTtlMinutes) {
            this.accessTokenTtlMinutes = accessTokenTtlMinutes;
        }

        public int getRefreshTokenTtlDays() {
            return refreshTokenTtlDays;
        }

        public void setRefreshTokenTtlDays(int refreshTokenTtlDays) {
            this.refreshTokenTtlDays = refreshTokenTtlDays;
        }

        public String getIssuer() {
            return issuer;
        }

        public void setIssuer(String issuer) {
            this.issuer = issuer;
        }

        public String getAudience() {
            return audience;
        }

        public void setAudience(String audience) {
            this.audience = audience;
        }
    }
}
