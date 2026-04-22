package com.spotlink.payment;

import com.spotlink.core.ConflictException;
import com.spotlink.core.NotFoundException;
import com.spotlink.reservation.Reservation;
import com.spotlink.reservation.ReservationRepository;
import com.spotlink.reservation.ReservationStatus;
import com.spotlink.security.CurrentUserService;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentService {

    private final PaymentIntentRepository intents;
    private final ReservationRepository reservations;
    private final PaymentProvider paymentProvider;
    private final CurrentUserService currentUser;

    public PaymentService(
            PaymentIntentRepository intents,
            ReservationRepository reservations,
            PaymentProvider paymentProvider,
            CurrentUserService currentUser) {
        this.intents = intents;
        this.reservations = reservations;
        this.paymentProvider = paymentProvider;
        this.currentUser = currentUser;
    }

    public List<PaymentDtos.PaymentMethodDto> methods() {
        return List.of(
                new PaymentDtos.PaymentMethodDto("pm_card_visa", "Visa", "4242", 12, 2032, true),
                new PaymentDtos.PaymentMethodDto("pm_card_sca_required", "Visa", "3155", 11, 2032, false));
    }

    @Transactional
    public PaymentDtos.PaymentIntentDto createIntent(PaymentDtos.CreatePaymentIntentRequest request) {
        UUID userId = currentUser.userId();
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
            return new PaymentDtos.PaymentProviderResult(intent.getStatus(), intent.getId(), intent.getRedirectUrl(), "Already confirmed");
        }
        PaymentProvider.ProviderResult result = paymentProvider.authorize(new PaymentProvider.ProviderRequest(
                intent.getId().toString(),
                intent.getAmountCents(),
                intent.getCurrency(),
                null,
                intent.getIdempotencyKey()));
        applyProviderResult(intent, result);
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
        Reservation reservation = reservations.findById(request.reservationId())
                .orElseThrow(() -> new NotFoundException("Reservation was not found."));
        if (!reservation.getCustomerId().equals(userId)) {
            throw new AccessDeniedException("Reservation does not belong to the current user.");
        }
        if (reservation.getStatus() == ReservationStatus.CANCELLED || reservation.getStatus() == ReservationStatus.EXPIRED) {
            throw new ConflictException("PAYMENT_NOT_ALLOWED", "Payment is not allowed for this reservation.");
        }

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
        if (saved.getStatus() == PaymentStatus.AUTHORIZED) {
            reservation.setStatus(ReservationStatus.CONFIRMED);
            reservation.setAccessInstructionsVisible(true);
        }
        return toDto(saved);
    }

    private void applyProviderResult(PaymentIntent intent, PaymentProvider.ProviderResult result) {
        intent.setStatus(result.status());
        intent.setProviderReference(result.providerReference());
        intent.setRedirectUrl(result.redirectUrl());
    }
}
