package com.spotlink.operator;

import com.spotlink.core.ApiPage;
import com.spotlink.reservation.ReservationDtos;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OperatorController {

    private final OperatorService operatorService;

    public OperatorController(OperatorService operatorService) {
        this.operatorService = operatorService;
    }

    @GetMapping({"/operator/me", "/v1/operator/me"})
    OperatorDtos.OperatorAccountDto me() {
        return operatorService.me();
    }

    @GetMapping({"/operator/dashboard/summary", "/v1/operator/dashboard/summary"})
    OperatorDtos.OperatorDashboardSummary summary() {
        return operatorService.summary();
    }

    @GetMapping({"/operator/resources/health", "/v1/operator/resources/health"})
    List<OperatorDtos.OperatorResourceHealth> resourceHealth() {
        return operatorService.resourceHealth();
    }

    @GetMapping({"/operator/bookings/upcoming", "/v1/operator/bookings/upcoming"})
    ApiPage<ReservationDtos.ReservationDto> upcomingBookings(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return operatorService.upcomingBookings(page, size);
    }

    @GetMapping({"/operator/bookings/{reservationId}", "/v1/operator/bookings/{reservationId}"})
    ReservationDtos.BookingDetailDto bookingDetail(@PathVariable UUID reservationId) {
        return operatorService.bookingDetail(reservationId);
    }

    @PostMapping({"/operator/bookings/{reservationId}/check-in", "/v1/operator/bookings/{reservationId}/check-in"})
    ReservationDtos.ReservationDto checkIn(
            @PathVariable UUID reservationId,
            @RequestBody(required = false) OperatorDtos.BookingActionRequest request) {
        return operatorService.checkIn(reservationId, request == null ? null : request.notes());
    }

    @PostMapping({"/operator/bookings/{reservationId}/no-show", "/v1/operator/bookings/{reservationId}/no-show"})
    ReservationDtos.ReservationDto noShow(
            @PathVariable UUID reservationId,
            @RequestBody(required = false) OperatorDtos.BookingActionRequest request) {
        return operatorService.markNoShow(reservationId, request == null ? null : request.reason());
    }

    @PostMapping({"/operator/bookings/{reservationId}/cancel", "/v1/operator/bookings/{reservationId}/cancel"})
    ReservationDtos.ReservationDto cancel(
            @PathVariable UUID reservationId,
            @RequestBody(required = false) OperatorDtos.BookingActionRequest request) {
        return operatorService.cancelBooking(reservationId, request == null ? null : request.reason());
    }

    @PostMapping({"/operator/resources/{resourceId}/pause", "/v1/operator/resources/{resourceId}/pause"})
    OperatorDtos.InventoryControlDto pauseSales(
            @PathVariable UUID resourceId,
            @Valid @RequestBody(required = false) OperatorDtos.BookingActionRequest request) {
        return operatorService.pauseSales(resourceId, request == null ? null : request.reason());
    }

    @PostMapping({"/operator/resources/{resourceId}/unpause", "/v1/operator/resources/{resourceId}/unpause"})
    OperatorDtos.InventoryControlDto unpauseSales(@PathVariable UUID resourceId) {
        return operatorService.unpauseSales(resourceId);
    }

    @PostMapping({"/operator/resources/{resourceId}/capacity", "/v1/operator/resources/{resourceId}/capacity"})
    OperatorDtos.InventoryControlDto adjustSellableCapacity(
            @PathVariable UUID resourceId,
            @Valid @RequestBody OperatorDtos.CapacityOverrideRequest request) {
        return operatorService.adjustSellableCapacity(resourceId, request.sellableCapacity(), request.reason());
    }
}
