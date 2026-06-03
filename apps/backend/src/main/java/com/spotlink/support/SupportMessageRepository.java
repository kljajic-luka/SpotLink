package com.spotlink.support;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SupportMessageRepository extends JpaRepository<SupportMessage, UUID> {

    List<SupportMessage> findByTicketIdOrderByCreatedAtAsc(UUID ticketId);

    List<SupportMessage> findBySenderUserId(UUID senderUserId);
}
