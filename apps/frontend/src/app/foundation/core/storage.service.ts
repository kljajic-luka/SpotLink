import { Injectable } from '@angular/core';

type StorageArea = 'local' | 'session';

@Injectable({ providedIn: 'root' })
export class StorageService {
  private readonly prefix = 'spotlink';

  get<T>(key: string, area: StorageArea = 'local'): T | null {
    const storage = this.resolveStorage(area);
    if (!storage) {
      return null;
    }

    const rawValue = storage.getItem(this.key(key));
    if (rawValue === null) {
      return null;
    }

    try {
      return JSON.parse(rawValue) as T;
    } catch {
      return rawValue as T;
    }
  }

  set<T>(key: string, value: T, area: StorageArea = 'local'): void {
    const storage = this.resolveStorage(area);
    if (!storage) {
      return;
    }

    storage.setItem(this.key(key), JSON.stringify(value));
  }

  remove(key: string, area: StorageArea = 'local'): void {
    this.resolveStorage(area)?.removeItem(this.key(key));
  }

  clearNamespace(area: StorageArea = 'local'): void {
    const storage = this.resolveStorage(area);
    if (!storage) {
      return;
    }

    const keys = Array.from({ length: storage.length }, (_, index) => storage.key(index)).filter(
      (value): value is string => Boolean(value?.startsWith(`${this.prefix}.`)),
    );

    keys.forEach((key) => storage.removeItem(key));
  }

  private key(key: string): string {
    return `${this.prefix}.${key}`;
  }

  private resolveStorage(area: StorageArea): Storage | null {
    if (typeof window === 'undefined') {
      return null;
    }

    return area === 'local' ? window.localStorage : window.sessionStorage;
  }
}
