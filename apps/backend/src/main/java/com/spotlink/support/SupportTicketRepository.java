package com.spotlink.support;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SupportTicketRepository extends JpaRepository<SupportTicket, UUID> {

    Page<SupportTicket> findByRequesterUserIdOrderByUpdatedAtDesc(UUID requesterUserId, Pageable pageable);

    Page<SupportTicket> findAllByOrderByUpdatedAtDesc(Pageable pageable);

    List<SupportTicket> findByReservationIdOrderByUpdatedAtDesc(UUID reservationId);

    long countByRequesterUserId(UUID requesterUserId);

    long countByStatusIn(Collection<SupportTicketStatus> statuses);
}
