import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { ApiClient, ApiPage } from '@foundation/networking';
import {
  GeocodeSuggestion,
  LocationSearchFilters,
  LocationSearchResult,
  ParkingLocation,
  ParkingResource,
} from './location.models';

@Injectable({ providedIn: 'root' })
export class LocationService {
  private readonly api = inject(ApiClient);

  search(filters: LocationSearchFilters): Observable<ApiPage<LocationSearchResult>> {
    return this.api.get<ApiPage<LocationSearchResult>>('/locations/search', {
      params: filters,
    });
  }

  geocode(query: string): Observable<GeocodeSuggestion[]> {
    return this.api.get<GeocodeSuggestion[]>('/locations/geocode', {
      params: {
        query,
      },
    });
  }

  getLocation(locationId: string): Observable<ParkingLocation> {
    return this.api.get<ParkingLocation>(`/locations/${encodeURIComponent(locationId)}`);
  }

  listResources(locationId: string): Observable<ParkingResource[]> {
    return this.api.get<ParkingResource[]>(
      `/locations/${encodeURIComponent(locationId)}/resources`,
    );
  }
}
