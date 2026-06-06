package com.spotlink;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spotlink.notification.DevicePlatform;
import com.spotlink.notification.DeviceToken;
import com.spotlink.notification.DeviceTokenRepository;
import com.spotlink.notification.Notification;
import com.spotlink.notification.NotificationRepository;
import com.spotlink.notification.NotificationService;
import com.spotlink.notification.NotificationType;
import com.spotlink.notification.PushDeliveryResult;
import com.spotlink.notification.PushNotificationPayload;
import com.spotlink.notification.PushProvider;
import com.spotlink.notification.SafeLoggingPushProvider;
import com.spotlink.user.UserPreferences;
import com.spotlink.user.UserPreferencesRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = {
        "spotlink.push.delivery-enabled=true",
        "spotlink.push.provider=safe-log"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
class PushDeliveryReadinessTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DeviceTokenRepository deviceTokens;

    @Autowired
    private NotificationRepository notifications;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private UserPreferencesRepository preferences;

    @Autowired
    private CapturingPushProvider pushProvider;

    @Autowired
    private MeterRegistry meterRegistry;

    @BeforeEach
    void resetProvider() {
        pushProvider.reset();
    }

    @Test
    void notificationPersistenceSurvivesProviderFailure(CapturedOutput output) throws Exception {
        RegisteredUser user = registerCustomer();
        String rawToken = "apns-failure-token-" + UUID.randomUUID();
        saveToken(user.userId(), rawToken, true);
        pushProvider.mode.set(Mode.THROW);

        double attemptedBefore = counter("attempted");
        double failedBefore = counter("failed");

        Notification notification = notificationService.create(
                user.userId(),
                NotificationType.RESERVATION_CONFIRMED,
                "Rezervacija potvrdjena",
                "Vase parking mesto je rezervisano.",
                UUID.randomUUID());

        assertThat(notifications.findById(notification.getId())).isPresent();
        assertThat(deviceTokens.findByDeviceToken(rawToken).orElseThrow().isActive()).isTrue();
        assertThat(pushProvider.attempts).hasValue(1);
        assertThat(counter("attempted")).isEqualTo(attemptedBefore + 1);
        assertThat(counter("failed")).isEqualTo(failedBefore + 1);
        assertThat(output).doesNotContain(rawToken);
    }

    @Test
    void inactiveTokensAreSkipped() throws Exception {
        RegisteredUser user = registerCustomer();
        saveToken(user.userId(), "apns-inactive-token-" + UUID.randomUUID(), false);

        double attemptedBefore = counter("attempted");

        notificationService.create(
                user.userId(),
                NotificationType.SUPPORT_REPLY,
                "Nova poruka podrske",
                "Podrska je odgovorila na vas zahtev.",
                UUID.randomUUID());

        assertThat(pushProvider.attempts).hasValue(0);
        assertThat(counter("attempted")).isEqualTo(attemptedBefore);
    }

    @Test
    void invalidTokenResponseDeactivatesTokenAndRecordsMetrics() throws Exception {
        RegisteredUser user = registerCustomer();
        String rawToken = "apns-invalid-token-" + UUID.randomUUID();
        saveToken(user.userId(), rawToken, true);
        pushProvider.mode.set(Mode.INVALID_TOKEN);

        double invalidBefore = counter("invalid_token");
        double failedBefore = counter("failed");

        notificationService.create(
                user.userId(),
                NotificationType.RESERVATION_CANCELLED,
                "Rezervacija otkazana",
                "Rezervacija je otkazana.",
                UUID.randomUUID());

        assertThat(deviceTokens.findByDeviceToken(rawToken).orElseThrow().isActive()).isFalse();
        assertThat(counter("invalid_token")).isEqualTo(invalidBefore + 1);
        assertThat(counter("failed")).isEqualTo(failedBefore + 1);
    }

    @Test
    void disabledProviderOutcomeIsCounted() throws Exception {
        RegisteredUser user = registerCustomer();
        saveToken(user.userId(), "apns-disabled-token-" + UUID.randomUUID(), true);
        pushProvider.mode.set(Mode.DISABLED);

        double disabledBefore = counter("disabled");

        notificationService.create(
                user.userId(),
                NotificationType.SYSTEM,
                "Sistemsko obavestenje",
                "Push slanje je trenutno iskljuceno.",
                null);

        assertThat(counter("disabled")).isEqualTo(disabledBefore + 1);
    }

    @Test
    void disabledReservationAlertsSkipReservationPushDelivery() throws Exception {
        RegisteredUser user = registerCustomer();
        saveToken(user.userId(), "apns-reservation-pref-token-" + UUID.randomUUID(), true);
        savePreferences(user.userId(), false, true, true);

        double skippedBefore = counter("preference_skipped");

        Notification notification = notificationService.create(
                user.userId(),
                NotificationType.RESERVATION_CONFIRMED,
                "Rezervacija potvrdjena",
                "Vase parking mesto je rezervisano.",
                UUID.randomUUID());

        assertThat(notifications.findById(notification.getId())).isPresent();
        assertThat(pushProvider.attempts).hasValue(0);
        assertThat(counter("preference_skipped")).isEqualTo(skippedBefore + 1);
    }

    @Test
    void disabledPaymentAlertsSkipPaymentPushDelivery() throws Exception {
        RegisteredUser user = registerCustomer();
        saveToken(user.userId(), "apns-payment-pref-token-" + UUID.randomUUID(), true);
        savePreferences(user.userId(), true, false, true);

        double skippedBefore = counter("preference_skipped");

        notificationService.create(
                user.userId(),
                NotificationType.PAYMENT_ACTION_REQUIRED,
                "Placanje zahteva proveru",
                "Potrebna je dodatna provera placanja.",
                UUID.randomUUID());

        assertThat(pushProvider.attempts).hasValue(0);
        assertThat(counter("preference_skipped")).isEqualTo(skippedBefore + 1);
    }

    @Test
    void disabledSupportAlertsSkipSupportAndOperatorPushDelivery() throws Exception {
        RegisteredUser user = registerCustomer();
        saveToken(user.userId(), "apns-support-pref-token-" + UUID.randomUUID(), true);
        savePreferences(user.userId(), true, true, false);

        double skippedBefore = counter("preference_skipped");

        notificationService.create(
                user.userId(),
                NotificationType.SUPPORT_REPLY,
                "Nova poruka podrske",
                "Podrska je odgovorila na vas zahtev.",
                UUID.randomUUID());
        notificationService.create(
                user.userId(),
                NotificationType.OPERATOR_ALERT,
                "Operativno obavestenje",
                "Operator ima vazno obavestenje.",
                UUID.randomUUID());

        assertThat(pushProvider.attempts).hasValue(0);
        assertThat(counter("preference_skipped")).isEqualTo(skippedBefore + 2);
    }

    @Test
    void enabledPreferencesStillDeliverPush() throws Exception {
        RegisteredUser user = registerCustomer();
        saveToken(user.userId(), "apns-enabled-pref-token-" + UUID.randomUUID(), true);
        savePreferences(user.userId(), true, true, true);

        double attemptedBefore = counter("attempted");
        double succeededBefore = counter("succeeded");

        notificationService.create(
                user.userId(),
                NotificationType.PAYMENT_ACTION_REQUIRED,
                "Placanje zahteva proveru",
                "Potrebna je dodatna provera placanja.",
                UUID.randomUUID());

        assertThat(pushProvider.attempts).hasValue(1);
        assertThat(counter("attempted")).isEqualTo(attemptedBefore + 1);
        assertThat(counter("succeeded")).isEqualTo(succeededBefore + 1);
    }

    @Test
    void preferenceSkippedDeliveryDoesNotLogTokenPayloadOrUserPii(CapturedOutput output) throws Exception {
        RegisteredUser user = registerCustomer();
        String rawToken = "apns-preference-redaction-token-" + UUID.randomUUID();
        String sensitiveEmail = "preference-secret-%s@spotlink.test".formatted(UUID.randomUUID());
        saveToken(user.userId(), rawToken, true);
        savePreferences(user.userId(), true, true, false);

        notificationService.create(
                user.userId(),
                NotificationType.SUPPORT_REPLY,
                "Poruka za " + sensitiveEmail,
                "Osetljiv tekst koji ne sme u log.",
                UUID.randomUUID());

        assertThat(pushProvider.attempts).hasValue(0);
        assertThat(output).doesNotContain(rawToken);
        assertThat(output).doesNotContain(sensitiveEmail);
        assertThat(output).doesNotContain("Osetljiv tekst koji ne sme u log.");
    }

    @Test
    void safeLoggingProviderDoesNotLogTokenSecretsOrPayload(CapturedOutput output) {
        String rawToken = "apns-sensitive-token-" + UUID.randomUUID();
        DeviceToken token = new DeviceToken();
        token.setUserId(UUID.randomUUID());
        token.setDeviceToken(rawToken);
        token.setPlatform(DevicePlatform.IOS);
        token.setActive(true);

        PushNotificationPayload payload = new PushNotificationPayload(
                UUID.randomUUID(),
                NotificationType.SYSTEM,
                "Naslov",
                "Osetljiv tekst payload-a",
                null);

        new SafeLoggingPushProvider("safe-log").deliver(token, payload);

        assertThat(output).doesNotContain(rawToken);
        assertThat(output).doesNotContain("Osetljiv tekst payload-a");
        assertThat(output).doesNotContain("APNS_PRIVATE_KEY");
    }

    private double counter(String outcome) {
        return meterRegistry.getMeters().stream()
                .filter(meter -> "spotlink.push.delivery".equals(meter.getId().getName()))
                .filter(meter -> "test".equals(meter.getId().getTag("provider")))
                .filter(meter -> outcome.equals(meter.getId().getTag("outcome")))
                .filter(Counter.class::isInstance)
                .map(Counter.class::cast)
                .mapToDouble(Counter::count)
                .sum();
    }

    private void saveToken(UUID userId, String token, boolean active) {
        DeviceToken deviceToken = new DeviceToken();
        deviceToken.setUserId(userId);
        deviceToken.setDeviceToken(token);
        deviceToken.setPlatform(DevicePlatform.IOS);
        deviceToken.setActive(active);
        deviceTokens.save(deviceToken);
    }

    private void savePreferences(UUID userId, boolean reservationAlerts, boolean paymentAlerts, boolean supportAlerts) {
        UserPreferences preference = preferences.findByUserId(userId).orElseGet(UserPreferences::new);
        preference.setUserId(userId);
        preference.setReservationAlerts(reservationAlerts);
        preference.setPaymentAlerts(paymentAlerts);
        preference.setSupportAlerts(supportAlerts);
        preferences.save(preference);
    }

    private RegisteredUser registerCustomer() throws Exception {
        MvcResult registration = mockMvc.perform(post("/auth/register/customer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "firstName": "Push",
                                  "lastName": "Delivery",
                                  "email": "push-delivery-%s@spotlink.test",
                                  "phone": "+381600000007",
                                  "password": "CorrectHorse123",
                                  "acceptsTerms": true
                                }
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode body = objectMapper.readTree(registration.getResponse().getContentAsString());
        return new RegisteredUser(UUID.fromString(body.get("user").get("id").asText()));
    }

    enum Mode {
        SUCCESS,
        THROW,
        INVALID_TOKEN,
        DISABLED
    }

    static final class CapturingPushProvider implements PushProvider {
        private final AtomicReference<Mode> mode = new AtomicReference<>(Mode.SUCCESS);
        private final AtomicInteger attempts = new AtomicInteger();

        @Override
        public PushDeliveryResult deliver(DeviceToken token, PushNotificationPayload payload) {
            attempts.incrementAndGet();
            return switch (mode.get()) {
                case SUCCESS -> PushDeliveryResult.success();
                case THROW -> throw new IllegalStateException("provider unavailable");
                case INVALID_TOKEN -> PushDeliveryResult.permanentInvalidToken("Unregistered");
                case DISABLED -> PushDeliveryResult.disabled("push_delivery_disabled");
            };
        }

        @Override
        public String name() {
            return "test";
        }

        void reset() {
            mode.set(Mode.SUCCESS);
            attempts.set(0);
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class PushDeliveryTestConfig {
        @Bean
        @Primary
        CapturingPushProvider capturingPushProvider() {
            return new CapturingPushProvider();
        }
    }

    private record RegisteredUser(UUID userId) {
    }
}
