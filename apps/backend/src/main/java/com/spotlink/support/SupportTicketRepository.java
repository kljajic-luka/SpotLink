package com.spotlink.support;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SupportTicketRepository extends JpaRepository<SupportTicket, UUID> {

    Page<SupportTicket> findByRequesterUserIdOrderByUpdatedAtDesc(UUID requesterUserId, Pageable pageable);

    Page<SupportTicket> findAllByOrderByUpdatedAtDesc(Pageable pageable);

    List<SupportTicket> findByReservationIdOrderByUpdatedAtDesc(UUID reservationId);

    Optional<SupportTicket> findFirstByRequesterUserIdAndCategoryAndSubjectAndStatusInOrderByUpdatedAtDesc(
            UUID requesterUserId,
            SupportTicketCategory category,
            String subject,
            Collection<SupportTicketStatus> statuses);

    long countByRequesterUserId(UUID requesterUserId);

    long countByRequesterUserIdAndCategoryAndSubject(UUID requesterUserId, SupportTicketCategory category, String subject);

    long countByStatusIn(Collection<SupportTicketStatus> statuses);
}
