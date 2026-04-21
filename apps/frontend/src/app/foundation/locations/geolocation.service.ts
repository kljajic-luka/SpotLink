import { Injectable, NgZone, computed, signal } from '@angular/core';

import { GeoCoordinates } from './location.models';

export interface BrowserPosition extends GeoCoordinates {
  accuracyMeters: number;
  capturedAt: string;
}

export interface BrowserLocationError {
  code: 'UNSUPPORTED' | 'DENIED' | 'UNAVAILABLE' | 'TIMEOUT' | 'UNKNOWN';
  message: string;
}

@Injectable({ providedIn: 'root' })
export class GeolocationService {
  private readonly positionSignal = signal<BrowserPosition | null>(null);
  private readonly errorSignal = signal<BrowserLocationError | null>(null);
  private readonly loadingSignal = signal(false);

  readonly position = this.positionSignal.asReadonly();
  readonly error = this.errorSignal.asReadonly();
  readonly loading = this.loadingSignal.asReadonly();
  readonly hasPosition = computed(() => this.positionSignal() !== null);

  constructor(private readonly zone: NgZone) {}

  isSupported(): boolean {
    return typeof navigator !== 'undefined' && 'geolocation' in navigator;
  }

  getCurrentPosition(): Promise<BrowserPosition> {
    if (!this.isSupported()) {
      const error: BrowserLocationError = {
        code: 'UNSUPPORTED',
        message: 'Location is not supported on this device.',
      };
      this.errorSignal.set(error);
      return Promise.reject(error);
    }

    this.loadingSignal.set(true);
    this.errorSignal.set(null);

    return new Promise((resolve, reject) => {
      navigator.geolocation.getCurrentPosition(
        (position) => {
          this.zone.run(() => {
            const mapped = {
              latitude: position.coords.latitude,
              longitude: position.coords.longitude,
              accuracyMeters: position.coords.accuracy,
              capturedAt: new Date(position.timestamp).toISOString(),
            };
            this.positionSignal.set(mapped);
            this.loadingSignal.set(false);
            resolve(mapped);
          });
        },
        (error) => {
          this.zone.run(() => {
            const mapped = this.mapError(error);
            this.errorSignal.set(mapped);
            this.loadingSignal.set(false);
            reject(mapped);
          });
        },
        {
          enableHighAccuracy: true,
          timeout: 15000,
          maximumAge: 60000,
        },
      );
    });
  }

  distanceMeters(from: GeoCoordinates, to: GeoCoordinates): number {
    const earthRadiusMeters = 6371000;
    const dLat = this.toRadians(to.latitude - from.latitude);
    const dLon = this.toRadians(to.longitude - from.longitude);
    const lat1 = this.toRadians(from.latitude);
    const lat2 = this.toRadians(to.latitude);

    const a =
      Math.sin(dLat / 2) ** 2 +
      Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLon / 2) ** 2;

    return earthRadiusMeters * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
  }

  private toRadians(value: number): number {
    return (value * Math.PI) / 180;
  }

  private mapError(error: GeolocationPositionError): BrowserLocationError {
    switch (error.code) {
      case error.PERMISSION_DENIED:
        return { code: 'DENIED', message: 'Location permission was denied.' };
      case error.POSITION_UNAVAILABLE:
        return { code: 'UNAVAILABLE', message: 'Current location is unavailable.' };
      case error.TIMEOUT:
        return { code: 'TIMEOUT', message: 'Location lookup timed out.' };
      default:
        return { code: 'UNKNOWN', message: error.message || 'Location lookup failed.' };
    }
  }
}
