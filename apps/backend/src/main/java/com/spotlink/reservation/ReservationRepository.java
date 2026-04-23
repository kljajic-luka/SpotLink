package com.spotlink.reservation;

import java.time.Instant;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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
              and (
                r.status in :confirmedStatuses
                or (
                  r.status = :pendingPaymentStatus
                  and r.paymentExpiresAt is not null
                  and r.paymentExpiresAt > :now
                )
              )
              and r.startsAt < :endsAt
              and r.endsAt > :startsAt
            """)
    long countOverlaps(
            @Param("resourceId") UUID resourceId,
            @Param("startsAt") Instant startsAt,
            @Param("endsAt") Instant endsAt,
            @Param("confirmedStatuses") Collection<ReservationStatus> confirmedStatuses,
            @Param("pendingPaymentStatus") ReservationStatus pendingPaymentStatus,
            @Param("now") Instant now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Reservation r
            set r.status = com.spotlink.reservation.ReservationStatus.EXPIRED,
                r.accessInstructionsVisible = false
            where r.status = com.spotlink.reservation.ReservationStatus.PENDING_PAYMENT
              and r.paymentExpiresAt is not null
              and r.paymentExpiresAt <= :now
            """)
    int expirePaymentHolds(@Param("now") Instant now);

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
