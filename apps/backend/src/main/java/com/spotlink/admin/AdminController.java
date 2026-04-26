package com.spotlink.admin;

import com.spotlink.core.ApiPage;
import com.spotlink.payment.PaymentDtos;
import com.spotlink.reservation.ReservationDtos;
import com.spotlink.reservation.ReservationStatus;
import com.spotlink.support.SupportDtos;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping({"/admin/dashboard/summary", "/v1/admin/dashboard/summary"})
    AdminDtos.AdminDashboardSummary summary() {
        return adminService.summary();
    }

    @GetMapping({"/admin/users", "/v1/admin/users"})
    ApiPage<AdminDtos.AdminUserSummary> users(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        return adminService.users(page, size);
    }

    @GetMapping({"/admin/audit-events", "/v1/admin/audit-events"})
    ApiPage<AdminDtos.AdminAuditEvent> auditEvents(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        return adminService.auditEvents(page, size);
    }

    @GetMapping({"/admin/bookings", "/v1/admin/bookings"})
    ApiPage<ReservationDtos.ReservationDto> bookings(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) UUID operatorId,
            @RequestParam(required = false) UUID locationId,
            @RequestParam(required = false) ReservationStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        return adminService.bookings(query, operatorId, locationId, status, page, size);
    }

    @GetMapping({"/admin/bookings/{reservationId}", "/v1/admin/bookings/{reservationId}"})
    ReservationDtos.BookingDetailDto bookingDetail(@PathVariable UUID reservationId) {
        return adminService.bookingDetail(reservationId);
    }

    @PostMapping({"/admin/bookings/{reservationId}/cancel", "/v1/admin/bookings/{reservationId}/cancel"})
    ReservationDtos.ReservationDto cancelBooking(
            @PathVariable UUID reservationId,
            @RequestBody(required = false) AdminDtos.AdminActionRequest request) {
        return adminService.cancelBooking(reservationId, request == null ? null : request.reason());
    }

    @PostMapping({"/admin/bookings/{reservationId}/refund-marker", "/v1/admin/bookings/{reservationId}/refund-marker"})
    PaymentDtos.RefundDto markRefund(
            @PathVariable UUID reservationId,
            @Valid @RequestBody(required = false) AdminDtos.RefundMarkerRequest request) {
        return adminService.markRefund(
                reservationId,
                request == null ? null : request.amountCents(),
                request == null ? null : request.reason());
    }

    @PostMapping({"/admin/locations/{locationId}/pause", "/v1/admin/locations/{locationId}/pause"})
    AdminDtos.PauseOperationResult pauseLocation(
            @PathVariable UUID locationId,
            @RequestBody(required = false) AdminDtos.AdminActionRequest request) {
        return adminService.pauseLocation(locationId, request == null ? null : request.reason());
    }

    @PostMapping({"/admin/operators/{operatorId}/pause", "/v1/admin/operators/{operatorId}/pause"})
    AdminDtos.PauseOperationResult pauseOperator(
            @PathVariable UUID operatorId,
            @RequestBody(required = false) AdminDtos.AdminActionRequest request) {
        return adminService.pauseOperator(operatorId, request == null ? null : request.reason());
    }

    @GetMapping({"/admin/payment-attempts", "/v1/admin/payment-attempts"})
    ApiPage<PaymentDtos.PaymentAttemptDto> paymentAttempts(
            @RequestParam(required = false) UUID reservationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        return adminService.paymentAttempts(reservationId, page, size);
    }

    @GetMapping({"/admin/support-cases", "/v1/admin/support-cases"})
    ApiPage<SupportDtos.SupportTicketDto> supportCases(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        return adminService.supportCases(page, size);
    }
}
