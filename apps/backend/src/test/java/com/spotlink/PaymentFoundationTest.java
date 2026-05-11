package com.spotlink;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.assertj.core.api.Assertions;
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
class PaymentFoundationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void paymentIntentFlowMatchesFrontendContract() throws Exception {
        MockHttpSession operatorSession = registerOperator();
        UUID resourceId = createParkingResource(operatorSession);
        MockHttpSession customerSession = registerCustomer();

        Instant startsAt = alignedFutureStart(3);
        Instant endsAt = startsAt.plus(2, ChronoUnit.HOURS);
        String reservationIdempotencyKey = "sl_rez_" + UUID.randomUUID();

        MvcResult reservationResult = mockMvc.perform(post("/reservations")
                        .with(csrf())
                        .session(customerSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "resourceId": "%s",
                                  "paymentMode": "ONLINE",
                                  "startsAt": "%s",
                                  "endsAt": "%s",
                                  "idempotencyKey": "%s"
                                }
                                """.formatted(resourceId, startsAt, endsAt, reservationIdempotencyKey)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING_PAYMENT"))
                .andExpect(jsonPath("$.bookingCode").exists())
                .andReturn();

        String reservationId = objectMapper.readTree(reservationResult.getResponse().getContentAsString())
                .get("id")
                .asText();

        mockMvc.perform(get("/payments/methods").session(customerSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("pm_card_visa"))
                .andExpect(jsonPath("$[0].default").value(true));

        String paymentIdempotencyKey = "sl_pay_" + UUID.randomUUID();
        MvcResult createdIntent = mockMvc.perform(post("/payments/intents")
                        .with(csrf())
                        .session(customerSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reservationId": "%s",
                                  "paymentMethodId": "pm_card_visa",
                                  "idempotencyKey": "%s"
                                }
                                """.formatted(reservationId, paymentIdempotencyKey)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reservationId").value(reservationId))
                .andExpect(jsonPath("$.status").value("AUTHORIZED"))
                .andReturn();

        JsonNode firstIntent = objectMapper.readTree(createdIntent.getResponse().getContentAsString());

        MvcResult replayedIntent = mockMvc.perform(post("/payments/intents")
                        .with(csrf())
                        .session(customerSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reservationId": "%s",
                                  "paymentMethodId": "pm_card_visa",
                                  "idempotencyKey": "%s"
                                }
                                """.formatted(reservationId, paymentIdempotencyKey)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(firstIntent.get("id").asText()))
                .andReturn();

        JsonNode secondIntent = objectMapper.readTree(replayedIntent.getResponse().getContentAsString());
        Assertions.assertThat(secondIntent.get("id").asText()).isEqualTo(firstIntent.get("id").asText());

        mockMvc.perform(get("/reservations/%s".formatted(reservationId)).session(customerSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.accessInstructionsVisible").value(true));

        mockMvc.perform(post("/payments/intents/%s/confirm".formatted(firstIntent.get("id").asText()))
                        .with(csrf())
                        .session(customerSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AUTHORIZED"))
                .andExpect(jsonPath("$.paymentIntentId").value(firstIntent.get("id").asText()));
    }

    private MockHttpSession registerOperator() throws Exception {
        MvcResult result = mockMvc.perform(post("/auth/register/operator")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "firstName": "Pat",
                                  "lastName": "Operator",
                                  "email": "operator-%s@spotlink.test",
                                  "password": "CorrectHorse123",
                                  "acceptsTerms": true,
                                  "companyName": "SpotLink Test Parking",
                                  "operatorType": "BUSINESS",
                                  "acceptsOperatorAgreement": true
                                }
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isCreated())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private Instant alignedFutureStart(long hoursAhead) {
        return Instant.now().truncatedTo(ChronoUnit.HOURS).plus(hoursAhead, ChronoUnit.HOURS);
    }

    private MockHttpSession registerCustomer() throws Exception {
        MvcResult result = mockMvc.perform(post("/auth/register/customer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "firstName": "Payton",
                                  "lastName": "Customer",
                                  "email": "customer-%s@spotlink.test",
                                  "password": "CorrectHorse123",
                                  "acceptsTerms": true
                                }
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isCreated())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private UUID createParkingResource(MockHttpSession operatorSession) throws Exception {
        MvcResult locationResult = mockMvc.perform(post("/locations")
                        .with(csrf())
                        .session(operatorSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Riverfront Garage",
                                  "address": {
                                    "line1": "Main Street 1",
                                    "city": "Belgrade",
                                    "country": "RS",
                                    "formattedAddress": "Main Street 1, Belgrade"
                                  },
                                  "coordinates": {
                                    "latitude": 44.812500,
                                    "longitude": 20.461200
                                  },
                                  "timezone": "Europe/Belgrade",
                                  "accessType": "SELF_PARK",
                                  "active": true
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        String locationId = objectMapper.readTree(locationResult.getResponse().getContentAsString())
                .get("id")
                .asText();

        MvcResult resourceResult = mockMvc.perform(post("/locations/%s/resources".formatted(locationId))
                        .with(csrf())
                        .session(operatorSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "type": "PARKING_SPOT",
                                  "label": "B-02",
                                  "hourlyRateCents": 200,
                                  "currency": "RSD",
                                  "instantReserve": true,
                                  "active": true
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(objectMapper.readTree(resourceResult.getResponse().getContentAsString())
                .get("id")
                .asText());
    }
}
