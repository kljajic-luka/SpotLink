export type ReservationStatus =
  | 'DRAFT'
  | 'PENDING_PAYMENT'
  | 'PENDING_OPERATOR_CONFIRMATION'
  | 'CONFIRMED'
  | 'ACTIVE'
  | 'COMPLETED'
  | 'CANCELLED'
  | 'EXPIRED'
  | 'REJECTED'
  | 'DISPUTED'
  | 'NO_SHOW';

export type PaymentMode = 'ONLINE' | 'PAY_ON_ARRIVAL';

export type ReservationCancellationPolicy = 'FULL_REFUND_BEFORE_START';

export type BookingHoldStatus = 'ACTIVE' | 'CONSUMED' | 'RELEASED' | 'EXPIRED';

export type BookingActorType = 'CUSTOMER' | 'OPERATOR' | 'ADMIN' | 'SYSTEM' | 'PAYMENT_PROVIDER';

export type BookingEventType =
  | 'LEGACY_IMPORTED'
  | 'CREATED'
  | 'HOLD_CREATED'
  | 'HOLD_EXPIRED'
  | 'STATUS_CHANGED'
  | 'OPERATOR_CONFIRMATION_REQUESTED'
  | 'OPERATOR_CONFIRMED'
  | 'OPERATOR_REJECTED'
  | 'PAYMENT_AUTHORIZED'
  | 'PAYMENT_FAILED'
  | 'CONFIRMED'
  | 'CANCELLED'
  | 'OPERATOR_CANCELLED'
  | 'CHECKED_IN'
  | 'NO_SHOW'
  | 'ADMIN_OVERRIDE'
  | 'REFUND_MARKED';

export type CheckinStatus = 'CHECKED_IN' | 'COMPLETED' | 'NO_SHOW';

export type PaymentAttemptStatus =
  | 'PENDING'
  | 'REQUIRES_ACTION'
  | 'AUTHORIZED'
  | 'FAILED'
  | 'CANCELLED'
  | 'REFUND_MARKED';

export type PaymentProviderEventStatus = 'RECEIVED' | 'PROCESSED' | 'FAILED';

export type RefundStatus = 'MARKED' | 'PROCESSED' | 'FAILED';

export interface Reservation {
  id: string;
  customerId: string;
  operatorId: string;
  locationId: string;
  resourceId: string;
  inventoryPoolId?: string;
  holdId?: string;
  vehicleId?: string;
  startsAt: string;
  endsAt: string;
  timezone: string;
  bookingCode?: string;
  status: ReservationStatus;
  paymentMode: PaymentMode;
  totalAmountCents: number;
  currency: string;
  accessInstructionsVisible: boolean;
  paymentExpiresAt?: string;
  cancellationPolicy?: ReservationCancellationPolicy;
  cancellableUntil?: string;
  refundEligibleCents?: number;
  operatorConfirmationExpiresAt?: string;
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
  paymentMode?: PaymentMode;
  idempotencyKey: string;
}

export interface BookingHold {
  id: string;
  inventoryPoolId: string;
  status: BookingHoldStatus;
  expiresAt: string;
  paymentMode: PaymentMode;
}

export interface BookingEvent {
  id: string;
  eventType: BookingEventType;
  actorType: BookingActorType;
  actorId?: string;
  notes?: string;
  payload?: Record<string, unknown>;
  occurredAt: string;
}

export interface Checkin {
  id: string;
  status: CheckinStatus;
  operatorUserId: string;
  checkinAt: string;
  checkoutAt?: string;
  notes?: string;
}

export interface PaymentProviderEvent {
  id: string;
  provider: string;
  externalEventId: string;
  eventType: string;
  status: PaymentProviderEventStatus;
  processedAt?: string;
}

export interface PaymentAttempt {
  id: string;
  reservationId: string;
  provider: string;
  status: PaymentAttemptStatus;
  paymentMode: PaymentMode;
  amountCents: number;
  currency: string;
  providerReference?: string;
  failureCode?: string;
  failureMessage?: string;
  lastTransitionAt: string;
  providerEvents: PaymentProviderEvent[];
}

export interface Refund {
  id: string;
  reservationId: string;
  paymentAttemptId?: string;
  amountCents: number;
  currency: string;
  status: RefundStatus;
  reason?: string;
  providerReference?: string;
  markedByUserId?: string;
  markedAt: string;
}

export interface SupportCase {
  id: string;
  category: string;
  status: string;
  subject: string;
  reservationId?: string;
  locationId?: string;
  createdAt: string;
  updatedAt: string;
}

export interface BookingDetail {
  reservation: Reservation;
  hold?: BookingHold;
  checkin?: Checkin;
  timeline: BookingEvent[];
  paymentAttempts: PaymentAttempt[];
  refunds: Refund[];
  supportCases: SupportCase[];
}
