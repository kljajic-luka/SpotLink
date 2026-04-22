package com.spotlink.reservation;

import java.time.Instant;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReservationRepository extends JpaRepository<Reservation, UUID> {

    Page<Reservation> findByCustomerIdOrderByStartsAtDesc(UUID customerId, Pageable pageable);

    Page<Reservation> findByOperatorIdOrderByStartsAtDesc(UUID operatorId, Pageable pageable);

    Optional<Reservation> findByCustomerIdAndIdempotencyKey(UUID customerId, String idempotencyKey);

    long countByCustomerIdAndStatus(UUID customerId, ReservationStatus status);

    long countByOperatorIdAndStatusIn(UUID operatorId, Collection<ReservationStatus> statuses);

    long countByStatusIn(Collection<ReservationStatus> statuses);

    long countByOperatorIdAndStartsAtLessThanAndEndsAtGreaterThan(UUID operatorId, Instant dayEnd, Instant dayStart);

    @Query("""
            select count(r) from Reservation r
            where r.resourceId = :resourceId
              and r.status in :blockingStatuses
              and r.startsAt < :endsAt
              and r.endsAt > :startsAt
            """)
    long countOverlaps(
            @Param("resourceId") UUID resourceId,
            @Param("startsAt") Instant startsAt,
            @Param("endsAt") Instant endsAt,
            @Param("blockingStatuses") Collection<ReservationStatus> blockingStatuses);

    Optional<Reservation> findFirstByResourceIdAndStatusInAndStartsAtLessThanAndEndsAtGreaterThan(
            UUID resourceId,
            Collection<ReservationStatus> statuses,
            Instant endsAfter,
            Instant startsBefore);

    Optional<Reservation> findFirstByResourceIdAndStatusInAndStartsAtAfterOrderByStartsAtAsc(
            UUID resourceId,
            Collection<ReservationStatus> statuses,
            Instant startsAt);
}
