export const INTERCEPTOR_XSRF = {
  cookieName: 'XSRF-TOKEN',
  headerName: 'X-XSRF-TOKEN',
} as const;

export const INTERCEPTOR_RETRY = {
  maxRetries: 2,
  baseDelayMs: 400,
  maxDelayMs: 4000,
  noRetryEndpoints: ['/auth/login', '/auth/register', '/payments', '/reservations'],
} as const;
