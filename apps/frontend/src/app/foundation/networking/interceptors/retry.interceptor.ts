import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { timer, throwError } from 'rxjs';
import { retry } from 'rxjs/operators';

import { SKIP_RETRY } from '../http-context.tokens';

const RETRYABLE_METHODS = new Set(['GET', 'HEAD', 'OPTIONS', 'PUT', 'DELETE']);
const RETRYABLE_STATUSES = new Set([0, 500, 502, 503, 504]);
const MAX_RETRIES = 2;
const BASE_DELAY_MS = 400;
const MAX_DELAY_MS = 4000;
const NO_RETRY_ENDPOINTS = ['/auth/login', '/auth/register', '/payments', '/reservations'];

export const retryInterceptor: HttpInterceptorFn = (request, next) => {
  if (request.context.get(SKIP_RETRY) || !shouldRetryRequest(request.method, request.url)) {
    return next(request);
  }

  return next(request).pipe(
    retry({
      count: MAX_RETRIES,
      delay: (error: HttpErrorResponse, retryIndex: number) => {
        if (!RETRYABLE_STATUSES.has(error.status)) {
          return throwError(() => error);
        }

        return timer(calculateDelay(retryIndex, error));
      },
    }),
  );
};

function shouldRetryRequest(method: string, url: string): boolean {
  return (
    RETRYABLE_METHODS.has(method.toUpperCase()) &&
    !NO_RETRY_ENDPOINTS.some((endpoint) => url.includes(endpoint))
  );
}

function calculateDelay(retryIndex: number, error: HttpErrorResponse): number {
  const retryAfter = error.headers?.get('Retry-After');
  if (retryAfter) {
    const retryAfterMs = Number.parseInt(retryAfter, 10) * 1000;
    if (Number.isFinite(retryAfterMs) && retryAfterMs > 0) {
      return Math.min(retryAfterMs, MAX_DELAY_MS);
    }
  }

  const exponentialDelay = BASE_DELAY_MS * 2 ** retryIndex;
  const jitter = Math.random() * 150;
  return Math.min(exponentialDelay + jitter, MAX_DELAY_MS);
}
