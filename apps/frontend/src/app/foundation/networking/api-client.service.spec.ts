import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { SPOTLINK_APP_CONFIG, SpotLinkAppConfig } from '@foundation/core';
import { ApiClient } from './api-client.service';

const config: SpotLinkAppConfig = {
  appName: 'SpotLink test',
  baseApiUrl: 'https://api.test.spotlink.local',
  supportEmail: 'support@test.spotlink.local',
  supportUrl: 'https://help.test.spotlink.local/support',
  privacyPolicyUrl: 'https://help.test.spotlink.local/privacy',
  termsUrl: 'https://help.test.spotlink.local/terms',
  accountDeletionUrl: 'https://help.test.spotlink.local/account-deletion',
  defaultLocale: 'sr-RS',
};

describe('ApiClient', () => {
  let service: ApiClient;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        ApiClient,
        provideHttpClient(),
        provideHttpClientTesting(),
        {
          provide: SPOTLINK_APP_CONFIG,
          useValue: config,
        },
      ],
    });

    service = TestBed.inject(ApiClient);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('serializuje query parametre i preskace null/undefined', () => {
    const startsAt = new Date('2026-04-22T12:30:00.000Z');

    service
      .get('/locations/search', {
        params: {
          query: 'centar',
          page: 1,
          size: 20,
          evChargingRequired: true,
          startsAt,
          resourceTypes: ['LOT', 'EV_CHARGER'],
          ignoredNull: null,
          ignoredUndefined: undefined,
        },
      })
      .subscribe();

    const req = httpMock.expectOne(
      (request) => request.url === 'https://api.test.spotlink.local/locations/search',
    );

    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('query')).toBe('centar');
    expect(req.request.params.get('page')).toBe('1');
    expect(req.request.params.get('size')).toBe('20');
    expect(req.request.params.get('evChargingRequired')).toBe('true');
    expect(req.request.params.get('startsAt')).toBe(startsAt.toISOString());
    expect(req.request.params.getAll('resourceTypes')).toEqual(['LOT', 'EV_CHARGER']);
    expect(req.request.params.has('ignoredNull')).toBeFalse();
    expect(req.request.params.has('ignoredUndefined')).toBeFalse();

    req.flush({ content: [], totalElements: 0, totalPages: 0, page: 0, size: 20 });
  });

  it('preskace nepodrzane objektne vrednosti', () => {
    service
      .get('/locations/search', {
        params: {
          query: 'zvezdara',
          raw: { nested: 'vrednost' },
        } as any,
      })
      .subscribe();

    const req = httpMock.expectOne(
      (request) => request.url === 'https://api.test.spotlink.local/locations/search',
    );

    expect(req.request.params.get('query')).toBe('zvezdara');
    expect(req.request.params.has('raw')).toBeFalse();

    req.flush({ content: [], totalElements: 0, totalPages: 0, page: 0, size: 20 });
  });
});
