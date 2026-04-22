import { HttpInterceptorFn } from '@angular/common/http';

import { RETRIED_REQUEST } from '../http-context.tokens';
import { INTERCEPTOR_XSRF } from './interceptor.constants';

const MUTATION_METHODS = new Set(['POST', 'PUT', 'PATCH', 'DELETE']);

export const authCredentialsInterceptor: HttpInterceptorFn = (request, next) => {
  let enriched = request.clone({
    withCredentials: true,
    context: request.context.set(RETRIED_REQUEST, request.context.get(RETRIED_REQUEST)),
  });

  if (
    MUTATION_METHODS.has(request.method.toUpperCase()) &&
    !request.headers.has(INTERCEPTOR_XSRF.headerName)
  ) {
    const xsrfToken = readCookie(INTERCEPTOR_XSRF.cookieName);
    if (xsrfToken) {
      enriched = enriched.clone({
        setHeaders: {
          [INTERCEPTOR_XSRF.headerName]: xsrfToken,
        },
      });
    }
  }

  return next(enriched);
};

function readCookie(name: string): string | null {
  if (typeof document === 'undefined' || !document.cookie) {
    return null;
  }

  const cookie = document.cookie
    .split(';')
    .map((item) => item.trim())
    .find((item) => item.startsWith(`${name}=`));

  return cookie ? decodeURIComponent(cookie.slice(name.length + 1)) : null;
}
