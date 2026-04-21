export type SupportTicketStatus = 'OPEN' | 'WAITING_ON_CUSTOMER' | 'WAITING_ON_OPERATOR' | 'RESOLVED';

export type SupportTicketCategory =
  | 'RESERVATION'
  | 'PAYMENT'
  | 'LOCATION_ACCESS'
  | 'SAFETY'
  | 'ACCOUNT'
  | 'OTHER';

export interface SupportTicket {
  id: string;
  category: SupportTicketCategory;
  status: SupportTicketStatus;
  subject: string;
  reservationId?: string;
  locationId?: string;
  createdAt: string;
  updatedAt?: string;
}

export interface SupportMessage {
  id: string;
  ticketId: string;
  senderUserId: string;
  senderName?: string;
  body: string;
  attachmentUrl?: string;
  createdAt: string;
}

export interface CreateSupportTicketRequest {
  category: SupportTicketCategory;
  subject: string;
  body: string;
  reservationId?: string;
  locationId?: string;
}
