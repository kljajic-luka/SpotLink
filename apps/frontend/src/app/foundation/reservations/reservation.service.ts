import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { ApiClient, ApiPage } from '@foundation/networking';
import {
  BookingDetail,
  CreateReservationRequest,
  Reservation,
  ReservationQuote,
  ReservationQuoteRequest,
} from './reservation.models';

@Injectable({ providedIn: 'root' })
export class ReservationService {
  private readonly api = inject(ApiClient);

  listMine(page = 0, size = 20): Observable<ApiPage<Reservation>> {
    return this.api.get<ApiPage<Reservation>>('/reservations/me', {
      params: {
        page,
        size,
      },
    });
  }

  getReservation(reservationId: string): Observable<Reservation> {
    return this.api.get<Reservation>(`/reservations/${encodeURIComponent(reservationId)}`);
  }

  getReservationDetail(reservationId: string): Observable<BookingDetail> {
    return this.api.get<BookingDetail>(`/reservations/${encodeURIComponent(reservationId)}/detail`);
  }

  quote(payload: ReservationQuoteRequest): Observable<ReservationQuote> {
    return this.api.post<ReservationQuote, ReservationQuoteRequest>('/reservations/quote', payload);
  }

  create(payload: CreateReservationRequest): Observable<Reservation> {
    return this.api.post<Reservation, CreateReservationRequest>('/reservations', payload);
  }

  cancel(reservationId: string, reason?: string): Observable<Reservation> {
    return this.api.post<Reservation, { reason?: string }>(
      `/reservations/${encodeURIComponent(reservationId)}/cancel`,
      { reason },
    );
  }
}
