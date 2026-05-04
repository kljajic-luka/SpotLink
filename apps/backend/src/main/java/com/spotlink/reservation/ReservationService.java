package com.spotlink.reservation;

import com.spotlink.core.ApiPage;
import com.spotlink.payment.PaymentDtos;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ReservationService {

    private final BookingOperationsService bookingOperations;

    public ReservationService(BookingOperationsService bookingOperations) {
        this.bookingOperations = bookingOperations;
    }

    public ApiPage<ReservationDtos.ReservationDto> mine(int page, int size) {
        return bookingOperations.mine(page, size);
    }

    public ReservationDtos.ReservationDto get(UUID reservationId) {
        return bookingOperations.getForCurrentUser(reservationId);
    }

    public ReservationDtos.BookingDetailDto getDetail(UUID reservationId) {
        return bookingOperations.getDetailForCurrentUser(reservationId);
    }

    public ReservationDtos.ReservationQuote quote(ReservationDtos.ReservationQuoteRequest request) {
        return bookingOperations.quote(request);
    }

    public ReservationDtos.ReservationDto create(ReservationDtos.CreateReservationRequest request) {
        return bookingOperations.create(request);
    }

    public ReservationDtos.ReservationDto cancel(UUID reservationId, ReservationDtos.CancelReservationRequest request) {
        return bookingOperations.cancelAsCustomer(reservationId, request.reason());
    }

    public int expireOverdueHolds() {
        return bookingOperations.expireOverdueHolds();
    }

    public void confirmAfterPayment(UUID reservationId, UUID actorUserId, String provider, String providerReference) {
        bookingOperations.confirmAfterPayment(reservationId, actorUserId, provider, providerReference);
    }

    public void recordPaymentFailure(UUID reservationId, UUID actorUserId, String provider, String message) {
        bookingOperations.recordPaymentFailure(reservationId, actorUserId, provider, message);
    }

    public ApiPage<ReservationDtos.ReservationDto> operatorUpcoming(int page, int size) {
        return bookingOperations.operatorUpcoming(page, size);
    }

    public ReservationDtos.BookingDetailDto operatorDetail(UUID reservationId) {
        return bookingOperations.operatorDetail(reservationId);
    }

    public ReservationDtos.ReservationDto cancelAsOperator(UUID reservationId, String reason) {
        return bookingOperations.cancelAsOperator(reservationId, reason);
    }

    public ReservationDtos.ReservationDto confirmManualAsOperator(UUID reservationId, String notes) {
        return bookingOperations.confirmManualAsOperator(reservationId, notes);
    }

    public ReservationDtos.ReservationDto rejectManualAsOperator(UUID reservationId, String reason) {
        return bookingOperations.rejectManualAsOperator(reservationId, reason);
    }

    public ReservationDtos.ReservationDto checkIn(UUID reservationId, String notes) {
        return bookingOperations.checkIn(reservationId, notes);
    }

    public ReservationDtos.ReservationDto markNoShow(UUID reservationId, String reason) {
        return bookingOperations.markNoShow(reservationId, reason);
    }

    public ApiPage<ReservationDtos.ReservationDto> adminSearch(String query, UUID operatorId, UUID locationId, ReservationStatus status, int page, int size) {
        return bookingOperations.adminSearch(query, operatorId, locationId, status, page, size);
    }

    public ReservationDtos.BookingDetailDto adminDetail(UUID reservationId) {
        return bookingOperations.adminDetail(reservationId);
    }

    public ReservationDtos.ReservationDto cancelAsAdmin(UUID reservationId, String reason) {
        return bookingOperations.cancelAsAdmin(reservationId, reason);
    }

    public ReservationDtos.ReservationDto confirmManualAsAdmin(UUID reservationId, String notes) {
        return bookingOperations.confirmManualAsAdmin(reservationId, notes);
    }

    public ReservationDtos.ReservationDto rejectManualAsAdmin(UUID reservationId, String reason) {
        return bookingOperations.rejectManualAsAdmin(reservationId, reason);
    }

    public PaymentDtos.RefundDto markRefundAsAdmin(UUID reservationId, Long amountCents, String reason) {
        return bookingOperations.markRefundAsAdmin(reservationId, amountCents, reason);
    }
}
