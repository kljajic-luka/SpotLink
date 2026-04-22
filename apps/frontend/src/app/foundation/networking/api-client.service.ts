import { HttpClient, HttpContext, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { SPOTLINK_APP_CONFIG } from '@foundation/core';

interface RequestOptions<TParams extends object = object> {
  params?: TParams;
  context?: HttpContext;
}

@Injectable({ providedIn: 'root' })
export class ApiClient {
  private readonly http = inject(HttpClient);
  private readonly config = inject(SPOTLINK_APP_CONFIG);

  get<T, TParams extends object = object>(
    path: string,
    options: RequestOptions<TParams> = {},
  ): Observable<T> {
    return this.http.get<T>(this.url(path), {
      params: this.params(options.params),
      context: options.context,
    });
  }

  post<TResponse, TBody = unknown>(
    path: string,
    body: TBody,
    options: RequestOptions = {},
  ): Observable<TResponse> {
    return this.http.post<TResponse>(this.url(path), body, {
      params: this.params(options.params),
      context: options.context,
    });
  }

  put<TResponse, TBody = unknown>(
    path: string,
    body: TBody,
    options: RequestOptions = {},
  ): Observable<TResponse> {
    return this.http.put<TResponse>(this.url(path), body, {
      params: this.params(options.params),
      context: options.context,
    });
  }

  patch<TResponse, TBody = unknown>(
    path: string,
    body: TBody,
    options: RequestOptions = {},
  ): Observable<TResponse> {
    return this.http.patch<TResponse>(this.url(path), body, {
      params: this.params(options.params),
      context: options.context,
    });
  }

  delete<T, TParams extends object = object>(
    path: string,
    options: RequestOptions<TParams> = {},
  ): Observable<T> {
    return this.http.delete<T>(this.url(path), {
      params: this.params(options.params),
      context: options.context,
    });
  }

  private url(path: string): string {
    if (/^https?:\/\//.test(path)) {
      return path;
    }

    const base = this.config.baseApiUrl.replace(/\/$/, '');
    const cleanPath = path.startsWith('/') ? path : `/${path}`;
    return `${base}${cleanPath}`;
  }

  private params<TParams extends object>(params?: TParams): HttpParams {
    let httpParams = new HttpParams();

    Object.entries(params ?? {}).forEach(([key, value]) => {
      if (value === null || value === undefined) {
        return;
      }

      if (Array.isArray(value)) {
        value.forEach((item) => {
          const serializedItem = this.serializeQueryValue(item);
          if (serializedItem !== null) {
            httpParams = httpParams.append(key, serializedItem);
          }
        });
        return;
      }

      const serializedValue = this.serializeQueryValue(value);
      if (serializedValue !== null) {
        httpParams = httpParams.set(key, serializedValue);
      }
    });

    return httpParams;
  }

  private serializeQueryValue(value: unknown): string | null {
    if (typeof value === 'string') {
      return value;
    }

    if (typeof value === 'number') {
      return Number.isFinite(value) ? String(value) : null;
    }

    if (typeof value === 'boolean') {
      return String(value);
    }

    if (value instanceof Date) {
      return Number.isNaN(value.getTime()) ? null : value.toISOString();
    }

    return null;
  }
}
