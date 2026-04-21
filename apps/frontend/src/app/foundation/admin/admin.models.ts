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
  status: 'ACTIVE' | 'SUSPENDED' | 'DELETED';
  createdAt: string;
}
