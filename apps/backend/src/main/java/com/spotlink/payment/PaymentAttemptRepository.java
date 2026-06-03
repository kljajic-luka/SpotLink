package com.spotlink.payment;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentAttemptRepository extends JpaRepository<PaymentAttempt, UUID> {

    Optional<PaymentAttempt> findByCustomerIdAndIdempotencyKey(UUID customerId, String idempotencyKey);

    long countByCustomerIdAndStatusIn(UUID customerId, Collection<PaymentAttemptStatus> statuses);

    List<PaymentAttempt> findByReservationIdOrderByCreatedAtDesc(UUID reservationId);

    Page<PaymentAttempt> findByReservationIdOrderByCreatedAtDesc(UUID reservationId, Pageable pageable);

    Page<PaymentAttempt> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
