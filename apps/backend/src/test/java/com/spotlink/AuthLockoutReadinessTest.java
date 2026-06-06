package com.spotlink;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spotlink.auth.AuthDtos;
import com.spotlink.auth.AuthLockoutStateRepository;
import com.spotlink.user.RegistrationStatus;
import com.spotlink.user.User;
import com.spotlink.user.UserRepository;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = {
        "spotlink.rate-limit.enabled=false",
        "spotlink.auth-lockout.enabled=true",
        "spotlink.auth-lockout.failed-attempt-threshold=3",
        "spotlink.auth-lockout.rolling-window=2s",
        "spotlink.auth-lockout.lockout-duration=1s"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
class AuthLockoutReadinessTest {

    private static final String PASSWORD = "CorrectHorse123";
    private static final String WRONG_PASSWORD = "WrongHorse123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository users;

    @Autowired
    private AuthLockoutStateRepository lockoutStates;

    @Autowired
    private MeterRegistry meterRegistry;

    @Test
    void repeatedWrongPasswordLocksExistingAccountAcrossWebAndMobileToken() throws Exception {
        String email = registerCustomer();
        User user = users.findByEmailIgnoreCase(email).orElseThrow();

        double failuresBefore = counter("spotlink.auth.lockout.failed_attempt", "operation", "login", "outcome", "recorded");
        double createdBefore = counter("spotlink.auth.lockout.created", "operation", "login");
        double blockedBefore = counter("spotlink.auth.lockout.blocked", "operation", "login");

        failedLogin("/auth/login", email, WRONG_PASSWORD)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        failedLogin("/auth/login", email, WRONG_PASSWORD)
                .andExpect(status().isUnauthorized());
        failedLogin("/auth/login", email, WRONG_PASSWORD)
                .andExpect(status().isUnauthorized());

        assertThat(lockoutStates.findAll().stream()
                .filter(state -> user.getId().equals(state.getUserId()))
                .findFirst()
                .orElseThrow()
                .getLockedUntil()).isNotNull();
        assertThat(counter("spotlink.auth.lockout.failed_attempt", "operation", "login", "outcome", "recorded"))
                .isEqualTo(failuresBefore + 3);
        assertThat(counter("spotlink.auth.lockout.created", "operation", "login"))
                .isEqualTo(createdBefore + 1);

        login("/auth/login", email, PASSWORD)
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.code").value("AUTH_TEMPORARILY_LOCKED"))
                .andExpect(jsonPath("$.message").value("Too many failed sign-in attempts. Try again later."))
                .andExpect(jsonPath("$.details.retryAfterSeconds").isString());
        token("/auth/token", email, PASSWORD)
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.code").value("AUTH_TEMPORARILY_LOCKED"));
        assertThat(counter("spotlink.auth.lockout.blocked", "operation", "login"))
                .isEqualTo(blockedBefore + 1);
    }

    @Test
    void mobileTokenLockoutAlsoAppliesToVersionedAlias() throws Exception {
        String email = registerCustomer();

        token("/auth/token", email, WRONG_PASSWORD).andExpect(status().isUnauthorized());
        token("/auth/token", email, WRONG_PASSWORD).andExpect(status().isUnauthorized());
        token("/auth/token", email, WRONG_PASSWORD).andExpect(status().isUnauthorized());

        token("/v1/auth/token", email, PASSWORD)
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.code").value("AUTH_TEMPORARILY_LOCKED"));
    }

    @Test
    void successfulLoginClearsPreviousFailureState() throws Exception {
        String email = registerCustomer();
        User user = users.findByEmailIgnoreCase(email).orElseThrow();
        double clearedBefore = counter("spotlink.auth.lockout.cleared", "operation", "login");

        failedLogin("/auth/login", email, WRONG_PASSWORD).andExpect(status().isUnauthorized());
        failedLogin("/auth/login", email, WRONG_PASSWORD).andExpect(status().isUnauthorized());
        assertThat(lockoutStates.findAll().stream().anyMatch(state -> user.getId().equals(state.getUserId()))).isTrue();

        login("/auth/login", email, PASSWORD).andExpect(status().isOk());

        assertThat(lockoutStates.findAll().stream().noneMatch(state -> user.getId().equals(state.getUserId()))).isTrue();
        assertThat(counter("spotlink.auth.lockout.cleared", "operation", "login"))
                .isEqualTo(clearedBefore + 1);

        failedLogin("/auth/login", email, WRONG_PASSWORD).andExpect(status().isUnauthorized());
        failedLogin("/auth/login", email, WRONG_PASSWORD).andExpect(status().isUnauthorized());
        login("/auth/login", email, PASSWORD).andExpect(status().isOk());
    }

    @Test
    void lockoutExpiresAfterConfiguredDuration() throws Exception {
        String email = registerCustomer();

        failedLogin("/auth/login", email, WRONG_PASSWORD).andExpect(status().isUnauthorized());
        failedLogin("/auth/login", email, WRONG_PASSWORD).andExpect(status().isUnauthorized());
        failedLogin("/auth/login", email, WRONG_PASSWORD).andExpect(status().isUnauthorized());
        login("/auth/login", email, PASSWORD).andExpect(status().isLocked());

        Thread.sleep(1_300L);

        login("/auth/login", email, PASSWORD).andExpect(status().isOk());
    }

    @Test
    void missingUserAndWrongPasswordShareGenericFailureBehavior(CapturedOutput output) throws Exception {
        String email = registerCustomer();
        String missingEmail = "missing-lockout-%s@spotlink.test".formatted(UUID.randomUUID());

        MvcResult existing = failedLogin("/auth/login", email, WRONG_PASSWORD)
                .andExpect(status().isUnauthorized())
                .andReturn();
        MvcResult missing = failedLogin("/auth/login", missingEmail, WRONG_PASSWORD)
                .andExpect(status().isUnauthorized())
                .andReturn();

        JsonNode existingBody = objectMapper.readTree(existing.getResponse().getContentAsString());
        JsonNode missingBody = objectMapper.readTree(missing.getResponse().getContentAsString());
        assertThat(missingBody.get("code").asText()).isEqualTo(existingBody.get("code").asText());
        assertThat(missingBody.get("message").asText()).isEqualTo(existingBody.get("message").asText());
        assertThat(missing.getResponse().getContentAsString()).doesNotContain(missingEmail);
        assertThat(output).doesNotContain(missingEmail);
    }

    @Test
    void deletedAndSuspendedUsersRemainBlockedWithoutCreatingLockoutState() throws Exception {
        String suspendedEmail = registerCustomer();
        String deletedEmail = registerCustomer();
        User suspended = users.findByEmailIgnoreCase(suspendedEmail).orElseThrow();
        User deleted = users.findByEmailIgnoreCase(deletedEmail).orElseThrow();
        suspended.setRegistrationStatus(RegistrationStatus.SUSPENDED);
        deleted.setRegistrationStatus(RegistrationStatus.DELETED);
        users.saveAll(List.of(suspended, deleted));

        login("/auth/login", suspendedEmail, PASSWORD)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        token("/auth/token", deletedEmail, PASSWORD)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        assertThat(lockoutStates.findAll().stream().noneMatch(state -> suspended.getId().equals(state.getUserId()))).isTrue();
        assertThat(lockoutStates.findAll().stream().noneMatch(state -> deleted.getId().equals(state.getUserId()))).isTrue();
    }

    @Test
    void logsAndMetricsDoNotExposeAuthSecretsOrPersonalIdentifiers(CapturedOutput output) throws Exception {
        String email = registerCustomer();
        String requestBody = """
                {
                  "email": "%s",
                  "password": "%s"
                }
                """.formatted(email, WRONG_PASSWORD);

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isUnauthorized());

        assertThat(output).doesNotContain(email);
        assertThat(output).doesNotContain(WRONG_PASSWORD);
        assertThat(output).doesNotContain(requestBody.strip());
        for (io.micrometer.core.instrument.Meter meter : meterRegistry.getMeters()) {
            meter.getId().getTags().forEach(tag -> {
                assertThat(tag.getValue()).doesNotContain("@");
                assertThat(tag.getValue()).doesNotContain(WRONG_PASSWORD);
                assertThat(tag.getValue()).doesNotContain(email);
            });
        }
    }

    private org.springframework.test.web.servlet.ResultActions login(String path, String email, String password) throws Exception {
        return mockMvc.perform(post(path)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new AuthDtos.LoginRequest(email, password))));
    }

    private org.springframework.test.web.servlet.ResultActions failedLogin(String path, String email, String password) throws Exception {
        return login(path, email, password);
    }

    private org.springframework.test.web.servlet.ResultActions token(String path, String email, String password) throws Exception {
        return mockMvc.perform(post(path)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "email": "%s",
                          "password": "%s",
                          "deviceId": "lockout-test-device"
                        }
                        """.formatted(email, password)));
    }

    private String registerCustomer() throws Exception {
        String email = "lockout-%s@spotlink.test".formatted(UUID.randomUUID());
        mockMvc.perform(post("/auth/register/customer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "firstName": "Lock",
                                  "lastName": "Out",
                                  "email": "%s",
                                  "phone": "+381600000777",
                                  "password": "%s",
                                  "acceptsTerms": true
                                }
                                """.formatted(email, PASSWORD)))
                .andExpect(status().isCreated());
        return email;
    }

    private double counter(String name, String... tags) {
        io.micrometer.core.instrument.Counter counter = meterRegistry.find(name).tags(tags).counter();
        return counter == null ? 0 : counter.count();
    }
}
