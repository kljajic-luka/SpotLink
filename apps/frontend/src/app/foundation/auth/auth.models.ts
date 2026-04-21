import { UserProfile } from '@foundation/user-profile';
import { UserRole } from '@foundation/core';

export type { UserRole } from '@foundation/core';

export type RegistrationStatus = 'INCOMPLETE' | 'ACTIVE' | 'SUSPENDED' | 'DELETED';

export interface LoginRequest {
  email: string;
  password: string;
}

export interface RegisterCustomerRequest {
  firstName: string;
  lastName: string;
  email: string;
  phone?: string;
  password: string;
  acceptsTerms: boolean;
}

export interface RegisterOperatorRequest extends RegisterCustomerRequest {
  companyName?: string;
  operatorType: 'INDIVIDUAL' | 'BUSINESS';
  acceptsOperatorAgreement: boolean;
}

export interface AuthResponse {
  authenticated: boolean;
  user: UserProfile;
  message?: string;
}

export interface PasswordResetRequest {
  email: string;
}

export interface CompletePasswordResetRequest {
  token: string;
  newPassword: string;
}
