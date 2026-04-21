import { UserRole } from '@foundation/core';

export interface UserProfile {
  id: string;
  email: string;
  firstName: string;
  lastName: string;
  phone?: string;
  avatarUrl?: string;
  bio?: string;
  roles: readonly UserRole[];
  operatorId?: string;
  registrationStatus?: 'INCOMPLETE' | 'ACTIVE' | 'SUSPENDED' | 'DELETED';
  createdAt?: string;
}

export interface UserProfileDetails extends UserProfile {
  stats: ProfileStats;
  preferences: UserPreferences;
}

export interface ProfileStats {
  completedReservations: number;
  activeVehicles: number;
  savedLocations: number;
  supportTickets: number;
}

export interface UserPreferences {
  locale: string;
  marketingOptIn: boolean;
  reservationAlerts: boolean;
  paymentAlerts: boolean;
  supportAlerts: boolean;
}

export interface UpdateProfileRequest {
  firstName?: string;
  lastName?: string;
  phone?: string;
  avatarUrl?: string;
  bio?: string;
  preferences?: Partial<UserPreferences>;
}
