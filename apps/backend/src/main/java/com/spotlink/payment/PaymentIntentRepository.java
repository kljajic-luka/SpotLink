package com.spotlink.payment;

import java.util.Optional;
import java.util.Collection;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentIntentRepository extends JpaRepository<PaymentIntent, UUID> {

    Optional<PaymentIntent> findByCustomerIdAndIdempotencyKey(UUID customerId, String idempotencyKey);

    long countByStatus(PaymentStatus status);

    @Query("select coalesce(sum(p.amountCents), 0) from PaymentIntent p where p.status in :statuses")
    long grossVolumeForStatuses(@Param("statuses") Collection<PaymentStatus> statuses);
}
