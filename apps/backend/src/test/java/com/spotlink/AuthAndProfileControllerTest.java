package com.spotlink;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spotlink.support.SupportService;
import com.spotlink.support.SupportTicket;
import com.spotlink.support.SupportTicketCategory;
import com.spotlink.support.SupportTicketRepository;
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

    @Autowired
    private SupportTicketRepository supportTickets;

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

    @Test
    void accountDeletionRequestCreatesSingleUnresolvedSupportTicket() throws Exception {
        MvcResult registration = mockMvc.perform(post("/auth/register/customer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "firstName": "Mila",
                                  "lastName": "Petrovic",
                                  "email": "mila-%s@spotlink.test",
                                  "phone": "+381600000002",
                                  "password": "CorrectHorse123",
                                  "acceptsTerms": true
                                }
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isCreated())
                .andReturn();

        MockHttpSession session = (MockHttpSession) registration.getRequest().getSession(false);

        MvcResult first = mockMvc.perform(post("/users/me/deletion-request")
                        .with(csrf())
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.category").value("ACCOUNT"))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.subject").value(SupportService.ACCOUNT_DELETION_SUBJECT))
                .andReturn();

        String firstTicketId = objectMapper.readTree(first.getResponse().getContentAsString()).get("id").asText();

        MvcResult duplicate = mockMvc.perform(post("/v1/users/me/deletion-request")
                        .with(csrf())
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(firstTicketId))
                .andReturn();

        String duplicateTicketId = objectMapper.readTree(duplicate.getResponse().getContentAsString()).get("id").asText();
        SupportTicket ticket = supportTickets.findById(UUID.fromString(firstTicketId)).orElseThrow();

        assertThat(duplicateTicketId).isEqualTo(firstTicketId);
        assertThat(supportTickets.countByRequesterUserIdAndCategoryAndSubject(
                ticket.getRequesterUserId(),
                SupportTicketCategory.ACCOUNT,
                SupportService.ACCOUNT_DELETION_SUBJECT)).isEqualTo(1);
    }
}
