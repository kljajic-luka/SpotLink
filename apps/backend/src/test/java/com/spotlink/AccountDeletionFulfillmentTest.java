package com.spotlink;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spotlink.admin.AuditLogRepository;
import com.spotlink.auth.AuthDtos;
import com.spotlink.auth.PasswordResetToken;
import com.spotlink.auth.PasswordResetTokenRepository;
import com.spotlink.auth.RefreshTokenRepository;
import com.spotlink.auth.RefreshTokenService;
import com.spotlink.core.IdempotencyRecord;
import com.spotlink.core.IdempotencyRecordRepository;
import com.spotlink.core.IdempotencyStatus;
import com.spotlink.notification.DevicePlatform;
import com.spotlink.notification.DeviceTokenRepository;
import com.spotlink.payment.PaymentAttemptRepository;
import com.spotlink.payment.PaymentAttemptStatus;
import com.spotlink.payment.PaymentIntent;
import com.spotlink.payment.PaymentIntentRepository;
import com.spotlink.payment.PaymentStatus;
import com.spotlink.reservation.Reservation;
import com.spotlink.reservation.ReservationRepository;
import com.spotlink.reservation.ReservationStatus;
import com.spotlink.support.SupportMessageRepository;
import com.spotlink.support.SupportService;
import com.spotlink.support.SupportTicketRepository;
import com.spotlink.support.SupportTicketStatus;
import com.spotlink.user.RegistrationStatus;
import com.spotlink.user.User;
import com.spotlink.user.UserPreferencesRepository;
import com.spotlink.user.UserRepository;
import com.spotlink.user.UserRole;
import com.spotlink.vehicle.VehicleProfile;
import com.spotlink.vehicle.VehicleRepository;
import com.spotlink.vehicle.VehicleType;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AccountDeletionFulfillmentTest {

    private static final String PASSWORD = "CorrectHorse123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository users;

    @Autowired
    private UserPreferencesRepository preferences;

    @Autowired
    private VehicleRepository vehicles;

    @Autowired
    private ReservationRepository reservations;

    @Autowired
    private PaymentIntentRepository paymentIntents;

    @Autowired
    private PaymentAttemptRepository paymentAttempts;

    @Autowired
    private PasswordResetTokenRepository passwordResetTokens;

    @Autowired
    private RefreshTokenRepository refreshTokens;

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Autowired
    private DeviceTokenRepository deviceTokens;

    @Autowired
    private IdempotencyRecordRepository idempotencyRecords;

    @Autowired
    private SupportTicketRepository supportTickets;

    @Autowired
    private SupportMessageRepository supportMessages;

    @Autowired
    private AuditLogRepository auditLogs;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void processAccountDeletionAnonymizesPiiRevokesArtifactsAndPreservesHistory() throws Exception {
        MockHttpSession operatorSession = registerOperator();
        UUID resourceId = createParkingResource(operatorSession);
        RegisteredUser customer = registerCustomer();
        UUID reservationId = createCompletedReservation(customer.session(), resourceId);
        UUID paymentIntentId = createTerminalPaymentIntent(customer.userId(), reservationId);
        UUID vehicleId = createVehicle(customer.userId());
        UUID resetTokenId = createPasswordResetToken(customer.userId());
        createIdempotencyRecord(customer.userId());
        String deviceToken = "apns-" + UUID.randomUUID();
        registerDeviceToken(customer.session(), deviceToken);
        String rawRefreshToken = mobileRefreshToken(customer.email());
        UUID ticketId = requestAccountDeletion(customer.session());
        MockHttpSession adminSession = createAdminSession();

        MvcResult processed = mockMvc.perform(post("/admin/support-cases/%s/process-account-deletion".formatted(ticketId))
                        .with(csrf())
                        .session(adminSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PROCESSED"))
                .andExpect(jsonPath("$.blockers").isEmpty())
                .andReturn();

        JsonNode body = objectMapper.readTree(processed.getResponse().getContentAsString());
        assertThat(body.get("userId").asText()).isEqualTo(customer.userId().toString());

        User deleted = users.findById(customer.userId()).orElseThrow();
        assertThat(deleted.getRegistrationStatus()).isEqualTo(RegistrationStatus.DELETED);
        assertThat(deleted.getEmail()).isEqualTo("deleted-" + customer.userId() + "@spotlink.invalid");
        assertThat(deleted.getFirstName()).isEqualTo("Deleted");
        assertThat(deleted.getLastName()).isEqualTo("User");
        assertThat(deleted.getPhone()).isNull();
        assertThat(deleted.getAvatarUrl()).isNull();
        assertThat(deleted.getBio()).isNull();
        assertThat(deleted.getRoles()).isEmpty();
        assertThat(preferences.findByUserId(customer.userId())).isEmpty();

        VehicleProfile anonymizedVehicle = vehicles.findById(vehicleId).orElseThrow();
        assertThat(anonymizedVehicle.getLicensePlate()).isNull();
        assertThat(anonymizedVehicle.getMake()).isNull();
        assertThat(anonymizedVehicle.getModel()).isNull();

        assertThat(passwordResetTokens.findById(resetTokenId).orElseThrow().getConsumedAt()).isNotNull();
        assertThat(refreshTokens.findByTokenHash(refreshTokenService.hash(rawRefreshToken)).orElseThrow().getRevokedAt()).isNotNull();
        assertThat(deviceTokens.findByDeviceToken(deviceToken).orElseThrow().isActive()).isFalse();
        assertThat(idempotencyRecords.countByUserId(customer.userId())).isZero();
        assertThat(supportTickets.findById(ticketId).orElseThrow().getStatus()).isEqualTo(SupportTicketStatus.RESOLVED);
        assertThat(supportMessages.findBySenderUserId(customer.userId()))
                .extracting("senderName")
                .containsOnly("Deleted user");

        assertThat(reservations.findById(reservationId)).isPresent();
        assertThat(paymentIntents.findById(paymentIntentId)).isPresent();

        mockMvc.perform(get("/auth/me").session(customer.session()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AuthDtos.LoginRequest(customer.email(), PASSWORD))))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/auth/token/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "refreshToken": "%s"
                                }
                                """.formatted(rawRefreshToken)))
                .andExpect(status().isUnauthorized());

        long processedAuditCount = auditLogs.findAll().stream()
                .filter(log -> "ACCOUNT_DELETION_PROCESSED".equals(log.getAction()))
                .filter(log -> ticketId.toString().equals(log.getResourceId()))
                .count();
        assertThat(processedAuditCount).isEqualTo(1);

        mockMvc.perform(post("/v1/admin/support-cases/%s/process-account-deletion".formatted(ticketId))
                        .with(csrf())
                        .session(adminSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ALREADY_PROCESSED"));

        long processedAuditCountAfterRetry = auditLogs.findAll().stream()
                .filter(log -> "ACCOUNT_DELETION_PROCESSED".equals(log.getAction()))
                .filter(log -> ticketId.toString().equals(log.getResourceId()))
                .count();
        assertThat(processedAuditCountAfterRetry).isEqualTo(1);
    }

    @Test
    void processAccountDeletionReturnsBlockersAndKeepsTicketOpen() throws Exception {
        MockHttpSession operatorSession = registerOperator();
        UUID resourceId = createParkingResource(operatorSession);
        RegisteredUser customer = registerCustomer();
        createFutureReservation(customer.session(), resourceId);
        UUID ticketId = requestAccountDeletion(customer.session());
        MockHttpSession adminSession = createAdminSession();

        mockMvc.perform(post("/admin/support-cases/%s/process-account-deletion".formatted(ticketId))
                        .with(csrf())
                        .session(adminSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("BLOCKED"))
                .andExpect(jsonPath("$.blockers[0].code").value("ACTIVE_OR_FUTURE_RESERVATIONS"));

        assertThat(users.findById(customer.userId()).orElseThrow().getRegistrationStatus()).isEqualTo(RegistrationStatus.ACTIVE);
        assertThat(supportTickets.findById(ticketId).orElseThrow().getStatus()).isEqualTo(SupportTicketStatus.OPEN);
        assertThat(auditLogs.findAll().stream()
                .filter(log -> "ACCOUNT_DELETION_BLOCKED".equals(log.getAction()))
                .filter(log -> ticketId.toString().equals(log.getResourceId()))
                .count()).isEqualTo(1);
    }

    @Test
    void processAccountDeletionBlocksUnresolvedPaymentsEvenWithoutFutureReservations() throws Exception {
        MockHttpSession operatorSession = registerOperator();
        UUID resourceId = createParkingResource(operatorSession);
        RegisteredUser customer = registerCustomer();
        UUID reservationId = createCompletedReservation(customer.session(), resourceId);
        PaymentIntent intent = new PaymentIntent();
        intent.setReservationId(reservationId);
        intent.setCustomerId(customer.userId());
        intent.setAmountCents(1_200);
        intent.setCurrency("RSD");
        intent.setStatus(PaymentStatus.AUTHORIZED);
        paymentIntents.saveAndFlush(intent);
        UUID ticketId = requestAccountDeletion(customer.session());
        MockHttpSession adminSession = createAdminSession();

        mockMvc.perform(post("/admin/support-cases/%s/process-account-deletion".formatted(ticketId))
                        .with(csrf())
                        .session(adminSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("BLOCKED"))
                .andExpect(jsonPath("$.blockers[0].code").value("UNRESOLVED_PAYMENT_INTENTS"));

        assertThat(users.findById(customer.userId()).orElseThrow().getRegistrationStatus()).isEqualTo(RegistrationStatus.ACTIVE);
    }

    private RegisteredUser registerCustomer() throws Exception {
        String email = "delete-%s@spotlink.test".formatted(UUID.randomUUID());
        MvcResult result = mockMvc.perform(post("/auth/register/customer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "firstName": "Mila",
                                  "lastName": "Petrovic",
                                  "email": "%s",
                                  "phone": "+381600000002",
                                  "password": "%s",
                                  "acceptsTerms": true
                                }
                                """.formatted(email, PASSWORD)))
                .andExpect(status().isCreated())
                .andReturn();
        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        UUID userId = UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString())
                .get("user")
                .get("id")
                .asText());
        User user = users.findById(userId).orElseThrow();
        user.setAvatarUrl("https://cdn.spotlink.test/avatar.jpg");
        user.setBio("Parking preference profile");
        users.saveAndFlush(user);
        return new RegisteredUser(session, userId, email);
    }

    private MockHttpSession registerOperator() throws Exception {
        MvcResult result = mockMvc.perform(post("/auth/register/operator")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "firstName": "Pat",
                                  "lastName": "Operator",
                                  "email": "operator-%s@spotlink.test",
                                  "password": "%s",
                                  "acceptsTerms": true,
                                  "companyName": "SpotLink Test Parking",
                                  "operatorType": "BUSINESS",
                                  "acceptsOperatorAgreement": true
                                }
                                """.formatted(UUID.randomUUID(), PASSWORD)))
                .andExpect(status().isCreated())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private MockHttpSession createAdminSession() throws Exception {
        String email = "admin-deletion-%s@spotlink.test".formatted(UUID.randomUUID());
        User admin = new User();
        admin.setEmail(email);
        admin.setPasswordHash(passwordEncoder.encode(PASSWORD));
        admin.setFirstName("Admin");
        admin.setLastName("User");
        admin.setRegistrationStatus(RegistrationStatus.ACTIVE);
        admin.setRoles(Set.of(UserRole.ADMIN));
        users.saveAndFlush(admin);

        MvcResult result = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AuthDtos.LoginRequest(email, PASSWORD))))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private UUID requestAccountDeletion(MockHttpSession session) throws Exception {
        MvcResult result = mockMvc.perform(post("/users/me/deletion-request")
                        .with(csrf())
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subject").value(SupportService.ACCOUNT_DELETION_SUBJECT))
                .andReturn();
        return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText());
    }

    private void registerDeviceToken(MockHttpSession session, String token) throws Exception {
        mockMvc.perform(post("/notifications/device-tokens")
                        .with(csrf())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "deviceToken": "%s",
                                  "platform": "IOS"
                                }
                                """.formatted(token)))
                .andExpect(status().isNoContent());
    }

    private String mobileRefreshToken(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/auth/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "%s",
                                  "deviceId": "ios-delete-test"
                                }
                                """.formatted(email, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("refreshToken").asText();
    }

    private UUID createPasswordResetToken(UUID userId) {
        PasswordResetToken token = new PasswordResetToken();
        token.setUserId(userId);
        token.setTokenHash("reset-" + UUID.randomUUID());
        token.setExpiresAt(Instant.now().plus(30, ChronoUnit.MINUTES));
        return passwordResetTokens.saveAndFlush(token).getId();
    }

    private void createIdempotencyRecord(UUID userId) {
        IdempotencyRecord record = new IdempotencyRecord();
        record.setUserId(userId);
        record.setScope("test.account-deletion");
        record.setIdempotencyKey("idem-" + UUID.randomUUID());
        record.setStatus(IdempotencyStatus.COMPLETED);
        record.setResponseStatus(200);
        record.setResponseBody("{}");
        record.setExpiresAt(Instant.now().plus(1, ChronoUnit.DAYS));
        idempotencyRecords.saveAndFlush(record);
    }

    private UUID createVehicle(UUID userId) {
        VehicleProfile vehicle = new VehicleProfile();
        vehicle.setUserId(userId);
        vehicle.setType(VehicleType.CAR);
        vehicle.setNickname("Porodicni auto");
        vehicle.setMake("Tesla");
        vehicle.setModel("Model 3");
        vehicle.setColor("Blue");
        vehicle.setLicensePlate("BG-123-AA");
        return vehicles.saveAndFlush(vehicle).getId();
    }

    private UUID createCompletedReservation(MockHttpSession customerSession, UUID resourceId) throws Exception {
        UUID reservationId = createFutureReservation(customerSession, resourceId);
        Reservation reservation = reservations.findById(reservationId).orElseThrow();
        reservation.setStatus(ReservationStatus.COMPLETED);
        reservation.setStartsAt(Instant.now().minus(3, ChronoUnit.DAYS));
        reservation.setEndsAt(Instant.now().minus(2, ChronoUnit.DAYS));
        reservation.setAccessInstructionsVisible(false);
        reservations.saveAndFlush(reservation);
        paymentAttempts.findByReservationIdOrderByCreatedAtDesc(reservationId).forEach(attempt -> {
            attempt.setStatus(PaymentAttemptStatus.CANCELLED);
            paymentAttempts.save(attempt);
        });
        paymentAttempts.flush();
        return reservationId;
    }

    private UUID createFutureReservation(MockHttpSession customerSession, UUID resourceId) throws Exception {
        Instant startsAt = Instant.now().truncatedTo(ChronoUnit.HOURS).plus(3, ChronoUnit.HOURS);
        Instant endsAt = startsAt.plus(2, ChronoUnit.HOURS);
        MvcResult reservationResult = mockMvc.perform(post("/reservations")
                        .with(csrf())
                        .session(customerSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "resourceId": "%s",
                                  "paymentMode": "PAY_ON_ARRIVAL",
                                  "startsAt": "%s",
                                  "endsAt": "%s",
                                  "idempotencyKey": "%s"
                                }
                                """.formatted(resourceId, startsAt, endsAt, "sl_rez_" + UUID.randomUUID())))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(objectMapper.readTree(reservationResult.getResponse().getContentAsString())
                .get("id")
                .asText());
    }

    private UUID createTerminalPaymentIntent(UUID customerId, UUID reservationId) {
        PaymentIntent intent = new PaymentIntent();
        intent.setReservationId(reservationId);
        intent.setCustomerId(customerId);
        intent.setAmountCents(1_200);
        intent.setCurrency("RSD");
        intent.setStatus(PaymentStatus.CAPTURED);
        return paymentIntents.saveAndFlush(intent).getId();
    }

    private UUID createParkingResource(MockHttpSession operatorSession) throws Exception {
        MvcResult locationResult = mockMvc.perform(post("/locations")
                        .with(csrf())
                        .session(operatorSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Deletion Test Garage",
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
                                  "label": "D-01",
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

    private record RegisteredUser(MockHttpSession session, UUID userId, String email) {
    }
}
