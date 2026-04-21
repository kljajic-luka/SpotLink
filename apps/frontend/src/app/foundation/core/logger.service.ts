import { Injectable, inject } from '@angular/core';
import { SPOTLINK_APP_CONFIG } from './app-config';
import { environment } from '@env/environment';

@Injectable({ providedIn: 'root' })
export class LoggerService {
  private readonly config = inject(SPOTLINK_APP_CONFIG);
  private readonly production = environment.production;

  log(...args: unknown[]): void {
    if (!this.production) {
      console.log(`[${this.config.appName}]`, ...args);
    }
  }

  info(...args: unknown[]): void {
    if (!this.production) {
      console.info(`[${this.config.appName}]`, ...args);
    }
  }

  warn(...args: unknown[]): void {
    if (!this.production) {
      console.warn(`[${this.config.appName}]`, ...args);
    }
  }

  error(...args: unknown[]): void {
    console.error(`[${this.config.appName}]`, ...args);
  }

  debug(...args: unknown[]): void {
    if (!this.production) {
      console.debug(`[${this.config.appName}]`, ...args);
    }
  }
}
