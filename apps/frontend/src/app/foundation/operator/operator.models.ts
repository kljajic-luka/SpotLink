import { ApiPage } from '@foundation/networking';
import { BookingDetail, Reservation } from '@foundation/reservations';

export interface OperatorAccount {
  id: string;
  displayName: string;
  legalName?: string;
  supportEmail?: string;
  active: boolean;
  createdAt: string;
}

export interface OperatorDashboardSummary {
  activeLocations: number;
  activeResources: number;
  reservationsToday: number;
  occupancyRate: number;
  pendingSupportTickets: number;
  grossRevenueCents: number;
  currency: string;
}

export interface OperatorResourceHealth {
  resourceId: string;
  label: string;
  online: boolean;
  currentReservationId?: string;
  nextReservationAt?: string;
  attentionRequired?: string;
}

export interface InventoryControl {
  resourceId: string;
  inventoryPoolId: string;
  paused: boolean;
  pauseReason?: string;
  baseCapacity: number;
}

export interface OperatorBookingActionRequest {
  reason?: string;
  notes?: string;
}

export interface CapacityOverrideRequest {
  sellableCapacity: number;
  reason?: string;
}

export interface OperatorUpcomingBookingsPage extends ApiPage<Reservation> {}

export interface OperatorBookingDetail extends BookingDetail {}
