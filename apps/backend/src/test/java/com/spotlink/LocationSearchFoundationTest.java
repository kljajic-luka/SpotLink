package com.spotlink;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
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

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LocationSearchFoundationTest {

    // Beograd koordinate za testove radijusa
    private static final double BEOGRAD_LAT = 44.8125;
    private static final double BEOGRAD_LON = 20.4612;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void searchBezKoordinataVracaSveAktivneLokacije() throws Exception {
        MockHttpSession operatorSession = registerOperator();
        createLocationInBeograd(operatorSession, "Centralna Garaza", BEOGRAD_LAT, BEOGRAD_LON);

        mockMvc.perform(get("/locations/search"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    void searchPoRadijusuUkljucujeLokacijeUnutarRadijusa() throws Exception {
        MockHttpSession operatorSession = registerOperator();
        createLocationInBeograd(operatorSession, "Bliska Garaza", BEOGRAD_LAT, BEOGRAD_LON);

        // Pretraga sa centrom u Beogradu, radijus 5km - lokacija je u centru, treba biti nadjena
        mockMvc.perform(get("/locations/search")
                        .param("latitude", String.valueOf(BEOGRAD_LAT))
                        .param("longitude", String.valueOf(BEOGRAD_LON))
                        .param("radiusKm", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isNotEmpty());
    }

    @Test
    void searchPoRadijusuIskljucujeLokacijeVanRadijusa() throws Exception {
        // Lokacija u Beogradu, pretraga iz Pariza (veoma daleko) sa radijusom 5km
        MockHttpSession operatorSession = registerOperator();
        createLocationInBeograd(operatorSession, "Garaza U Beogradu", BEOGRAD_LAT, BEOGRAD_LON);

        mockMvc.perform(get("/locations/search")
                        .param("latitude", "48.8566")    // Pariz
                        .param("longitude", "2.3522")
                        .param("radiusKm", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void searchSamoJednomKoordinatomVracaValidacionuGresku() throws Exception {
        mockMvc.perform(get("/locations/search")
                        .param("latitude", String.valueOf(BEOGRAD_LAT)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void searchSamoJednimVremenomVracaValidacionuGresku() throws Exception {
        Instant startsAt = Instant.now().plus(1, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS);
        mockMvc.perform(get("/locations/search")
                        .param("startsAt", startsAt.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void searchSaEVFilteromVracaSamoEVResurse() throws Exception {
        MockHttpSession operatorSession = registerOperator();
        String locationId = createLocationInBeograd(operatorSession, "EV Parking", BEOGRAD_LAT, BEOGRAD_LON);
        createResource(operatorSession, locationId, "EV_CHARGER", 500);
        createResource(operatorSession, locationId, "PARKING_SPOT", 200);

        MvcResult result = mockMvc.perform(get("/locations/search")
                        .param("evChargingRequired", "true"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode content = objectMapper.readTree(result.getResponse().getContentAsString());
        // Svaka vracena lokacija treba da ima samo EV resurse
        content.get("content").forEach(loc -> {
            loc.get("resources").forEach(res ->
                org.assertj.core.api.Assertions.assertThat(res.get("type").asText()).isEqualTo("EV_CHARGER")
            );
        });
    }

    @Test
    void searchVracaTacnuStartingPriceCents() throws Exception {
        MockHttpSession operatorSession = registerOperator();
        String locationId = createLocationInBeograd(operatorSession, "Cena Test Parking", BEOGRAD_LAT + 0.001, BEOGRAD_LON + 0.001);
        createResource(operatorSession, locationId, "PARKING_SPOT", 300);
        createResource(operatorSession, locationId, "PARKING_SPOT", 500);

        MvcResult result = mockMvc.perform(get("/locations/search")
                        .param("query", "Cena Test Parking"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].startingPriceCents").value(300))
                .andReturn();
    }

    @Test
    void searchSaVremenomIspisujeBlokiranesLokacije() throws Exception {
        MockHttpSession operatorSession = registerOperator();
        String locationId = createLocationInBeograd(operatorSession, "Blokirana Lokacija", BEOGRAD_LAT + 0.002, BEOGRAD_LON + 0.002);
        createResource(operatorSession, locationId, "PARKING_SPOT", 200);

        Instant startsAt = Instant.now().plus(10, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS);
        Instant endsAt = startsAt.plus(2, ChronoUnit.HOURS);

        // Kreiranje blokade koja pokriva trazeni period
        mockMvc.perform(post("/locations/%s/availability/exceptions".formatted(locationId))
                        .with(csrf())
                        .session(operatorSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "label": "Zatvoreno za dogadjaj",
                                  "startsAt": "%s",
                                  "endsAt": "%s"
                                }
                                """.formatted(startsAt.minus(1, ChronoUnit.HOURS), endsAt.plus(1, ChronoUnit.HOURS))))
                .andExpect(status().isCreated());

        // Pretraga za period koji je blokiran treba da ne vrati ovu lokaciju
        MvcResult result = mockMvc.perform(get("/locations/search")
                        .param("query", "Blokirana Lokacija")
                        .param("startsAt", startsAt.toString())
                        .param("endsAt", endsAt.toString()))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode content = objectMapper.readTree(result.getResponse().getContentAsString());
        org.assertj.core.api.Assertions.assertThat(content.get("totalElements").asLong()).isEqualTo(0);
    }

    @Test
    void searchRezervacijePodKapacitetomOstavljajuResursDostupnim() throws Exception {
        MockHttpSession operatorSession = registerOperator();
        String locationId = createLocationInBeograd(operatorSession, "Visoki Kapacitet Parking", BEOGRAD_LAT + 0.003, BEOGRAD_LON + 0.003);
        // Resurs sa kapacitetom 5
        String resourceId = createResourceWithCapacity(operatorSession, locationId, "PARKING_SPOT", 200, 5);

        MockHttpSession customerSession = registerCustomer();
        Instant startsAt = Instant.now().plus(20, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS);
        Instant endsAt = startsAt.plus(2, ChronoUnit.HOURS);

        // Kreiramo 4 rezervacije (kapacitet je 5, treba ostati 1 dostupno)
        for (int i = 0; i < 4; i++) {
            mockMvc.perform(post("/reservations")
                            .with(csrf())
                            .session(customerSession)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "resourceId": "%s",
                                      "startsAt": "%s",
                                      "endsAt": "%s",
                                      "idempotencyKey": "sl_test_%s"
                                    }
                                    """.formatted(resourceId, startsAt, endsAt, UUID.randomUUID())))
                    .andExpect(status().isCreated());
        }

        MvcResult result = mockMvc.perform(get("/locations/search")
                        .param("query", "Visoki Kapacitet Parking")
                        .param("startsAt", startsAt.toString())
                        .param("endsAt", endsAt.toString()))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode content = objectMapper.readTree(result.getResponse().getContentAsString());
        org.assertj.core.api.Assertions.assertThat(content.get("totalElements").asLong()).isEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(content.get("content").get(0).get("availableResourceCount").asLong()).isEqualTo(1);
    }

    @Test
    void operatorDobavijaPartnerProfil() throws Exception {
        MockHttpSession operatorSession = registerOperator();

        mockMvc.perform(get("/operator/partner-profile")
                        .session(operatorSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.partnerType").value("PILOT"))
                .andExpect(jsonPath("$.onboardingStatus").value("PENDING"));
    }

    @Test
    void radnoVremeISkemuciDostupnostiRadiIspravno() throws Exception {
        MockHttpSession operatorSession = registerOperator();
        String locationId = createLocationInBeograd(operatorSession, "Radno Vreme Test", BEOGRAD_LAT + 0.004, BEOGRAD_LON + 0.004);
        createResource(operatorSession, locationId, "PARKING_SPOT", 200);

        // Postavljamo radno vreme samo za ponedeljak 08:00-18:00
        mockMvc.perform(put("/locations/%s/availability/hours".formatted(locationId))
                        .with(csrf())
                        .session(operatorSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "entries": [
                                    { "dayOfWeek": "MONDAY", "openTime": "08:00", "closeTime": "18:00" }
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        // GET radno vreme
        mockMvc.perform(get("/locations/%s/availability/hours".formatted(locationId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].dayOfWeek").value("MONDAY"));

        // Kreiranje i brisanje izuzetka
        MvcResult exResult = mockMvc.perform(post("/locations/%s/availability/exceptions".formatted(locationId))
                        .with(csrf())
                        .session(operatorSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "label": "Drzavni praznik",
                                  "startsAt": "2025-01-01T00:00:00Z",
                                  "endsAt": "2025-01-02T00:00:00Z"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.label").value("Drzavni praznik"))
                .andReturn();

        String exceptionId = objectMapper.readTree(exResult.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(delete("/locations/%s/availability/exceptions/%s".formatted(locationId, exceptionId))
                        .with(csrf())
                        .session(operatorSession))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/locations/%s/availability/exceptions".formatted(locationId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    // Pomocne metode

    private MockHttpSession registerOperator() throws Exception {
        MvcResult result = mockMvc.perform(post("/auth/register/operator")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "firstName": "Ops",
                                  "lastName": "Operator",
                                  "email": "ops-%s@spotlink.test",
                                  "password": "CorrectHorse123",
                                  "acceptsTerms": true,
                                  "companyName": "Test Parking Operator",
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
                                  "firstName": "Cust",
                                  "lastName": "Customer",
                                  "email": "cust-%s@spotlink.test",
                                  "password": "CorrectHorse123",
                                  "acceptsTerms": true
                                }
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isCreated())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private String createLocationInBeograd(MockHttpSession session, String name, double lat, double lon) throws Exception {
        MvcResult result = mockMvc.perform(post("/locations")
                        .with(csrf())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "%s",
                                  "address": {
                                    "line1": "Knez Mihailova 1",
                                    "city": "Beograd",
                                    "country": "RS",
                                    "formattedAddress": "Knez Mihailova 1, Beograd"
                                  },
                                  "coordinates": {
                                    "latitude": %s,
                                    "longitude": %s
                                  },
                                  "timezone": "Europe/Belgrade",
                                  "accessType": "SELF_PARK",
                                  "active": true
                                }
                                """.formatted(name, lat, lon)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    private String createResource(MockHttpSession session, String locationId, String type, int hourlyRateCents) throws Exception {
        MvcResult result = mockMvc.perform(post("/locations/%s/resources".formatted(locationId))
                        .with(csrf())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "type": "%s",
                                  "label": "Mesto-%s",
                                  "hourlyRateCents": %d,
                                  "currency": "USD",
                                  "instantReserve": true,
                                  "active": true
                                }
                                """.formatted(type, UUID.randomUUID().toString().substring(0, 8), hourlyRateCents)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    private String createResourceWithCapacity(MockHttpSession session, String locationId, String type, int hourlyRateCents, int capacity) throws Exception {
        MvcResult result = mockMvc.perform(post("/locations/%s/resources".formatted(locationId))
                        .with(csrf())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "type": "%s",
                                  "label": "Kapacitet-%s",
                                  "hourlyRateCents": %d,
                                  "currency": "USD",
                                  "instantReserve": true,
                                  "active": true,
                                  "capacity": %d
                                }
                                """.formatted(type, UUID.randomUUID().toString().substring(0, 8), hourlyRateCents, capacity)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }
}
