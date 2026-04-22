package com.spotlink.reservation;

import com.spotlink.core.ApiPage;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ReservationController {

    private final ReservationService reservationService;

    public ReservationController(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @GetMapping({"/reservations/me", "/v1/reservations/me"})
    ApiPage<ReservationDtos.ReservationDto> mine(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return reservationService.mine(page, size);
    }

    @GetMapping({"/reservations/{reservationId}", "/v1/reservations/{reservationId}"})
    ReservationDtos.ReservationDto get(@PathVariable UUID reservationId) {
        return reservationService.get(reservationId);
    }

    @PostMapping({"/reservations/quote", "/v1/reservations/quote"})
    ReservationDtos.ReservationQuote quote(@Valid @RequestBody ReservationDtos.ReservationQuoteRequest request) {
        return reservationService.quote(request);
    }

    @PostMapping({"/reservations", "/v1/reservations"})
    @ResponseStatus(HttpStatus.CREATED)
    ReservationDtos.ReservationDto create(@Valid @RequestBody ReservationDtos.CreateReservationRequest request) {
        return reservationService.create(request);
    }

    @PostMapping({"/reservations/{reservationId}/cancel", "/v1/reservations/{reservationId}/cancel"})
    ReservationDtos.ReservationDto cancel(
            @PathVariable UUID reservationId,
            @RequestBody(required = false) ReservationDtos.CancelReservationRequest request) {
        return reservationService.cancel(
                reservationId,
                request == null ? new ReservationDtos.CancelReservationRequest(null) : request);
    }
}
