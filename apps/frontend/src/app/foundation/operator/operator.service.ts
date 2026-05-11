import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { ApiClient } from '@foundation/networking';
import {
  CapacityOverrideRequest,
  InventoryControl,
  OperatorBookingActionRequest,
  OperatorBookingDetail,
  OperatorAccount,
  OperatorDashboardSummary,
  OperatorUpcomingBookingsPage,
  OperatorResourceHealth,
} from './operator.models';

@Injectable({ providedIn: 'root' })
export class OperatorService {
  private readonly api = inject(ApiClient);

  getCurrentOperator(): Observable<OperatorAccount> {
    return this.api.get<OperatorAccount>('/operator/me');
  }

  getDashboardSummary(): Observable<OperatorDashboardSummary> {
    return this.api.get<OperatorDashboardSummary>('/operator/dashboard/summary');
  }

  getResourceHealth(): Observable<OperatorResourceHealth[]> {
    return this.api.get<OperatorResourceHealth[]>('/operator/resources/health');
  }

  getUpcomingBookings(page = 0, size = 20): Observable<OperatorUpcomingBookingsPage> {
    return this.api.get<OperatorUpcomingBookingsPage>('/operator/bookings/upcoming', {
      params: {
        page,
        size,
      },
    });
  }

  getBookingDetail(reservationId: string): Observable<OperatorBookingDetail> {
    return this.api.get<OperatorBookingDetail>(
      `/operator/bookings/${encodeURIComponent(reservationId)}`,
    );
  }

  checkIn(reservationId: string, payload: OperatorBookingActionRequest): Observable<OperatorBookingDetail['reservation']> {
    return this.api.post<OperatorBookingDetail['reservation'], OperatorBookingActionRequest>(
      `/operator/bookings/${encodeURIComponent(reservationId)}/check-in`,
      payload,
    );
  }

  markNoShow(
    reservationId: string,
    payload: OperatorBookingActionRequest,
  ): Observable<OperatorBookingDetail['reservation']> {
    return this.api.post<OperatorBookingDetail['reservation'], OperatorBookingActionRequest>(
      `/operator/bookings/${encodeURIComponent(reservationId)}/no-show`,
      payload,
    );
  }

  confirmBooking(
    reservationId: string,
    payload: OperatorBookingActionRequest,
  ): Observable<OperatorBookingDetail['reservation']> {
    return this.api.post<OperatorBookingDetail['reservation'], OperatorBookingActionRequest>(
      `/operator/bookings/${encodeURIComponent(reservationId)}/confirm`,
      payload,
    );
  }

  rejectBooking(
    reservationId: string,
    payload: OperatorBookingActionRequest,
  ): Observable<OperatorBookingDetail['reservation']> {
    return this.api.post<OperatorBookingDetail['reservation'], OperatorBookingActionRequest>(
      `/operator/bookings/${encodeURIComponent(reservationId)}/reject`,
      payload,
    );
  }

  cancelBooking(
    reservationId: string,
    payload: OperatorBookingActionRequest,
  ): Observable<OperatorBookingDetail['reservation']> {
    return this.api.post<OperatorBookingDetail['reservation'], OperatorBookingActionRequest>(
      `/operator/bookings/${encodeURIComponent(reservationId)}/cancel`,
      payload,
    );
  }

  confirmManualBooking(
    reservationId: string,
    payload: OperatorBookingActionRequest,
  ): Observable<OperatorBookingDetail['reservation']> {
    return this.api.post<OperatorBookingDetail['reservation'], OperatorBookingActionRequest>(
      `/operator/bookings/${encodeURIComponent(reservationId)}/confirm`,
      payload,
    );
  }

  rejectManualBooking(
    reservationId: string,
    payload: OperatorBookingActionRequest,
  ): Observable<OperatorBookingDetail['reservation']> {
    return this.api.post<OperatorBookingDetail['reservation'], OperatorBookingActionRequest>(
      `/operator/bookings/${encodeURIComponent(reservationId)}/reject`,
      payload,
    );
  }

  pauseSales(resourceId: string, reason?: string): Observable<InventoryControl> {
    return this.api.post<InventoryControl, OperatorBookingActionRequest>(
      `/operator/resources/${encodeURIComponent(resourceId)}/pause`,
      { reason },
    );
  }

  unpauseSales(resourceId: string): Observable<InventoryControl> {
    return this.api.post<InventoryControl>(
      `/operator/resources/${encodeURIComponent(resourceId)}/unpause`,
      {},
    );
  }

  adjustSellableCapacity(
    resourceId: string,
    payload: CapacityOverrideRequest,
  ): Observable<InventoryControl> {
    return this.api.post<InventoryControl, CapacityOverrideRequest>(
      `/operator/resources/${encodeURIComponent(resourceId)}/capacity`,
      payload,
    );
  }
}
