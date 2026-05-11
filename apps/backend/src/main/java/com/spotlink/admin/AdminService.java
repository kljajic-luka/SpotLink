package com.spotlink.admin;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spotlink.core.ApiPage;
import com.spotlink.core.AppProperties;
import com.spotlink.inventory.AvailabilityOverrideSource;
import com.spotlink.inventory.InventoryPool;
import com.spotlink.inventory.InventoryPoolService;
import com.spotlink.operator.OperatorAccountRepository;
import com.spotlink.payment.PaymentAttempt;
import com.spotlink.payment.PaymentAttemptRepository;
import com.spotlink.payment.PaymentDtos;
import com.spotlink.payment.PaymentProviderEvent;
import com.spotlink.payment.PaymentProviderEventRepository;
import com.spotlink.payment.PaymentIntentRepository;
import com.spotlink.payment.PaymentStatus;
import com.spotlink.reservation.ReservationDtos;
import com.spotlink.reservation.ReservationService;
import com.spotlink.reservation.ReservationRepository;
import com.spotlink.reservation.ReservationStatus;
import com.spotlink.security.CurrentUserService;
import com.spotlink.support.SupportDtos;
import com.spotlink.support.SupportTicketRepository;
import com.spotlink.support.SupportTicketStatus;
import com.spotlink.user.User;
import com.spotlink.user.UserRepository;
import com.spotlink.location.ParkingLocation;
import com.spotlink.location.ParkingLocationRepository;
import java.util.Collection;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminService {

    private static final List<ReservationStatus> ACTIVE_RESERVATION_STATUSES = List.of(
            ReservationStatus.PENDING_OPERATOR_CONFIRMATION,
            ReservationStatus.PENDING_PAYMENT,
            ReservationStatus.PENDING_OPERATOR_CONFIRMATION,
            ReservationStatus.CONFIRMED,
            ReservationStatus.ACTIVE,
            ReservationStatus.DISPUTED);

    private final UserRepository users;
    private final OperatorAccountRepository operators;
    private final ParkingLocationRepository locations;
    private final ReservationRepository reservations;
    private final SupportTicketRepository supportTickets;
    private final PaymentIntentRepository payments;
    private final PaymentAttemptRepository paymentAttempts;
    private final PaymentProviderEventRepository paymentProviderEvents;
    private final AuditLogRepository auditLogs;
    private final ReservationService reservationService;
    private final InventoryPoolService inventoryPools;
    private final CurrentUserService currentUser;
    private final AppProperties appProperties;
    private final ObjectMapper objectMapper;

    public AdminService(
            UserRepository users,
            OperatorAccountRepository operators,
            ParkingLocationRepository locations,
            ReservationRepository reservations,
            SupportTicketRepository supportTickets,
            PaymentIntentRepository payments,
            PaymentAttemptRepository paymentAttempts,
            PaymentProviderEventRepository paymentProviderEvents,
            AuditLogRepository auditLogs,
            ReservationService reservationService,
            InventoryPoolService inventoryPools,
            CurrentUserService currentUser,
            AppProperties appProperties,
            ObjectMapper objectMapper) {
        this.users = users;
        this.operators = operators;
        this.locations = locations;
        this.reservations = reservations;
        this.supportTickets = supportTickets;
        this.payments = payments;
        this.paymentAttempts = paymentAttempts;
        this.paymentProviderEvents = paymentProviderEvents;
        this.auditLogs = auditLogs;
        this.reservationService = reservationService;
        this.inventoryPools = inventoryPools;
        this.currentUser = currentUser;
        this.appProperties = appProperties;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public AdminDtos.AdminDashboardSummary summary() {
        return new AdminDtos.AdminDashboardSummary(
                users.count(),
                operators.countByActiveTrue(),
                reservations.countByStatusIn(ACTIVE_RESERVATION_STATUSES),
                supportTickets.countByStatusIn(List.of(
                        SupportTicketStatus.OPEN,
                        SupportTicketStatus.WAITING_ON_CUSTOMER,
                        SupportTicketStatus.WAITING_ON_OPERATOR)),
                payments.grossVolumeForStatuses(List.of(PaymentStatus.AUTHORIZED, PaymentStatus.CAPTURED)),
                appProperties.getDefaultCurrency());
    }

    @Transactional(readOnly = true)
    public ApiPage<AdminDtos.AdminUserSummary> users(int page, int size) {
        return ApiPage.from(users.findAll(PageRequest.of(page, Math.min(size, 100))).map(this::toUserSummary));
    }

    @Transactional(readOnly = true)
    public ApiPage<AdminDtos.AdminAuditEvent> auditEvents(int page, int size) {
        return ApiPage.from(auditLogs.findAllByOrderByCreatedAtDesc(PageRequest.of(page, Math.min(size, 100))).map(this::toAuditEvent));
    }

    @Transactional(readOnly = true)
    public ApiPage<ReservationDtos.ReservationDto> bookings(String query, UUID operatorId, UUID locationId, ReservationStatus status, int page, int size) {
        return reservationService.adminSearch(query, operatorId, locationId, status, page, size);
    }

    @Transactional(readOnly = true)
    public ReservationDtos.BookingDetailDto bookingDetail(UUID reservationId) {
        return reservationService.adminDetail(reservationId);
    }

    @Transactional
    public ReservationDtos.ReservationDto cancelBooking(UUID reservationId, String reason) {
        return reservationService.cancelAsAdmin(reservationId, reason);
    }

    @Transactional
    public ReservationDtos.ReservationDto confirmManualBooking(UUID reservationId, String notes) {
        return reservationService.confirmManualAsAdmin(reservationId, notes);
    }

    @Transactional
    public ReservationDtos.ReservationDto rejectManualBooking(UUID reservationId, String reason) {
        return reservationService.rejectManualAsAdmin(reservationId, reason);
    }

    @Transactional
    public PaymentDtos.RefundDto markRefund(UUID reservationId, Long amountCents, String reason) {
        return reservationService.markRefundAsAdmin(reservationId, amountCents, reason);
    }

    @Transactional
    public AdminDtos.PauseOperationResult pauseLocation(UUID locationId, String reason) {
        locations.findById(locationId).orElseThrow(() -> new com.spotlink.core.NotFoundException("Parking location was not found."));
        int affectedPools = pausePools(inventoryPools.findByLocationIds(List.of(locationId)), reason, "ADMIN_PAUSED_LOCATION");
        return new AdminDtos.PauseOperationResult(locationId, affectedPools, reason);
    }

    @Transactional
    public AdminDtos.PauseOperationResult pauseOperator(UUID operatorId, String reason) {
        operators.findById(operatorId).orElseThrow(() -> new com.spotlink.core.NotFoundException("Operator account was not found."));
        List<UUID> locationIds = locations.findByOperatorIdAndActiveTrueOrderByName(operatorId).stream().map(ParkingLocation::getId).toList();
        int affectedPools = pausePools(inventoryPools.findByLocationIds(locationIds), reason, "ADMIN_PAUSED_OPERATOR");
        return new AdminDtos.PauseOperationResult(operatorId, affectedPools, reason);
    }

    @Transactional(readOnly = true)
    public ApiPage<PaymentDtos.PaymentAttemptDto> paymentAttempts(UUID reservationId, int page, int size) {
        var pageable = PageRequest.of(page, Math.min(size, 100));
        var result = reservationId == null
                ? paymentAttempts.findAllByOrderByCreatedAtDesc(pageable)
                : paymentAttempts.findByReservationIdOrderByCreatedAtDesc(reservationId, pageable);
        return ApiPage.from(result.map(this::toPaymentAttemptDto));
    }

    @Transactional(readOnly = true)
    public ApiPage<SupportDtos.SupportTicketDto> supportCases(int page, int size) {
        return ApiPage.from(supportTickets.findAllByOrderByUpdatedAtDesc(PageRequest.of(page, Math.min(size, 100))).map(this::toSupportTicketDto));
    }

    private AdminDtos.AdminUserSummary toUserSummary(User user) {
        return new AdminDtos.AdminUserSummary(
                user.getId(),
                user.getEmail(),
                user.getFirstName() + " " + user.getLastName(),
                new ArrayList<>(user.getRoles()),
                user.getRegistrationStatus(),
                user.getCreatedAt());
    }

    private AdminDtos.AdminAuditEvent toAuditEvent(AuditLog event) {
        return new AdminDtos.AdminAuditEvent(
                event.getId(),
                event.getActorUserId(),
                event.getAction(),
                event.getResourceType(),
                event.getResourceId(),
                event.getCreatedAt(),
                parseMetadata(event.getMetadata()));
    }

    private Map<String, Object> parseMetadata(String metadata) {
        if (metadata == null || metadata.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(metadata, new TypeReference<>() {
            });
        } catch (Exception ignored) {
            return Map.of("raw", metadata);
        }
    }

    private int pausePools(Collection<InventoryPool> pools, String reason, String action) {
        int affected = 0;
        UUID actorUserId = currentUser.userId();
        for (InventoryPool pool : pools) {
            inventoryPools.pause(pool, actorUserId, AvailabilityOverrideSource.ADMIN, reason);
            affected++;
            auditLogs.save(buildAuditLog(actorUserId, action, pool.getId().toString(), reason));
        }
        return affected;
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

    private SupportDtos.SupportTicketDto toSupportTicketDto(com.spotlink.support.SupportTicket ticket) {
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

    private AuditLog buildAuditLog(UUID actorUserId, String action, String resourceId, String reason) {
        AuditLog auditLog = new AuditLog();
        auditLog.setActorUserId(actorUserId);
        auditLog.setAction(action);
        auditLog.setResourceType("inventory_pool");
        auditLog.setResourceId(resourceId);
        auditLog.setMetadata(reason);
        auditLog.setOccurredAt(java.time.Instant.now());
        return auditLog;
    }
}
