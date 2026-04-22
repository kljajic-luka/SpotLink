import { Reservation } from './reservation.models';
import { toReservationCardViewModel } from './reservation.view-model';

const baseReservation: Reservation = {
  id: 'rez_1',
  customerId: 'cust_1',
  operatorId: 'op_1',
  locationId: 'loc_1',
  resourceId: 'res_1',
  startsAt: '2026-04-22T12:00:00.000Z',
  endsAt: '2026-04-22T14:00:00.000Z',
  timezone: 'Europe/Belgrade',
  status: 'PENDING_PAYMENT',
  totalAmountCents: 1299,
  currency: 'USD',
  accessInstructionsVisible: false,
  createdAt: '2026-04-22T10:00:00.000Z',
};

describe('toReservationCardViewModel', () => {
  it('mapira status PENDING_PAYMENT na warning tone', () => {
    const vm = toReservationCardViewModel(baseReservation, 'Lokacija A');

    expect(vm.statusTone).toBe('warning');
    expect(vm.title).toBe('Lokacija A');
  });

  it('vraca N/A kada su datumi nevalidni', () => {
    const vm = toReservationCardViewModel({
      ...baseReservation,
      startsAt: '',
      endsAt: 'nije-datum',
    });

    expect(vm.schedule).toBe('N/A - N/A');
  });

  it('koristi fallback format kada valuta nije validna', () => {
    const vm = toReservationCardViewModel({
      ...baseReservation,
      currency: '',
      totalAmountCents: 500,
    });

    expect(vm.amount).toBe('5.00');
  });
});
