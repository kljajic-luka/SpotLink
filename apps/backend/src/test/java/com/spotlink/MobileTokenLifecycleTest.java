package com.spotlink;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MobileTokenLifecycleTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void tokenLoginReturnsAccessAndRefreshTokenAndBearerCanReadCurrentUser() throws Exception {
        String email = registerCustomer();

        JsonNode token = token(email, "/auth/token");

        assertThat(token.get("accessToken").asText()).isNotBlank();
        assertThat(token.get("refreshToken").asText()).startsWith("sl_refresh_");
        assertThat(token.get("tokenType").asText()).isEqualTo("Bearer");
        assertThat(token.get("expiresInSeconds").asLong()).isGreaterThan(0);
        assertThat(token.get("refreshExpiresInSeconds").asLong()).isGreaterThan(token.get("expiresInSeconds").asLong());
        assertThat(token.get("roles").get(0).asText()).isEqualTo("CUSTOMER");

        mockMvc.perform(get("/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token.get("accessToken").asText()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email));
    }

    @Test
    void refreshRotatesRefreshTokenAndRejectsOldRefreshTokenReuse() throws Exception {
        String email = registerCustomer();
        JsonNode token = token(email, "/auth/token");
        String oldRefreshToken = token.get("refreshToken").asText();

        MvcResult refreshed = mockMvc.perform(post("/auth/token/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "refreshToken": "%s",
                                  "deviceId": "ios-test-device"
                                }
                                """.formatted(oldRefreshToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isString())
                .andExpect(jsonPath("$.refreshToken").isString())
                .andReturn();

        JsonNode refreshedBody = objectMapper.readTree(refreshed.getResponse().getContentAsString());
        assertThat(refreshedBody.get("refreshToken").asText()).isNotEqualTo(oldRefreshToken);

        mockMvc.perform(post("/auth/token/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "refreshToken": "%s"
                                }
                                """.formatted(oldRefreshToken)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void revokeInvalidatesRefreshToken() throws Exception {
        String email = registerCustomer();
        JsonNode token = token(email, "/auth/token");
        String refreshToken = token.get("refreshToken").asText();

        mockMvc.perform(post("/auth/token/revoke")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "refreshToken": "%s"
                                }
                                """.formatted(refreshToken)))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/auth/token/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "refreshToken": "%s"
                                }
                                """.formatted(refreshToken)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutWithRefreshTokenRevokesMobileRefreshToken() throws Exception {
        String email = registerCustomer();
        JsonNode token = token(email, "/auth/token");
        String refreshToken = token.get("refreshToken").asText();

        mockMvc.perform(post("/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "refreshToken": "%s"
                                }
                                """.formatted(refreshToken)))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/auth/token/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "refreshToken": "%s"
                                }
                                """.formatted(refreshToken)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void invalidBearerTokenIsRejected() throws Exception {
        mockMvc.perform(get("/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer not-a-valid-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void v1AuthAliasSupportsTokenAndCurrentUser() throws Exception {
        String email = registerCustomer();
        JsonNode token = token(email, "/v1/auth/token");

        mockMvc.perform(get("/v1/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token.get("accessToken").asText()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email));
    }

    @Test
    void v1HealthAliasWorks() throws Exception {
        mockMvc.perform(get("/v1/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    private JsonNode token(String email, String path) throws Exception {
        MvcResult result = mockMvc.perform(post(path)
                        .header("X-Device-Id", "ios-test-device")
                        .header(HttpHeaders.USER_AGENT, "SpotLinkTests/1.0")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "CorrectHorse123",
                                  "deviceId": "ios-test-device"
                                }
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private String registerCustomer() throws Exception {
        String email = "mobile-%s@spotlink.test".formatted(UUID.randomUUID());
        mockMvc.perform(post("/auth/register/customer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "firstName": "Mira",
                                  "lastName": "Mobile",
                                  "email": "%s",
                                  "phone": "+381600000002",
                                  "password": "CorrectHorse123",
                                  "acceptsTerms": true
                                }
                                """.formatted(email)))
                .andExpect(status().isCreated());
        return email;
    }

}
