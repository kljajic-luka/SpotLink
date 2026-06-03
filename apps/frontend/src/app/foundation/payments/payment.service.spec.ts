import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { SPOTLINK_APP_CONFIG, SpotLinkAppConfig } from '@foundation/core';
import { canUseOnlinePayments } from './payment.models';
import { PaymentService } from './payment.service';

const config: SpotLinkAppConfig = {
  appName: 'SpotLink test',
  baseApiUrl: 'https://api.test.spotlink.local',
  supportEmail: 'support@test.spotlink.local',
  supportUrl: 'https://help.test.spotlink.local/support',
  privacyPolicyUrl: 'https://help.test.spotlink.local/privacy',
  termsUrl: 'https://help.test.spotlink.local/terms',
  accountDeletionUrl: 'https://help.test.spotlink.local/account-deletion',
  defaultLocale: 'sr-RS',
};

describe('PaymentService', () => {
  let service: PaymentService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        PaymentService,
        provideHttpClient(),
        provideHttpClientTesting(),
        {
          provide: SPOTLINK_APP_CONFIG,
          useValue: config,
        },
      ],
    });

    service = TestBed.inject(PaymentService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('loads backend payment capabilities before exposing online payment actions', () => {
    service.capabilities().subscribe((capabilities) => {
      expect(capabilities.onlinePaymentsEnabled).toBeFalse();
      expect(capabilities.activeProvider).toBe('UNCONFIGURED');
      expect(capabilities.mockPaymentMethodsAllowed).toBeFalse();
      expect(capabilities.operations.authorize).toBeFalse();
      expect(canUseOnlinePayments(capabilities)).toBeFalse();
    });

    const req = httpMock.expectOne('https://api.test.spotlink.local/payments/capabilities');
    expect(req.request.method).toBe('GET');
    req.flush({
      onlinePaymentsEnabled: false,
      activeProvider: 'UNCONFIGURED',
      mockProvider: false,
      mockPaymentMethodsAllowed: false,
      operations: {
        authorize: false,
        capture: false,
        cancel: false,
        refund: false,
        webhook: false,
        reconciliation: false,
      },
    });
  });

  it('uses provider-ready intent cancel endpoint', () => {
    service.cancelIntent('pi_123').subscribe((result) => {
      expect(result.status).toBe('CANCELLED');
      expect(result.paymentIntentId).toBe('pi_123');
    });

    const req = httpMock.expectOne('https://api.test.spotlink.local/payments/intents/pi_123/cancel');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({});
    req.flush({
      status: 'CANCELLED',
      paymentIntentId: 'pi_123',
      message: 'Cancelled',
    });
  });
});
