import {
  BookingDetail,
  PaymentAttempt,
  Refund,
  Reservation,
  ReservationStatus,
  SupportCase,
} from '@foundation/reservations';
import { ApiPage } from '@foundation/networking';

export interface AdminDashboardSummary {
  users: number;
  operators: number;
  activeReservations: number;
  openSupportTickets: number;
  grossMarketplaceVolumeCents: number;
  currency: string;
}

export interface AdminAuditEvent {
  id: string;
  actorUserId: string;
  action: string;
  resourceType: string;
  resourceId: string;
  createdAt: string;
  metadata?: Record<string, unknown>;
}

export interface AdminUserSummary {
  id: string;
  email: string;
  name: string;
  roles: string[];
  status: 'INCOMPLETE' | 'ACTIVE' | 'SUSPENDED' | 'DELETED';
  createdAt: string;
}

export interface AdminBookingSearchFilters {
  query?: string;
  operatorId?: string;
  locationId?: string;
  status?: ReservationStatus;
  page?: number;
  size?: number;
}

export interface AdminActionRequest {
  reason?: string;
}

export interface RefundMarkerRequest extends AdminActionRequest {
  amountCents?: number;
}

export interface PauseOperationResult {
  targetId: string;
  affectedPools: number;
  reason?: string;
}

export interface AdminBookingsPage extends ApiPage<Reservation> {}

export interface AdminBookingDetail extends BookingDetail {}

export type AdminPaymentAttempt = PaymentAttempt;

export type AdminSupportCase = SupportCase;

export type AdminRefund = Refund;
