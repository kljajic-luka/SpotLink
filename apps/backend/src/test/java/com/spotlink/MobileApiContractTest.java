package com.spotlink;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MobileApiContractTest {

    private static final List<ExpectedOperation> MOBILE_CRITICAL_OPERATIONS = List.of(
            operation("/health", "get"),
            operation("/v1/health", "get"),
            operation("/auth/login", "post"),
            operation("/v1/auth/login", "post"),
            operation("/auth/register/customer", "post"),
            operation("/v1/auth/register/customer", "post"),
            operation("/auth/token", "post"),
            operation("/v1/auth/token", "post"),
            operation("/auth/token/refresh", "post"),
            operation("/v1/auth/token/refresh", "post"),
            operation("/auth/token/revoke", "post"),
            operation("/v1/auth/token/revoke", "post"),
            operation("/auth/logout", "post"),
            operation("/v1/auth/logout", "post"),
            operation("/auth/me", "get"),
            operation("/v1/auth/me", "get"),
            operation("/users/me/profile", "get"),
            operation("/v1/users/me/profile", "get"),
            operation("/users/me/profile", "patch"),
            operation("/v1/users/me/profile", "patch"),
            operation("/users/me/deletion-request", "post"),
            operation("/v1/users/me/deletion-request", "post"),
            operation("/vehicles/me", "get"),
            operation("/v1/vehicles/me", "get"),
            operation("/vehicles", "post"),
            operation("/v1/vehicles", "post"),
            operation("/vehicles/{vehicleId}", "delete"),
            operation("/v1/vehicles/{vehicleId}", "delete"),
            operation("/locations/search", "get"),
            operation("/v1/locations/search", "get"),
            operation("/locations/geocode", "get"),
            operation("/v1/locations/geocode", "get"),
            operation("/locations/{locationId}", "get"),
            operation("/v1/locations/{locationId}", "get"),
            operation("/locations/{locationId}/resources", "get"),
            operation("/v1/locations/{locationId}/resources", "get"),
            operation("/reservations/me", "get"),
            operation("/v1/reservations/me", "get"),
            operation("/reservations/quote", "post"),
            operation("/v1/reservations/quote", "post"),
            operation("/reservations", "post"),
            operation("/v1/reservations", "post"),
            operation("/reservations/{reservationId}/cancel", "post"),
            operation("/v1/reservations/{reservationId}/cancel", "post"),
            operation("/payments/capabilities", "get"),
            operation("/v1/payments/capabilities", "get"),
            operation("/payments/methods", "get"),
            operation("/v1/payments/methods", "get"),
            operation("/payments/intents", "post"),
            operation("/v1/payments/intents", "post"),
            operation("/payments/intents/{paymentIntentId}/confirm", "post"),
            operation("/v1/payments/intents/{paymentIntentId}/confirm", "post"),
            operation("/payments/intents/{paymentIntentId}/cancel", "post"),
            operation("/v1/payments/intents/{paymentIntentId}/cancel", "post"),
            operation("/support/tickets", "get"),
            operation("/v1/support/tickets", "get"),
            operation("/support/tickets", "post"),
            operation("/v1/support/tickets", "post"),
            operation("/support/tickets/{ticketId}/messages", "get"),
            operation("/v1/support/tickets/{ticketId}/messages", "get"),
            operation("/support/tickets/{ticketId}/messages", "post"),
            operation("/v1/support/tickets/{ticketId}/messages", "post"),
            operation("/notifications", "get"),
            operation("/v1/notifications", "get"),
            operation("/notifications/unread-count", "get"),
            operation("/v1/notifications/unread-count", "get"),
            operation("/notifications/{notificationId}/read", "post"),
            operation("/v1/notifications/{notificationId}/read", "post"),
            operation("/notifications/device-tokens", "post"),
            operation("/v1/notifications/device-tokens", "post"),
            operation("/notifications/device-tokens/unregister", "post"),
            operation("/v1/notifications/device-tokens/unregister", "post"),
            operation("/operator/dashboard/summary", "get"),
            operation("/v1/operator/dashboard/summary", "get"),
            operation("/admin/dashboard/summary", "get"),
            operation("/v1/admin/dashboard/summary", "get"),
            operation("/admin/support-cases", "get"),
            operation("/v1/admin/support-cases", "get"),
            operation("/admin/support-cases/{ticketId}/process-account-deletion", "post"),
            operation("/v1/admin/support-cases/{ticketId}/process-account-deletion", "post"));

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void generatedOpenApiContainsMobileCriticalEndpointsAndV1Aliases() throws Exception {
        MvcResult result = mockMvc.perform(get("/openapi")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode paths = objectMapper.readTree(result.getResponse().getContentAsString()).path("paths");
        assertThat(paths.isObject()).isTrue();

        for (ExpectedOperation expected : MOBILE_CRITICAL_OPERATIONS) {
            JsonNode path = openApiPath(paths, expected.path());
            assertThat(path.has(expected.method()))
                    .as("Expected generated OpenAPI to expose %s %s", expected.method().toUpperCase(), externalPath(expected.path()))
                    .isTrue();
        }
    }

    @Test
    void standardErrorEnvelopeIncludesRequestIdAndSupportFields() throws Exception {
        String requestId = "mobile-contract-request-001";

        MvcResult result = mockMvc.perform(post("/auth/token")
                        .header("X-Request-Id", requestId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("X-Request-Id", requestId))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("Input validation failed"))
                .andExpect(jsonPath("$.requestId").value(requestId))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.path").exists())
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.path("path").asText()).endsWith("/auth/token");
    }

    @Test
    void noContentEndpointsReturnEmptyBodiesAndRequestIdHeaders() throws Exception {
        String requestId = "mobile-contract-reset-001";

        MvcResult result = mockMvc.perform(post("/v1/auth/password/reset-request")
                        .header("X-Request-Id", requestId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "missing-customer@spotlink.test"
                                }
                                """))
                .andExpect(status().isNoContent())
                .andExpect(header().string("X-Request-Id", requestId))
                .andReturn();

        assertThat(result.getResponse().getContentAsByteArray()).isEmpty();
        assertThat(result.getResponse().getContentAsString()).isBlank();
    }

    @Test
    void versionedAliasesMatchRepresentativeUnversionedBehavior() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));

        mockMvc.perform(get("/v1/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));

        RegisteredCustomer customer = registerCustomer();

        JsonNode unversionedToken = token(customer.email(), "/auth/token");
        JsonNode versionedToken = token(customer.email(), "/v1/auth/token");
        assertThat(unversionedToken.path("tokenType").asText()).isEqualTo("Bearer");
        assertThat(versionedToken.path("tokenType").asText()).isEqualTo("Bearer");
        assertThat(versionedToken.at("/user/email").asText()).isEqualTo(customer.email());

        JsonNode unversionedCapabilities = readJson(mockMvc.perform(get("/payments/capabilities")
                        .session(customer.session()))
                .andExpect(status().isOk())
                .andReturn());
        JsonNode versionedCapabilities = readJson(mockMvc.perform(get("/v1/payments/capabilities")
                        .session(customer.session()))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(versionedCapabilities).isEqualTo(unversionedCapabilities);

        String token = "apns-contract-" + UUID.randomUUID();
        mockMvc.perform(post("/v1/notifications/device-tokens")
                        .with(csrf())
                        .session(customer.session())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(deviceTokenBody(token)))
                .andExpect(status().isNoContent())
                .andExpect(result -> assertThat(result.getResponse().getContentAsByteArray()).isEmpty());

        mockMvc.perform(post("/v1/notifications/device-tokens/unregister")
                        .with(csrf())
                        .session(customer.session())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(deviceTokenBody(token)))
                .andExpect(status().isNoContent())
                .andExpect(result -> assertThat(result.getResponse().getContentAsByteArray()).isEmpty());
    }

    private JsonNode readJson(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private JsonNode openApiPath(JsonNode paths, String rawPath) {
        if (paths.has(rawPath)) {
            return paths.get(rawPath);
        }
        String externalPath = externalPath(rawPath);
        if (paths.has(externalPath)) {
            return paths.get(externalPath);
        }
        throw new AssertionError("Generated OpenAPI is missing mobile path " + externalPath);
    }

    private static String externalPath(String rawPath) {
        return "/api" + rawPath;
    }

    private JsonNode token(String email, String path) throws Exception {
        MvcResult result = mockMvc.perform(post(path)
                        .header("X-Device-Id", "ios-contract-device")
                        .header(HttpHeaders.USER_AGENT, "SpotLinkContractTests/1.0")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "CorrectHorse123",
                                  "deviceId": "ios-contract-device"
                                }
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andReturn();
        return readJson(result);
    }

    private RegisteredCustomer registerCustomer() throws Exception {
        String email = "contract-%s@spotlink.test".formatted(UUID.randomUUID());
        MvcResult result = mockMvc.perform(post("/auth/register/customer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "firstName": "Contract",
                                  "lastName": "Customer",
                                  "email": "%s",
                                  "phone": "+381600000004",
                                  "password": "CorrectHorse123",
                                  "acceptsTerms": true
                                }
                                """.formatted(email)))
                .andExpect(status().isCreated())
                .andReturn();
        return new RegisteredCustomer(email, (MockHttpSession) result.getRequest().getSession(false));
    }

    private String deviceTokenBody(String token) {
        return """
                {
                  "deviceToken": "%s",
                  "platform": "IOS"
                }
                """.formatted(token);
    }

    private static ExpectedOperation operation(String path, String method) {
        return new ExpectedOperation(path, method);
    }

    private record ExpectedOperation(String path, String method) {
    }

    private record RegisteredCustomer(String email, MockHttpSession session) {
    }
}
