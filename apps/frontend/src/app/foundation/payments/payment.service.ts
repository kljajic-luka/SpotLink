import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { ApiClient } from '@foundation/networking';
import {
  CreatePaymentIntentRequest,
  PaymentIntent,
  PaymentMethod,
  PaymentProviderResult,
} from './payment.models';

@Injectable({ providedIn: 'root' })
export class PaymentService {
  private readonly api = inject(ApiClient);

  listPaymentMethods(): Observable<PaymentMethod[]> {
    return this.api.get<PaymentMethod[]>('/payments/methods');
  }

  createIntent(payload: CreatePaymentIntentRequest): Observable<PaymentIntent> {
    return this.api.post<PaymentIntent, CreatePaymentIntentRequest>('/payments/intents', payload);
  }

  confirmIntent(paymentIntentId: string): Observable<PaymentProviderResult> {
    return this.api.post<PaymentProviderResult>(
      `/payments/intents/${encodeURIComponent(paymentIntentId)}/confirm`,
      {},
    );
  }
}
