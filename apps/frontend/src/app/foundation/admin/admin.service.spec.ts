import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { SPOTLINK_APP_CONFIG, SpotLinkAppConfig } from '@foundation/core';
import { AdminService } from './admin.service';

const config: SpotLinkAppConfig = {
  appName: 'SpotLink test',
  baseApiUrl: 'https://api.test.spotlink.local',
  supportEmail: 'support@test.spotlink.local',
  defaultLocale: 'sr-RS',
};

describe('AdminService', () => {
  let service: AdminService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        AdminService,
        provideHttpClient(),
        provideHttpClientTesting(),
        {
          provide: SPOTLINK_APP_CONFIG,
          useValue: config,
        },
      ],
    });

    service = TestBed.inject(AdminService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('searches bookings with typed admin filters', () => {
    service
      .searchBookings({
        query: 'resv-001',
        status: 'CONFIRMED',
        page: 2,
        size: 10,
      })
      .subscribe((page) => {
        expect(page.content[0]?.status).toBe('CONFIRMED');
      });

    const req = httpMock.expectOne(
      (request) => request.url === 'https://api.test.spotlink.local/admin/bookings',
    );

    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('query')).toBe('resv-001');
    expect(req.request.params.get('status')).toBe('CONFIRMED');
    expect(req.request.params.get('page')).toBe('2');
    expect(req.request.params.get('size')).toBe('10');

    req.flush({
      content: [reservationFixture],
      totalElements: 1,
      totalPages: 1,
      page: 2,
      size: 10,
    });
  });

  it('loads booking detail with timeline, payment attempts, support cases, and refunds', () => {
    service.getBookingDetail('resv-001').subscribe((detail) => {
      expect(detail.timeline[0]?.eventType).toBe('ADMIN_OVERRIDE');
      expect(detail.paymentAttempts[0]?.status).toBe('AUTHORIZED');
      expect(detail.supportCases[0]?.subject).toBe('Gate issue');
      expect(detail.refunds[0]?.status).toBe('MARKED');
    });

    const req = httpMock.expectOne(
      'https://api.test.spotlink.local/admin/bookings/resv-001',
    );

    expect(req.request.method).toBe('GET');
    req.flush(bookingDetailFixture);
  });

  it('posts critical admin actions to real backend endpoints', () => {
    service.cancelBooking('resv-001', 'manual override').subscribe();
    let req = httpMock.expectOne(
      'https://api.test.spotlink.local/admin/bookings/resv-001/cancel',
    );
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ reason: 'manual override' });
    req.flush({ ...reservationFixture, status: 'CANCELLED' });

    service.markRefund('resv-001', { amountCents: 450, reason: 'refund marker' }).subscribe();
    req = httpMock.expectOne(
      'https://api.test.spotlink.local/admin/bookings/resv-001/refund-marker',
    );
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ amountCents: 450, reason: 'refund marker' });
    req.flush(bookingDetailFixture.refunds[0]);

    service.pauseLocation('loc-001', 'maintenance').subscribe();
    req = httpMock.expectOne('https://api.test.spotlink.local/admin/locations/loc-001/pause');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ reason: 'maintenance' });
    req.flush({ targetId: 'loc-001', affectedPools: 2, reason: 'maintenance' });

    service.pauseOperator('op-001', 'risk hold').subscribe();
    req = httpMock.expectOne('https://api.test.spotlink.local/admin/operators/op-001/pause');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ reason: 'risk hold' });
    req.flush({ targetId: 'op-001', affectedPools: 4, reason: 'risk hold' });
  });

  it('loads admin inspection feeds', () => {
    service.listPaymentAttempts('resv-001').subscribe((page) => {
      expect(page.content[0]?.reservationId).toBe('resv-001');
    });
    let req = httpMock.expectOne(
      (request) => request.url === 'https://api.test.spotlink.local/admin/payment-attempts',
    );
    expect(req.request.params.get('reservationId')).toBe('resv-001');
    req.flush({ content: bookingDetailFixture.paymentAttempts, totalElements: 1, totalPages: 1, page: 0, size: 25 });

    service.listSupportCases().subscribe((page) => {
      expect(page.content[0]?.subject).toBe('Gate issue');
    });
    req = httpMock.expectOne(
      (request) => request.url === 'https://api.test.spotlink.local/admin/support-cases',
    );
    expect(req.request.params.get('page')).toBe('0');
    expect(req.request.params.get('size')).toBe('25');
    req.flush({ content: bookingDetailFixture.supportCases, totalElements: 1, totalPages: 1, page: 0, size: 25 });

    service.listAuditEvents().subscribe((page) => {
      expect(page.content[0]?.action).toBe('ADMIN_CANCELLED_BOOKING');
    });
    req = httpMock.expectOne(
      (request) => request.url === 'https://api.test.spotlink.local/admin/audit-events',
    );
    expect(req.request.params.get('page')).toBe('0');
    expect(req.request.params.get('size')).toBe('25');
    req.flush({
      content: [
        {
          id: 'audit-001',
          actorUserId: 'admin-001',
          action: 'ADMIN_CANCELLED_BOOKING',
          resourceType: 'reservation',
          resourceId: 'resv-001',
          createdAt: '2026-04-26T10:00:00Z',
          metadata: { reason: 'manual override' },
        },
      ],
      totalElements: 1,
      totalPages: 1,
      page: 0,
      size: 25,
    });
  });
});

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
  status: 'CONFIRMED',
  paymentMode: 'PAY_ON_ARRIVAL',
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
    status: 'CONSUMED',
    expiresAt: '2026-04-26T10:15:00Z',
    paymentMode: 'PAY_ON_ARRIVAL',
  },
  checkin: undefined,
  timeline: [
    {
      id: 'event-001',
      eventType: 'ADMIN_OVERRIDE',
      actorType: 'ADMIN',
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
      status: 'AUTHORIZED',
      paymentMode: 'PAY_ON_ARRIVAL',
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
      status: 'MARKED',
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
