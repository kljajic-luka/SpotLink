import { Injectable } from '@angular/core';
import { Observable, of } from 'rxjs';

import {
  CreatePaymentIntentRequest,
  PaymentCapabilities,
  PaymentIntent,
  PaymentProviderResult,
} from './payment.models';
import { PaymentProviderAdapter } from './payment-provider.adapter';

@Injectable({ providedIn: 'root' })
export class MockPaymentAdapter extends PaymentProviderAdapter {
  override capabilities(): Observable<PaymentCapabilities> {
    return of({
      onlinePaymentsEnabled: true,
      activeProvider: 'MOCK',
      mockProvider: true,
      mockPaymentMethodsAllowed: true,
      operations: {
        authorize: true,
        capture: true,
        cancel: true,
        refund: true,
        webhook: false,
        reconciliation: false,
      },
    });
  }

  override createIntent(payload: CreatePaymentIntentRequest): Observable<PaymentIntent> {
    return of({
      id: `mock_pi_${payload.idempotencyKey}`,
      reservationId: payload.reservationId,
      amountCents: 0,
      currency: 'USD',
      status: 'AUTHORIZED',
    });
  }

  override confirmIntent(paymentIntentId: string): Observable<PaymentProviderResult> {
    return of({
      status: 'CAPTURED',
      paymentIntentId,
      message: 'Mock payment captured.',
    });
  }

  override cancelIntent(paymentIntentId: string): Observable<PaymentProviderResult> {
    return of({
      status: 'CANCELLED',
      paymentIntentId,
      message: 'Mock payment cancelled.',
    });
  }
}
