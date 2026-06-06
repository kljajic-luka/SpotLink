package com.spotlink.user;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spotlink.admin.AuditService;
import com.spotlink.auth.AuthLockoutService;
import com.spotlink.auth.PasswordResetToken;
import com.spotlink.auth.PasswordResetTokenRepository;
import com.spotlink.auth.RefreshTokenService;
import com.spotlink.core.IdempotencyRecordRepository;
import com.spotlink.core.NotFoundException;
import com.spotlink.core.OperationalMetrics;
import com.spotlink.notification.DeviceToken;
import com.spotlink.notification.DeviceTokenRepository;
import com.spotlink.notification.NotificationRepository;
import com.spotlink.operator.OperatorAccountRepository;
import com.spotlink.payment.PaymentAttemptRepository;
import com.spotlink.payment.PaymentAttemptStatus;
import com.spotlink.payment.PaymentIntentRepository;
import com.spotlink.payment.PaymentStatus;
import com.spotlink.reservation.ReservationRepository;
import com.spotlink.reservation.ReservationStatus;
import com.spotlink.security.CurrentUserService;
import com.spotlink.support.SupportMessage;
import com.spotlink.support.SupportMessageRepository;
import com.spotlink.support.SupportService;
import com.spotlink.support.SupportTicket;
import com.spotlink.support.SupportTicketCategory;
import com.spotlink.support.SupportTicketRepository;
import com.spotlink.support.SupportTicketStatus;
import com.spotlink.vehicle.VehicleProfile;
import com.spotlink.vehicle.VehicleRepository;
import com.spotlink.vehicle.VehicleVerificationStatus;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountDeletionFulfillmentService {

    private static final List<ReservationStatus> ACTIVE_OR_FUTURE_RESERVATION_STATUSES = List.of(
            ReservationStatus.DRAFT,
            ReservationStatus.PENDING_PAYMENT,
            ReservationStatus.PENDING_OPERATOR_CONFIRMATION,
            ReservationStatus.CONFIRMED,
            ReservationStatus.ACTIVE);

    private static final List<PaymentStatus> UNRESOLVED_PAYMENT_INTENT_STATUSES = List.of(
            PaymentStatus.REQUIRES_METHOD,
            PaymentStatus.REQUIRES_ACTION,
            PaymentStatus.AUTHORIZED);

    private static final List<PaymentAttemptStatus> UNRESOLVED_PAYMENT_ATTEMPT_STATUSES = List.of(
            PaymentAttemptStatus.PENDING,
            PaymentAttemptStatus.REQUIRES_ACTION,
            PaymentAttemptStatus.AUTHORIZED);

    private final SupportTicketRepository supportTickets;
    private final SupportMessageRepository supportMessages;
    private final UserRepository users;
    private final UserPreferencesRepository preferences;
    private final OperatorAccountRepository operators;
    private final VehicleRepository vehicles;
    private final ReservationRepository reservations;
    private final PaymentIntentRepository paymentIntents;
    private final PaymentAttemptRepository paymentAttempts;
    private final PasswordResetTokenRepository passwordResetTokens;
    private final RefreshTokenService refreshTokens;
    private final AuthLockoutService authLockouts;
    private final DeviceTokenRepository deviceTokens;
    private final NotificationRepository notifications;
    private final IdempotencyRecordRepository idempotencyRecords;
    private final CurrentUserService currentUser;
    private final AuditService auditService;
    private final OperationalMetrics metrics;
    private final PasswordEncoder passwordEncoder;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public AccountDeletionFulfillmentService(
            SupportTicketRepository supportTickets,
            SupportMessageRepository supportMessages,
            UserRepository users,
            UserPreferencesRepository preferences,
            OperatorAccountRepository operators,
            VehicleRepository vehicles,
            ReservationRepository reservations,
            PaymentIntentRepository paymentIntents,
            PaymentAttemptRepository paymentAttempts,
            PasswordResetTokenRepository passwordResetTokens,
            RefreshTokenService refreshTokens,
            AuthLockoutService authLockouts,
            DeviceTokenRepository deviceTokens,
            NotificationRepository notifications,
            IdempotencyRecordRepository idempotencyRecords,
            CurrentUserService currentUser,
            AuditService auditService,
            OperationalMetrics metrics,
            PasswordEncoder passwordEncoder,
            ObjectMapper objectMapper,
            Clock clock) {
        this.supportTickets = supportTickets;
        this.supportMessages = supportMessages;
        this.users = users;
        this.preferences = preferences;
        this.operators = operators;
        this.vehicles = vehicles;
        this.reservations = reservations;
        this.paymentIntents = paymentIntents;
        this.paymentAttempts = paymentAttempts;
        this.passwordResetTokens = passwordResetTokens;
        this.refreshTokens = refreshTokens;
        this.authLockouts = authLockouts;
        this.deviceTokens = deviceTokens;
        this.notifications = notifications;
        this.idempotencyRecords = idempotencyRecords;
        this.currentUser = currentUser;
        this.auditService = auditService;
        this.metrics = metrics;
        this.passwordEncoder = passwordEncoder;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional
    public AccountDeletionDtos.AccountDeletionFulfillmentResponse processSupportTicket(UUID ticketId) {
        UUID actorUserId = currentUser.userId();
        SupportTicket ticket = accountDeletionTicket(ticketId);
        User user = users.findById(ticket.getRequesterUserId())
                .orElseThrow(() -> new NotFoundException("Account deletion request was not found."));
        Instant now = Instant.now(clock);

        if (user.getRegistrationStatus() == RegistrationStatus.DELETED) {
            if (ticket.getStatus() != SupportTicketStatus.RESOLVED) {
                ticket.setStatus(SupportTicketStatus.RESOLVED);
            }
            metrics.increment("spotlink.account_deletion.fulfillment", "outcome", "already_processed");
            return response(
                    ticket,
                    user.getId(),
                    AccountDeletionDtos.AccountDeletionFulfillmentStatus.ALREADY_PROCESSED,
                    List.of(),
                    now);
        }

        List<AccountDeletionDtos.AccountDeletionBlocker> blockers = blockersFor(user.getId(), now);
        if (!blockers.isEmpty()) {
            metrics.increment("spotlink.account_deletion.fulfillment", "outcome", "blocked");
            auditService.record(
                    actorUserId,
                    "ACCOUNT_DELETION_BLOCKED",
                    "support_ticket",
                    ticket.getId().toString(),
                    metadata(Map.of(
                            "requesterUserId", user.getId(),
                            "blockers", blockers.stream().map(AccountDeletionDtos.AccountDeletionBlocker::code).toList())));
            return response(
                    ticket,
                    user.getId(),
                    AccountDeletionDtos.AccountDeletionFulfillmentStatus.BLOCKED,
                    blockers,
                    null);
        }

        anonymizeUser(user);
        revokeAuthArtifacts(user.getId(), now);
        anonymizeVehicles(user.getId());
        anonymizeOperatorAccount(user.getId());
        anonymizeSupportMessages(user.getId());
        preferences.deleteByUserId(user.getId());
        notifications.deleteByUserId(user.getId());
        idempotencyRecords.deleteByUserId(user.getId());

        user.setRegistrationStatus(RegistrationStatus.DELETED);
        ticket.setStatus(SupportTicketStatus.RESOLVED);
        auditService.record(
                actorUserId,
                "ACCOUNT_DELETION_PROCESSED",
                "support_ticket",
                ticket.getId().toString(),
                metadata(Map.of("requesterUserId", user.getId())));
        metrics.increment("spotlink.account_deletion.fulfillment", "outcome", "processed");

        return response(
                ticket,
                user.getId(),
                AccountDeletionDtos.AccountDeletionFulfillmentStatus.PROCESSED,
                List.of(),
                now);
    }

    private SupportTicket accountDeletionTicket(UUID ticketId) {
        SupportTicket ticket = supportTickets.findById(ticketId)
                .orElseThrow(() -> new NotFoundException("Account deletion request was not found."));
        if (ticket.getCategory() != SupportTicketCategory.ACCOUNT
                || !SupportService.ACCOUNT_DELETION_SUBJECT.equals(ticket.getSubject())) {
            throw new NotFoundException("Account deletion request was not found.");
        }
        return ticket;
    }

    private List<AccountDeletionDtos.AccountDeletionBlocker> blockersFor(UUID userId, Instant now) {
        List<AccountDeletionDtos.AccountDeletionBlocker> blockers = new ArrayList<>();
        long activeReservations = reservations.countByCustomerIdAndStatusInAndEndsAtAfter(
                userId,
                ACTIVE_OR_FUTURE_RESERVATION_STATUSES,
                now);
        if (activeReservations > 0) {
            blockers.add(new AccountDeletionDtos.AccountDeletionBlocker(
                    "ACTIVE_OR_FUTURE_RESERVATIONS",
                    "Resolve active or future reservations before completing account deletion.",
                    activeReservations));
        }

        long disputedReservations = reservations.countByCustomerIdAndStatus(userId, ReservationStatus.DISPUTED);
        if (disputedReservations > 0) {
            blockers.add(new AccountDeletionDtos.AccountDeletionBlocker(
                    "DISPUTED_RESERVATIONS",
                    "Resolve disputed reservations before completing account deletion.",
                    disputedReservations));
        }

        long unresolvedPaymentIntents = paymentIntents.countByCustomerIdAndStatusIn(
                userId,
                UNRESOLVED_PAYMENT_INTENT_STATUSES);
        if (unresolvedPaymentIntents > 0) {
            blockers.add(new AccountDeletionDtos.AccountDeletionBlocker(
                    "UNRESOLVED_PAYMENT_INTENTS",
                    "Resolve in-flight payment intents before completing account deletion.",
                    unresolvedPaymentIntents));
        }

        long unresolvedPaymentAttempts = paymentAttempts.countByCustomerIdAndStatusIn(
                userId,
                UNRESOLVED_PAYMENT_ATTEMPT_STATUSES);
        if (unresolvedPaymentAttempts > 0) {
            blockers.add(new AccountDeletionDtos.AccountDeletionBlocker(
                    "UNRESOLVED_PAYMENT_ATTEMPTS",
                    "Resolve in-flight payment attempts before completing account deletion.",
                    unresolvedPaymentAttempts));
        }
        return blockers;
    }

    private void anonymizeUser(User user) {
        String deletedIdentity = "deleted-" + user.getId();
        user.setEmail(deletedIdentity + "@spotlink.invalid");
        user.setFirstName("Deleted");
        user.setLastName("User");
        user.setPhone(null);
        user.setAvatarUrl(null);
        user.setBio(null);
        user.setPasswordHash(passwordEncoder.encode(UUID.randomUUID().toString()));
        user.getRoles().clear();
    }

    private void revokeAuthArtifacts(UUID userId, Instant now) {
        users.findById(userId).ifPresent(authLockouts::clearForUser);
        refreshTokens.revokeAllForUser(userId);
        for (PasswordResetToken token : passwordResetTokens.findByUserIdAndConsumedAtIsNull(userId)) {
            token.setConsumedAt(now);
        }
        for (DeviceToken token : deviceTokens.findByUserId(userId)) {
            token.setActive(false);
        }
    }

    private void anonymizeVehicles(UUID userId) {
        for (VehicleProfile vehicle : vehicles.findByUserIdOrderByCreatedAtDesc(userId)) {
            vehicle.setNickname(null);
            vehicle.setMake(null);
            vehicle.setModel(null);
            vehicle.setColor(null);
            vehicle.setLicensePlate(null);
            vehicle.setHeightMeters(null);
            vehicle.setLengthMeters(null);
            vehicle.setEvCapable(false);
            vehicle.setVerificationStatus(VehicleVerificationStatus.UNVERIFIED);
        }
    }

    private void anonymizeOperatorAccount(UUID userId) {
        operators.findByUserId(userId).ifPresent(operator -> {
            operator.setDisplayName("Deleted account");
            operator.setLegalName(null);
            operator.setSupportEmail(null);
            operator.setActive(false);
        });
    }

    private void anonymizeSupportMessages(UUID userId) {
        for (SupportMessage message : supportMessages.findBySenderUserId(userId)) {
            message.setSenderName("Deleted user");
            message.setAttachmentUrl(null);
        }
    }

    private AccountDeletionDtos.AccountDeletionFulfillmentResponse response(
            SupportTicket ticket,
            UUID userId,
            AccountDeletionDtos.AccountDeletionFulfillmentStatus status,
            List<AccountDeletionDtos.AccountDeletionBlocker> blockers,
            Instant processedAt) {
        return new AccountDeletionDtos.AccountDeletionFulfillmentResponse(
                ticket.getId(),
                userId,
                status,
                blockers,
                processedAt);
    }

    private String metadata(Map<String, ?> values) {
        try {
            return objectMapper.writeValueAsString(values);
        } catch (JsonProcessingException ex) {
            return null;
        }
    }
}
