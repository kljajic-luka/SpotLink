export type ReservationStatus =
  | 'DRAFT'
  | 'PENDING_PAYMENT'
  | 'CONFIRMED'
  | 'ACTIVE'
  | 'COMPLETED'
  | 'CANCELLED'
  | 'EXPIRED'
  | 'DISPUTED';

export interface Reservation {
  id: string;
  customerId: string;
  operatorId: string;
  locationId: string;
  resourceId: string;
  vehicleId?: string;
  startsAt: string;
  endsAt: string;
  timezone: string;
  status: ReservationStatus;
  totalAmountCents: number;
  currency: string;
  accessInstructionsVisible: boolean;
  createdAt: string;
  updatedAt?: string;
}

export interface ReservationQuoteRequest {
  resourceId: string;
  vehicleId?: string;
  startsAt: string;
  endsAt: string;
  promoCode?: string;
}

export interface ReservationQuote {
  resourceId: string;
  startsAt: string;
  endsAt: string;
  subtotalCents: number;
  feesCents: number;
  discountCents: number;
  totalAmountCents: number;
  currency: string;
  expiresAt: string;
}

export interface CreateReservationRequest extends ReservationQuoteRequest {
  quoteId?: string;
  paymentMethodId?: string;
  idempotencyKey: string;
}

export interface ReservationTimelineItem {
  label: string;
  occurredAt: string;
  status: ReservationStatus;
}
