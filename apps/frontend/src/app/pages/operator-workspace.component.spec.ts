import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { of, Subject } from 'rxjs';

import { SPOTLINK_APP_CONFIG, SpotLinkAppConfig } from '@foundation/core';
import {
  OperatorAccount,
  OperatorBookingDetail,
  OperatorDashboardSummary,
  OperatorResourceHealth,
  OperatorService,
} from '@foundation/operator';
import { Reservation } from '@foundation/reservations';
import { OperatorWorkspaceComponent } from './operator-workspace.component';

const appConfig: SpotLinkAppConfig = {
  appName: 'SpotLink test',
  baseApiUrl: 'https://api.test.spotlink.local',
  supportEmail: 'support@test.spotlink.local',
  supportUrl: 'https://help.test.spotlink.local/support',
  privacyPolicyUrl: 'https://help.test.spotlink.local/privacy',
  termsUrl: 'https://help.test.spotlink.local/terms',
  accountDeletionUrl: 'https://help.test.spotlink.local/account-deletion',
  defaultLocale: 'sr-RS',
};

describe('OperatorWorkspaceComponent', () => {
  let fixture: ComponentFixture<OperatorWorkspaceComponent>;
  let component: OperatorWorkspaceComponent;
  let operatorService: jasmine.SpyObj<OperatorService>;

  beforeEach(() => {
    operatorService = jasmine.createSpyObj<OperatorService>('OperatorService', [
      'getCurrentOperator',
      'getDashboardSummary',
      'getResourceHealth',
      'getUpcomingBookings',
      'getBookingDetail',
      'checkIn',
      'confirmBooking',
      'rejectBooking',
      'markNoShow',
      'cancelBooking',
      'pauseSales',
      'unpauseSales',
      'adjustSellableCapacity',
    ]);

    operatorService.getCurrentOperator.and.returnValue(of(operatorFixture));
    operatorService.getDashboardSummary.and.returnValue(of(summaryFixture));
    operatorService.getResourceHealth.and.returnValue(of([resourceFixture]));
    operatorService.getUpcomingBookings.and.returnValue(of(page([pendingReservationFixture])));
    operatorService.getBookingDetail.and.returnValue(of(bookingDetailFixture));

    TestBed.configureTestingModule({
      imports: [OperatorWorkspaceComponent],
      providers: [
        {
          provide: OperatorService,
          useValue: operatorService,
        },
        {
          provide: SPOTLINK_APP_CONFIG,
          useValue: appConfig,
        },
      ],
    });

    fixture = TestBed.createComponent(OperatorWorkspaceComponent);
    component = fixture.componentInstance;
    component.detail.set(bookingDetailFixture);
  });

  it('only enables partner confirmation actions while waiting on the operator', () => {
    expect(component.canConfirm('PENDING_OPERATOR_CONFIRMATION')).toBeTrue();
    expect(component.canReject('PENDING_OPERATOR_CONFIRMATION')).toBeTrue();
    expect(component.canCheckIn('PENDING_OPERATOR_CONFIRMATION')).toBeFalse();

    expect(component.canConfirm('CONFIRMED')).toBeFalse();
    expect(component.canReject('CONFIRMED')).toBeFalse();
    expect(component.canCheckIn('CONFIRMED')).toBeTrue();
  });

  it('renders partner confirmation controls for pending reservations only', () => {
    fixture.detectChanges();

    expect(actionButton(fixture, 'Potvrdi rezervaciju').disabled).toBeFalse();
    expect(actionButton(fixture, 'Odbij rezervaciju').disabled).toBeFalse();
    expect(actionButton(fixture, 'Check-in vozaca').disabled).toBeTrue();

    component.detail.set({
      ...bookingDetailFixture,
      reservation: {
        ...pendingReservationFixture,
        status: 'CONFIRMED',
        accessInstructionsVisible: true,
      },
    });
    fixture.detectChanges();

    expect(actionButton(fixture, 'Potvrdi rezervaciju').disabled).toBeTrue();
    expect(actionButton(fixture, 'Odbij rezervaciju').disabled).toBeTrue();
    expect(actionButton(fixture, 'Check-in vozaca').disabled).toBeFalse();
  });

  it('confirms a pending operator reservation with notes and refreshes the workspace', () => {
    const confirmResult = new Subject<Reservation>();
    operatorService.confirmBooking.and.returnValue(confirmResult.asObservable());
    spyOn(window, 'confirm');
    component.bookingActionText = 'Gate team accepted';

    component.runBookingAction('confirm');

    expect(window.confirm).not.toHaveBeenCalled();
    expect(operatorService.confirmBooking).toHaveBeenCalledWith('resv-001', {
      notes: 'Gate team accepted',
    });
    expect(component.bookingActionBusy()).toBe('confirm');

    confirmResult.next({ ...pendingReservationFixture, status: 'CONFIRMED' });
    confirmResult.complete();

    expect(component.bookingActionBusy()).toBeNull();
    expect(component.bookingActionText).toBe('');
    expect(component.flashMessage()).toBe('Rezervacija je potvrdjena.');
    expect(operatorService.getUpcomingBookings).toHaveBeenCalled();
    expect(operatorService.getBookingDetail).toHaveBeenCalledWith('resv-001');
  });

  it('requires operator acknowledgement before rejecting a reservation', () => {
    spyOn(window, 'confirm').and.returnValue(false);
    component.bookingActionText = 'No staffed access';

    component.runBookingAction('reject');

    expect(window.confirm).toHaveBeenCalledWith('Odbiti ovu rezervaciju?');
    expect(operatorService.rejectBooking).not.toHaveBeenCalled();
    expect(component.bookingActionBusy()).toBeNull();
  });

  it('rejects a pending operator reservation with a reason and refreshes the workspace', () => {
    const rejectResult = new Subject<Reservation>();
    operatorService.rejectBooking.and.returnValue(rejectResult.asObservable());
    spyOn(window, 'confirm').and.returnValue(true);
    component.bookingActionText = 'No staffed access';

    component.runBookingAction('reject');

    expect(operatorService.rejectBooking).toHaveBeenCalledWith('resv-001', {
      reason: 'No staffed access',
    });
    expect(component.bookingActionBusy()).toBe('reject');

    rejectResult.next({ ...pendingReservationFixture, status: 'REJECTED' });
    rejectResult.complete();

    expect(component.bookingActionBusy()).toBeNull();
    expect(component.bookingActionText).toBe('');
    expect(component.flashMessage()).toBe('Rezervacija je odbijena.');
    expect(operatorService.getUpcomingBookings).toHaveBeenCalled();
    expect(operatorService.getBookingDetail).toHaveBeenCalledWith('resv-001');
  });
});

function page<T>(content: T[]) {
  return {
    content,
    totalElements: content.length,
    totalPages: content.length ? 1 : 0,
    page: 0,
    size: 20,
  };
}

function actionButton(
  fixture: ComponentFixture<OperatorWorkspaceComponent>,
  label: string,
): HTMLButtonElement {
  const button = fixture.debugElement
    .queryAll(By.css('button'))
    .map((debugElement) => debugElement.nativeElement as HTMLButtonElement)
    .find((candidate) => candidate.textContent?.includes(label));

  expect(button).withContext(`Expected action button "${label}" to render`).toBeTruthy();
  return button!;
}

const operatorFixture: OperatorAccount = {
  id: 'op-001',
  displayName: 'Airport Partner',
  supportEmail: 'operator@test.spotlink.local',
  active: true,
  createdAt: '2026-04-25T09:00:00Z',
};

const summaryFixture: OperatorDashboardSummary = {
  activeLocations: 1,
  activeResources: 1,
  reservationsToday: 1,
  occupancyRate: 0.25,
  pendingSupportTickets: 0,
  grossRevenueCents: 2500,
  currency: 'RSD',
};

const resourceFixture: OperatorResourceHealth = {
  resourceId: 'resource-001',
  label: 'Airport Zone A',
  online: true,
  nextReservationAt: '2026-04-26T10:00:00Z',
};

const pendingReservationFixture: Reservation = {
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
  bookingCode: 'SL-1234ABCD',
  status: 'PENDING_OPERATOR_CONFIRMATION',
  paymentMode: 'PAY_ON_ARRIVAL',
  totalAmountCents: 2500,
  currency: 'RSD',
  accessInstructionsVisible: false,
  cancellationPolicy: 'FULL_REFUND_BEFORE_START',
  cancellableUntil: '2026-04-26T10:00:00Z',
  refundEligibleCents: 2500,
  operatorConfirmationExpiresAt: '2026-04-25T10:15:00Z',
  createdAt: '2026-04-25T10:00:00Z',
};

const bookingDetailFixture: OperatorBookingDetail = {
  reservation: pendingReservationFixture,
  hold: {
    id: 'hold-001',
    inventoryPoolId: 'pool-001',
    status: 'CONSUMED',
    expiresAt: '2026-04-25T10:15:00Z',
    paymentMode: 'PAY_ON_ARRIVAL',
  },
  checkin: undefined,
  timeline: [
    {
      id: 'event-001',
      eventType: 'OPERATOR_CONFIRMATION_REQUESTED',
      actorType: 'SYSTEM',
      payload: { deadline: '2026-04-25T10:15:00Z' },
      occurredAt: '2026-04-25T10:00:00Z',
    },
  ],
  paymentAttempts: [],
  refunds: [],
  supportCases: [],
};
