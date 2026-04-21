export type VehicleType = 'CAR' | 'MOTORCYCLE' | 'VAN' | 'TRUCK' | 'BICYCLE' | 'OTHER';

export type VehicleVerificationStatus = 'UNVERIFIED' | 'PENDING' | 'VERIFIED' | 'REJECTED';

export interface VehicleProfile {
  id: string;
  userId: string;
  type: VehicleType;
  nickname?: string;
  make?: string;
  model?: string;
  color?: string;
  licensePlate?: string;
  heightMeters?: number;
  lengthMeters?: number;
  evCapable?: boolean;
  verificationStatus: VehicleVerificationStatus;
  createdAt: string;
  updatedAt?: string;
}

export interface VehicleUpsertRequest {
  type: VehicleType;
  nickname?: string;
  make?: string;
  model?: string;
  color?: string;
  licensePlate?: string;
  heightMeters?: number;
  lengthMeters?: number;
  evCapable?: boolean;
}

export interface VehicleFitRule {
  maxHeightMeters?: number;
  maxLengthMeters?: number;
  allowedVehicleTypes?: readonly VehicleType[];
  evOnly?: boolean;
}
