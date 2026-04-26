package com.spotlink.reservation;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReservationRepository extends JpaRepository<Reservation, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Reservation r where r.id = :reservationId")
    Optional<Reservation> findByIdForUpdate(@Param("reservationId") UUID reservationId);

    Page<Reservation> findByCustomerIdOrderByStartsAtDesc(UUID customerId, Pageable pageable);

    Page<Reservation> findByOperatorIdOrderByStartsAtDesc(UUID operatorId, Pageable pageable);

    Page<Reservation> findByOperatorIdAndStatusInAndStartsAtGreaterThanEqualOrderByStartsAtAsc(
            UUID operatorId,
            Collection<ReservationStatus> statuses,
            Instant startsAt,
            Pageable pageable);

    List<Reservation> findByLocationIdInAndStatusInAndStartsAtGreaterThanEqualOrderByStartsAtAsc(
            Collection<UUID> locationIds,
            Collection<ReservationStatus> statuses,
            Instant startsAt);

    Optional<Reservation> findByCustomerIdAndIdempotencyKey(UUID customerId, String idempotencyKey);

    List<Reservation> findByHoldIdIn(Collection<UUID> holdIds);

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

    @Query("""
            select count(r) from Reservation r
            where r.inventoryPoolId = :inventoryPoolId
              and r.status in :blockingStatuses
              and r.startsAt < :endsAt
              and r.endsAt > :startsAt
            """)
    long countPoolOverlaps(
            @Param("inventoryPoolId") UUID inventoryPoolId,
            @Param("startsAt") Instant startsAt,
            @Param("endsAt") Instant endsAt,
            @Param("blockingStatuses") Collection<ReservationStatus> blockingStatuses);

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

    @Query("""
            select r from Reservation r
            where (:operatorId is null or r.operatorId = :operatorId)
              and (:locationId is null or r.locationId = :locationId)
              and (:status is null or r.status = :status)
            order by r.startsAt desc
            """)
    Page<Reservation> adminSearch(
            @Param("operatorId") UUID operatorId,
            @Param("locationId") UUID locationId,
            @Param("status") ReservationStatus status,
            Pageable pageable);

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
