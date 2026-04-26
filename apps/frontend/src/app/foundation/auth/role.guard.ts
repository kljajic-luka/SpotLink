import { inject } from '@angular/core';
import { CanActivateFn, Router, UrlTree } from '@angular/router';
import { Observable, catchError, map, of } from 'rxjs';

import { AuthService } from './auth.service';
import { UserRole } from './auth.models';

export const requireRole = (roles: readonly UserRole[]): CanActivateFn => {
  return (_route, state): boolean | UrlTree | Observable<boolean | UrlTree> => {
    const auth = inject(AuthService);
    const router = inject(Router);

    const decide = () => {
      if (auth.hasAnyRole(roles)) {
        return true;
      }

      return router.createUrlTree([auth.isAuthenticated() ? '/forbidden' : '/unauthorized'], {
        queryParams: {
          returnUrl: state.url,
        },
      });
    };

    if (auth.isAuthenticated()) {
      return decide();
    }

    return auth.initializeSession().pipe(
      map(() => decide()),
      catchError(() =>
        of(
          router.createUrlTree(['/unauthorized'], {
            queryParams: {
              returnUrl: state.url,
            },
          }),
        ),
      ),
    );
  };
};
