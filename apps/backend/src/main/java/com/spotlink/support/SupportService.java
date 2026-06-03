package com.spotlink.support;

import com.spotlink.core.ApiPage;
import com.spotlink.core.NotFoundException;
import com.spotlink.admin.AuditService;
import com.spotlink.security.CurrentUserService;
import com.spotlink.user.User;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SupportService {

    public static final String ACCOUNT_DELETION_SUBJECT = "Account deletion request";

    private static final List<SupportTicketStatus> UNRESOLVED_STATUSES = List.of(
            SupportTicketStatus.OPEN,
            SupportTicketStatus.WAITING_ON_CUSTOMER,
            SupportTicketStatus.WAITING_ON_OPERATOR);

    private final SupportTicketRepository tickets;
    private final SupportMessageRepository messages;
    private final CurrentUserService currentUser;
    private final AuditService auditService;

    public SupportService(
            SupportTicketRepository tickets,
            SupportMessageRepository messages,
            CurrentUserService currentUser,
            AuditService auditService) {
        this.tickets = tickets;
        this.messages = messages;
        this.currentUser = currentUser;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public ApiPage<SupportDtos.SupportTicketDto> mine(int page, int size) {
        return ApiPage.from(tickets.findByRequesterUserIdOrderByUpdatedAtDesc(
                currentUser.userId(),
                PageRequest.of(page, Math.min(size, 100))).map(this::toDto));
    }

    @Transactional
    public SupportDtos.SupportTicketDto create(SupportDtos.CreateSupportTicketRequest request) {
        User user = currentUser.user();
        SupportTicket ticket = new SupportTicket();
        ticket.setRequesterUserId(user.getId());
        ticket.setCategory(request.category());
        ticket.setSubject(request.subject());
        ticket.setReservationId(request.reservationId());
        ticket.setLocationId(request.locationId());
        SupportTicket saved = tickets.save(ticket);

        SupportMessage message = new SupportMessage();
        message.setTicketId(saved.getId());
        message.setSenderUserId(user.getId());
        message.setSenderName(user.getFirstName() + " " + user.getLastName());
        message.setBody(request.body());
        messages.save(message);
        return toDto(saved);
    }

    @Transactional
    public SupportDtos.SupportTicketDto requestAccountDeletion() {
        User user = currentUser.user();
        return tickets.findFirstByRequesterUserIdAndCategoryAndSubjectAndStatusInOrderByUpdatedAtDesc(
                        user.getId(),
                        SupportTicketCategory.ACCOUNT,
                        ACCOUNT_DELETION_SUBJECT,
                        UNRESOLVED_STATUSES)
                .map(this::toDto)
                .orElseGet(() -> toDto(createAccountDeletionTicket(user)));
    }

    @Transactional(readOnly = true)
    public java.util.List<SupportDtos.SupportMessageDto> messages(UUID ticketId) {
        SupportTicket ticket = ticketForCurrentUser(ticketId);
        return messages.findByTicketIdOrderByCreatedAtAsc(ticket.getId()).stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public SupportDtos.SupportMessageDto addMessage(UUID ticketId, SupportDtos.CreateMessageRequest request) {
        SupportTicket ticket = ticketForCurrentUser(ticketId);
        User user = currentUser.user();
        SupportMessage message = new SupportMessage();
        message.setTicketId(ticket.getId());
        message.setSenderUserId(user.getId());
        message.setSenderName(user.getFirstName() + " " + user.getLastName());
        message.setBody(request.body());
        ticket.setStatus(SupportTicketStatus.WAITING_ON_OPERATOR);
        return toDto(messages.save(message));
    }

    private SupportTicket createAccountDeletionTicket(User user) {
        SupportTicket ticket = new SupportTicket();
        ticket.setRequesterUserId(user.getId());
        ticket.setCategory(SupportTicketCategory.ACCOUNT);
        ticket.setSubject(ACCOUNT_DELETION_SUBJECT);
        SupportTicket saved = tickets.save(ticket);

        SupportMessage message = new SupportMessage();
        message.setTicketId(saved.getId());
        message.setSenderUserId(user.getId());
        message.setSenderName(user.getFirstName() + " " + user.getLastName());
        message.setBody("Korisnik je zatrazio brisanje naloga. Podrska treba da pregleda zahtev pre bilo kakvog brisanja ili anonimizacije.");
        messages.save(message);
        auditService.record(
                user.getId(),
                "ACCOUNT_DELETION_REQUESTED",
                "support_ticket",
                saved.getId().toString(),
                null);
        return saved;
    }

    private SupportTicket ticketForCurrentUser(UUID ticketId) {
        SupportTicket ticket = tickets.findById(ticketId)
                .orElseThrow(() -> new NotFoundException("Support ticket was not found."));
        if (!ticket.getRequesterUserId().equals(currentUser.userId())) {
            throw new AccessDeniedException("Support ticket does not belong to the current user.");
        }
        return ticket;
    }

    private SupportDtos.SupportTicketDto toDto(SupportTicket ticket) {
        return new SupportDtos.SupportTicketDto(
                ticket.getId(),
                ticket.getCategory(),
                ticket.getStatus(),
                ticket.getSubject(),
                ticket.getReservationId(),
                ticket.getLocationId(),
                ticket.getCreatedAt(),
                ticket.getUpdatedAt());
    }

    private SupportDtos.SupportMessageDto toDto(SupportMessage message) {
        return new SupportDtos.SupportMessageDto(
                message.getId(),
                message.getTicketId(),
                message.getSenderUserId(),
                message.getSenderName(),
                message.getBody(),
                message.getAttachmentUrl(),
                message.getCreatedAt());
    }
}
