import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';

import { AuthService } from './auth.service';
import { UserRole } from './auth.models';

export const requireRole = (roles: readonly UserRole[]): CanActivateFn => {
  return () => {
    const auth = inject(AuthService);
    const router = inject(Router);

    if (auth.hasAnyRole(roles)) {
      return true;
    }

    return router.createUrlTree(['/'], {
      queryParams: {
        auth: auth.isAuthenticated() ? 'forbidden' : 'required',
      },
    });
  };
};
