package com.spotlink.core;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.PropertySource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class RuntimeSafetyGuard implements InitializingBean {

    private static final Set<String> HARDENED_PROFILES = Set.of("staging", "prod", "production");
    private static final Set<String> PRODUCTION_PROFILES = Set.of("prod", "production");
    private static final String MOCK_PAYMENT_PROPERTY = "spotlink.mock-payment.enabled";
    private static final String MOCK_PAYMENT_ENV = "MOCK_PAYMENT_ENABLED";

    private final AppProperties appProperties;
    private final Environment environment;

    public RuntimeSafetyGuard(AppProperties appProperties, Environment environment) {
        this.appProperties = appProperties;
        this.environment = environment;
    }

    @Override
    public void afterPropertiesSet() {
        if (!hasAnyActiveProfile(HARDENED_PROFILES)) {
            return;
        }
        requireExternalDatabase();
        requireExternalJwtSecret();
        requireExplicitCors();
        requireSecureCookies();
        requireMockPaymentPolicy();
    }

    private void requireExternalDatabase() {
        String url = property("spring.datasource.url");
        String driver = property("spring.datasource.driver-class-name");
        String username = property("spring.datasource.username");
        String password = property("spring.datasource.password");

        if (!StringUtils.hasText(url)) {
            fail("DATABASE_URL must be configured for staging/production profiles.");
        }
        String normalizedUrl = url.toLowerCase(Locale.ROOT);
        String normalizedDriver = StringUtils.hasText(driver) ? driver.toLowerCase(Locale.ROOT) : "";
        if (normalizedUrl.startsWith("jdbc:h2:") || normalizedUrl.contains(":h2:")
                || "org.h2.driver".equals(normalizedDriver)) {
            fail("H2/default database configuration is not allowed for staging/production profiles.");
        }
        if (!StringUtils.hasText(username) || "sa".equalsIgnoreCase(username.trim())) {
            fail("DATABASE_USERNAME must be explicitly configured for staging/production profiles.");
        }
        if (!StringUtils.hasText(password)) {
            fail("DATABASE_PASSWORD must be explicitly configured for staging/production profiles.");
        }
    }

    private void requireExternalJwtSecret() {
        String secret = appProperties.getJwt().getSecret();
        if (!StringUtils.hasText(secret)) {
            fail("JWT_SECRET must be configured for staging/production profiles.");
        }
        if (secret.getBytes(java.nio.charset.StandardCharsets.UTF_8).length < 32) {
            fail("JWT_SECRET must be at least 32 bytes for staging/production profiles.");
        }
        if (AppProperties.Jwt.DEFAULT_DEV_SECRET.equals(secret)
                || AppProperties.Jwt.EXAMPLE_PLACEHOLDER_SECRET.equals(secret)) {
            fail("JWT_SECRET must not use the development/default placeholder for staging/production profiles.");
        }
    }

    private void requireExplicitCors() {
        List<String> origins = appProperties.getCors().getAllowedOrigins();
        if (origins == null || origins.isEmpty()) {
            fail("CORS_ORIGINS must be explicitly configured for staging/production profiles.");
        }
        for (String origin : origins) {
            String value = origin == null ? "" : origin.trim();
            if (!StringUtils.hasText(value)) {
                fail("CORS_ORIGINS must not contain blank origins for staging/production profiles.");
            }
            String normalized = value.toLowerCase(Locale.ROOT);
            if ("*".equals(value) || value.contains("*")) {
                fail("Wildcard CORS origins are not allowed for staging/production profiles.");
            }
            if (normalized.startsWith("http://localhost") || normalized.startsWith("http://127.")
                    || normalized.startsWith("http://[::1]")) {
                fail("Localhost CORS origins are not allowed for staging/production profiles.");
            }
            if (!normalized.startsWith("https://")) {
                fail("CORS_ORIGINS must use https origins for staging/production profiles.");
            }
        }
    }

    private void requireSecureCookies() {
        if (!appProperties.getCookie().isSecure()) {
            fail("COOKIE_SECURE=true is required for staging/production profiles.");
        }
    }

    private void requireMockPaymentPolicy() {
        if (appProperties.isMockPaymentEnabled() && hasAnyActiveProfile(PRODUCTION_PROFILES)) {
            fail("MOCK_PAYMENT_ENABLED=true is not allowed for production profiles.");
        }
        if (appProperties.isMockPaymentEnabled()
                && hasAnyActiveProfile(Set.of("staging"))
                && !hasExplicitMockPaymentSetting()) {
            fail("MOCK_PAYMENT_ENABLED must be explicitly set for staging when mock payments are used.");
        }
    }

    private boolean hasExplicitMockPaymentSetting() {
        if (environment instanceof ConfigurableEnvironment configurableEnvironment) {
            for (PropertySource<?> source : configurableEnvironment.getPropertySources()) {
                String sourceName = source.getName().toLowerCase(Locale.ROOT);
                if ("configurationproperties".equals(sourceName) || sourceName.contains("application.properties")
                        || sourceName.contains("application-test.properties")) {
                    continue;
                }
                if (source.containsProperty(MOCK_PAYMENT_PROPERTY) || source.containsProperty(MOCK_PAYMENT_ENV)) {
                    return true;
                }
            }
        }
        return System.getenv().containsKey(MOCK_PAYMENT_ENV)
                || System.getProperty(MOCK_PAYMENT_ENV) != null
                || System.getProperty(MOCK_PAYMENT_PROPERTY) != null;
    }

    private boolean hasAnyActiveProfile(Set<String> profileNames) {
        for (String profile : environment.getActiveProfiles()) {
            if (profileNames.contains(profile.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private String property(String name) {
        return environment.getProperty(name, "");
    }

    private void fail(String message) {
        throw new IllegalStateException(message);
    }
}
