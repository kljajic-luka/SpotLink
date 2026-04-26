import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { finalize } from 'rxjs';

import { AuthService } from '@foundation/auth';
import { UiButtonComponent } from '@foundation/design-system';

@Component({
  selector: 'sl-login-page',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, UiButtonComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="auth-page">
      <form class="login-panel" (ngSubmit)="login()">
        <div class="login-panel__header">
          <h1>Login</h1>
          <a routerLink="/unauthorized">Session status</a>
        </div>

        @if (errorMessage()) {
          <p class="error" role="alert">{{ errorMessage() }}</p>
        }

        <label for="email">Email</label>
        <input
          id="email"
          name="email"
          type="email"
          autocomplete="email"
          required
          [(ngModel)]="email"
        />

        <label for="password">Password</label>
        <input
          id="password"
          name="password"
          type="password"
          autocomplete="current-password"
          required
          [(ngModel)]="password"
        />

        <sl-ui-button type="submit" [loading]="isSubmitting()" [fullWidth]="true">Login</sl-ui-button>
      </form>
    </section>
  `,
  styles: [
    `
      .auth-page {
        min-height: calc(100vh - 72px);
        display: grid;
        place-items: start center;
        padding: var(--sl-space-5) var(--sl-space-2);
        background: var(--sl-color-surface-muted);
      }

      .login-panel {
        width: min(100%, 420px);
        display: grid;
        gap: var(--sl-space-2);
        padding: var(--sl-space-3);
        border: 1px solid var(--sl-color-border);
        border-radius: var(--sl-radius-md);
        background: var(--sl-color-surface);
        box-shadow: var(--sl-shadow-sm);
      }

      .login-panel__header {
        display: flex;
        justify-content: space-between;
        align-items: center;
        gap: var(--sl-space-2);
      }

      h1 {
        margin: 0;
        font-size: var(--sl-font-size-2xl);
      }

      a {
        color: var(--sl-color-primary);
        font-weight: 700;
        text-decoration: none;
      }

      label {
        color: var(--sl-color-text-strong);
        font-size: var(--sl-font-size-sm);
        font-weight: 700;
      }

      input {
        width: 100%;
        min-height: 44px;
        border: 1px solid var(--sl-color-border-strong);
        border-radius: var(--sl-radius-sm);
        padding: 0 0.9rem;
        background: var(--sl-color-surface);
        color: var(--sl-color-text-strong);
        font: inherit;
      }

      .error {
        margin: 0;
        padding: 0.8rem 0.9rem;
        border: 1px solid rgba(185, 28, 28, 0.22);
        border-radius: var(--sl-radius-sm);
        background: rgba(185, 28, 28, 0.08);
        color: var(--sl-color-danger);
        font-weight: 700;
      }
    `,
  ],
})
export class LoginPageComponent {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  readonly isSubmitting = signal(false);
  readonly errorMessage = signal<string | null>(null);

  email = '';
  password = '';

  login(): void {
    if (!this.email || !this.password) {
      this.errorMessage.set('Email i password su obavezni.');
      return;
    }

    this.isSubmitting.set(true);
    this.errorMessage.set(null);

    this.auth
      .login({ email: this.email, password: this.password })
      .pipe(finalize(() => this.isSubmitting.set(false)))
      .subscribe({
        next: () => {
          const returnUrl = this.route.snapshot.queryParamMap.get('returnUrl');
          void this.router.navigateByUrl(returnUrl || this.defaultRoute());
        },
        error: (error) => {
          this.errorMessage.set(this.toErrorMessage(error));
        },
      });
  }

  private defaultRoute(): string {
    if (this.auth.hasAnyRole(['ADMIN'])) {
      return '/admin';
    }
    if (this.auth.hasAnyRole(['OPERATOR'])) {
      return '/operator';
    }
    return '/unauthorized';
  }

  private toErrorMessage(error: unknown): string {
    const candidate = error as { error?: { message?: string }; message?: string };
    return candidate?.error?.message || candidate?.message || 'Login nije uspeo.';
  }
}
