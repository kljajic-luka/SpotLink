import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { ApiClient, ApiPage } from '@foundation/networking';
import {
  AdminActionRequest,
  AdminAuditEvent,
  AdminBookingDetail,
  AdminBookingSearchFilters,
  AdminBookingsPage,
  AdminDashboardSummary,
  AdminPaymentAttempt,
  AdminSupportCase,
  AdminUserSummary,
  PauseOperationResult,
  RefundMarkerRequest,
  AdminRefund,
} from './admin.models';

@Injectable({ providedIn: 'root' })
export class AdminService {
  private readonly api = inject(ApiClient);

  getDashboardSummary(): Observable<AdminDashboardSummary> {
    return this.api.get<AdminDashboardSummary>('/admin/dashboard/summary');
  }

  listUsers(page = 0, size = 25): Observable<ApiPage<AdminUserSummary>> {
    return this.api.get<ApiPage<AdminUserSummary>>('/admin/users', {
      params: {
        page,
        size,
      },
    });
  }

  listAuditEvents(page = 0, size = 25): Observable<ApiPage<AdminAuditEvent>> {
    return this.api.get<ApiPage<AdminAuditEvent>>('/admin/audit-events', {
      params: {
        page,
        size,
      },
    });
  }

  searchBookings(filters: AdminBookingSearchFilters = {}): Observable<AdminBookingsPage> {
    return this.api.get<AdminBookingsPage>('/admin/bookings', {
      params: {
        query: filters.query,
        operatorId: filters.operatorId,
        locationId: filters.locationId,
        status: filters.status,
        page: filters.page ?? 0,
        size: filters.size ?? 25,
      },
    });
  }

  getBookingDetail(reservationId: string): Observable<AdminBookingDetail> {
    return this.api.get<AdminBookingDetail>(`/admin/bookings/${encodeURIComponent(reservationId)}`);
  }

  cancelBooking(reservationId: string, reason?: string): Observable<AdminBookingDetail['reservation']> {
    return this.api.post<AdminBookingDetail['reservation'], AdminActionRequest>(
      `/admin/bookings/${encodeURIComponent(reservationId)}/cancel`,
      { reason },
    );
  }

  markRefund(reservationId: string, payload: RefundMarkerRequest): Observable<AdminRefund> {
    return this.api.post<AdminRefund, RefundMarkerRequest>(
      `/admin/bookings/${encodeURIComponent(reservationId)}/refund-marker`,
      payload,
    );
  }

  pauseLocation(locationId: string, reason?: string): Observable<PauseOperationResult> {
    return this.api.post<PauseOperationResult, AdminActionRequest>(
      `/admin/locations/${encodeURIComponent(locationId)}/pause`,
      { reason },
    );
  }

  pauseOperator(operatorId: string, reason?: string): Observable<PauseOperationResult> {
    return this.api.post<PauseOperationResult, AdminActionRequest>(
      `/admin/operators/${encodeURIComponent(operatorId)}/pause`,
      { reason },
    );
  }

  listPaymentAttempts(
    reservationId?: string,
    page = 0,
    size = 25,
  ): Observable<ApiPage<AdminPaymentAttempt>> {
    return this.api.get<ApiPage<AdminPaymentAttempt>>('/admin/payment-attempts', {
      params: {
        reservationId,
        page,
        size,
      },
    });
  }

  listSupportCases(page = 0, size = 25): Observable<ApiPage<AdminSupportCase>> {
    return this.api.get<ApiPage<AdminSupportCase>>('/admin/support-cases', {
      params: {
        page,
        size,
      },
    });
  }
}
