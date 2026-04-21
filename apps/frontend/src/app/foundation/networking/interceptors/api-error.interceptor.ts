import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { catchError, throwError } from 'rxjs';

import { ApiError } from '../api.types';

export const apiErrorInterceptor: HttpInterceptorFn = (request, next) => {
  return next(request).pipe(
    catchError((error: HttpErrorResponse) => {
      const mappedError: ApiError = {
        status: error.status,
        code: getErrorCode(error.error),
        message: getUserMessage(error),
        requestId: error.headers?.get('X-Request-Id') ?? undefined,
        details: asRecord(error.error),
      };

      return throwError(() => mappedError);
    }),
  );
};

function getErrorCode(errorBody: unknown): string | undefined {
  if (!isRecord(errorBody)) {
    return undefined;
  }

  return typeof errorBody['code'] === 'string' ? errorBody['code'] : undefined;
}

function getUserMessage(error: HttpErrorResponse): string {
  const body = asRecord(error.error);
  if (typeof body?.['message'] === 'string') {
    return body['message'];
  }

  switch (error.status) {
    case 0:
      return 'Network connection failed. Check your connection and try again.';
    case 401:
      return 'Your session has expired. Sign in again to continue.';
    case 403:
      return 'You do not have permission to perform this action.';
    case 404:
      return 'The requested resource was not found.';
    case 409:
      return 'This action conflicts with the current state. Refresh and try again.';
    case 422:
      return 'Review the highlighted fields and try again.';
    case 429:
      return 'Too many requests. Wait a moment and try again.';
    default:
      return error.status >= 500
        ? 'The service is temporarily unavailable. Try again later.'
        : 'Something went wrong. Try again.';
  }
}

function asRecord(value: unknown): Record<string, unknown> | undefined {
  return isRecord(value) ? value : undefined;
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null;
}
