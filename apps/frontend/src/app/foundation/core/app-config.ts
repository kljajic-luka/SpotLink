import { InjectionToken } from '@angular/core';
import { environment } from '@env/environment';

export interface SpotLinkAppConfig {
  appName: string;
  baseApiUrl: string;
  supportEmail: string;
  defaultLocale: string;
}

export const SPOTLINK_APP_CONFIG = new InjectionToken<SpotLinkAppConfig>('SPOTLINK_APP_CONFIG', {
  providedIn: 'root',
  factory: () => ({
    appName: environment.appName,
    baseApiUrl: environment.baseApiUrl,
    supportEmail: environment.supportEmail,
    defaultLocale: environment.defaultLocale,
  }),
});
