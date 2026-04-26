package com.spotlink.payment;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RefundRepository extends JpaRepository<Refund, UUID> {

    List<Refund> findByReservationIdOrderByMarkedAtDesc(UUID reservationId);
}