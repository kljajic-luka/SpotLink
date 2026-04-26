package com.spotlink.reservation;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BookingHoldRepository extends JpaRepository<BookingHold, UUID> {

    Optional<BookingHold> findByReservationId(UUID reservationId);

    Optional<BookingHold> findByCustomerIdAndIdempotencyKey(UUID customerId, String idempotencyKey);

    List<BookingHold> findByStatusAndExpiresAtLessThanEqual(BookingHoldStatus status, Instant expiresAt);

    @Query("""
            select count(h) from BookingHold h
            where h.inventoryPoolId = :inventoryPoolId
              and h.status = com.spotlink.reservation.BookingHoldStatus.ACTIVE
              and h.expiresAt > :now
              and h.startsAt < :endsAt
              and h.endsAt > :startsAt
            """)
    long countActiveOverlaps(
            @Param("inventoryPoolId") UUID inventoryPoolId,
            @Param("startsAt") Instant startsAt,
            @Param("endsAt") Instant endsAt,
            @Param("now") Instant now);
}