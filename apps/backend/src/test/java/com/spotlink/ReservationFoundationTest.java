package com.spotlink;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spotlink.reservation.BookingHoldRepository;
import com.spotlink.reservation.Reservation;
import com.spotlink.reservation.ReservationRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
class ReservationFoundationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ReservationRepository reservationRepository;

        @Autowired
        private BookingHoldRepository bookingHoldRepository;

    @Test
    void reservationCreationIsIdempotentAndBlocksOverlaps() throws Exception {
        MockHttpSession operatorSession = registerOperator();
        UUID resourceId = createParkingResource(operatorSession);
        MockHttpSession customerSession = registerCustomer();

        Instant startsAt = Instant.now().plus(2, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS);
        Instant endsAt = startsAt.plus(2, ChronoUnit.HOURS);

        mockMvc.perform(post("/reservations/quote")
                        .with(csrf())
                        .session(customerSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "resourceId": "%s",
                                  "startsAt": "%s",
                                  "endsAt": "%s"
                                }
                                """.formatted(resourceId, startsAt, endsAt)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalAmountCents").value(450));

        String idempotencyKey = "sl_test_" + UUID.randomUUID();
        MvcResult created = createReservation(customerSession, resourceId, startsAt, endsAt, idempotencyKey)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING_PAYMENT"))
                .andExpect(jsonPath("$.paymentExpiresAt").exists())
                .andReturn();
        JsonNode first = objectMapper.readTree(created.getResponse().getContentAsString());

        MvcResult replayed = createReservation(customerSession, resourceId, startsAt, endsAt, idempotencyKey)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(first.get("id").asText()))
                .andReturn();
        JsonNode second = objectMapper.readTree(replayed.getResponse().getContentAsString());
        org.assertj.core.api.Assertions.assertThat(second.get("id").asText()).isEqualTo(first.get("id").asText());

        createReservation(customerSession, resourceId, startsAt.plus(30, ChronoUnit.MINUTES), endsAt.plus(30, ChronoUnit.MINUTES), "sl_test_" + UUID.randomUUID())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESOURCE_UNAVAILABLE"));

        Reservation staleHold = reservationRepository.findById(UUID.fromString(first.get("id").asText())).orElseThrow();
        staleHold.setPaymentExpiresAt(Instant.now().minus(1, ChronoUnit.MINUTES));
        reservationRepository.saveAndFlush(staleHold);
        bookingHoldRepository.findByReservationId(staleHold.getId()).ifPresent(hold -> {
            hold.setExpiresAt(Instant.now().minus(1, ChronoUnit.MINUTES));
            bookingHoldRepository.saveAndFlush(hold);
        });

        createReservation(customerSession, resourceId, startsAt.plus(30, ChronoUnit.MINUTES), endsAt.plus(30, ChronoUnit.MINUTES), "sl_test_" + UUID.randomUUID())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING_PAYMENT"));
    }

    @Test
    void mobileBearerCanQuoteReservationWithoutCsrfToken() throws Exception {
        MockHttpSession operatorSession = registerOperator();
        UUID resourceId = createParkingResource(operatorSession);
        String customerEmail = "customer-%s@spotlink.test".formatted(UUID.randomUUID());
        registerCustomer(customerEmail);
        JsonNode token = token(customerEmail);

        Instant startsAt = Instant.now().plus(2, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS);
        Instant endsAt = startsAt.plus(2, ChronoUnit.HOURS);

        mockMvc.perform(post("/reservations/quote")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token.get("accessToken").asText())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "resourceId": "%s",
                                  "startsAt": "%s",
                                  "endsAt": "%s"
                                }
                                """.formatted(resourceId, startsAt, endsAt)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resourceId").value(resourceId.toString()))
                .andExpect(jsonPath("$.totalAmountCents").value(450));
    }

    private org.springframework.test.web.servlet.ResultActions createReservation(
            MockHttpSession session,
            UUID resourceId,
            Instant startsAt,
            Instant endsAt,
            String idempotencyKey) throws Exception {
        return mockMvc.perform(post("/reservations")
                .with(csrf())
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "resourceId": "%s",
                          "paymentMode": "ONLINE",
                          "startsAt": "%s",
                          "endsAt": "%s",
                          "idempotencyKey": "%s"
                        }
                        """.formatted(resourceId, startsAt, endsAt, idempotencyKey)));
    }

    private MockHttpSession registerOperator() throws Exception {
        MvcResult result = mockMvc.perform(post("/auth/register/operator")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "firstName": "Olivia",
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

    private MockHttpSession registerCustomer() throws Exception {
        return registerCustomer("customer-%s@spotlink.test".formatted(UUID.randomUUID()));
    }

    private MockHttpSession registerCustomer(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/auth/register/customer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "firstName": "Casey",
                                  "lastName": "Customer",
                                  "email": "%s",
                                  "password": "CorrectHorse123",
                                  "acceptsTerms": true
                                }
                                """.formatted(email)))
                .andExpect(status().isCreated())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private JsonNode token(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/auth/token")
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

    private UUID createParkingResource(MockHttpSession operatorSession) throws Exception {
        MvcResult locationResult = mockMvc.perform(post("/locations")
                        .with(csrf())
                        .session(operatorSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Central Garage",
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
                                  "accessType": "GATE_CODE",
                                  "active": true
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        String locationId = objectMapper.readTree(locationResult.getResponse().getContentAsString()).get("id").asText();

        MvcResult resourceResult = mockMvc.perform(post("/locations/%s/resources".formatted(locationId))
                        .with(csrf())
                        .session(operatorSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "type": "PARKING_SPOT",
                                  "label": "A-01",
                                  "hourlyRateCents": 200,
                                  "currency": "RSD",
                                  "instantReserve": true,
                                  "active": true
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(objectMapper.readTree(resourceResult.getResponse().getContentAsString()).get("id").asText());
    }
}
