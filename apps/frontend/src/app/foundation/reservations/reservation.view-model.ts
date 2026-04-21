import { Reservation, ReservationStatus } from './reservation.models';

export interface ReservationCardViewModel {
  id: string;
  title: string;
  schedule: string;
  amount: string;
  status: ReservationStatus;
  statusTone: 'neutral' | 'info' | 'success' | 'warning' | 'danger';
}

export function toReservationCardViewModel(
  reservation: Reservation,
  locationName = 'Parking reservation',
): ReservationCardViewModel {
  return {
    id: reservation.id,
    title: locationName,
    schedule: `${formatDateTime(reservation.startsAt)} - ${formatDateTime(reservation.endsAt)}`,
    amount: formatMoney(reservation.totalAmountCents, reservation.currency),
    status: reservation.status,
    statusTone: statusTone(reservation.status),
  };
}

function statusTone(status: ReservationStatus): ReservationCardViewModel['statusTone'] {
  switch (status) {
    case 'CONFIRMED':
    case 'ACTIVE':
    case 'COMPLETED':
      return 'success';
    case 'PENDING_PAYMENT':
      return 'warning';
    case 'CANCELLED':
    case 'EXPIRED':
    case 'DISPUTED':
      return 'danger';
    case 'DRAFT':
      return 'neutral';
  }
}

function formatDateTime(value: string): string {
  return new Intl.DateTimeFormat(undefined, {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(new Date(value));
}

function formatMoney(amountCents: number, currency: string): string {
  return new Intl.NumberFormat(undefined, {
    style: 'currency',
    currency,
  }).format(amountCents / 100);
}
