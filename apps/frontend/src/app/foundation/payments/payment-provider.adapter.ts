import { Observable } from 'rxjs';

import { CreatePaymentIntentRequest, PaymentIntent, PaymentProviderResult } from './payment.models';

export abstract class PaymentProviderAdapter {
  abstract createIntent(payload: CreatePaymentIntentRequest): Observable<PaymentIntent>;

  abstract confirmIntent(paymentIntentId: string): Observable<PaymentProviderResult>;

  abstract cancelIntent(paymentIntentId: string): Observable<PaymentProviderResult>;
}
