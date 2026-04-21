import { HttpInterceptorFn } from '@angular/common/http';

import { RETRIED_REQUEST } from '../http-context.tokens';

const XSRF_COOKIE = 'XSRF-TOKEN';
const XSRF_HEADER = 'X-XSRF-TOKEN';
const MUTATION_METHODS = new Set(['POST', 'PUT', 'PATCH', 'DELETE']);

export const authCredentialsInterceptor: HttpInterceptorFn = (request, next) => {
  let enriched = request.clone({
    withCredentials: true,
    context: request.context.set(RETRIED_REQUEST, request.context.get(RETRIED_REQUEST)),
  });

  if (MUTATION_METHODS.has(request.method.toUpperCase()) && !request.headers.has(XSRF_HEADER)) {
    const xsrfToken = readCookie(XSRF_COOKIE);
    if (xsrfToken) {
      enriched = enriched.clone({
        setHeaders: {
          [XSRF_HEADER]: xsrfToken,
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
