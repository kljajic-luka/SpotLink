import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';

import { UiButtonComponent } from '@foundation/design-system';

@Component({
  selector: 'sl-unauthorized-page',
  standalone: true,
  imports: [RouterLink, UiButtonComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="access-state">
      <div class="access-state__panel" role="alert">
        <h1>Unauthorized</h1>
        <p>Login is required before this workspace can open.</p>
        <a [routerLink]="['/login']" [queryParams]="{ returnUrl: returnUrl() }">
          <sl-ui-button>Login</sl-ui-button>
        </a>
      </div>
    </section>
  `,
  styles: [
    `
      .access-state {
        min-height: calc(100vh - 72px);
        display: grid;
        place-items: start center;
        padding: var(--sl-space-5) var(--sl-space-2);
        background: var(--sl-color-surface-muted);
      }

      .access-state__panel {
        width: min(100%, 520px);
        display: grid;
        gap: var(--sl-space-2);
        padding: var(--sl-space-3);
        border: 1px solid var(--sl-color-border);
        border-radius: var(--sl-radius-md);
        background: var(--sl-color-surface);
        box-shadow: var(--sl-shadow-sm);
      }

      h1,
      p {
        margin: 0;
      }

      a {
        width: fit-content;
        text-decoration: none;
      }
    `,
  ],
})
export class UnauthorizedPageComponent {
  private readonly route = inject(ActivatedRoute);

  returnUrl(): string {
    return this.route.snapshot.queryParamMap.get('returnUrl') || '/operator';
  }
}

@Component({
  selector: 'sl-forbidden-page',
  standalone: true,
  imports: [RouterLink, UiButtonComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="access-state">
      <div class="access-state__panel" role="alert">
        <h1>Forbidden</h1>
        <p>The current account does not have the role required for this workspace.</p>
        <a routerLink="/operator">
          <sl-ui-button variant="secondary">Open operator workspace</sl-ui-button>
        </a>
      </div>
    </section>
  `,
  styles: [
    `
      .access-state {
        min-height: calc(100vh - 72px);
        display: grid;
        place-items: start center;
        padding: var(--sl-space-5) var(--sl-space-2);
        background: var(--sl-color-surface-muted);
      }

      .access-state__panel {
        width: min(100%, 520px);
        display: grid;
        gap: var(--sl-space-2);
        padding: var(--sl-space-3);
        border: 1px solid var(--sl-color-border);
        border-radius: var(--sl-radius-md);
        background: var(--sl-color-surface);
        box-shadow: var(--sl-shadow-sm);
      }

      h1,
      p {
        margin: 0;
      }

      a {
        width: fit-content;
        text-decoration: none;
      }
    `,
  ],
})
export class ForbiddenPageComponent {}
