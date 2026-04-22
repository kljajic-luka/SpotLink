package com.spotlink;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthAndProfileControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void customerRegistrationCreatesSessionAndProfileCanBeUpdated() throws Exception {
        MvcResult registration = mockMvc.perform(post("/auth/register/customer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "firstName": "Ada",
                                  "lastName": "Lovelace",
                                  "email": "ada-%s@spotlink.test",
                                  "phone": "+381600000001",
                                  "password": "CorrectHorse123",
                                  "acceptsTerms": true
                                }
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.user.roles[0]").value("CUSTOMER"))
                .andReturn();

        MockHttpSession session = (MockHttpSession) registration.getRequest().getSession(false);

        mockMvc.perform(get("/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Ada"));

        MvcResult update = mockMvc.perform(patch("/users/me/profile")
                        .with(csrf())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "firstName": "Augusta",
                                  "preferences": {
                                    "marketingOptIn": true,
                                    "reservationAlerts": false
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Augusta"))
                .andExpect(jsonPath("$.preferences.marketingOptIn").value(true))
                .andExpect(jsonPath("$.preferences.reservationAlerts").value(false))
                .andReturn();

        JsonNode body = objectMapper.readTree(update.getResponse().getContentAsString());
        mockMvc.perform(get("/users/%s/profile".formatted(body.get("id").asText())).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stats.activeVehicles").value(0));
    }
}
