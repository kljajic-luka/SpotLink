package com.spotlink.reservation;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class BookingHoldExpiryScheduler {

    private final ReservationService reservationService;

    public BookingHoldExpiryScheduler(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @Scheduled(fixedDelayString = "${spotlink.hold-expiry-scan-ms:60000}")
    public void expireOverdueHolds() {
        reservationService.expireOverdueHolds();
    }
}