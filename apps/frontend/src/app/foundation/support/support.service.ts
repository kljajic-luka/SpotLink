import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { ApiClient, ApiPage } from '@foundation/networking';
import { CreateSupportTicketRequest, SupportMessage, SupportTicket } from './support.models';

@Injectable({ providedIn: 'root' })
export class SupportService {
  private readonly api = inject(ApiClient);

  listTickets(page = 0, size = 20): Observable<ApiPage<SupportTicket>> {
    return this.api.get<ApiPage<SupportTicket>>('/support/tickets', {
      params: {
        page,
        size,
      },
    });
  }

  createTicket(payload: CreateSupportTicketRequest): Observable<SupportTicket> {
    return this.api.post<SupportTicket, CreateSupportTicketRequest>('/support/tickets', payload);
  }

  listMessages(ticketId: string): Observable<SupportMessage[]> {
    return this.api.get<SupportMessage[]>(
      `/support/tickets/${encodeURIComponent(ticketId)}/messages`,
    );
  }

  sendMessage(ticketId: string, body: string): Observable<SupportMessage> {
    return this.api.post<SupportMessage, { body: string }>(
      `/support/tickets/${encodeURIComponent(ticketId)}/messages`,
      { body },
    );
  }
}
