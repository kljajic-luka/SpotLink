import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, catchError, map, of, tap } from 'rxjs';

import { ApiClient, SKIP_AUTH } from '@foundation/networking';
import { StorageService } from '@foundation/core';
import { UserProfile } from '@foundation/user-profile';
import {
  AuthResponse,
  CompletePasswordResetRequest,
  LoginRequest,
  PasswordResetRequest,
  RegisterCustomerRequest,
  RegisterOperatorRequest,
  UserRole,
} from './auth.models';
import { HttpContext } from '@angular/common/http';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly api = inject(ApiClient);
  private readonly storage = inject(StorageService);
  private readonly currentUserSignal = signal<UserProfile | null>(this.storage.get('currentUser'));

  readonly currentUser = this.currentUserSignal.asReadonly();
  readonly isAuthenticated = computed(() => this.currentUserSignal() !== null);
  readonly roles = computed(() => this.currentUserSignal()?.roles ?? []);

  initializeSession(): Observable<UserProfile | null> {
    return this.api.get<UserProfile>('/auth/me').pipe(
      tap((user) => this.setCurrentUser(user)),
      catchError(() => {
        this.clearSession();
        return of(null);
      }),
    );
  }

  login(payload: LoginRequest): Observable<UserProfile> {
    return this.api
      .post<AuthResponse, LoginRequest>('/auth/login', payload, {
        context: new HttpContext().set(SKIP_AUTH, true),
      })
      .pipe(
        map((response) => response.user),
        tap((user) => this.setCurrentUser(user)),
      );
  }

  registerCustomer(payload: RegisterCustomerRequest): Observable<UserProfile> {
    return this.api
      .post<AuthResponse, RegisterCustomerRequest>('/auth/register/customer', payload, {
        context: new HttpContext().set(SKIP_AUTH, true),
      })
      .pipe(
        map((response) => response.user),
        tap((user) => this.setCurrentUser(user)),
      );
  }

  registerOperator(payload: RegisterOperatorRequest): Observable<UserProfile> {
    return this.api
      .post<AuthResponse, RegisterOperatorRequest>('/auth/register/operator', payload, {
        context: new HttpContext().set(SKIP_AUTH, true),
      })
      .pipe(
        map((response) => response.user),
        tap((user) => this.setCurrentUser(user)),
      );
  }

  requestPasswordReset(payload: PasswordResetRequest): Observable<void> {
    return this.api.post<void, PasswordResetRequest>('/auth/password/reset-request', payload, {
      context: new HttpContext().set(SKIP_AUTH, true),
    });
  }

  completePasswordReset(payload: CompletePasswordResetRequest): Observable<void> {
    return this.api.post<void, CompletePasswordResetRequest>('/auth/password/reset', payload, {
      context: new HttpContext().set(SKIP_AUTH, true),
    });
  }

  logout(): Observable<void> {
    return this.api.post<void>('/auth/logout', {}).pipe(
      catchError(() => of(undefined)),
      tap(() => this.clearSession()),
    );
  }

  hasAnyRole(allowedRoles: readonly UserRole[]): boolean {
    const userRoles = this.roles();
    return allowedRoles.some((role) => userRoles.includes(role));
  }

  setCurrentUser(user: UserProfile): void {
    this.currentUserSignal.set(user);
    this.storage.set('currentUser', user);
  }

  clearSession(): void {
    this.currentUserSignal.set(null);
    this.storage.remove('currentUser');
  }
}
