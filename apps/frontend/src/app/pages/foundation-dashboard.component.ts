import { ChangeDetectionStrategy, Component } from '@angular/core';

import { StatusPillComponent, UiButtonComponent } from '@foundation/design-system';
import { EmptyStateComponent, LoadingSkeletonComponent } from '@foundation/shared-components';

interface FoundationModuleSummary {
  name: string;
  folder: string;
  reused: string;
  next: string;
  tone: 'neutral' | 'info' | 'success' | 'warning' | 'danger';
}

@Component({
  selector: 'sl-foundation-dashboard',
  standalone: true,
  imports: [
    EmptyStateComponent,
    LoadingSkeletonComponent,
    StatusPillComponent,
    UiButtonComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="hero">
      <div class="sl-container hero__grid">
        <div class="hero__copy">
          <sl-status-pill tone="info">Foundation adapted</sl-status-pill>
          <h1>Parking marketplace foundation for SpotLink</h1>
          <p>
            A lean Angular base with neutral services, domain models, and shared UI ready for
            reservations, operators, payments, support, notifications, and admin workflows.
          </p>
          <div class="hero__actions">
            <sl-ui-button>Explore modules</sl-ui-button>
            <sl-ui-button variant="secondary">Read migration notes</sl-ui-button>
          </div>
        </div>

        <div class="map-panel" aria-label="Parking availability preview">
          <div class="map-panel__street map-panel__street--main"></div>
          <div class="map-panel__street map-panel__street--side"></div>
          <div class="map-panel__spot map-panel__spot--one">
            <span>P1</span>
          </div>
          <div class="map-panel__spot map-panel__spot--two">
            <span>EV</span>
          </div>
          <div class="map-panel__spot map-panel__spot--three">
            <span>Lot</span>
          </div>
          <div class="map-panel__pin" aria-hidden="true"></div>
        </div>
      </div>
    </section>

    <section class="module-band" id="modules">
      <div class="sl-container">
        <div class="section-heading">
          <h2>Foundation Modules</h2>
          <p>Each folder is intentionally small and can be wired to real backend endpoints later.</p>
        </div>

        <div class="module-grid">
          @for (module of modules; track module.name) {
            <article class="module-card">
              <div class="module-card__header">
                <h3>{{ module.name }}</h3>
                <sl-status-pill [tone]="module.tone">{{ module.folder }}</sl-status-pill>
              </div>
              <p>{{ module.reused }}</p>
              <span>{{ module.next }}</span>
            </article>
          }
        </div>
      </div>
    </section>

    <section class="workflow-band" id="next">
      <div class="sl-container workflow-grid">
        <div>
          <h2>Ready for MVP workflows</h2>
          <p>
            The shell is prepared for search, quote, reserve, pay, access instructions, operator
            oversight, support escalation, and admin review.
          </p>
          <div class="workflow-actions">
            <sl-loading-skeleton width="14rem" height="0.75rem"></sl-loading-skeleton>
            <sl-loading-skeleton width="10rem" height="0.75rem"></sl-loading-skeleton>
          </div>
        </div>

        <sl-empty-state
          title="Backend contracts are the next dependency"
          description="The frontend foundation now has stable DTO boundaries. The next build step is implementing matching API contracts."
          actionLabel="Create API contract"
        ></sl-empty-state>
      </div>
    </section>
  `,
  styles: [
    `
      .hero {
        padding: var(--sl-space-8) 0 var(--sl-space-6);
        background:
          radial-gradient(circle at 8% 15%, rgba(245, 158, 11, 0.18), transparent 28%),
          linear-gradient(135deg, #f7f8f5 0%, #e9f2ef 54%, #f3f0e7 100%);
      }

      .hero__grid {
        display: grid;
        grid-template-columns: minmax(0, 1fr) minmax(320px, 0.8fr);
        align-items: center;
        gap: var(--sl-space-6);
      }

      .hero__copy {
        display: grid;
        gap: var(--sl-space-2);
      }

      .hero__copy p {
        max-width: 46rem;
        font-size: var(--sl-font-size-lg);
      }

      .hero__actions {
        display: flex;
        flex-wrap: wrap;
        gap: var(--sl-space-1);
        margin-top: var(--sl-space-1);
      }

      .map-panel {
        position: relative;
        min-height: 420px;
        overflow: hidden;
        border: 1px solid var(--sl-color-border);
        border-radius: var(--sl-radius-md);
        background:
          linear-gradient(90deg, rgba(15, 118, 110, 0.08) 1px, transparent 1px),
          linear-gradient(0deg, rgba(15, 118, 110, 0.08) 1px, transparent 1px),
          #fbfcf8;
        background-size: 42px 42px;
        box-shadow: var(--sl-shadow-md);
      }

      .map-panel__street {
        position: absolute;
        background: #dbe4df;
        border: 1px solid #c7d3ce;
      }

      .map-panel__street--main {
        inset: 44% -10%;
        height: 76px;
        transform: rotate(-12deg);
      }

      .map-panel__street--side {
        left: 53%;
        top: -12%;
        width: 64px;
        height: 130%;
        transform: rotate(16deg);
      }

      .map-panel__spot {
        position: absolute;
        display: grid;
        place-items: center;
        width: 96px;
        height: 72px;
        border: 2px solid var(--sl-color-primary);
        border-radius: var(--sl-radius-sm);
        background: var(--sl-color-surface);
        color: var(--sl-color-primary);
        font-weight: 800;
        box-shadow: var(--sl-shadow-sm);
      }

      .map-panel__spot--one {
        left: 13%;
        top: 18%;
      }

      .map-panel__spot--two {
        right: 16%;
        top: 22%;
        border-color: var(--sl-color-info);
        color: var(--sl-color-info);
      }

      .map-panel__spot--three {
        left: 34%;
        bottom: 14%;
        border-color: var(--sl-color-accent);
        color: var(--sl-color-warning);
      }

      .map-panel__pin {
        position: absolute;
        left: 49%;
        top: 48%;
        width: 24px;
        height: 24px;
        border: 5px solid var(--sl-color-primary);
        border-radius: 50% 50% 50% 0;
        background: var(--sl-color-surface);
        transform: rotate(-45deg);
        box-shadow: 0 0 0 10px rgba(15, 118, 110, 0.14);
      }

      .module-band,
      .workflow-band {
        padding: var(--sl-space-6) 0;
      }

      .section-heading {
        display: grid;
        gap: 0.5rem;
        margin-bottom: var(--sl-space-3);
      }

      .module-grid {
        display: grid;
        grid-template-columns: repeat(auto-fit, minmax(260px, 1fr));
        gap: var(--sl-space-2);
      }

      .module-card {
        display: grid;
        gap: 0.75rem;
        min-height: 208px;
        padding: var(--sl-space-2);
        border: 1px solid var(--sl-color-border);
        border-radius: var(--sl-radius-md);
        background: var(--sl-color-surface);
        box-shadow: var(--sl-shadow-xs);
      }

      .module-card__header {
        display: grid;
        gap: 0.5rem;
        align-content: start;
      }

      .module-card span:last-child {
        align-self: end;
        color: var(--sl-color-text-strong);
        font-size: var(--sl-font-size-sm);
        font-weight: 700;
      }

      .workflow-band {
        background: var(--sl-color-surface-muted);
      }

      .workflow-grid {
        display: grid;
        grid-template-columns: minmax(0, 0.8fr) minmax(320px, 1fr);
        align-items: center;
        gap: var(--sl-space-4);
      }

      .workflow-grid > div:first-child {
        display: grid;
        gap: var(--sl-space-2);
      }

      .workflow-actions {
        display: grid;
        gap: 0.625rem;
        max-width: 18rem;
      }

      @media (max-width: 860px) {
        .hero__grid,
        .workflow-grid {
          grid-template-columns: 1fr;
        }

        .hero {
          padding-top: var(--sl-space-4);
        }

        .map-panel {
          min-height: 320px;
        }
      }
    `,
  ],
})
export class FoundationDashboardComponent {
  readonly modules: FoundationModuleSummary[] = [
    {
      name: 'Core',
      folder: 'core',
      reused: 'Logger, storage, idempotency, config, and view-state helpers from donor patterns.',
      next: 'Add feature flags when MVP experiments start.',
      tone: 'success',
    },
    {
      name: 'DesignSystem',
      folder: 'design-system',
      reused: 'Portable tokens and dependency-free shared controls.',
      next: 'Connect final brand typography and icon set.',
      tone: 'success',
    },
    {
      name: 'Networking',
      folder: 'networking',
      reused: 'API client, cookie credentials, XSRF, retry, and error mapping patterns.',
      next: 'Bind to real backend OpenAPI contracts.',
      tone: 'success',
    },
    {
      name: 'Auth',
      folder: 'auth',
      reused: 'Cookie-first session shape and role guard concept.',
      next: 'Choose managed auth provider and callback routes.',
      tone: 'info',
    },
    {
      name: 'UserProfile',
      folder: 'user-profile',
      reused: 'Profile DTO boundaries with customer and operator vocabulary.',
      next: 'Add avatar upload once storage is selected.',
      tone: 'info',
    },
    {
      name: 'Vehicles',
      folder: 'vehicles',
      reused: 'Vehicle metadata kept only where parking fit needs it.',
      next: 'Add license plate privacy rules.',
      tone: 'info',
    },
    {
      name: 'Locations',
      folder: 'locations',
      reused: 'Geospatial model and browser geolocation patterns.',
      next: 'Wire map provider and geocoder.',
      tone: 'success',
    },
    {
      name: 'Reservations',
      folder: 'reservations',
      reused: 'Reservation lifecycle boundaries adapted from donor patterns.',
      next: 'Add availability calendar and quote UI.',
      tone: 'success',
    },
    {
      name: 'Payments',
      folder: 'payments',
      reused: 'Provider adapter and mock payment pattern.',
      next: 'Select Stripe, Adyen, or local PSP.',
      tone: 'warning',
    },
    {
      name: 'Support',
      folder: 'support',
      reused: 'Ticket and message boundaries from chat/support patterns.',
      next: 'Add file attachments and SLA tags.',
      tone: 'info',
    },
    {
      name: 'Notifications',
      folder: 'notifications',
      reused: 'Read state, device token, and unread count patterns.',
      next: 'Add Web Push registration.',
      tone: 'info',
    },
    {
      name: 'Operator',
      folder: 'operator',
      reused: 'Operator dashboard boundary for parking teams.',
      next: 'Add location/resource management pages.',
      tone: 'success',
    },
    {
      name: 'Admin',
      folder: 'admin',
      reused: 'Admin dashboard, audit, and user listing service boundaries.',
      next: 'Add moderation and risk queues.',
      tone: 'info',
    },
    {
      name: 'Analytics',
      folder: 'analytics',
      reused: 'Best-effort telemetry queue using sendBeacon.',
      next: 'Define product event taxonomy.',
      tone: 'success',
    },
    {
      name: 'SharedComponents',
      folder: 'shared-components',
      reused: 'Empty, error, loading, and image primitives.',
      next: 'Add modal and toast once product flows need them.',
      tone: 'success',
    },
  ];
}
