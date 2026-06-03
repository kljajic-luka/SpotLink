package com.spotlink.reservation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spotlink.admin.AuditService;
import com.spotlink.core.ApiPage;
import com.spotlink.core.AppProperties;
import com.spotlink.core.ConflictException;
import com.spotlink.core.IdempotencyRecord;
import com.spotlink.core.IdempotencyService;
import com.spotlink.core.IdempotencyStatus;
import com.spotlink.core.NotFoundException;
import com.spotlink.inventory.InventoryPool;
import com.spotlink.inventory.InventoryPoolService;
import com.spotlink.location.LocationService;
import com.spotlink.location.ParkingLocation;
import com.spotlink.location.ParkingResource;
import com.spotlink.operator.OperatorAccount;
import com.spotlink.operator.OperatorAccountRepository;
import com.spotlink.partner.ConfirmationMode;
import com.spotlink.payment.PaymentAttempt;
import com.spotlink.payment.PaymentAttemptRepository;
import com.spotlink.payment.PaymentAttemptStatus;
import com.spotlink.payment.PaymentAuthority;
import com.spotlink.payment.PaymentDtos;
import com.spotlink.payment.PaymentProviderEvent;
import com.spotlink.payment.PaymentProviderEventRepository;
import com.spotlink.payment.PaymentProviderEventStatus;
import com.spotlink.payment.Refund;
import com.spotlink.payment.RefundRepository;
import com.spotlink.security.CurrentUserService;
import com.spotlink.support.SupportDtos;
import com.spotlink.support.SupportTicket;
import com.spotlink.support.SupportTicketRepository;
import com.spotlink.vehicle.VehicleProfile;
import com.spotlink.vehicle.VehicleService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookingOperationsService {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private static final List<ReservationStatus> UPCOMING_OPERATOR_STATUSES = List.of(
            ReservationStatus.PENDING_OPERATOR_CONFIRMATION,
            ReservationStatus.PENDING_PAYMENT,
            ReservationStatus.CONFIRMED,
            ReservationStatus.ACTIVE,
            ReservationStatus.DISPUTED,
            ReservationStatus.NO_SHOW);

    private final ReservationRepository reservations;
    private final BookingHoldRepository bookingHolds;
    private final BookingEventRepository bookingEvents;
    private final CheckinRepository checkins;
    private final PaymentAttemptRepository paymentAttempts;
    private final PaymentProviderEventRepository paymentProviderEvents;
    private final PaymentAuthority paymentAuthority;
    private final RefundRepository refunds;
    private final SupportTicketRepository supportTickets;
    private final InventoryPoolService inventoryPools;
    private final LocationService locationService;
    private final ReservationAvailabilityPolicy availabilityPolicy;
    private final VehicleService vehicleService;
    private final OperatorAccountRepository operators;
    private final CurrentUserService currentUser;
    private final IdempotencyService idempotency;
    private final ObjectMapper objectMapper;
    private final AppProperties appProperties;
    private final Clock clock;
    private final AuditService auditService;
    private final ReservationStateMachine stateMachine;

    public BookingOperationsService(
            ReservationRepository reservations,
            BookingHoldRepository bookingHolds,
            BookingEventRepository bookingEvents,
            CheckinRepository checkins,
            PaymentAttemptRepository paymentAttempts,
            PaymentProviderEventRepository paymentProviderEvents,
            PaymentAuthority paymentAuthority,
            RefundRepository refunds,
            SupportTicketRepository supportTickets,
            InventoryPoolService inventoryPools,
            LocationService locationService,
            ReservationAvailabilityPolicy availabilityPolicy,
            VehicleService vehicleService,
            OperatorAccountRepository operators,
            CurrentUserService currentUser,
            IdempotencyService idempotency,
            ObjectMapper objectMapper,
            AppProperties appProperties,
            Clock clock,
            AuditService auditService,
            ReservationStateMachine stateMachine) {
        this.reservations = reservations;
        this.bookingHolds = bookingHolds;
        this.bookingEvents = bookingEvents;
        this.checkins = checkins;
        this.paymentAttempts = paymentAttempts;
        this.paymentProviderEvents = paymentProviderEvents;
        this.paymentAuthority = paymentAuthority;
        this.refunds = refunds;
        this.supportTickets = supportTickets;
        this.inventoryPools = inventoryPools;
        this.locationService = locationService;
        this.availabilityPolicy = availabilityPolicy;
        this.vehicleService = vehicleService;
        this.operators = operators;
        this.currentUser = currentUser;
        this.idempotency = idempotency;
        this.objectMapper = objectMapper;
        this.appProperties = appProperties;
        this.clock = clock;
        this.auditService = auditService;
        this.stateMachine = stateMachine;
    }

    @Transactional(readOnly = true)
    public ApiPage<ReservationDtos.ReservationDto> mine(int page, int size) {
        UUID userId = currentUser.userId();
        return ApiPage.from(reservations.findByCustomerIdOrderByStartsAtDesc(userId, PageRequest.of(page, Math.min(size, 100)))
                .map(this::toDto));
    }

    @Transactional(readOnly = true)
    public ReservationDtos.ReservationDto getForCurrentUser(UUID reservationId) {
        Reservation reservation = reservations.findById(reservationId)
                .orElseThrow(() -> new NotFoundException("Reservation was not found."));
        requireParticipant(reservation);
        return toDto(reservation);
    }

    @Transactional(readOnly = true)
    public ReservationDtos.BookingDetailDto getDetailForCurrentUser(UUID reservationId) {
        Reservation reservation = reservations.findById(reservationId)
                .orElseThrow(() -> new NotFoundException("Reservation was not found."));
        requireParticipant(reservation);
        return toDetailDto(reservation);
    }

    @Transactional(readOnly = true)
    public ReservationDtos.ReservationQuote quote(ReservationDtos.ReservationQuoteRequest request) {
        InventoryPool pool = inventoryPools.requireByResourceId(request.resourceId());
        ParkingLocation location = locationService.requireLocation(pool.getLocationId());
        availabilityPolicy.assertBookable(location, pool, request.startsAt(), request.endsAt(), Instant.now(clock));
        validateVehicleFit(pool, request.vehicleId());
        return buildQuote(request.resourceId(), pool, request.startsAt(), request.endsAt(), request.promoCode(), Instant.now(clock));
    }

    @Transactional
    public ReservationDtos.ReservationDto create(ReservationDtos.CreateReservationRequest request) {
        UUID userId = currentUser.userId();
        Instant now = Instant.now(clock);
        expireOverdueHolds(now);
        IdempotencyRecord idempotencyRecord = idempotency.begin(userId, "reservation:create", request.idempotencyKey());
        if (idempotencyRecord.getStatus() == IdempotencyStatus.COMPLETED) {
            return reservations.findByCustomerIdAndIdempotencyKey(userId, request.idempotencyKey())
                    .map(this::toDto)
                    .orElseGet(() -> readCachedReservation(idempotencyRecord));
        }
        if (idempotencyRecord.getStatus() == IdempotencyStatus.PROCESSING && idempotencyRecord.getResponseStatus() != null) {
            throw new ConflictException("IDEMPOTENCY_IN_PROGRESS", "This reservation request is already being processed.");
        }

        try {
            ParkingResource resource = locationService.requireResource(request.resourceId());
            InventoryPool pool = inventoryPools.requireByResourceIdForUpdate(request.resourceId());
            ParkingLocation location = locationService.requireLocation(pool.getLocationId());
            availabilityPolicy.assertBookable(location, pool, request.startsAt(), request.endsAt(), now);
            validateVehicleFit(pool, request.vehicleId());

            PaymentMode paymentMode = resolvePaymentMode(pool, request.paymentMode());
            ConfirmationMode confirmationMode = pool.getConfirmationMode() == null ? ConfirmationMode.INSTANT : pool.getConfirmationMode();
            ReservationStatus initialStatus = initialStatus(paymentMode, confirmationMode);
            ReservationDtos.ReservationQuote quote = buildQuote(resource.getId(), pool, request.startsAt(), request.endsAt(), request.promoCode(), now);
            Instant holdExpiresAt = holdExpiresAt(initialStatus, quote.expiresAt(), now);

            Reservation reservation = new Reservation();
            reservation.setCustomerId(userId);
            reservation.setOperatorId(location.getOperatorId());
            reservation.setLocationId(location.getId());
            reservation.setResourceId(resource.getId());
            reservation.setInventoryPoolId(pool.getId());
            reservation.setVehicleId(request.vehicleId());
            reservation.setStartsAt(request.startsAt());
            reservation.setEndsAt(request.endsAt());
            reservation.setTimezone(location.getTimezone());
            reservation.setBookingCode(generateBookingCode());
            reservation.setCancellationPolicy(ReservationCancellationPolicy.FULL_REFUND_BEFORE_START);
            reservation.setCancellableUntil(request.startsAt());
            reservation.setRefundEligibleCents(0);
            reservation.setStatus(initialStatus);
            reservation.setPaymentMode(paymentMode);
            reservation.setTotalAmountCents(quote.totalAmountCents());
            reservation.setCurrency(quote.currency());
            reservation.setAccessInstructionsVisible(initialStatus == ReservationStatus.CONFIRMED);
            reservation.setPaymentExpiresAt(initialStatus == ReservationStatus.PENDING_PAYMENT ? quote.expiresAt() : null);
            reservation.setOperatorConfirmationExpiresAt(initialStatus == ReservationStatus.PENDING_OPERATOR_CONFIRMATION ? holdExpiresAt : null);
            reservation.setIdempotencyKey(request.idempotencyKey());
            Reservation saved = reservations.save(reservation);

            BookingHold hold = new BookingHold();
            hold.setReservationId(saved.getId());
            hold.setInventoryPoolId(pool.getId());
            hold.setCustomerId(userId);
            hold.setStartsAt(request.startsAt());
            hold.setEndsAt(request.endsAt());
            hold.setExpiresAt(holdExpiresAt);
            hold.setStatus(initialStatus == ReservationStatus.CONFIRMED ? BookingHoldStatus.CONSUMED : BookingHoldStatus.ACTIVE);
            hold.setIdempotencyKey(request.idempotencyKey());
            hold.setAmountCents(quote.totalAmountCents());
            hold.setCurrency(quote.currency());
            hold.setPaymentMode(paymentMode);
            BookingHold savedHold = bookingHolds.save(hold);

            saved.setHoldId(savedHold.getId());
            recordEvent(saved.getId(), BookingEventType.CREATED, BookingActorType.CUSTOMER, userId, null,
                    metadata(
                            "paymentMode", paymentMode.name(),
                            "inventoryPoolId", pool.getId(),
                            "confirmationMode", confirmationMode.name(),
                            "bookingCode", saved.getBookingCode()));
            recordEvent(saved.getId(), BookingEventType.HOLD_CREATED, BookingActorType.SYSTEM, null, null,
                    metadata("holdId", savedHold.getId(), "expiresAt", savedHold.getExpiresAt()));

            if (initialStatus == ReservationStatus.PENDING_OPERATOR_CONFIRMATION) {
                recordEvent(saved.getId(), BookingEventType.OPERATOR_CONFIRMATION_REQUESTED, BookingActorType.SYSTEM, null, null,
                        metadata("expiresAt", saved.getOperatorConfirmationExpiresAt()));
            }

            if (initialStatus == ReservationStatus.CONFIRMED) {
                createPayOnArrivalAttempt(saved, now);
                recordEvent(saved.getId(), BookingEventType.CONFIRMED, BookingActorType.SYSTEM, null, null,
                        metadata("paymentMode", paymentMode.name()));
            }

            auditReservationAction(userId, "RESERVATION_CREATED", saved, metadata(
                    "paymentMode", paymentMode.name(),
                    "inventoryPoolId", pool.getId(),
                    "holdId", savedHold.getId(),
                    "confirmationMode", confirmationMode.name(),
                    "bookingCode", saved.getBookingCode()));

            ReservationDtos.ReservationDto dto = toDto(saved);
            idempotency.complete(idempotencyRecord, 201, objectMapper.writeValueAsString(dto));
            return dto;
        } catch (RuntimeException | JsonProcessingException ex) {
            idempotency.fail(idempotencyRecord, 409, ex.getMessage());
            if (ex instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException("Could not serialize idempotency response.", ex);
        }
    }

    @Transactional
    public ReservationDtos.ReservationDto cancelAsCustomer(UUID reservationId, String reason) {
        Reservation reservation = reservations.findByIdForUpdate(reservationId)
                .orElseThrow(() -> new NotFoundException("Reservation was not found."));
        requireParticipant(reservation);
        Instant now = Instant.now(clock);
        if (!canCustomerCancel(reservation, now)) {
            throw new ConflictException("CANCELLATION_NOT_ALLOWED", "Reservation can only be cancelled before it starts.");
        }
        long refundEligibleCents = refundEligibleCentsAtCancellation(reservation, now);
        reservation.setRefundEligibleCents(refundEligibleCents);
        applyTransition(reservation, ReservationStatus.CANCELLED, BookingEventType.CANCELLED, BookingActorType.CUSTOMER,
                currentUser.userId(), reason);
        releaseHold(reservation);
        cancelPendingAttempts(reservation.getId());
        auditReservationAction(currentUser.userId(), "RESERVATION_CANCELLED", reservation, metadata(
                "reason", reason,
                "refundEligibleCents", refundEligibleCents));
        return toDto(reservation);
    }

    @Transactional
    public int expireOverdueHolds() {
        return expireOverdueHolds(Instant.now(clock));
    }

    @Transactional(noRollbackFor = ConflictException.class)
    public ReservationDtos.ReservationDto confirmAsOperator(UUID reservationId, String notes) {
        Instant now = Instant.now(clock);
        Reservation reservation = reservations.findByIdForUpdate(reservationId)
                .orElseThrow(() -> new NotFoundException("Reservation was not found."));
        requireOperatorReservation(reservation);
        expireReservationIfHoldOverdue(reservation, now);
        requirePendingOperatorConfirmation(reservation);
        UUID actorUserId = currentUser.userId();

        if (reservation.getPaymentMode() == PaymentMode.PAY_ON_ARRIVAL) {
            applyTransition(reservation, ReservationStatus.CONFIRMED, BookingEventType.OPERATOR_CONFIRMED, BookingActorType.OPERATOR,
                    actorUserId, notes);
            consumeHold(reservation);
            createPayOnArrivalAttempt(reservation, now);
        } else {
            paymentAuthority.requireOnlinePaymentsEnabled();
            applyTransition(reservation, ReservationStatus.PENDING_PAYMENT, BookingEventType.OPERATOR_CONFIRMED, BookingActorType.OPERATOR,
                    actorUserId, notes);
            Instant paymentExpiresAt = now.plus(Duration.ofMinutes(appProperties.getQuoteTtlMinutes()));
            reservation.setPaymentExpiresAt(paymentExpiresAt);
            refreshHoldExpiry(reservation, paymentExpiresAt);
        }

        auditReservationAction(actorUserId, "OPERATOR_CONFIRMED_BOOKING", reservation, metadata(
                "notes", notes,
                "paymentMode", reservation.getPaymentMode().name()));
        return toDto(reservation);
    }

    @Transactional(noRollbackFor = ConflictException.class)
    public ReservationDtos.ReservationDto rejectAsOperator(UUID reservationId, String reason) {
        Instant now = Instant.now(clock);
        Reservation reservation = reservations.findByIdForUpdate(reservationId)
                .orElseThrow(() -> new NotFoundException("Reservation was not found."));
        requireOperatorReservation(reservation);
        expireReservationIfHoldOverdue(reservation, now);
        requirePendingOperatorConfirmation(reservation);
        UUID actorUserId = currentUser.userId();
        applyTransition(reservation, ReservationStatus.REJECTED, BookingEventType.OPERATOR_REJECTED, BookingActorType.OPERATOR,
                actorUserId, reason);
        releaseHold(reservation);
        cancelPendingAttempts(reservation.getId());
        auditReservationAction(actorUserId, "OPERATOR_REJECTED_BOOKING", reservation, metadata("reason", reason));
        return toDto(reservation);
    }

    @Transactional
    public void confirmAfterPayment(UUID reservationId, UUID actorUserId, String provider, String providerReference) {
        Reservation reservation = reservations.findByIdForUpdate(reservationId)
                .orElseThrow(() -> new NotFoundException("Reservation was not found."));
        if (reservation.getStatus() == ReservationStatus.CONFIRMED || reservation.getStatus() == ReservationStatus.ACTIVE) {
            return;
        }
        applyTransition(reservation, ReservationStatus.CONFIRMED, BookingEventType.PAYMENT_AUTHORIZED, BookingActorType.PAYMENT_PROVIDER,
                actorUserId, provider);
        consumeHold(reservation);
        reservation.setPaymentExpiresAt(null);
        auditReservationAction(actorUserId, "PAYMENT_AUTHORIZED", reservation, metadata(
                "provider", provider,
                "providerReference", providerReference));
    }

    @Transactional
    public void recordPaymentFailure(UUID reservationId, UUID actorUserId, String provider, String message) {
        Reservation reservation = reservations.findByIdForUpdate(reservationId)
                .orElseThrow(() -> new NotFoundException("Reservation was not found."));
        recordEvent(reservation.getId(), BookingEventType.PAYMENT_FAILED, BookingActorType.PAYMENT_PROVIDER, actorUserId, message,
                metadata("provider", provider));
        auditReservationAction(actorUserId, "PAYMENT_FAILED", reservation, metadata("provider", provider, "message", message));
    }

    @Transactional(readOnly = true)
    public ApiPage<ReservationDtos.ReservationDto> operatorUpcoming(int page, int size) {
        OperatorAccount operator = currentOperator();
        Instant now = Instant.now(clock);
        return ApiPage.from(reservations.findByOperatorIdAndStatusInAndStartsAtGreaterThanEqualOrderByStartsAtAsc(
                        operator.getId(),
                        UPCOMING_OPERATOR_STATUSES,
                        now,
                        PageRequest.of(page, Math.min(size, 100)))
                .map(this::toDto));
    }

    @Transactional(readOnly = true)
    public ReservationDtos.BookingDetailDto operatorDetail(UUID reservationId) {
        return toDetailDto(requireOperatorReservation(reservationId));
    }

    @Transactional
    public ReservationDtos.ReservationDto cancelAsOperator(UUID reservationId, String reason) {
        Reservation reservation = reservations.findByIdForUpdate(reservationId)
                .orElseThrow(() -> new NotFoundException("Reservation was not found."));
        requireOperatorReservation(reservation);
        UUID actorUserId = currentUser.userId();
        applyTransition(reservation, ReservationStatus.CANCELLED, BookingEventType.OPERATOR_CANCELLED, BookingActorType.OPERATOR,
                actorUserId, reason);
        releaseHold(reservation);
        cancelPendingAttempts(reservation.getId());
        auditReservationAction(actorUserId, "OPERATOR_CANCELLED_BOOKING", reservation, metadata("reason", reason));
        return toDto(reservation);
    }

    @Transactional
    public ReservationDtos.ReservationDto checkIn(UUID reservationId, String notes) {
        Reservation reservation = reservations.findByIdForUpdate(reservationId)
                .orElseThrow(() -> new NotFoundException("Reservation was not found."));
        requireOperatorReservation(reservation);
        UUID actorUserId = currentUser.userId();
        applyTransition(reservation, ReservationStatus.ACTIVE, BookingEventType.CHECKED_IN, BookingActorType.OPERATOR, actorUserId, notes);
        Checkin checkin = checkins.findByReservationId(reservation.getId()).orElseGet(Checkin::new);
        checkin.setReservationId(reservation.getId());
        checkin.setOperatorUserId(actorUserId);
        checkin.setStatus(CheckinStatus.CHECKED_IN);
        checkin.setCheckinAt(Instant.now(clock));
        checkin.setNotes(notes);
        checkins.save(checkin);
        auditReservationAction(actorUserId, "BOOKING_CHECKED_IN", reservation, metadata("notes", notes));
        return toDto(reservation);
    }

    @Transactional
    public ReservationDtos.ReservationDto markNoShow(UUID reservationId, String reason) {
        Reservation reservation = reservations.findByIdForUpdate(reservationId)
                .orElseThrow(() -> new NotFoundException("Reservation was not found."));
        requireOperatorReservation(reservation);
        UUID actorUserId = currentUser.userId();
        applyTransition(reservation, ReservationStatus.NO_SHOW, BookingEventType.NO_SHOW, BookingActorType.OPERATOR, actorUserId, reason);
        Checkin checkin = checkins.findByReservationId(reservation.getId()).orElseGet(Checkin::new);
        checkin.setReservationId(reservation.getId());
        checkin.setOperatorUserId(actorUserId);
        checkin.setStatus(CheckinStatus.NO_SHOW);
        checkin.setCheckinAt(Instant.now(clock));
        checkin.setNotes(reason);
        checkins.save(checkin);
        auditReservationAction(actorUserId, "BOOKING_MARKED_NO_SHOW", reservation, metadata("reason", reason));
        return toDto(reservation);
    }

    @Transactional(readOnly = true)
    public ApiPage<ReservationDtos.ReservationDto> adminSearch(
            String query,
            UUID operatorId,
            UUID locationId,
            ReservationStatus status,
            int page,
            int size) {
        PageRequest pageRequest = PageRequest.of(page, Math.min(size, 100));
        if (query != null && !query.isBlank()) {
            Optional<UUID> reservationId = parseUuid(query);
            if (reservationId.isPresent()) {
                return reservations.findById(reservationId.get())
                        .filter(reservation -> matchesFilters(reservation, operatorId, locationId, status))
                        .map(this::toDto)
                        .map(dto -> ApiPage.from(new PageImpl<>(List.of(dto), pageRequest, 1)))
                        .orElseGet(() -> ApiPage.from(new PageImpl<>(List.of(), pageRequest, 0)));
            }
            return reservations.findByBookingCodeIgnoreCase(query.trim())
                    .filter(reservation -> matchesFilters(reservation, operatorId, locationId, status))
                    .map(this::toDto)
                    .map(dto -> ApiPage.from(new PageImpl<>(List.of(dto), pageRequest, 1)))
                    .orElseGet(() -> ApiPage.from(new PageImpl<>(List.of(), pageRequest, 0)));
        }
        return ApiPage.from(reservations.adminSearch(operatorId, locationId, status, pageRequest).map(this::toDto));
    }

    @Transactional(readOnly = true)
    public ReservationDtos.BookingDetailDto adminDetail(UUID reservationId) {
        Reservation reservation = reservations.findById(reservationId)
                .orElseThrow(() -> new NotFoundException("Reservation was not found."));
        return toDetailDto(reservation);
    }

    @Transactional
    public ReservationDtos.ReservationDto cancelAsAdmin(UUID reservationId, String reason) {
        Reservation reservation = reservations.findByIdForUpdate(reservationId)
                .orElseThrow(() -> new NotFoundException("Reservation was not found."));
        UUID actorUserId = currentUser.userId();
        applyTransition(reservation, ReservationStatus.CANCELLED, BookingEventType.ADMIN_OVERRIDE, BookingActorType.ADMIN, actorUserId, reason);
        releaseHold(reservation);
        cancelPendingAttempts(reservation.getId());
        auditReservationAction(actorUserId, "ADMIN_CANCELLED_BOOKING", reservation, metadata("reason", reason));
        return toDto(reservation);
    }

    @Transactional(noRollbackFor = ConflictException.class)
    public ReservationDtos.ReservationDto confirmAsAdmin(UUID reservationId, String notes) {
        Instant now = Instant.now(clock);
        Reservation reservation = reservations.findByIdForUpdate(reservationId)
                .orElseThrow(() -> new NotFoundException("Reservation was not found."));
        expireReservationIfHoldOverdue(reservation, now);
        requirePendingOperatorConfirmation(reservation);
        UUID actorUserId = currentUser.userId();

        if (reservation.getPaymentMode() == PaymentMode.PAY_ON_ARRIVAL) {
            applyTransition(reservation, ReservationStatus.CONFIRMED, BookingEventType.OPERATOR_CONFIRMED, BookingActorType.ADMIN,
                    actorUserId, notes);
            consumeHold(reservation);
            createPayOnArrivalAttempt(reservation, now);
        } else {
            paymentAuthority.requireOnlinePaymentsEnabled();
            applyTransition(reservation, ReservationStatus.PENDING_PAYMENT, BookingEventType.OPERATOR_CONFIRMED, BookingActorType.ADMIN,
                    actorUserId, notes);
            Instant paymentExpiresAt = now.plus(Duration.ofMinutes(appProperties.getQuoteTtlMinutes()));
            reservation.setPaymentExpiresAt(paymentExpiresAt);
            refreshHoldExpiry(reservation, paymentExpiresAt);
        }

        auditReservationAction(actorUserId, "ADMIN_CONFIRMED_BOOKING", reservation, metadata(
                "notes", notes,
                "paymentMode", reservation.getPaymentMode().name()));
        return toDto(reservation);
    }

    @Transactional(noRollbackFor = ConflictException.class)
    public ReservationDtos.ReservationDto rejectAsAdmin(UUID reservationId, String reason) {
        Instant now = Instant.now(clock);
        Reservation reservation = reservations.findByIdForUpdate(reservationId)
                .orElseThrow(() -> new NotFoundException("Reservation was not found."));
        expireReservationIfHoldOverdue(reservation, now);
        requirePendingOperatorConfirmation(reservation);
        UUID actorUserId = currentUser.userId();

        applyTransition(reservation, ReservationStatus.REJECTED, BookingEventType.OPERATOR_REJECTED, BookingActorType.ADMIN,
                actorUserId, reason);
        releaseHold(reservation);
        cancelPendingAttempts(reservation.getId());
        auditReservationAction(actorUserId, "ADMIN_REJECTED_BOOKING", reservation, metadata("reason", reason));
        return toDto(reservation);
    }

    @Transactional
    public PaymentDtos.RefundDto markRefundAsAdmin(UUID reservationId, Long amountCents, String reason) {
        Reservation reservation = reservations.findByIdForUpdate(reservationId)
                .orElseThrow(() -> new NotFoundException("Reservation was not found."));
        PaymentAttempt attempt = paymentAttempts.findByReservationIdOrderByCreatedAtDesc(reservationId).stream()
                .findFirst()
                .orElse(null);
        UUID actorUserId = currentUser.userId();

        Refund refund = new Refund();
        refund.setReservationId(reservationId);
        refund.setPaymentAttemptId(attempt == null ? null : attempt.getId());
        refund.setAmountCents(amountCents == null ? reservation.getTotalAmountCents() : amountCents);
        refund.setCurrency(reservation.getCurrency());
        refund.setReason(reason);
        refund.setMarkedByUserId(actorUserId);
        refund.setMarkedAt(Instant.now(clock));
        Refund saved = refunds.save(refund);

        if (attempt != null) {
            attempt.setStatus(PaymentAttemptStatus.REFUND_MARKED);
            attempt.setLastTransitionAt(Instant.now(clock));
            recordPaymentProviderEvent(
                    attempt,
                    "refund:" + saved.getId() + ":marked",
                    "REFUND_MARKED",
                    metadata("refundId", saved.getId(), "amountCents", saved.getAmountCents(), "reason", reason));
        }
        recordEvent(reservationId, BookingEventType.REFUND_MARKED, BookingActorType.ADMIN, actorUserId, reason,
                metadata("refundId", saved.getId(), "amountCents", saved.getAmountCents()));
        auditReservationAction(actorUserId, "ADMIN_MARKED_REFUND", reservation, metadata(
                "refundId", saved.getId(),
                "amountCents", saved.getAmountCents(),
                "reason", reason));
        return toRefundDto(saved);
    }

    public ReservationDtos.ReservationDto toDto(Reservation reservation) {
        Instant now = Instant.now(clock);
        return new ReservationDtos.ReservationDto(
                reservation.getId(),
                reservation.getCustomerId(),
                reservation.getOperatorId(),
                reservation.getLocationId(),
                reservation.getResourceId(),
                reservation.getInventoryPoolId(),
                reservation.getHoldId(),
                reservation.getVehicleId(),
                reservation.getStartsAt(),
                reservation.getEndsAt(),
                reservation.getTimezone(),
                reservation.getBookingCode(),
                reservation.getCancellationPolicy(),
                reservation.getCancellableUntil(),
                displayedRefundEligibleCents(reservation, now),
                reservation.getStatus(),
                reservation.getPaymentMode(),
                reservation.getTotalAmountCents(),
                reservation.getCurrency(),
                reservation.isAccessInstructionsVisible(),
                reservation.getPaymentExpiresAt(),
                reservation.getOperatorConfirmationExpiresAt(),
                reservation.getCreatedAt(),
                reservation.getUpdatedAt());
    }

    private ReservationDtos.ReservationQuote buildQuote(
            UUID resourceId,
            InventoryPool pool,
            Instant startsAt,
            Instant endsAt,
            String promoCode,
            Instant now) {
        long subtotal = calculateSubtotal(pool, startsAt, endsAt);
        long fees = Math.max(50, Math.round(subtotal * 0.08));
        long discount = promoCode == null || promoCode.isBlank() ? 0 : Math.round(subtotal * 0.05);
        return new ReservationDtos.ReservationQuote(
                resourceId,
                startsAt,
                endsAt,
                subtotal,
                fees,
                discount,
                subtotal + fees - discount,
                pool.getCurrency(),
                now.plus(Duration.ofMinutes(appProperties.getQuoteTtlMinutes())));
    }

    private void validateVehicleFit(InventoryPool pool, UUID vehicleId) {
        if (vehicleId == null) {
            return;
        }
        VehicleProfile vehicle = vehicleService.requireOwnedEntity(vehicleId, currentUser.userId());
        if (!pool.allowsVehicleType(vehicle.getType())) {
            throw new ConflictException("VEHICLE_NOT_COMPATIBLE", "Vehicle type is not compatible with this parking inventory.");
        }
        if (pool.isEvOnly() && !vehicle.isEvCapable()) {
            throw new ConflictException("VEHICLE_NOT_COMPATIBLE", "This inventory requires an EV-capable vehicle.");
        }
        if (pool.getMaxHeightMeters() != null && vehicle.getHeightMeters() != null
                && vehicle.getHeightMeters().compareTo(pool.getMaxHeightMeters()) > 0) {
            throw new ConflictException("VEHICLE_NOT_COMPATIBLE", "Vehicle height exceeds this inventory limit.");
        }
        if (pool.getMaxLengthMeters() != null && vehicle.getLengthMeters() != null
                && vehicle.getLengthMeters().compareTo(pool.getMaxLengthMeters()) > 0) {
            throw new ConflictException("VEHICLE_NOT_COMPATIBLE", "Vehicle length exceeds this inventory limit.");
        }
    }

    private long calculateSubtotal(InventoryPool pool, Instant startsAt, Instant endsAt) {
        long minutes = Duration.between(startsAt, endsAt).toMinutes();
        long hours = Math.max(1, (long) Math.ceil(minutes / 60.0));
        if (pool.getDailyRateCents() != null && hours >= 8) {
            long days = (long) Math.ceil(hours / 24.0);
            return days * pool.getDailyRateCents();
        }
        return hours * pool.getHourlyRateCents();
    }

    private PaymentMode resolvePaymentMode(InventoryPool pool, PaymentMode requestedMode) {
        PaymentMode paymentMode = requestedMode == null
                ? (pool.isPayOnArrivalEnabled() ? PaymentMode.PAY_ON_ARRIVAL : PaymentMode.ONLINE)
                : requestedMode;
        if (paymentMode == PaymentMode.PAY_ON_ARRIVAL && !pool.isPayOnArrivalEnabled()) {
            throw new ConflictException("PAY_ON_ARRIVAL_NOT_AVAILABLE", "Pay on arrival is not available for this inventory.");
        }
        if (paymentMode == PaymentMode.ONLINE) {
            paymentAuthority.requireOnlinePaymentsEnabled();
        }
        return paymentMode;
    }

    private ReservationStatus initialStatus(PaymentMode paymentMode, ConfirmationMode confirmationMode) {
        if (confirmationMode == ConfirmationMode.MANUAL) {
            return ReservationStatus.PENDING_OPERATOR_CONFIRMATION;
        }
        return paymentMode == PaymentMode.PAY_ON_ARRIVAL ? ReservationStatus.CONFIRMED : ReservationStatus.PENDING_PAYMENT;
    }

    private Instant holdExpiresAt(ReservationStatus status, Instant quoteExpiresAt, Instant now) {
        if (status == ReservationStatus.PENDING_OPERATOR_CONFIRMATION) {
            return now.plus(Duration.ofHours(appProperties.getManualConfirmationTtlHours()));
        }
        return quoteExpiresAt;
    }

    private int expireOverdueHolds(Instant now) {
        List<BookingHold> expiredHolds = bookingHolds.findByStatusAndExpiresAtLessThanEqual(BookingHoldStatus.ACTIVE, now);
        if (expiredHolds.isEmpty()) {
            return 0;
        }
        for (BookingHold hold : expiredHolds) {
            hold.setStatus(BookingHoldStatus.EXPIRED);
            if (hold.getReservationId() != null) {
                reservations.findByIdForUpdate(hold.getReservationId()).ifPresent(reservation -> {
                    if (reservation.getStatus() == ReservationStatus.PENDING_PAYMENT
                            || reservation.getStatus() == ReservationStatus.PENDING_OPERATOR_CONFIRMATION) {
                        applyTransition(reservation, ReservationStatus.EXPIRED, BookingEventType.HOLD_EXPIRED, BookingActorType.SYSTEM, null,
                                "Booking hold expired");
                        cancelPendingAttempts(reservation.getId());
                    }
                });
            }
        }
        return expiredHolds.size();
    }

    private void expireReservationIfHoldOverdue(Reservation reservation, Instant now) {
        if (reservation.getHoldId() == null
                || (reservation.getStatus() != ReservationStatus.PENDING_PAYMENT
                && reservation.getStatus() != ReservationStatus.PENDING_OPERATOR_CONFIRMATION)) {
            return;
        }

        bookingHolds.findById(reservation.getHoldId()).ifPresent(hold -> {
            if (hold.getStatus() == BookingHoldStatus.ACTIVE && !hold.getExpiresAt().isAfter(now)) {
                hold.setStatus(BookingHoldStatus.EXPIRED);
                applyTransition(reservation, ReservationStatus.EXPIRED, BookingEventType.HOLD_EXPIRED, BookingActorType.SYSTEM, null,
                        "Booking hold expired");
                cancelPendingAttempts(reservation.getId());
            }
        });
    }

    private void createPayOnArrivalAttempt(Reservation reservation, Instant now) {
        PaymentAttempt attempt = new PaymentAttempt();
        attempt.setReservationId(reservation.getId());
        attempt.setCustomerId(reservation.getCustomerId());
        attempt.setProvider(PaymentMode.PAY_ON_ARRIVAL.name());
        attempt.setStatus(PaymentAttemptStatus.PENDING);
        attempt.setPaymentMode(PaymentMode.PAY_ON_ARRIVAL);
        attempt.setAmountCents(reservation.getTotalAmountCents());
        attempt.setCurrency(reservation.getCurrency());
        attempt.setProviderReference("poa_" + reservation.getId());
        attempt.setIdempotencyKey(reservation.getIdempotencyKey());
        attempt.setLastTransitionAt(now);
        paymentAttempts.save(attempt);
    }

    private void applyTransition(
            Reservation reservation,
            ReservationStatus target,
            BookingEventType eventType,
            BookingActorType actorType,
            UUID actorId,
            String notes) {
        ReservationStatus from = reservation.getStatus();
        stateMachine.assertTransitionAllowed(from, target);
        reservation.setStatus(target);
        if (target == ReservationStatus.CONFIRMED || target == ReservationStatus.ACTIVE) {
            reservation.setAccessInstructionsVisible(true);
        }
        if (target == ReservationStatus.CANCELLED
                || target == ReservationStatus.EXPIRED
                || target == ReservationStatus.NO_SHOW
                || target == ReservationStatus.REJECTED) {
            reservation.setAccessInstructionsVisible(false);
        }
        if (target == ReservationStatus.CONFIRMED
                || target == ReservationStatus.CANCELLED
                || target == ReservationStatus.EXPIRED
                || target == ReservationStatus.REJECTED) {
            reservation.setPaymentExpiresAt(null);
        }
        if (target != ReservationStatus.PENDING_OPERATOR_CONFIRMATION) {
            reservation.setOperatorConfirmationExpiresAt(null);
        }
        recordEvent(reservation.getId(), eventType, actorType, actorId, notes, metadata(
                "from", from.name(),
                "to", target.name()));
    }

    private void releaseHold(Reservation reservation) {
        if (reservation.getHoldId() == null) {
            return;
        }
        bookingHolds.findById(reservation.getHoldId()).ifPresent(hold -> {
            if (hold.getStatus() == BookingHoldStatus.ACTIVE) {
                hold.setStatus(BookingHoldStatus.RELEASED);
            }
        });
    }

    private void consumeHold(Reservation reservation) {
        if (reservation.getHoldId() == null) {
            return;
        }
        bookingHolds.findById(reservation.getHoldId()).ifPresent(hold -> {
            if (hold.getStatus() == BookingHoldStatus.ACTIVE) {
                hold.setStatus(BookingHoldStatus.CONSUMED);
            }
        });
    }

    private void refreshHoldExpiry(Reservation reservation, Instant expiresAt) {
        if (reservation.getHoldId() == null) {
            return;
        }
        bookingHolds.findById(reservation.getHoldId()).ifPresent(hold -> {
            if (hold.getStatus() == BookingHoldStatus.ACTIVE) {
                hold.setExpiresAt(expiresAt);
            }
        });
    }

    private void cancelPendingAttempts(UUID reservationId) {
        for (PaymentAttempt attempt : paymentAttempts.findByReservationIdOrderByCreatedAtDesc(reservationId)) {
            if (attempt.getStatus() == PaymentAttemptStatus.PENDING || attempt.getStatus() == PaymentAttemptStatus.REQUIRES_ACTION) {
                attempt.setStatus(PaymentAttemptStatus.CANCELLED);
                attempt.setLastTransitionAt(Instant.now(clock));
                recordPaymentProviderEvent(
                        attempt,
                        attempt.getId() + ":LOCAL_CANCELLED",
                        "LOCAL_CANCELLED",
                        metadata("reservationId", reservationId));
            }
        }
    }

    private void recordPaymentProviderEvent(
            PaymentAttempt attempt,
            String externalEventId,
            String eventType,
            Map<String, Object> payload) {
        if (paymentProviderEvents.existsByProviderAndExternalEventId(attempt.getProvider(), externalEventId)) {
            return;
        }
        PaymentProviderEvent event = new PaymentProviderEvent();
        event.setPaymentAttemptId(attempt.getId());
        event.setProvider(attempt.getProvider());
        event.setExternalEventId(externalEventId);
        event.setEventType(eventType);
        event.setPayload(serializePayload(payload));
        event.setStatus(PaymentProviderEventStatus.PROCESSED);
        event.setProcessedAt(Instant.now(clock));
        paymentProviderEvents.save(event);
    }

    private void recordEvent(
            UUID reservationId,
            BookingEventType eventType,
            BookingActorType actorType,
            UUID actorId,
            String notes,
            Map<String, Object> payload) {
        BookingEvent event = new BookingEvent();
        event.setReservationId(reservationId);
        event.setEventType(eventType);
        event.setActorType(actorType);
        event.setActorId(actorId);
        event.setNotes(notes);
        event.setPayload(serializePayload(payload));
        event.setOccurredAt(Instant.now(clock));
        bookingEvents.save(event);
    }

    private void auditReservationAction(UUID actorUserId, String action, Reservation reservation, Map<String, Object> metadata) {
        if (actorUserId == null) {
            return;
        }
        auditService.record(actorUserId, action, "reservation", reservation.getId().toString(), serializePayload(metadata));
    }

    private ReservationDtos.BookingDetailDto toDetailDto(Reservation reservation) {
        ReservationDtos.BookingHoldDto holdDto = reservation.getHoldId() == null
                ? null
                : bookingHolds.findById(reservation.getHoldId()).map(this::toHoldDto).orElse(null);
        ReservationDtos.CheckinDto checkinDto = checkins.findByReservationId(reservation.getId()).map(this::toCheckinDto).orElse(null);
        List<ReservationDtos.BookingEventDto> timeline = bookingEvents.findByReservationIdOrderByOccurredAtAsc(reservation.getId())
                .stream()
                .map(this::toEventDto)
                .toList();
        List<PaymentDtos.PaymentAttemptDto> attempts = paymentAttempts.findByReservationIdOrderByCreatedAtDesc(reservation.getId())
                .stream()
                .map(this::toPaymentAttemptDto)
                .toList();
        List<PaymentDtos.RefundDto> refundDtos = refunds.findByReservationIdOrderByMarkedAtDesc(reservation.getId())
                .stream()
                .map(this::toRefundDto)
                .toList();
        List<SupportDtos.SupportTicketDto> supportCaseDtos = supportTickets.findByReservationIdOrderByUpdatedAtDesc(reservation.getId())
                .stream()
                .map(this::toSupportTicketDto)
                .toList();
        return new ReservationDtos.BookingDetailDto(toDto(reservation), holdDto, checkinDto, timeline, attempts, refundDtos, supportCaseDtos);
    }

    private ReservationDtos.BookingHoldDto toHoldDto(BookingHold hold) {
        return new ReservationDtos.BookingHoldDto(
                hold.getId(),
                hold.getInventoryPoolId(),
                hold.getStatus(),
                hold.getExpiresAt(),
                hold.getPaymentMode());
    }

    private ReservationDtos.CheckinDto toCheckinDto(Checkin checkin) {
        return new ReservationDtos.CheckinDto(
                checkin.getId(),
                checkin.getStatus(),
                checkin.getOperatorUserId(),
                checkin.getCheckinAt(),
                checkin.getCheckoutAt(),
                checkin.getNotes());
    }

    private ReservationDtos.BookingEventDto toEventDto(BookingEvent event) {
        return new ReservationDtos.BookingEventDto(
                event.getId(),
                event.getEventType(),
                event.getActorType(),
                event.getActorId(),
                event.getNotes(),
                parsePayload(event.getPayload()),
                event.getOccurredAt());
    }

    private PaymentDtos.PaymentAttemptDto toPaymentAttemptDto(PaymentAttempt attempt) {
        List<PaymentDtos.PaymentProviderEventDto> events = paymentProviderEvents.findByPaymentAttemptIdOrderByCreatedAtDesc(attempt.getId())
                .stream()
                .map(this::toPaymentProviderEventDto)
                .toList();
        return new PaymentDtos.PaymentAttemptDto(
                attempt.getId(),
                attempt.getReservationId(),
                attempt.getProvider(),
                attempt.getStatus(),
                attempt.getPaymentMode(),
                attempt.getAmountCents(),
                attempt.getCurrency(),
                attempt.getProviderReference(),
                attempt.getFailureCode(),
                attempt.getFailureMessage(),
                attempt.getLastTransitionAt(),
                events);
    }

    private PaymentDtos.PaymentProviderEventDto toPaymentProviderEventDto(PaymentProviderEvent event) {
        return new PaymentDtos.PaymentProviderEventDto(
                event.getId(),
                event.getProvider(),
                event.getExternalEventId(),
                event.getEventType(),
                event.getStatus(),
                event.getProcessedAt());
    }

    private PaymentDtos.RefundDto toRefundDto(Refund refund) {
        return new PaymentDtos.RefundDto(
                refund.getId(),
                refund.getReservationId(),
                refund.getPaymentAttemptId(),
                refund.getAmountCents(),
                refund.getCurrency(),
                refund.getStatus(),
                refund.getReason(),
                refund.getProviderReference(),
                refund.getMarkedByUserId(),
                refund.getMarkedAt());
    }

    private SupportDtos.SupportTicketDto toSupportTicketDto(SupportTicket ticket) {
        return new SupportDtos.SupportTicketDto(
                ticket.getId(),
                ticket.getCategory(),
                ticket.getStatus(),
                ticket.getSubject(),
                ticket.getReservationId(),
                ticket.getLocationId(),
                ticket.getCreatedAt(),
                ticket.getUpdatedAt());
    }

    private void requireParticipant(Reservation reservation) {
        UUID userId = currentUser.userId();
        if (reservation.getCustomerId().equals(userId)) {
            return;
        }
        throw new AccessDeniedException("Reservation does not belong to the current user.");
    }

    private OperatorAccount currentOperator() {
        return operators.findByUserId(currentUser.userId())
                .orElseThrow(() -> new AccessDeniedException("Operator account is required."));
    }

    private Reservation requireOperatorReservation(UUID reservationId) {
        Reservation reservation = reservations.findById(reservationId)
                .orElseThrow(() -> new NotFoundException("Reservation was not found."));
        requireOperatorReservation(reservation);
        return reservation;
    }

    private void requireOperatorReservation(Reservation reservation) {
        if (!reservation.getOperatorId().equals(currentOperator().getId())) {
            throw new AccessDeniedException("Reservation does not belong to the current operator.");
        }
    }

    private void requirePendingOperatorConfirmation(Reservation reservation) {
        if (reservation.getStatus() != ReservationStatus.PENDING_OPERATOR_CONFIRMATION) {
            throw new ConflictException(
                    "INVALID_RESERVATION_TRANSITION",
                    "Reservation is not waiting for operator confirmation.");
        }
    }

    private boolean canCustomerCancel(Reservation reservation, Instant now) {
        if (!now.isBefore(reservation.getStartsAt())) {
            return false;
        }
        return !isTerminalStatus(reservation.getStatus());
    }

    private boolean isTerminalStatus(ReservationStatus status) {
        return status == ReservationStatus.CANCELLED
                || status == ReservationStatus.COMPLETED
                || status == ReservationStatus.EXPIRED
                || status == ReservationStatus.REJECTED
                || status == ReservationStatus.NO_SHOW;
    }

    private long refundEligibleCentsAtCancellation(Reservation reservation, Instant now) {
        return now.isBefore(reservation.getStartsAt()) ? reservation.getTotalAmountCents() : 0;
    }

    private long displayedRefundEligibleCents(Reservation reservation, Instant now) {
        if (reservation.getRefundEligibleCents() > 0) {
            return reservation.getRefundEligibleCents();
        }
        return canCustomerCancel(reservation, now) ? reservation.getTotalAmountCents() : 0;
    }

    private String generateBookingCode() {
        for (int attempt = 0; attempt < 5; attempt++) {
            String code = "SL-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
            if (!reservations.existsByBookingCode(code)) {
                return code;
            }
        }
        throw new ConflictException("BOOKING_CODE_GENERATION_FAILED", "Could not allocate a booking code.");
    }

    private boolean matchesFilters(Reservation reservation, UUID operatorId, UUID locationId, ReservationStatus status) {
        if (operatorId != null && !operatorId.equals(reservation.getOperatorId())) {
            return false;
        }
        if (locationId != null && !locationId.equals(reservation.getLocationId())) {
            return false;
        }
        return status == null || status == reservation.getStatus();
    }

    private Optional<UUID> parseUuid(String value) {
        try {
            return Optional.of(UUID.fromString(value));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    private ReservationDtos.ReservationDto readCachedReservation(IdempotencyRecord record) {
        try {
            return objectMapper.readValue(record.getResponseBody(), ReservationDtos.ReservationDto.class);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Could not read cached idempotency response.", ex);
        }
    }

    private String serializePayload(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Could not serialize booking payload.", ex);
        }
    }

    private Map<String, Object> parsePayload(String payload) {
        if (payload == null || payload.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(payload, MAP_TYPE);
        } catch (JsonProcessingException ex) {
            return Map.of("raw", payload);
        }
    }

    private Map<String, Object> metadata(Object... entries) {
        Map<String, Object> payload = new LinkedHashMap<>();
        for (int index = 0; index + 1 < entries.length; index += 2) {
            if (entries[index + 1] != null) {
                payload.put((String) entries[index], entries[index + 1]);
            }
        }
        return payload;
    }
}
