export type PaymentStatus =
  | 'REQUIRES_METHOD'
  | 'REQUIRES_ACTION'
  | 'AUTHORIZED'
  | 'CAPTURED'
  | 'FAILED'
  | 'REFUNDED'
  | 'CANCELLED';

export interface PaymentMethod {
  id: string;
  brand: string;
  last4: string;
  expMonth?: number;
  expYear?: number;
  default: boolean;
}

export interface PaymentOperationCapabilities {
  authorize: boolean;
  capture: boolean;
  cancel: boolean;
  refund: boolean;
  webhook: boolean;
  reconciliation: boolean;
}

export interface PaymentCapabilities {
  onlinePaymentsEnabled: boolean;
  activeProvider: string;
  mockProvider: boolean;
  mockPaymentMethodsAllowed: boolean;
  operations: PaymentOperationCapabilities;
}

export function canUseOnlinePayments(capabilities: PaymentCapabilities | null | undefined): boolean {
  return capabilities?.onlinePaymentsEnabled === true && capabilities.operations.authorize;
}

export interface PaymentIntent {
  id: string;
  reservationId: string;
  amountCents: number;
  currency: string;
  status: PaymentStatus;
  redirectUrl?: string;
  clientSecret?: string;
}

export interface CreatePaymentIntentRequest {
  reservationId: string;
  paymentMethodId?: string;
  idempotencyKey: string;
}

export interface PaymentProviderResult {
  status: PaymentStatus;
  paymentIntentId?: string;
  redirectUrl?: string;
  message?: string;
}
