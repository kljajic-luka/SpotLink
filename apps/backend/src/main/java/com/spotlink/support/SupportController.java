package com.spotlink.support;

import com.spotlink.core.ApiPage;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SupportController {

    private final SupportService supportService;

    public SupportController(SupportService supportService) {
        this.supportService = supportService;
    }

    @GetMapping({"/support/tickets", "/v1/support/tickets"})
    ApiPage<SupportDtos.SupportTicketDto> tickets(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return supportService.mine(page, size);
    }

    @PostMapping({"/support/tickets", "/v1/support/tickets"})
    @ResponseStatus(HttpStatus.CREATED)
    SupportDtos.SupportTicketDto create(@Valid @RequestBody SupportDtos.CreateSupportTicketRequest request) {
        return supportService.create(request);
    }

    @GetMapping({"/support/tickets/{ticketId}/messages", "/v1/support/tickets/{ticketId}/messages"})
    List<SupportDtos.SupportMessageDto> messages(@PathVariable UUID ticketId) {
        return supportService.messages(ticketId);
    }

    @PostMapping({"/support/tickets/{ticketId}/messages", "/v1/support/tickets/{ticketId}/messages"})
    @ResponseStatus(HttpStatus.CREATED)
    SupportDtos.SupportMessageDto addMessage(
            @PathVariable UUID ticketId,
            @Valid @RequestBody SupportDtos.CreateMessageRequest request) {
        return supportService.addMessage(ticketId, request);
    }
}
