package com.spotlink.admin;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spotlink.core.ApiPage;
import com.spotlink.core.AppProperties;
import com.spotlink.operator.OperatorAccountRepository;
import com.spotlink.payment.PaymentIntentRepository;
import com.spotlink.payment.PaymentStatus;
import com.spotlink.reservation.ReservationRepository;
import com.spotlink.reservation.ReservationStatus;
import com.spotlink.support.SupportTicketRepository;
import com.spotlink.support.SupportTicketStatus;
import com.spotlink.user.User;
import com.spotlink.user.UserRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminService {

    private static final List<ReservationStatus> ACTIVE_RESERVATION_STATUSES = List.of(
            ReservationStatus.PENDING_PAYMENT,
            ReservationStatus.CONFIRMED,
            ReservationStatus.ACTIVE,
            ReservationStatus.DISPUTED);

    private final UserRepository users;
    private final OperatorAccountRepository operators;
    private final ReservationRepository reservations;
    private final SupportTicketRepository supportTickets;
    private final PaymentIntentRepository payments;
    private final AuditEventRepository auditEvents;
    private final AppProperties appProperties;
    private final ObjectMapper objectMapper;

    public AdminService(
            UserRepository users,
            OperatorAccountRepository operators,
            ReservationRepository reservations,
            SupportTicketRepository supportTickets,
            PaymentIntentRepository payments,
            AuditEventRepository auditEvents,
            AppProperties appProperties,
            ObjectMapper objectMapper) {
        this.users = users;
        this.operators = operators;
        this.reservations = reservations;
        this.supportTickets = supportTickets;
        this.payments = payments;
        this.auditEvents = auditEvents;
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
        return ApiPage.from(auditEvents.findAllByOrderByCreatedAtDesc(PageRequest.of(page, Math.min(size, 100))).map(this::toAuditEvent));
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

    private AdminDtos.AdminAuditEvent toAuditEvent(AuditEvent event) {
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
}
