package com.spotlink;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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

@SpringBootTest(properties = {
        "spotlink.mock-payment.enabled=false",
        "spotlink.payment.online-enabled=true",
        "spotlink.payment.provider=mock"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PaymentAuthorityDisabledTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void disabledMockProviderDoesNotExposeOnlinePaymentsOrMockMethods() throws Exception {
        MockHttpSession customerSession = registerCustomer();

        mockMvc.perform(get("/payments/capabilities").session(customerSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.onlinePaymentsEnabled").value(false))
                .andExpect(jsonPath("$.activeProvider").value("UNCONFIGURED"))
                .andExpect(jsonPath("$.mockProvider").value(false))
                .andExpect(jsonPath("$.mockPaymentMethodsAllowed").value(false))
                .andExpect(jsonPath("$.operations.authorize").value(false))
                .andExpect(jsonPath("$.operations.cancel").value(false))
                .andExpect(jsonPath("$.operations.refund").value(false));

        mockMvc.perform(get("/payments/methods").session(customerSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void onlineReservationFailsClearlyWhenNoProviderIsAvailable() throws Exception {
        MockHttpSession operatorSession = registerOperator();
        UUID resourceId = createParkingResource(operatorSession);
        MockHttpSession customerSession = registerCustomer();
        Instant startsAt = Instant.now().truncatedTo(ChronoUnit.HOURS).plus(3, ChronoUnit.HOURS);
        Instant endsAt = startsAt.plus(2, ChronoUnit.HOURS);

        mockMvc.perform(post("/reservations")
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
                                """.formatted(resourceId, startsAt, endsAt, "sl_rez_" + UUID.randomUUID())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ONLINE_PAYMENTS_DISABLED"));
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
