package com.spotlink.core;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "spotlink")
public class AppProperties {

    private final Cors cors = new Cors();
    private final Cookie cookie = new Cookie();
    private final Jwt jwt = new Jwt();
    private final Mail mail = new Mail();
    private final MockPayment mockPayment = new MockPayment();
    private final Payment payment = new Payment();
    private final PasswordReset passwordReset = new PasswordReset();
    private final Push push = new Push();
    private final RateLimit rateLimit = new RateLimit();
    private String defaultCurrency = "RSD";
    private int quoteTtlMinutes = 15;
    private int bookingSlotMinutes = 15;
    private int minReservationMinutes = 15;
    private int maxReservationDays = 30;
    private int manualConfirmationTtlHours = 24;
    private long holdExpiryScanMs = 60000;

    public Cors getCors() {
        return cors;
    }

    public Cookie getCookie() {
        return cookie;
    }

    public Jwt getJwt() {
        return jwt;
    }

    public Mail getMail() {
        return mail;
    }

    public MockPayment getMockPayment() {
        return mockPayment;
    }

    public Payment getPayment() {
        return payment;
    }

    public PasswordReset getPasswordReset() {
        return passwordReset;
    }

    public Push getPush() {
        return push;
    }

    public RateLimit getRateLimit() {
        return rateLimit;
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

    public int getBookingSlotMinutes() {
        return bookingSlotMinutes;
    }

    public void setBookingSlotMinutes(int bookingSlotMinutes) {
        this.bookingSlotMinutes = bookingSlotMinutes;
    }

    public int getMinReservationMinutes() {
        return minReservationMinutes;
    }

    public void setMinReservationMinutes(int minReservationMinutes) {
        this.minReservationMinutes = minReservationMinutes;
    }

    public int getMaxReservationDays() {
        return maxReservationDays;
    }

    public void setMaxReservationDays(int maxReservationDays) {
        this.maxReservationDays = maxReservationDays;
    }

    public int getManualConfirmationTtlHours() {
        return manualConfirmationTtlHours;
    }

    public void setManualConfirmationTtlHours(int manualConfirmationTtlHours) {
        this.manualConfirmationTtlHours = manualConfirmationTtlHours;
    }

    public boolean isMockPaymentEnabled() {
        return mockPayment.isEnabled();
    }

    public void setMockPaymentEnabled(boolean mockPaymentEnabled) {
        this.mockPayment.setEnabled(mockPaymentEnabled);
    }

    public long getHoldExpiryScanMs() {
        return holdExpiryScanMs;
    }

    public void setHoldExpiryScanMs(long holdExpiryScanMs) {
        this.holdExpiryScanMs = holdExpiryScanMs;
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

    public static class MockPayment {
        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    public static class Payment {
        private String provider = "mock";
        private boolean onlineEnabled = true;

        public String getProvider() {
            return provider;
        }

        public void setProvider(String provider) {
            this.provider = provider;
        }

        public boolean isOnlineEnabled() {
            return onlineEnabled;
        }

        public void setOnlineEnabled(boolean onlineEnabled) {
            this.onlineEnabled = onlineEnabled;
        }
    }

    public static class PasswordReset {
        private boolean deliveryEnabled = true;
        private String resetUrl = "http://localhost:4200/reset-password";
        private int tokenTtlMinutes = 30;

        public boolean isDeliveryEnabled() {
            return deliveryEnabled;
        }

        public void setDeliveryEnabled(boolean deliveryEnabled) {
            this.deliveryEnabled = deliveryEnabled;
        }

        public String getResetUrl() {
            return resetUrl;
        }

        public void setResetUrl(String resetUrl) {
            this.resetUrl = resetUrl;
        }

        public int getTokenTtlMinutes() {
            return tokenTtlMinutes;
        }

        public void setTokenTtlMinutes(int tokenTtlMinutes) {
            this.tokenTtlMinutes = tokenTtlMinutes;
        }
    }

    public static class Mail {
        private String provider = "safe-log";
        private final Smtp smtp = new Smtp();

        public String getProvider() {
            return provider;
        }

        public void setProvider(String provider) {
            this.provider = provider;
        }

        public Smtp getSmtp() {
            return smtp;
        }
    }

    public static class Smtp {
        private String host = "";
        private int port = 587;
        private String username = "";
        private String password = "";
        private String from = "";
        private boolean starttlsEnabled = true;
        private boolean authEnabled = true;
        private int connectionTimeoutMs = 5000;
        private int readTimeoutMs = 5000;
        private int writeTimeoutMs = 5000;

        public String getHost() {
            return host;
        }

        public void setHost(String host) {
            this.host = host;
        }

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public String getFrom() {
            return from;
        }

        public void setFrom(String from) {
            this.from = from;
        }

        public boolean isStarttlsEnabled() {
            return starttlsEnabled;
        }

        public void setStarttlsEnabled(boolean starttlsEnabled) {
            this.starttlsEnabled = starttlsEnabled;
        }

        public boolean isAuthEnabled() {
            return authEnabled;
        }

        public void setAuthEnabled(boolean authEnabled) {
            this.authEnabled = authEnabled;
        }

        public int getConnectionTimeoutMs() {
            return connectionTimeoutMs;
        }

        public void setConnectionTimeoutMs(int connectionTimeoutMs) {
            this.connectionTimeoutMs = connectionTimeoutMs;
        }

        public int getReadTimeoutMs() {
            return readTimeoutMs;
        }

        public void setReadTimeoutMs(int readTimeoutMs) {
            this.readTimeoutMs = readTimeoutMs;
        }

        public int getWriteTimeoutMs() {
            return writeTimeoutMs;
        }

        public void setWriteTimeoutMs(int writeTimeoutMs) {
            this.writeTimeoutMs = writeTimeoutMs;
        }
    }

    public static class Push {
        private boolean deliveryEnabled;
        private String provider = "none";
        private final Apns apns = new Apns();

        public boolean isDeliveryEnabled() {
            return deliveryEnabled;
        }

        public void setDeliveryEnabled(boolean deliveryEnabled) {
            this.deliveryEnabled = deliveryEnabled;
        }

        public String getProvider() {
            return provider;
        }

        public void setProvider(String provider) {
            this.provider = provider;
        }

        public Apns getApns() {
            return apns;
        }
    }

    public static class Apns {
        private String environment = "sandbox";
        private String bundleId = "";
        private String teamId = "";
        private String keyId = "";
        private String privateKey = "";
        private String privateKeyPath = "";
        private int requestTimeoutSeconds = 10;

        public String getEnvironment() {
            return environment;
        }

        public void setEnvironment(String environment) {
            this.environment = environment;
        }

        public String getBundleId() {
            return bundleId;
        }

        public void setBundleId(String bundleId) {
            this.bundleId = bundleId;
        }

        public String getTeamId() {
            return teamId;
        }

        public void setTeamId(String teamId) {
            this.teamId = teamId;
        }

        public String getKeyId() {
            return keyId;
        }

        public void setKeyId(String keyId) {
            this.keyId = keyId;
        }

        public String getPrivateKey() {
            return privateKey;
        }

        public void setPrivateKey(String privateKey) {
            this.privateKey = privateKey;
        }

        public String getPrivateKeyPath() {
            return privateKeyPath;
        }

        public void setPrivateKeyPath(String privateKeyPath) {
            this.privateKeyPath = privateKeyPath;
        }

        public int getRequestTimeoutSeconds() {
            return requestTimeoutSeconds;
        }

        public void setRequestTimeoutSeconds(int requestTimeoutSeconds) {
            this.requestTimeoutSeconds = requestTimeoutSeconds;
        }
    }

    public static class RateLimit {
        private boolean enabled = true;
        private final Rule login = new Rule(10, Duration.ofMinutes(1));
        private final Rule mobileToken = new Rule(10, Duration.ofMinutes(1));
        private final Rule registration = new Rule(5, Duration.ofMinutes(1));
        private final Rule passwordResetRequest = new Rule(5, Duration.ofMinutes(1));
        private final Rule passwordResetComplete = new Rule(10, Duration.ofMinutes(1));
        private final Rule analytics = new Rule(60, Duration.ofMinutes(1));

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public Rule getLogin() {
            return login;
        }

        public Rule getMobileToken() {
            return mobileToken;
        }

        public Rule getRegistration() {
            return registration;
        }

        public Rule getPasswordResetRequest() {
            return passwordResetRequest;
        }

        public Rule getPasswordResetComplete() {
            return passwordResetComplete;
        }

        public Rule getAnalytics() {
            return analytics;
        }

        public static class Rule {
            private boolean enabled = true;
            private int permits;
            private Duration window;

            public Rule() {
                this(10, Duration.ofMinutes(1));
            }

            public Rule(int permits, Duration window) {
                this.permits = permits;
                this.window = window;
            }

            public boolean isEnabled() {
                return enabled;
            }

            public void setEnabled(boolean enabled) {
                this.enabled = enabled;
            }

            public int getPermits() {
                return permits;
            }

            public void setPermits(int permits) {
                this.permits = permits;
            }

            public Duration getWindow() {
                return window;
            }

            public void setWindow(Duration window) {
                this.window = window;
            }
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
        public static final String EXAMPLE_PLACEHOLDER_SECRET = "change-me-to-a-secure-random-32-byte-secret-string";

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
