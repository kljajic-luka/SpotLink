import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { timer, throwError } from 'rxjs';
import { retry } from 'rxjs/operators';

import { SKIP_RETRY } from '../http-context.tokens';
import { INTERCEPTOR_RETRY } from './interceptor.constants';

const RETRYABLE_METHODS = new Set(['GET', 'HEAD', 'OPTIONS', 'PUT', 'DELETE']);
const RETRYABLE_STATUSES = new Set([0, 500, 502, 503, 504]);

export const retryInterceptor: HttpInterceptorFn = (request, next) => {
  if (request.context.get(SKIP_RETRY) || !shouldRetryRequest(request.method, request.url)) {
    return next(request);
  }

  return next(request).pipe(
    retry({
      count: INTERCEPTOR_RETRY.maxRetries,
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
    !INTERCEPTOR_RETRY.noRetryEndpoints.some((endpoint) => url.includes(endpoint))
  );
}

function calculateDelay(retryIndex: number, error: HttpErrorResponse): number {
  const retryAfter = error.headers?.get('Retry-After');
  if (retryAfter) {
    const retryAfterMs = Number.parseInt(retryAfter, 10) * 1000;
    if (Number.isFinite(retryAfterMs) && retryAfterMs > 0) {
      return Math.min(retryAfterMs, INTERCEPTOR_RETRY.maxDelayMs);
    }
  }

  const exponentialDelay = INTERCEPTOR_RETRY.baseDelayMs * 2 ** retryIndex;
  const jitter = Math.random() * 150;
  return Math.min(exponentialDelay + jitter, INTERCEPTOR_RETRY.maxDelayMs);
}
