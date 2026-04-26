package com.spotlink.reservation;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CheckinRepository extends JpaRepository<Checkin, UUID> {

    Optional<Checkin> findByReservationId(UUID reservationId);
}