package com.spotlink.core;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import com.spotlink.notification.MailProvider;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.ObjectProvider;
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
    private static final String PUSH_DELIVERY_PROPERTY = "spotlink.push.delivery-enabled";
    private static final String PUSH_DELIVERY_ENV = "PUSH_DELIVERY_ENABLED";

    private final AppProperties appProperties;
    private final Environment environment;
    private final ObjectProvider<MailProvider> mailProvider;

    public RuntimeSafetyGuard(
            AppProperties appProperties,
            Environment environment,
            ObjectProvider<MailProvider> mailProvider) {
        this.appProperties = appProperties;
        this.environment = environment;
        this.mailProvider = mailProvider;
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
        requirePasswordResetDeliveryPolicy();
        requirePushDeliveryPolicy();
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

    private void requirePasswordResetDeliveryPolicy() {
        if (!appProperties.getPasswordReset().isDeliveryEnabled()) {
            return;
        }
        MailProvider provider = mailProvider.getIfAvailable();
        if (provider == null || !provider.productionReady()) {
            fail("PASSWORD_RESET_DELIVERY_ENABLED=true requires a production-ready MailProvider for staging/production profiles.");
        }
    }

    private void requirePushDeliveryPolicy() {
        if (!hasExplicitPushDeliverySetting()) {
            fail("PUSH_DELIVERY_ENABLED must be explicitly set for staging/production profiles.");
        }
        if (!appProperties.getPush().isDeliveryEnabled()) {
            return;
        }

        String provider = normalized(appProperties.getPush().getProvider());
        if (!Set.of("apns", "safe-log", "none").contains(provider)) {
            fail("PUSH_PROVIDER must be one of none, safe-log, or apns.");
        }
        if (!"apns".equals(provider)) {
            fail("PUSH_DELIVERY_ENABLED=true requires PUSH_PROVIDER=apns for staging/production profiles.");
        }

        AppProperties.Apns apns = appProperties.getPush().getApns();
        String apnsEnvironment = normalized(apns.getEnvironment());
        if (!Set.of("sandbox", "production").contains(apnsEnvironment)) {
            fail("APNS_ENVIRONMENT must be sandbox or production.");
        }
        if ("sandbox".equals(apnsEnvironment) && hasAnyActiveProfile(PRODUCTION_PROFILES)) {
            fail("APNS_ENVIRONMENT=sandbox is not allowed for production profiles.");
        }
        requireText(apns.getBundleId(), "APNS_BUNDLE_ID is required when PUSH_PROVIDER=apns.");
        requireText(apns.getTeamId(), "APNS_TEAM_ID is required when PUSH_PROVIDER=apns.");
        requireText(apns.getKeyId(), "APNS_KEY_ID is required when PUSH_PROVIDER=apns.");
        if (!StringUtils.hasText(apns.getPrivateKey()) && !StringUtils.hasText(apns.getPrivateKeyPath())) {
            fail("APNS_PRIVATE_KEY or APNS_PRIVATE_KEY_PATH is required when PUSH_PROVIDER=apns.");
        }
    }

    private boolean hasExplicitMockPaymentSetting() {
        return hasExplicitSetting(MOCK_PAYMENT_PROPERTY, MOCK_PAYMENT_ENV);
    }

    private boolean hasExplicitPushDeliverySetting() {
        return hasExplicitSetting(PUSH_DELIVERY_PROPERTY, PUSH_DELIVERY_ENV);
    }

    private boolean hasExplicitSetting(String propertyName, String environmentName) {
        if (environment instanceof ConfigurableEnvironment configurableEnvironment) {
            for (PropertySource<?> source : configurableEnvironment.getPropertySources()) {
                String sourceName = source.getName().toLowerCase(Locale.ROOT);
                if ("configurationproperties".equals(sourceName) || sourceName.contains("application.properties")
                        || sourceName.contains("application-test.properties")) {
                    continue;
                }
                if (source.containsProperty(propertyName) || source.containsProperty(environmentName)) {
                    return true;
                }
            }
        }
        return System.getenv().containsKey(environmentName)
                || System.getProperty(environmentName) != null
                || System.getProperty(propertyName) != null;
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

    private String normalized(String value) {
        return StringUtils.hasText(value) ? value.trim().toLowerCase(Locale.ROOT) : "";
    }

    private void requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            fail(message);
        }
    }

    private void fail(String message) {
        throw new IllegalStateException(message);
    }
}
