import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, Subject } from 'rxjs';

import { AdminService } from '@foundation/admin';
import { AdminPortalComponent } from './admin-portal.component';

describe('AdminPortalComponent', () => {
  let fixture: ComponentFixture<AdminPortalComponent>;
  let component: AdminPortalComponent;
  let adminService: jasmine.SpyObj<AdminService>;

  beforeEach(() => {
    adminService = jasmine.createSpyObj<AdminService>('AdminService', [
      'searchBookings',
      'getBookingDetail',
      'cancelBooking',
      'markRefund',
      'pauseLocation',
      'pauseOperator',
      'processAccountDeletion',
      'listPaymentAttempts',
      'listSupportCases',
      'listAuditEvents',
    ]);

    adminService.searchBookings.and.returnValue(of(page([reservationFixture])));
    adminService.getBookingDetail.and.returnValue(of(bookingDetailFixture));
    adminService.listPaymentAttempts.and.returnValue(of(page(bookingDetailFixture.paymentAttempts)));
    adminService.listSupportCases.and.returnValue(of(page(bookingDetailFixture.supportCases)));
    adminService.listAuditEvents.and.returnValue(of(page([])));
    adminService.markRefund.and.returnValue(of(bookingDetailFixture.refunds[0]));
    adminService.pauseLocation.and.returnValue(of({ targetId: 'loc-001', affectedPools: 1 }));
    adminService.pauseOperator.and.returnValue(of({ targetId: 'op-001', affectedPools: 2 }));
    adminService.processAccountDeletion.and.returnValue(of({
      ticketId: 'support-delete-001',
      userId: 'user-001',
      status: 'PROCESSED',
      blockers: [],
      processedAt: '2026-04-26T10:15:00Z',
    }));

    TestBed.configureTestingModule({
      imports: [AdminPortalComponent],
      providers: [
        {
          provide: AdminService,
          useValue: adminService,
        },
      ],
    });

    fixture = TestBed.createComponent(AdminPortalComponent);
    component = fixture.componentInstance;
    component.detail.set(bookingDetailFixture);
  });

  it('requires confirmation before manual cancel', () => {
    spyOn(window, 'confirm').and.returnValue(false);

    component.cancelBooking();

    expect(adminService.cancelBooking).not.toHaveBeenCalled();
    expect(component.actionBusy()).toBeNull();
  });

  it('shows loading and refreshes audit-visible data after manual cancel succeeds', () => {
    const cancelResult = new Subject<any>();
    adminService.cancelBooking.and.returnValue(cancelResult.asObservable());
    spyOn(window, 'confirm').and.returnValue(true);
    component.actionReason = 'manual override';

    component.cancelBooking();

    expect(adminService.cancelBooking).toHaveBeenCalledWith('resv-001', 'manual override');
    expect(component.actionBusy()).toBe('cancel');

    cancelResult.next({ ...reservationFixture, status: 'CANCELLED' });
    cancelResult.complete();

    expect(component.actionBusy()).toBeNull();
    expect(component.flashMessage()).toContain('Audit events refreshed');
    expect(adminService.getBookingDetail).toHaveBeenCalledWith('resv-001');
    expect(adminService.listAuditEvents).toHaveBeenCalled();
  });

  it('reports manual action failures', () => {
    const cancelResult = new Subject<any>();
    adminService.cancelBooking.and.returnValue(cancelResult.asObservable());
    spyOn(window, 'confirm').and.returnValue(true);

    component.cancelBooking();
    cancelResult.error({ error: { message: 'Invalid transition' } });

    expect(component.actionBusy()).toBeNull();
    expect(component.actionError()).toBe('Invalid transition');
  });

  it('processes account deletion support cases through the admin service', () => {
    spyOn(window, 'confirm').and.returnValue(true);

    component.processAccountDeletion(accountDeletionSupportCase);

    expect(adminService.processAccountDeletion).toHaveBeenCalledWith('support-delete-001');
    expect(component.flashMessage()).toContain('Account deletion processed');
    expect(adminService.listSupportCases).toHaveBeenCalled();
    expect(adminService.listAuditEvents).toHaveBeenCalled();
  });

  it('shows blockers when account deletion fulfillment is blocked', () => {
    adminService.processAccountDeletion.and.returnValue(of({
      ticketId: 'support-delete-001',
      userId: 'user-001',
      status: 'BLOCKED',
      blockers: [
        {
          code: 'ACTIVE_OR_FUTURE_RESERVATIONS',
          message: 'Resolve active or future reservations before completing account deletion.',
          count: 1,
        },
      ],
    }));
    spyOn(window, 'confirm').and.returnValue(true);

    component.processAccountDeletion(accountDeletionSupportCase);

    expect(component.actionError()).toContain('ACTIVE_OR_FUTURE_RESERVATIONS (1)');
  });
});

function page<T>(content: T[]) {
  return {
    content,
    totalElements: content.length,
    totalPages: content.length ? 1 : 0,
    page: 0,
    size: 25,
  };
}

const reservationFixture = {
  id: 'resv-001',
  customerId: 'user-001',
  operatorId: 'op-001',
  locationId: 'loc-001',
  resourceId: 'resource-001',
  inventoryPoolId: 'pool-001',
  holdId: 'hold-001',
  startsAt: '2026-04-26T10:00:00Z',
  endsAt: '2026-04-26T12:00:00Z',
  timezone: 'Europe/Belgrade',
  status: 'CONFIRMED' as const,
  paymentMode: 'PAY_ON_ARRIVAL' as const,
  totalAmountCents: 530,
  currency: 'RSD',
  accessInstructionsVisible: true,
  createdAt: '2026-04-25T10:00:00Z',
};

const bookingDetailFixture = {
  reservation: reservationFixture,
  hold: {
    id: 'hold-001',
    inventoryPoolId: 'pool-001',
    status: 'CONSUMED' as const,
    expiresAt: '2026-04-26T10:15:00Z',
    paymentMode: 'PAY_ON_ARRIVAL' as const,
  },
  checkin: undefined,
  timeline: [
    {
      id: 'event-001',
      eventType: 'ADMIN_OVERRIDE' as const,
      actorType: 'ADMIN' as const,
      actorId: 'admin-001',
      notes: 'manual override',
      payload: { reason: 'manual override' },
      occurredAt: '2026-04-26T10:05:00Z',
    },
  ],
  paymentAttempts: [
    {
      id: 'attempt-001',
      reservationId: 'resv-001',
      provider: 'PAY_ON_ARRIVAL',
      status: 'AUTHORIZED' as const,
      paymentMode: 'PAY_ON_ARRIVAL' as const,
      amountCents: 530,
      currency: 'RSD',
      lastTransitionAt: '2026-04-26T10:05:00Z',
      providerEvents: [],
    },
  ],
  refunds: [
    {
      id: 'refund-001',
      reservationId: 'resv-001',
      amountCents: 450,
      currency: 'RSD',
      status: 'MARKED' as const,
      reason: 'refund marker',
      markedAt: '2026-04-26T10:10:00Z',
    },
  ],
  supportCases: [
    {
      id: 'support-001',
      category: 'LOCATION_ACCESS',
      status: 'OPEN',
      subject: 'Gate issue',
      reservationId: 'resv-001',
      locationId: 'loc-001',
      createdAt: '2026-04-26T10:02:00Z',
      updatedAt: '2026-04-26T10:03:00Z',
    },
  ],
};

const accountDeletionSupportCase = {
  id: 'support-delete-001',
  category: 'ACCOUNT',
  status: 'OPEN',
  subject: 'Account deletion request',
  createdAt: '2026-04-26T10:02:00Z',
  updatedAt: '2026-04-26T10:03:00Z',
};
