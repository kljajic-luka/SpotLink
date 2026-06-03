import { InjectionToken } from '@angular/core';
import { environment } from '@env/environment';

export interface SpotLinkAppConfig {
  appName: string;
  baseApiUrl: string;
  supportEmail: string;
  supportUrl: string;
  privacyPolicyUrl: string;
  termsUrl: string;
  accountDeletionUrl: string;
  defaultLocale: string;
}

export const SPOTLINK_APP_CONFIG = new InjectionToken<SpotLinkAppConfig>('SPOTLINK_APP_CONFIG', {
  providedIn: 'root',
  factory: () => ({
    appName: environment.appName,
    baseApiUrl: environment.baseApiUrl,
    supportEmail: environment.supportEmail,
    supportUrl: environment.supportUrl,
    privacyPolicyUrl: environment.privacyPolicyUrl,
    termsUrl: environment.termsUrl,
    accountDeletionUrl: environment.accountDeletionUrl,
    defaultLocale: environment.defaultLocale,
  }),
});
