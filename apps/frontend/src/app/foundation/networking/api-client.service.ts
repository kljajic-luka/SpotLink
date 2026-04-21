import { HttpClient, HttpContext, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { SPOTLINK_APP_CONFIG } from '@foundation/core';
import { QueryParams } from './api.types';

interface RequestOptions {
  params?: QueryParams;
  context?: HttpContext;
}

@Injectable({ providedIn: 'root' })
export class ApiClient {
  private readonly http = inject(HttpClient);
  private readonly config = inject(SPOTLINK_APP_CONFIG);

  get<T>(path: string, options: RequestOptions = {}): Observable<T> {
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

  delete<T>(path: string, options: RequestOptions = {}): Observable<T> {
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

  private params(params?: QueryParams): HttpParams {
    let httpParams = new HttpParams();

    Object.entries(params ?? {}).forEach(([key, value]) => {
      if (value === null || value === undefined) {
        return;
      }

      if (Array.isArray(value)) {
        value.forEach((item) => {
          if (item !== null && item !== undefined) {
            httpParams = httpParams.append(key, String(item));
          }
        });
        return;
      }

      httpParams = httpParams.set(key, String(value));
    });

    return httpParams;
  }
}
