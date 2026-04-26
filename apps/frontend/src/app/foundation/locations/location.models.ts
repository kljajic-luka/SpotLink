import { VehicleFitRule } from '@foundation/vehicles';
import { PaymentMode } from '@foundation/reservations';

export interface GeoCoordinates {
  latitude: number;
  longitude: number;
}

export interface Address {
  line1: string;
  line2?: string;
  city: string;
  region?: string;
  postalCode?: string;
  country: string;
  formattedAddress?: string;
}

export type ParkingResourceType = 'PARKING_SPOT' | 'GARAGE' | 'DRIVEWAY' | 'EV_CHARGER' | 'LOT';

export type ParkingAccessType = 'SELF_PARK' | 'VALET' | 'GATE_CODE' | 'APP_UNLOCK' | 'ATTENDANT';

export interface ParkingLocation {
  id: string;
  operatorId: string;
  name: string;
  address: Address;
  coordinates: GeoCoordinates;
  timezone: string;
  accessType: ParkingAccessType;
  publicNotes?: string;
  active: boolean;
}

export interface ParkingResource {
  id: string;
  locationId: string;
  type: ParkingResourceType;
  label: string;
  floor?: string;
  bayNumber?: string;
  fitRule?: VehicleFitRule;
  hourlyRateCents: number;
  dailyRateCents?: number;
  currency: string;
  instantReserve: boolean;
  active: boolean;
  capacity?: number;
  confirmationMode?: 'INSTANT' | 'MANUAL';
  payOnArrivalEnabled?: boolean;
  supportedPaymentModes?: PaymentMode[];
}

export interface LocationSearchFilters {
  query?: string;
  latitude?: number;
  longitude?: number;
  radiusKm?: number;
  resourceTypes?: readonly ParkingResourceType[];
  evChargingRequired?: boolean;
  startsAt?: string;
  endsAt?: string;
  page?: number;
  size?: number;
}

export interface LocationSearchResult {
  location: ParkingLocation;
  resources: ParkingResource[];
  distanceKm?: number;
  startingPriceCents?: number;
  availableResourceCount: number;
}

export interface GeocodeSuggestion {
  id: string;
  address: Address;
  coordinates: GeoCoordinates;
  accuracyMeters?: number;
}
