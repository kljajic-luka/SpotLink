import { TestBed } from '@angular/core/testing';
import { provideRouter, Router, UrlTree } from '@angular/router';
import { firstValueFrom, isObservable, of } from 'rxjs';

import { AuthService } from './auth.service';
import { requireRole } from './role.guard';

describe('requireRole', () => {
  let auth: {
    hasAnyRole: jasmine.Spy;
    initializeSession: jasmine.Spy;
    isAuthenticated: jasmine.Spy;
  };
  let router: Router;

  beforeEach(() => {
    auth = {
      hasAnyRole: jasmine.createSpy('hasAnyRole'),
      initializeSession: jasmine.createSpy('initializeSession'),
      isAuthenticated: jasmine.createSpy('isAuthenticated'),
    };

    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        {
          provide: AuthService,
          useValue: auth,
        },
      ],
    });

    router = TestBed.inject(Router);
  });

  it('allows users with a required role', () => {
    auth.isAuthenticated.and.returnValue(true);
    auth.hasAnyRole.and.returnValue(true);

    const result = TestBed.runInInjectionContext(() =>
      requireRole(['ADMIN'])({} as never, { url: '/admin' } as never),
    );

    expect(result).toBeTrue();
    expect(auth.initializeSession).not.toHaveBeenCalled();
  });

  it('redirects authenticated users without role to forbidden', () => {
    auth.isAuthenticated.and.returnValue(true);
    auth.hasAnyRole.and.returnValue(false);

    const result = TestBed.runInInjectionContext(() =>
      requireRole(['ADMIN'])({} as never, { url: '/admin' } as never),
    ) as UrlTree;

    expect(router.serializeUrl(result)).toBe('/forbidden?returnUrl=%2Fadmin');
  });

  it('initializes session before redirecting unauthenticated users', async () => {
    auth.isAuthenticated.and.returnValue(false);
    auth.hasAnyRole.and.returnValue(false);
    auth.initializeSession.and.returnValue(of(null));

    const result = TestBed.runInInjectionContext(() =>
      requireRole(['OPERATOR'])({} as never, { url: '/operator' } as never),
    );
    const resolved = isObservable(result) ? await firstValueFrom(result) : result;

    expect(auth.initializeSession).toHaveBeenCalled();
    expect(router.serializeUrl(resolved as UrlTree)).toBe('/unauthorized?returnUrl=%2Foperator');
  });
});
