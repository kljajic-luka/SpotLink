import { Reservation, ReservationStatus } from './reservation.models';

const DATETIME_FORMATTER = new Intl.DateTimeFormat(undefined, {
  dateStyle: 'medium',
  timeStyle: 'short',
});

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
    case 'NO_SHOW':
    case 'CANCELLED':
    case 'EXPIRED':
    case 'DISPUTED':
      return 'danger';
    case 'DRAFT':
      return 'neutral';
  }
}

function formatDateTime(value: string): string {
  if (!value) {
    return 'N/A';
  }

  const parsed = new Date(value);
  if (Number.isNaN(parsed.getTime())) {
    return 'N/A';
  }

  return DATETIME_FORMATTER.format(parsed);
}

function formatMoney(amountCents: number, currency: string): string {
  const normalizedCurrency = currency?.trim().toUpperCase();
  if (!normalizedCurrency || normalizedCurrency.length !== 3) {
    return `${(amountCents / 100).toFixed(2)}`;
  }

  try {
    return new Intl.NumberFormat(undefined, {
      style: 'currency',
      currency: normalizedCurrency,
    }).format(amountCents / 100);
  } catch {
    return `${(amountCents / 100).toFixed(2)} ${normalizedCurrency}`;
  }
}
