package com.spotlink;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spotlink.notification.DevicePlatform;
import com.spotlink.notification.DeviceToken;
import com.spotlink.notification.DeviceTokenRepository;
import com.spotlink.notification.Notification;
import com.spotlink.notification.NotificationService;
import com.spotlink.notification.NotificationType;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class NotificationDeviceTokenLifecycleTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DeviceTokenRepository deviceTokens;

    @Autowired
    private NotificationService notificationService;

    @Test
    void registerDeviceTokenCreatesActiveTokenForCurrentUser() throws Exception {
        RegisteredUser user = registerCustomer();
        String token = apnsToken();

        registerDeviceToken(user.session(), token)
                .andExpect(status().isNoContent());

        DeviceToken saved = deviceTokens.findByDeviceToken(token).orElseThrow();
        assertThat(saved.getUserId()).isEqualTo(user.userId());
        assertThat(saved.getPlatform()).isEqualTo(DevicePlatform.IOS);
        assertThat(saved.isActive()).isTrue();
        assertThat(notificationService.activeDeviceTokens(user.userId(), DevicePlatform.IOS))
                .extracting(DeviceToken::getDeviceToken)
                .contains(token);
    }

    @Test
    void duplicateRegisterReactivatesExistingToken() throws Exception {
        RegisteredUser user = registerCustomer();
        String token = apnsToken();

        registerDeviceToken(user.session(), token)
                .andExpect(status().isNoContent());
        UUID tokenId = deviceTokens.findByDeviceToken(token).orElseThrow().getId();

        unregisterDeviceToken(user.session(), token)
                .andExpect(status().isNoContent());
        assertThat(deviceTokens.findByDeviceToken(token).orElseThrow().isActive()).isFalse();

        registerDeviceToken(user.session(), token)
                .andExpect(status().isNoContent());

        DeviceToken saved = deviceTokens.findByDeviceToken(token).orElseThrow();
        assertThat(saved.getId()).isEqualTo(tokenId);
        assertThat(saved.getUserId()).isEqualTo(user.userId());
        assertThat(saved.isActive()).isTrue();
    }

    @Test
    void unregisterDeviceTokenDeactivatesOnlyCurrentUsersToken() throws Exception {
        RegisteredUser user = registerCustomer();
        String token = apnsToken();

        registerDeviceToken(user.session(), token)
                .andExpect(status().isNoContent());
        unregisterDeviceToken(user.session(), token)
                .andExpect(status().isNoContent());

        DeviceToken saved = deviceTokens.findByDeviceToken(token).orElseThrow();
        assertThat(saved.getUserId()).isEqualTo(user.userId());
        assertThat(saved.isActive()).isFalse();
        assertThat(notificationService.activeDeviceTokens(user.userId(), DevicePlatform.IOS)).isEmpty();
    }

    @Test
    void unregisterMissingOrForeignTokenDoesNotLeakOwnership() throws Exception {
        RegisteredUser owner = registerCustomer();
        RegisteredUser other = registerCustomer();
        String token = apnsToken();

        registerDeviceToken(owner.session(), token)
                .andExpect(status().isNoContent());

        unregisterDeviceToken(other.session(), token)
                .andExpect(status().isNoContent());
        unregisterDeviceToken(other.session(), apnsToken())
                .andExpect(status().isNoContent());

        DeviceToken saved = deviceTokens.findByDeviceToken(token).orElseThrow();
        assertThat(saved.getUserId()).isEqualTo(owner.userId());
        assertThat(saved.isActive()).isTrue();
        assertThat(notificationService.activeDeviceTokens(owner.userId(), DevicePlatform.IOS))
                .extracting(DeviceToken::getDeviceToken)
                .containsExactly(token);
        assertThat(notificationService.activeDeviceTokens(other.userId(), DevicePlatform.IOS)).isEmpty();
    }

    @Test
    void unreadNotificationBehaviorStaysIntactAcrossTokenLifecycle() throws Exception {
        RegisteredUser user = registerCustomer();
        String token = apnsToken();
        Notification notification = notificationService.create(
                user.userId(),
                NotificationType.RESERVATION_CONFIRMED,
                "Rezervacija potvrdjena",
                "Vase parking mesto je rezervisano.",
                UUID.randomUUID());

        mockMvc.perform(get("/notifications/unread-count").session(user.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1));

        registerDeviceToken(user.session(), token)
                .andExpect(status().isNoContent());
        unregisterDeviceToken(user.session(), token)
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/v1/notifications/unread-count").session(user.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1));

        mockMvc.perform(post("/notifications/%s/read".formatted(notification.getId()))
                        .with(csrf())
                        .session(user.session()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/notifications/unread-count").session(user.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(0));
    }

    private org.springframework.test.web.servlet.ResultActions registerDeviceToken(MockHttpSession session, String token)
            throws Exception {
        return mockMvc.perform(post("/notifications/device-tokens")
                .with(csrf())
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content(deviceTokenBody(token)));
    }

    private org.springframework.test.web.servlet.ResultActions unregisterDeviceToken(MockHttpSession session, String token)
            throws Exception {
        return mockMvc.perform(post("/notifications/device-tokens/unregister")
                .with(csrf())
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content(deviceTokenBody(token)));
    }

    private RegisteredUser registerCustomer() throws Exception {
        MvcResult registration = mockMvc.perform(post("/auth/register/customer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "firstName": "Push",
                                  "lastName": "Tester",
                                  "email": "push-%s@spotlink.test",
                                  "phone": "+381600000003",
                                  "password": "CorrectHorse123",
                                  "acceptsTerms": true
                                }
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isCreated())
                .andReturn();

        MockHttpSession session = (MockHttpSession) registration.getRequest().getSession(false);
        JsonNode body = objectMapper.readTree(registration.getResponse().getContentAsString());
        return new RegisteredUser(session, UUID.fromString(body.get("user").get("id").asText()));
    }

    private String apnsToken() {
        return "apns-" + UUID.randomUUID();
    }

    private String deviceTokenBody(String token) {
        return """
                {
                  "deviceToken": "%s",
                  "platform": "IOS"
                }
                """.formatted(token);
    }

    private record RegisteredUser(MockHttpSession session, UUID userId) {
    }
}
