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
