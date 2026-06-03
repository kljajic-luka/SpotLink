package com.spotlink.payment;

import com.spotlink.core.ConflictException;
import com.spotlink.core.NotFoundException;
import com.spotlink.reservation.Reservation;
import com.spotlink.reservation.ReservationRepository;
import com.spotlink.reservation.ReservationStatus;
import com.spotlink.reservation.PaymentMode;
import com.spotlink.reservation.ReservationService;
import com.spotlink.security.CurrentUserService;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentService {

    private final PaymentIntentRepository intents;
    private final PaymentAttemptRepository paymentAttempts;
    private final PaymentProviderEventRepository paymentProviderEvents;
    private final ReservationRepository reservations;
    private final ReservationService reservationService;
    private final PaymentProvider paymentProvider;
    private final PaymentAuthority paymentAuthority;
    private final CurrentUserService currentUser;
    private final Clock clock;

    public PaymentService(
            PaymentIntentRepository intents,
            PaymentAttemptRepository paymentAttempts,
            PaymentProviderEventRepository paymentProviderEvents,
            ReservationRepository reservations,
            ReservationService reservationService,
            PaymentProvider paymentProvider,
            PaymentAuthority paymentAuthority,
            CurrentUserService currentUser,
            Clock clock) {
        this.intents = intents;
        this.paymentAttempts = paymentAttempts;
        this.paymentProviderEvents = paymentProviderEvents;
        this.reservations = reservations;
        this.reservationService = reservationService;
        this.paymentProvider = paymentProvider;
        this.paymentAuthority = paymentAuthority;
        this.currentUser = currentUser;
        this.clock = clock;
    }

    public PaymentDtos.PaymentCapabilitiesDto capabilities() {
        return paymentAuthority.capabilities();
    }

    public List<PaymentDtos.PaymentMethodDto> methods() {
        if (!paymentAuthority.mockPaymentMethodsAllowed()) {
            return List.of();
        }
        return List.of(
                new PaymentDtos.PaymentMethodDto("pm_card_visa", "Visa", "4242", 12, 2032, true),
                new PaymentDtos.PaymentMethodDto("pm_card_sca_required", "Visa", "3155", 11, 2032, false));
    }

    @Transactional
    public PaymentDtos.PaymentIntentDto createIntent(PaymentDtos.CreatePaymentIntentRequest request) {
        UUID userId = currentUser.userId();
        reservationService.expireOverdueHolds();
        return intents.findByCustomerIdAndIdempotencyKey(userId, request.idempotencyKey())
                .map(this::toDto)
                .orElseGet(() -> createNewIntent(request, userId));
    }

    @Transactional
    public PaymentDtos.PaymentProviderResult confirm(UUID paymentIntentId) {
        UUID userId = currentUser.userId();
        PaymentIntent intent = intents.findById(paymentIntentId)
                .orElseThrow(() -> new NotFoundException("Payment intent was not found."));
        if (!intent.getCustomerId().equals(userId)) {
            throw new AccessDeniedException("Payment intent does not belong to the current user.");
        }
        if (intent.getStatus() == PaymentStatus.AUTHORIZED || intent.getStatus() == PaymentStatus.CAPTURED) {
            reservationService.confirmAfterPayment(intent.getReservationId(), userId, paymentProvider.name(), intent.getProviderReference());
            return new PaymentDtos.PaymentProviderResult(intent.getStatus(), intent.getId(), intent.getRedirectUrl(), "Already confirmed");
        }
        if (intent.getStatus() == PaymentStatus.CANCELLED) {
            return new PaymentDtos.PaymentProviderResult(intent.getStatus(), intent.getId(), intent.getRedirectUrl(), "Already cancelled");
        }
        paymentAuthority.requireOnlinePaymentsEnabled();
        PaymentProvider.ProviderResult result = paymentProvider.authorize(new PaymentProvider.ProviderRequest(
                intent.getId().toString(),
                intent.getAmountCents(),
                intent.getCurrency(),
                null,
                intent.getIdempotencyKey()));
        applyProviderResult(intent, result);
        PaymentAttempt attempt = syncPaymentAttempt(intent, userId, result);
        recordProviderEvent(attempt, "AUTHORIZE", result, Instant.now(clock));
        if (intent.getStatus() == PaymentStatus.AUTHORIZED) {
            reservationService.confirmAfterPayment(intent.getReservationId(), userId, paymentProvider.name(), result.providerReference());
        }
        if (intent.getStatus() == PaymentStatus.FAILED) {
            reservationService.recordPaymentFailure(intent.getReservationId(), userId, paymentProvider.name(), result.message());
        }
        return new PaymentDtos.PaymentProviderResult(intent.getStatus(), intent.getId(), intent.getRedirectUrl(), result.message());
    }

    @Transactional
    public PaymentDtos.PaymentProviderResult cancel(UUID paymentIntentId) {
        UUID userId = currentUser.userId();
        PaymentIntent intent = intents.findById(paymentIntentId)
                .orElseThrow(() -> new NotFoundException("Payment intent was not found."));
        if (!intent.getCustomerId().equals(userId)) {
            throw new AccessDeniedException("Payment intent does not belong to the current user.");
        }
        if (intent.getStatus() == PaymentStatus.CANCELLED) {
            return new PaymentDtos.PaymentProviderResult(intent.getStatus(), intent.getId(), intent.getRedirectUrl(), "Already cancelled");
        }
        if (intent.getStatus() == PaymentStatus.AUTHORIZED
                || intent.getStatus() == PaymentStatus.CAPTURED
                || intent.getStatus() == PaymentStatus.REFUNDED) {
            throw new ConflictException("PAYMENT_CANCEL_NOT_ALLOWED", "Payment intent can no longer be cancelled.");
        }
        paymentAuthority.requireOnlinePaymentsEnabled();
        PaymentProvider.ProviderResult result = paymentProvider.cancel(new PaymentProvider.ProviderRequest(
                intent.getId().toString(),
                intent.getAmountCents(),
                intent.getCurrency(),
                null,
                intent.getIdempotencyKey()));
        applyProviderResult(intent, result);
        PaymentAttempt attempt = syncPaymentAttempt(intent, userId, result);
        recordProviderEvent(attempt, "CANCEL", result, Instant.now(clock));
        return new PaymentDtos.PaymentProviderResult(intent.getStatus(), intent.getId(), intent.getRedirectUrl(), result.message());
    }

    PaymentDtos.PaymentIntentDto toDto(PaymentIntent intent) {
        return new PaymentDtos.PaymentIntentDto(
                intent.getId(),
                intent.getReservationId(),
                intent.getAmountCents(),
                intent.getCurrency(),
                intent.getStatus(),
                intent.getRedirectUrl(),
                intent.getClientSecret());
    }

    private PaymentDtos.PaymentIntentDto createNewIntent(PaymentDtos.CreatePaymentIntentRequest request, UUID userId) {
        Instant now = Instant.now(clock);
        Reservation reservation = reservations.findById(request.reservationId())
                .orElseThrow(() -> new NotFoundException("Reservation was not found."));
        if (!reservation.getCustomerId().equals(userId)) {
            throw new AccessDeniedException("Reservation does not belong to the current user.");
        }
        if (reservation.getPaymentMode() == PaymentMode.PAY_ON_ARRIVAL) {
            throw new ConflictException("PAYMENT_NOT_ALLOWED", "This reservation uses pay on arrival.");
        }
        if (reservation.getStatus() == ReservationStatus.PENDING_PAYMENT
                && reservation.getPaymentExpiresAt() != null
                && !reservation.getPaymentExpiresAt().isAfter(now)) {
            reservation.setStatus(ReservationStatus.EXPIRED);
            reservation.setAccessInstructionsVisible(false);
            throw new ConflictException("PAYMENT_HOLD_EXPIRED", "Reservation payment hold has expired.");
        }
        if (reservation.getStatus() != ReservationStatus.PENDING_PAYMENT) {
            throw new ConflictException("PAYMENT_NOT_ALLOWED", "Payment is not allowed for this reservation.");
        }
        paymentAuthority.requireOnlinePaymentsEnabled();

        PaymentIntent intent = new PaymentIntent();
        intent.setReservationId(reservation.getId());
        intent.setCustomerId(userId);
        intent.setAmountCents(reservation.getTotalAmountCents());
        intent.setCurrency(reservation.getCurrency());
        intent.setStatus(PaymentStatus.REQUIRES_METHOD);
        intent.setClientSecret("sl_pi_secret_" + UUID.randomUUID());
        intent.setIdempotencyKey(request.idempotencyKey());

        PaymentIntent saved = intents.save(intent);
        PaymentProvider.ProviderResult result = paymentProvider.authorize(new PaymentProvider.ProviderRequest(
                saved.getId().toString(),
                saved.getAmountCents(),
                saved.getCurrency(),
                request.paymentMethodId(),
                request.idempotencyKey()));
        applyProviderResult(saved, result);
        PaymentAttempt attempt = syncPaymentAttempt(saved, userId, result);
        recordProviderEvent(attempt, "AUTHORIZE", result, now);
        if (saved.getStatus() == PaymentStatus.AUTHORIZED) {
            reservationService.confirmAfterPayment(reservation.getId(), userId, paymentProvider.name(), result.providerReference());
        }
        if (saved.getStatus() == PaymentStatus.FAILED) {
            reservationService.recordPaymentFailure(reservation.getId(), userId, paymentProvider.name(), result.message());
        }
        return toDto(saved);
    }

    private void applyProviderResult(PaymentIntent intent, PaymentProvider.ProviderResult result) {
        intent.setStatus(result.status());
        intent.setProviderReference(result.providerReference());
        intent.setRedirectUrl(result.redirectUrl());
    }

    private PaymentAttempt syncPaymentAttempt(PaymentIntent intent, UUID userId, PaymentProvider.ProviderResult result) {
        PaymentAttempt attempt = paymentAttempts.findByCustomerIdAndIdempotencyKey(userId, intent.getIdempotencyKey())
                .orElseGet(PaymentAttempt::new);
        attempt.setReservationId(intent.getReservationId());
        attempt.setCustomerId(userId);
        attempt.setProvider(paymentProvider.name());
        attempt.setPaymentMode(PaymentMode.ONLINE);
        attempt.setAmountCents(intent.getAmountCents());
        attempt.setCurrency(intent.getCurrency());
        attempt.setProviderReference(result.providerReference());
        attempt.setIdempotencyKey(intent.getIdempotencyKey());
        attempt.setStatus(mapStatus(result.status()));
        attempt.setFailureCode(result.status() == PaymentStatus.FAILED ? "PAYMENT_FAILED" : null);
        attempt.setFailureMessage(result.status() == PaymentStatus.FAILED ? result.message() : null);
        attempt.setLastTransitionAt(Instant.now(clock));
        return paymentAttempts.save(attempt);
    }

    private void recordProviderEvent(PaymentAttempt attempt, String operation, PaymentProvider.ProviderResult result, Instant now) {
        String externalEventId = result.providerReference() == null
                ? attempt.getId() + ":" + operation + ":" + result.status().name()
                : result.providerReference();
        if (paymentProviderEvents.existsByProviderAndExternalEventId(paymentProvider.name(), externalEventId)) {
            return;
        }
        PaymentProviderEvent event = new PaymentProviderEvent();
        event.setPaymentAttemptId(attempt.getId());
        event.setProvider(paymentProvider.name());
        event.setExternalEventId(externalEventId);
        event.setEventType(operation + "_" + result.status().name());
        event.setStatus(PaymentProviderEventStatus.PROCESSED);
        event.setProcessedAt(now);
        paymentProviderEvents.save(event);
    }

    private PaymentAttemptStatus mapStatus(PaymentStatus status) {
        return switch (status) {
            case AUTHORIZED, CAPTURED -> PaymentAttemptStatus.AUTHORIZED;
            case REQUIRES_ACTION -> PaymentAttemptStatus.REQUIRES_ACTION;
            case FAILED -> PaymentAttemptStatus.FAILED;
            case CANCELLED -> PaymentAttemptStatus.CANCELLED;
            case REFUNDED -> PaymentAttemptStatus.REFUND_MARKED;
            case REQUIRES_METHOD -> PaymentAttemptStatus.PENDING;
        };
    }
}
