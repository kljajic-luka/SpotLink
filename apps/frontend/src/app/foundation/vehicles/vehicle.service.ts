import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { ApiClient } from '@foundation/networking';
import { VehicleProfile, VehicleUpsertRequest } from './vehicle.models';

@Injectable({ providedIn: 'root' })
export class VehicleService {
  private readonly api = inject(ApiClient);

  listMyVehicles(): Observable<VehicleProfile[]> {
    return this.api.get<VehicleProfile[]>('/vehicles/me');
  }

  createVehicle(payload: VehicleUpsertRequest): Observable<VehicleProfile> {
    return this.api.post<VehicleProfile, VehicleUpsertRequest>('/vehicles', payload);
  }

  updateVehicle(vehicleId: string, payload: VehicleUpsertRequest): Observable<VehicleProfile> {
    return this.api.put<VehicleProfile, VehicleUpsertRequest>(
      `/vehicles/${encodeURIComponent(vehicleId)}`,
      payload,
    );
  }

  deleteVehicle(vehicleId: string): Observable<void> {
    return this.api.delete<void>(`/vehicles/${encodeURIComponent(vehicleId)}`);
  }
}
