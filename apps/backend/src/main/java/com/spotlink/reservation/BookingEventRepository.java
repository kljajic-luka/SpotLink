package com.spotlink.reservation;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookingEventRepository extends JpaRepository<BookingEvent, UUID> {

    List<BookingEvent> findByReservationIdOrderByOccurredAtAsc(UUID reservationId);
}