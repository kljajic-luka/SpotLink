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
import com.spotlink.payment.PaymentAttemptRepository;
import com.spotlink.reservation.BookingEventRepository;
import com.spotlink.reservation.BookingHold;
import com.spotlink.reservation.BookingHoldRepository;
import com.spotlink.reservation.BookingHoldStatus;
import com.spotlink.reservation.ReservationRepository;
import com.spotlink.reservation.ReservationService;
import com.spotlink.reservation.ReservationStatus;
import com.spotlink.support.SupportTicketRepository;
import com.spotlink.user.RegistrationStatus;
import com.spotlink.user.User;
import com.spotlink.user.UserRepository;
import com.spotlink.user.UserRole;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
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
class BookingOpsHardeningTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private BookingHoldRepository bookingHoldRepository;

    @Autowired
    private BookingEventRepository bookingEventRepository;

    @Autowired
    private PaymentAttemptRepository paymentAttemptRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private SupportTicketRepository supportTicketRepository;

    @Autowired
    private ReservationService reservationService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void holdCreationBlocksOverlapUntilExpirySweepReleasesCapacity() throws Exception {
        MockHttpSession operatorSession = registerOperator();
        UUID resourceId = createParkingResource(operatorSession, 1);
        MockHttpSession customerOne = registerCustomer("Casey");
        MockHttpSession customerTwo = registerCustomer("Jordan");

        Instant startsAt = alignedFutureStart(2);
        Instant endsAt = startsAt.plus(2, ChronoUnit.HOURS);

        MvcResult created = createReservation(customerOne, resourceId, startsAt, endsAt, "ONLINE", "sl_hold_" + UUID.randomUUID())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING_PAYMENT"))
                .andReturn();

        UUID reservationId = UUID.fromString(objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText());
        BookingHold hold = bookingHoldRepository.findByReservationId(reservationId).orElseThrow();
        assertThat(hold.getStatus()).isEqualTo(BookingHoldStatus.ACTIVE);
        assertThat(bookingEventRepository.findByReservationIdOrderByOccurredAtAsc(reservationId))
                .extracting(event -> event.getEventType().name())
                .contains("CREATED", "HOLD_CREATED");

        createReservation(customerTwo, resourceId, startsAt.plus(15, ChronoUnit.MINUTES), endsAt.plus(15, ChronoUnit.MINUTES), "ONLINE", "sl_overlap_" + UUID.randomUUID())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESOURCE_UNAVAILABLE"));

        hold.setExpiresAt(Instant.now().minus(1, ChronoUnit.MINUTES));
        bookingHoldRepository.saveAndFlush(hold);
        reservationService.expireOverdueHolds();

        assertThat(bookingHoldRepository.findById(hold.getId()).orElseThrow().getStatus()).isEqualTo(BookingHoldStatus.EXPIRED);
        assertThat(reservationRepository.findById(reservationId).orElseThrow().getStatus()).isEqualTo(ReservationStatus.EXPIRED);

        createReservation(customerTwo, resourceId, startsAt.plus(15, ChronoUnit.MINUTES), endsAt.plus(15, ChronoUnit.MINUTES), "ONLINE", "sl_after_expiry_" + UUID.randomUUID())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING_PAYMENT"));
    }

    @Test
    void manualConfirmationBookingStartsPendingAndBlocksCapacityUntilOperatorDecision() throws Exception {
        MockHttpSession operatorSession = registerOperator();
        UUID resourceId = createParkingResource(operatorSession, 1, "MANUAL");
        MockHttpSession customerOne = registerCustomer("Mika");
        MockHttpSession customerTwo = registerCustomer("Noa");

        Instant startsAt = alignedFutureStart(2);
        Instant endsAt = startsAt.plus(2, ChronoUnit.HOURS);

        MvcResult created = createReservation(customerOne, resourceId, startsAt, endsAt, "PAY_ON_ARRIVAL", "sl_manual_" + UUID.randomUUID())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING_OPERATOR_CONFIRMATION"))
                .andExpect(jsonPath("$.accessInstructionsVisible").value(false))
                .andExpect(jsonPath("$.bookingCode").exists())
                .andReturn();
        String reservationId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText();

        assertThat(bookingHoldRepository.findByReservationId(UUID.fromString(reservationId)).orElseThrow().getStatus())
                .isEqualTo(BookingHoldStatus.ACTIVE);
        assertThat(bookingEventRepository.findByReservationIdOrderByOccurredAtAsc(UUID.fromString(reservationId)))
                .extracting(event -> event.getEventType().name())
                .contains("OPERATOR_CONFIRMATION_REQUESTED");

        createReservation(customerTwo, resourceId, startsAt.plus(15, ChronoUnit.MINUTES), endsAt.plus(15, ChronoUnit.MINUTES), "PAY_ON_ARRIVAL", "sl_manual_overlap_" + UUID.randomUUID())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESOURCE_UNAVAILABLE"));

        mockMvc.perform(post("/operator/bookings/%s/check-in".formatted(reservationId))
                        .with(csrf())
                        .session(operatorSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_RESERVATION_TRANSITION"));

        MvcResult upcoming = mockMvc.perform(get("/operator/bookings/upcoming")
                        .session(operatorSession))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(upcoming.getResponse().getContentAsString())
                .contains(reservationId, "PENDING_OPERATOR_CONFIRMATION", "bookingCode");
    }

    @Test
    void operatorAndAdminCanConfirmPendingManualBookings() throws Exception {
        MockHttpSession operatorSession = registerOperator();
        UUID resourceId = createParkingResource(operatorSession, 2, "MANUAL");
        MockHttpSession customerSession = registerCustomer("Riley");
        MockHttpSession adminSession = createAdminSession();

        Instant startsAt = alignedFutureStart(3);
        Instant endsAt = startsAt.plus(2, ChronoUnit.HOURS);

        MvcResult operatorPending = createReservation(customerSession, resourceId, startsAt, endsAt, "PAY_ON_ARRIVAL", "sl_manual_op_confirm_" + UUID.randomUUID())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING_OPERATOR_CONFIRMATION"))
                .andReturn();
        String operatorReservationId = objectMapper.readTree(operatorPending.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(post("/operator/bookings/%s/confirm".formatted(operatorReservationId))
                        .with(csrf())
                        .session(operatorSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "notes": "kapacitet potvrdjen"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.accessInstructionsVisible").value(true))
                .andExpect(jsonPath("$.bookingCode").exists());

        MvcResult adminPending = createReservation(customerSession, resourceId, startsAt.plus(3, ChronoUnit.HOURS), endsAt.plus(3, ChronoUnit.HOURS), "PAY_ON_ARRIVAL", "sl_manual_admin_confirm_" + UUID.randomUUID())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING_OPERATOR_CONFIRMATION"))
                .andReturn();
        String adminReservationId = objectMapper.readTree(adminPending.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(post("/admin/bookings/%s/confirm".formatted(adminReservationId))
                        .with(csrf())
                        .session(adminSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reason": "admin pilot approval"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.accessInstructionsVisible").value(true));

        assertThat(auditLogRepository.findAll())
                .extracting(log -> log.getAction())
                .contains("OPERATOR_CONFIRMED_BOOKING", "ADMIN_CONFIRMED_BOOKING");
    }

    @Test
    void operatorAndAdminCanRejectPendingManualBookings() throws Exception {
        MockHttpSession operatorSession = registerOperator();
        UUID resourceId = createParkingResource(operatorSession, 2, "MANUAL");
        MockHttpSession customerSession = registerCustomer("Sky");
        MockHttpSession adminSession = createAdminSession();

        Instant startsAt = alignedFutureStart(4);
        Instant endsAt = startsAt.plus(2, ChronoUnit.HOURS);

        MvcResult operatorPending = createReservation(customerSession, resourceId, startsAt, endsAt, "PAY_ON_ARRIVAL", "sl_manual_op_reject_" + UUID.randomUUID())
                .andExpect(status().isCreated())
                .andReturn();
        String operatorReservationId = objectMapper.readTree(operatorPending.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(post("/operator/bookings/%s/reject".formatted(operatorReservationId))
                        .with(csrf())
                        .session(operatorSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reason": "mesto nije dostupno"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.accessInstructionsVisible").value(false));

        MvcResult adminPending = createReservation(customerSession, resourceId, startsAt.plus(3, ChronoUnit.HOURS), endsAt.plus(3, ChronoUnit.HOURS), "PAY_ON_ARRIVAL", "sl_manual_admin_reject_" + UUID.randomUUID())
                .andExpect(status().isCreated())
                .andReturn();
        String adminReservationId = objectMapper.readTree(adminPending.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(post("/admin/bookings/%s/reject".formatted(adminReservationId))
                        .with(csrf())
                        .session(adminSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reason": "operator reported blackout"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.accessInstructionsVisible").value(false));

        assertThat(auditLogRepository.findAll())
                .extracting(log -> log.getAction())
                .contains("OPERATOR_REJECTED_BOOKING", "ADMIN_REJECTED_BOOKING");
    }

    @Test
    void operatorCannotActOnAnotherOperatorsManualBooking() throws Exception {
        MockHttpSession owningOperatorSession = registerOperator();
        MockHttpSession otherOperatorSession = registerOperator();
        UUID resourceId = createParkingResource(owningOperatorSession, 1, "MANUAL");
        MockHttpSession customerSession = registerCustomer("Quinn");

        Instant startsAt = alignedFutureStart(5);
        Instant endsAt = startsAt.plus(2, ChronoUnit.HOURS);

        MvcResult created = createReservation(customerSession, resourceId, startsAt, endsAt, "PAY_ON_ARRIVAL", "sl_manual_scope_" + UUID.randomUUID())
                .andExpect(status().isCreated())
                .andReturn();
        String reservationId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(post("/operator/bookings/%s/confirm".formatted(reservationId))
                        .with(csrf())
                        .session(otherOperatorSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/operator/bookings/%s/reject".formatted(reservationId))
                        .with(csrf())
                        .session(otherOperatorSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());

        assertThat(reservationRepository.findById(UUID.fromString(reservationId)).orElseThrow().getStatus())
                .isEqualTo(ReservationStatus.PENDING_OPERATOR_CONFIRMATION);
    }

    @Test
    void manualConfirmRejectFailOutsidePendingOperatorState() throws Exception {
        MockHttpSession operatorSession = registerOperator();
        UUID resourceId = createParkingResource(operatorSession, 2, "MANUAL");
        MockHttpSession customerSession = registerCustomer("Sage");
        MockHttpSession adminSession = createAdminSession();

        Instant startsAt = alignedFutureStart(6);
        Instant endsAt = startsAt.plus(2, ChronoUnit.HOURS);

        MvcResult confirmed = createReservation(customerSession, resourceId, startsAt, endsAt, "PAY_ON_ARRIVAL", "sl_manual_invalid_confirmed_" + UUID.randomUUID())
                .andExpect(status().isCreated())
                .andReturn();
        String confirmedId = objectMapper.readTree(confirmed.getResponse().getContentAsString()).get("id").asText();
        mockMvc.perform(post("/operator/bookings/%s/confirm".formatted(confirmedId))
                        .with(csrf())
                        .session(operatorSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        mockMvc.perform(post("/operator/bookings/%s/confirm".formatted(confirmedId))
                        .with(csrf())
                        .session(operatorSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_RESERVATION_TRANSITION"));

        mockMvc.perform(post("/admin/bookings/%s/reject".formatted(confirmedId))
                        .with(csrf())
                        .session(adminSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_RESERVATION_TRANSITION"));

        MvcResult cancelled = createReservation(customerSession, resourceId, startsAt.plus(3, ChronoUnit.HOURS), endsAt.plus(3, ChronoUnit.HOURS), "PAY_ON_ARRIVAL", "sl_manual_invalid_cancelled_" + UUID.randomUUID())
                .andExpect(status().isCreated())
                .andReturn();
        String cancelledId = objectMapper.readTree(cancelled.getResponse().getContentAsString()).get("id").asText();
        mockMvc.perform(post("/admin/bookings/%s/cancel".formatted(cancelledId))
                        .with(csrf())
                        .session(adminSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reason": "cancel pre potvrde"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        mockMvc.perform(post("/operator/bookings/%s/confirm".formatted(cancelledId))
                        .with(csrf())
                        .session(operatorSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_RESERVATION_TRANSITION"));

        mockMvc.perform(post("/operator/bookings/%s/reject".formatted(cancelledId))
                        .with(csrf())
                        .session(operatorSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_RESERVATION_TRANSITION"));
    }

    @Test
    void manualOnlineBookingsCanBeConfirmedForPaymentOrRejectedByOperator() throws Exception {
        MockHttpSession operatorSession = registerOperator();
        UUID resourceId = createParkingResource(operatorSession, 1, "MANUAL");
        MockHttpSession customerSession = registerCustomer("Riley");

        Instant startsAt = alignedFutureStart(7);
        Instant endsAt = startsAt.plus(2, ChronoUnit.HOURS);

        MvcResult pendingOnline = createReservation(
                customerSession,
                resourceId,
                startsAt,
                endsAt,
                "ONLINE",
                "sl_manual_confirm_" + UUID.randomUUID())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING_OPERATOR_CONFIRMATION"))
                .andExpect(jsonPath("$.paymentExpiresAt").doesNotExist())
                .andExpect(jsonPath("$.operatorConfirmationExpiresAt").exists())
                .andExpect(jsonPath("$.bookingCode").exists())
                .andReturn();
        String confirmedReservationId = objectMapper.readTree(pendingOnline.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(get("/operator/bookings/%s".formatted(confirmedReservationId))
                        .session(operatorSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reservation.status").value("PENDING_OPERATOR_CONFIRMATION"))
                .andExpect(jsonPath("$.timeline").isArray());

        mockMvc.perform(post("/operator/bookings/%s/confirm".formatted(confirmedReservationId))
                        .with(csrf())
                        .session(operatorSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "notes": "slot approved"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_PAYMENT"))
                .andExpect(jsonPath("$.paymentExpiresAt").exists())
                .andExpect(jsonPath("$.operatorConfirmationExpiresAt").doesNotExist());

        UUID confirmedId = UUID.fromString(confirmedReservationId);
        assertThat(reservationRepository.findById(confirmedId).orElseThrow().getStatus())
                .isEqualTo(ReservationStatus.PENDING_PAYMENT);
        assertThat(bookingHoldRepository.findByReservationId(confirmedId).orElseThrow().getStatus())
                .isEqualTo(BookingHoldStatus.ACTIVE);
        assertThat(bookingEventRepository.findByReservationIdOrderByOccurredAtAsc(confirmedId))
                .extracting(event -> event.getEventType().name())
                .contains("OPERATOR_CONFIRMATION_REQUESTED", "OPERATOR_CONFIRMED");

        MvcResult pendingRejected = createReservation(
                customerSession,
                resourceId,
                startsAt.plus(3, ChronoUnit.HOURS),
                endsAt.plus(3, ChronoUnit.HOURS),
                "ONLINE",
                "sl_manual_reject_" + UUID.randomUUID())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING_OPERATOR_CONFIRMATION"))
                .andReturn();
        String rejectedReservationId = objectMapper.readTree(pendingRejected.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(post("/operator/bookings/%s/reject".formatted(rejectedReservationId))
                        .with(csrf())
                        .session(operatorSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reason": "slot unavailable"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.accessInstructionsVisible").value(false))
                .andExpect(jsonPath("$.paymentExpiresAt").doesNotExist())
                .andExpect(jsonPath("$.operatorConfirmationExpiresAt").doesNotExist());

        UUID rejectedId = UUID.fromString(rejectedReservationId);
        assertThat(reservationRepository.findById(rejectedId).orElseThrow().getStatus())
                .isEqualTo(ReservationStatus.REJECTED);
        assertThat(bookingHoldRepository.findByReservationId(rejectedId).orElseThrow().getStatus())
                .isEqualTo(BookingHoldStatus.RELEASED);
        assertThat(bookingEventRepository.findByReservationIdOrderByOccurredAtAsc(rejectedId))
                .extracting(event -> event.getEventType().name())
                .contains("OPERATOR_CONFIRMATION_REQUESTED", "OPERATOR_REJECTED");
        assertThat(auditLogRepository.findAll())
                .extracting(log -> log.getAction())
                .contains("OPERATOR_CONFIRMED_BOOKING", "OPERATOR_REJECTED_BOOKING");
    }

    @Test
    void manualPayOnArrivalBookingConfirmsImmediatelyWhenOperatorApproves() throws Exception {
        MockHttpSession operatorSession = registerOperator();
        UUID resourceId = createParkingResource(operatorSession, 1, "MANUAL");
        MockHttpSession customerSession = registerCustomer("Sawyer");

        Instant startsAt = alignedFutureStart(8);
        Instant endsAt = startsAt.plus(2, ChronoUnit.HOURS);

        MvcResult pendingPayOnArrival = createReservation(
                customerSession,
                resourceId,
                startsAt,
                endsAt,
                "PAY_ON_ARRIVAL",
                "sl_manual_poa_" + UUID.randomUUID())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING_OPERATOR_CONFIRMATION"))
                .andExpect(jsonPath("$.operatorConfirmationExpiresAt").exists())
                .andReturn();
        String reservationId = objectMapper.readTree(pendingPayOnArrival.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(post("/operator/bookings/%s/confirm".formatted(reservationId))
                        .with(csrf())
                        .session(operatorSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "notes": "pay on arrival approved"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.accessInstructionsVisible").value(true))
                .andExpect(jsonPath("$.paymentExpiresAt").doesNotExist())
                .andExpect(jsonPath("$.operatorConfirmationExpiresAt").doesNotExist());

        UUID parsedReservationId = UUID.fromString(reservationId);
        assertThat(bookingHoldRepository.findByReservationId(parsedReservationId).orElseThrow().getStatus())
                .isEqualTo(BookingHoldStatus.CONSUMED);
        assertThat(paymentAttemptRepository.findByReservationIdOrderByCreatedAtDesc(parsedReservationId))
                .hasSize(1)
                .extracting(attempt -> attempt.getPaymentMode().name(), attempt -> attempt.getStatus().name())
                .containsExactly(org.assertj.core.groups.Tuple.tuple("PAY_ON_ARRIVAL", "PENDING"));
    }

    @Test
    void expiredManualConfirmationCannotBeConfirmedByOperator() throws Exception {
        MockHttpSession operatorSession = registerOperator();
        UUID resourceId = createParkingResource(operatorSession, 1, "MANUAL");
        MockHttpSession customerSession = registerCustomer("Quinn");

        Instant startsAt = alignedFutureStart(5);
        Instant endsAt = startsAt.plus(2, ChronoUnit.HOURS);

        MvcResult pendingReservation = createReservation(
                customerSession,
                resourceId,
                startsAt,
                endsAt,
                "PAY_ON_ARRIVAL",
                "sl_manual_expired_" + UUID.randomUUID())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING_OPERATOR_CONFIRMATION"))
                .andReturn();
        UUID reservationId = UUID.fromString(objectMapper.readTree(pendingReservation.getResponse().getContentAsString()).get("id").asText());
        BookingHold hold = bookingHoldRepository.findByReservationId(reservationId).orElseThrow();
        hold.setExpiresAt(Instant.now().minus(1, ChronoUnit.MINUTES));
        bookingHoldRepository.saveAndFlush(hold);

        mockMvc.perform(post("/operator/bookings/%s/confirm".formatted(reservationId))
                        .with(csrf())
                        .session(operatorSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "notes": "too late"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_RESERVATION_TRANSITION"));

        assertThat(reservationRepository.findById(reservationId).orElseThrow().getStatus())
                .isEqualTo(ReservationStatus.EXPIRED);
        assertThat(bookingHoldRepository.findById(hold.getId()).orElseThrow().getStatus())
                .isEqualTo(BookingHoldStatus.EXPIRED);
        assertThat(paymentAttemptRepository.findByReservationIdOrderByCreatedAtDesc(reservationId))
                .isEmpty();
        assertThat(bookingEventRepository.findByReservationIdOrderByOccurredAtAsc(reservationId))
                .extracting(event -> event.getEventType().name())
                .contains("OPERATOR_CONFIRMATION_REQUESTED", "HOLD_EXPIRED")
                .doesNotContain("OPERATOR_CONFIRMED");
    }

    @Test
    void operatorPauseAndLifecycleActionsUseCentralStateTransitions() throws Exception {
        MockHttpSession operatorSession = registerOperator();
        UUID resourceId = createParkingResource(operatorSession, 1);
        MockHttpSession customerSession = registerCustomer("Avery");

        Instant startsAt = alignedFutureStart(3);
        Instant endsAt = startsAt.plus(2, ChronoUnit.HOURS);

        mockMvc.perform(post("/operator/resources/%s/pause".formatted(resourceId))
                        .with(csrf())
                        .session(operatorSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reason": "manualna pauza"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paused").value(true));

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
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESOURCE_PAUSED"));

        mockMvc.perform(post("/operator/resources/%s/unpause".formatted(resourceId))
                        .with(csrf())
                        .session(operatorSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paused").value(false));

        MvcResult pendingReservation = createReservation(customerSession, resourceId, startsAt, endsAt, "ONLINE", "sl_pending_" + UUID.randomUUID())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING_PAYMENT"))
                .andReturn();
        String pendingReservationId = objectMapper.readTree(pendingReservation.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(post("/operator/bookings/%s/check-in".formatted(pendingReservationId))
                        .with(csrf())
                        .session(operatorSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_RESERVATION_TRANSITION"));

        MvcResult confirmedReservation = createReservation(
                customerSession,
                resourceId,
                startsAt.plus(3, ChronoUnit.HOURS),
                endsAt.plus(3, ChronoUnit.HOURS),
                "PAY_ON_ARRIVAL",
                "sl_poa_" + UUID.randomUUID())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andReturn();
        String confirmedReservationId = objectMapper.readTree(confirmedReservation.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(post("/operator/bookings/%s/check-in".formatted(confirmedReservationId))
                        .with(csrf())
                        .session(operatorSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "notes": "vozac stigao"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        MvcResult noShowReservation = createReservation(
                customerSession,
                resourceId,
                startsAt.plus(6, ChronoUnit.HOURS),
                endsAt.plus(6, ChronoUnit.HOURS),
                "PAY_ON_ARRIVAL",
                "sl_noshow_" + UUID.randomUUID())
                .andExpect(status().isCreated())
                .andReturn();
        String noShowReservationId = objectMapper.readTree(noShowReservation.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(post("/operator/bookings/%s/no-show".formatted(noShowReservationId))
                        .with(csrf())
                        .session(operatorSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reason": "vozac se nije pojavio"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NO_SHOW"));

        MvcResult cancellableReservation = createReservation(
                customerSession,
                resourceId,
                startsAt.plus(9, ChronoUnit.HOURS),
                endsAt.plus(9, ChronoUnit.HOURS),
                "PAY_ON_ARRIVAL",
                "sl_cancel_" + UUID.randomUUID())
                .andExpect(status().isCreated())
                .andReturn();
        String cancellableReservationId = objectMapper.readTree(cancellableReservation.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(post("/operator/bookings/%s/cancel".formatted(cancellableReservationId))
                        .with(csrf())
                        .session(operatorSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reason": "operativna intervencija"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        mockMvc.perform(get("/operator/bookings/upcoming")
                        .session(operatorSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    void adminOverrideAndRefundMarkerArePersistedAndAudited() throws Exception {
        MockHttpSession operatorSession = registerOperator();
        UUID resourceId = createParkingResource(operatorSession, 1);
        MockHttpSession customerSession = registerCustomer("Morgan");
        MockHttpSession adminSession = createAdminSession();

        Instant startsAt = alignedFutureStart(4);
        Instant endsAt = startsAt.plus(2, ChronoUnit.HOURS);

        MvcResult created = createReservation(customerSession, resourceId, startsAt, endsAt, "PAY_ON_ARRIVAL", "sl_admin_" + UUID.randomUUID())
                .andExpect(status().isCreated())
                .andReturn();
        String reservationId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(post("/admin/bookings/%s/cancel".formatted(reservationId))
                        .with(csrf())
                        .session(adminSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reason": "manualni override"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        mockMvc.perform(post("/admin/bookings/%s/refund-marker".formatted(reservationId))
                        .with(csrf())
                        .session(adminSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reason": "rucni povracaj",
                                  "amountCents": 450
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reason").value("rucni povracaj"));

        JsonNode detail = objectMapper.readTree(mockMvc.perform(get("/admin/bookings/%s".formatted(reservationId))
                        .session(adminSession))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
        assertThat(detail.path("timeline")).extracting(JsonNode::size).isNotNull();
        assertThat(detail.path("timeline").toString()).contains("ADMIN_OVERRIDE", "REFUND_MARKED");

        assertThat(auditLogRepository.findAll())
                .extracting(log -> log.getAction())
                .contains("ADMIN_CANCELLED_BOOKING", "ADMIN_MARKED_REFUND");
    }

    @Test
    void paymentSuccessFailureSupportInspectionAndIdempotencyAreTracked() throws Exception {
        MockHttpSession operatorSession = registerOperator();
        UUID resourceId = createParkingResource(operatorSession, 1);
        MockHttpSession customerSession = registerCustomer("Taylor");
        MockHttpSession adminSession = createAdminSession();

        Instant startsAt = alignedFutureStart(5);
        Instant endsAt = startsAt.plus(2, ChronoUnit.HOURS);

        MvcResult paidReservation = createReservation(customerSession, resourceId, startsAt, endsAt, "ONLINE", "sl_pay_ok_" + UUID.randomUUID())
                .andExpect(status().isCreated())
                .andReturn();
        String paidReservationId = objectMapper.readTree(paidReservation.getResponse().getContentAsString()).get("id").asText();

        String paymentIdempotencyKey = "sl_pi_" + UUID.randomUUID();
        MvcResult firstIntent = createPaymentIntent(customerSession, paidReservationId, "pm_card_visa", paymentIdempotencyKey)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("AUTHORIZED"))
                .andReturn();

        createPaymentIntent(customerSession, paidReservationId, "pm_card_visa", paymentIdempotencyKey)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(objectMapper.readTree(firstIntent.getResponse().getContentAsString()).get("id").asText()));

        assertThat(paymentAttemptRepository.findByReservationIdOrderByCreatedAtDesc(UUID.fromString(paidReservationId)))
                .hasSize(1)
                .extracting(attempt -> attempt.getStatus().name())
                .containsExactly("AUTHORIZED");

        MvcResult failedReservation = createReservation(
                customerSession,
                resourceId,
                startsAt.plus(3, ChronoUnit.HOURS),
                endsAt.plus(3, ChronoUnit.HOURS),
                "ONLINE",
                "sl_pay_fail_" + UUID.randomUUID())
                .andExpect(status().isCreated())
                .andReturn();
        String failedReservationId = objectMapper.readTree(failedReservation.getResponse().getContentAsString()).get("id").asText();

        createPaymentIntent(customerSession, failedReservationId, "pm_card_declined", "sl_decline_" + UUID.randomUUID())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("FAILED"));

        mockMvc.perform(get("/reservations/%s".formatted(failedReservationId)).session(customerSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_PAYMENT"));

        mockMvc.perform(get("/reservations/%s/detail".formatted(failedReservationId)).session(customerSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reservation.id").value(failedReservationId))
                .andExpect(jsonPath("$.timeline").isArray())
                .andExpect(jsonPath("$.paymentAttempts[0].status").value("FAILED"));

        mockMvc.perform(post("/support/tickets")
                        .with(csrf())
                        .session(customerSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "category": "PAYMENT",
                                  "subject": "Problem sa naplatom",
                                  "body": "Kartica je odbijena tokom testa.",
                                  "reservationId": "%s"
                                }
                                """.formatted(failedReservationId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.subject").value("Problem sa naplatom"));

        JsonNode paymentAttempts = objectMapper.readTree(mockMvc.perform(get("/admin/payment-attempts")
                        .session(adminSession)
                        .param("reservationId", failedReservationId))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
        assertThat(paymentAttempts.toString()).contains("FAILED", "AUTHORIZE_FAILED");

        JsonNode supportCases = objectMapper.readTree(mockMvc.perform(get("/admin/support-cases")
                        .session(adminSession))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
        assertThat(supportCases.toString()).contains("Problem sa naplatom");
        assertThat(supportTicketRepository.count()).isPositive();
    }

    private org.springframework.test.web.servlet.ResultActions createReservation(
            MockHttpSession session,
            UUID resourceId,
            Instant startsAt,
            Instant endsAt,
            String paymentMode,
            String idempotencyKey) throws Exception {
        String paymentModeField = paymentMode == null ? "" : "\n  \"paymentMode\": \"%s\",".formatted(paymentMode);
        return mockMvc.perform(post("/reservations")
                .with(csrf())
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "resourceId": "%s",%s
                          "startsAt": "%s",
                          "endsAt": "%s",
                          "idempotencyKey": "%s"
                        }
                        """.formatted(resourceId, paymentModeField, startsAt, endsAt, idempotencyKey)));
    }

    private Instant alignedFutureStart(long hoursAhead) {
        return Instant.now().truncatedTo(ChronoUnit.HOURS).plus(hoursAhead, ChronoUnit.HOURS);
    }

    private org.springframework.test.web.servlet.ResultActions createPaymentIntent(
            MockHttpSession session,
            String reservationId,
            String paymentMethodId,
            String idempotencyKey) throws Exception {
        return mockMvc.perform(post("/payments/intents")
                .with(csrf())
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "reservationId": "%s",
                          "paymentMethodId": "%s",
                          "idempotencyKey": "%s"
                        }
                        """.formatted(reservationId, paymentMethodId, idempotencyKey)));
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

    private MockHttpSession registerCustomer(String firstName) throws Exception {
        MvcResult result = mockMvc.perform(post("/auth/register/customer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "firstName": "%s",
                                  "lastName": "Customer",
                                  "email": "customer-%s@spotlink.test",
                                  "password": "CorrectHorse123",
                                  "acceptsTerms": true
                                }
                                """.formatted(firstName, UUID.randomUUID())))
                .andExpect(status().isCreated())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private MockHttpSession createAdminSession() throws Exception {
        String email = "admin-%s@spotlink.test".formatted(UUID.randomUUID());
        User admin = new User();
        admin.setEmail(email);
        admin.setPasswordHash(passwordEncoder.encode("CorrectHorse123"));
        admin.setFirstName("Admin");
        admin.setLastName("User");
        admin.setRegistrationStatus(RegistrationStatus.ACTIVE);
        admin.setRoles(Set.of(UserRole.ADMIN));
        userRepository.saveAndFlush(admin);

        MvcResult result = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AuthDtos.LoginRequest(email, "CorrectHorse123"))))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private UUID createParkingResource(MockHttpSession operatorSession, int capacity) throws Exception {
        return createParkingResource(operatorSession, capacity, "INSTANT");
    }

    private UUID createParkingResource(MockHttpSession operatorSession, int capacity, String confirmationMode) throws Exception {
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
                                  "accessType": "SELF_PARK",
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
                                  "active": true,
                                  "capacity": %s,
                                  "confirmationMode": "%s"
                                }
                                """.formatted(capacity, confirmationMode)))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(objectMapper.readTree(resourceResult.getResponse().getContentAsString()).get("id").asText());
    }
}
