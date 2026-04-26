import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { finalize, forkJoin } from 'rxjs';

import { SPOTLINK_APP_CONFIG } from '@foundation/core';
import { StatusPillComponent, StatusTone, UiButtonComponent } from '@foundation/design-system';
import {
  EmptyStateComponent,
  ErrorStateComponent,
  LoadingSkeletonComponent,
} from '@foundation/shared-components';
import {
  InventoryControl,
  OperatorAccount,
  OperatorBookingDetail,
  OperatorDashboardSummary,
  OperatorResourceHealth,
  OperatorService,
} from '@foundation/operator';
import {
  BookingEvent,
  PaymentAttempt,
  PaymentMode,
  Refund,
  Reservation,
  ReservationStatus,
  SupportCase,
  toReservationCardViewModel,
} from '@foundation/reservations';

@Component({
  selector: 'sl-operator-workspace',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    EmptyStateComponent,
    ErrorStateComponent,
    LoadingSkeletonComponent,
    StatusPillComponent,
    UiButtonComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="hero" id="pregled">
      <div class="sl-container hero__grid">
        <div class="hero__copy">
          <sl-status-pill tone="info">Operator portal</sl-status-pill>
          <h1>Operativa za Beograd pilot</h1>
          <p>
            Angular sada radi kao portal za operatere. Fokus je na dolazecim rezervacijama,
            brzim intervencijama i kontroli prodajnog kapaciteta uz pilot podrazumevane vrednosti
            za Srbiju.
          </p>

          <dl class="pilot-meta">
            <div>
              <dt>Valuta</dt>
              <dd>RSD</dd>
            </div>
            <div>
              <dt>Vremenska zona</dt>
              <dd>Europe/Belgrade</dd>
            </div>
            <div>
              <dt>Placanje</dt>
              <dd>Placanje po dolasku kao pilot podrazumevano</dd>
            </div>
          </dl>

          @if (operator()) {
            <div class="operator-chip">
              <strong>{{ operator()!.displayName }}</strong>
              <span>{{ operatorSupportLabel() }}</span>
            </div>
          }
        </div>

        <div class="summary-grid">
          @if (workspaceLoading()) {
            @for (placeholder of summaryPlaceholders; track placeholder) {
              <article class="summary-card summary-card--loading">
                <sl-loading-skeleton width="7rem" height="0.8rem"></sl-loading-skeleton>
                <sl-loading-skeleton width="5rem" height="2.2rem"></sl-loading-skeleton>
                <sl-loading-skeleton width="9rem" height="0.8rem"></sl-loading-skeleton>
              </article>
            }
          } @else {
            <article class="summary-card">
              <span>Aktivne lokacije</span>
              <strong>{{ summary()?.activeLocations ?? 0 }}</strong>
              <small>Operater vodi prodaju kroz lokacije koje su vec aktivne.</small>
            </article>
            <article class="summary-card">
              <span>Aktivni resursi</span>
              <strong>{{ summary()?.activeResources ?? 0 }}</strong>
              <small>Pregled resursa koji ulaze u pooled pilot kapacitet.</small>
            </article>
            <article class="summary-card">
              <span>Rezervacije danas</span>
              <strong>{{ summary()?.reservationsToday ?? 0 }}</strong>
              <small>Trenutni dnevni operativni promet.</small>
            </article>
            <article class="summary-card">
              <span>Otvoreni tiketi</span>
              <strong>{{ summary()?.pendingSupportTickets ?? 0 }}</strong>
              <small>Slucajevi koji cekaju operatera ili podrska tim.</small>
            </article>
          }
        </div>
      </div>
    </section>

    @if (workspaceError()) {
      <section class="section">
        <div class="sl-container">
          <sl-error-state
            title="Portal nije uspeo da ucita podatke"
            [message]="workspaceError()!"
            (retry)="reloadWorkspace()"
          ></sl-error-state>
        </div>
      </section>
    } @else {
      <section class="section" id="rezervacije">
        <div class="sl-container section-heading">
          <div>
            <h2>Dolazece rezervacije</h2>
            <p>
              Operater vidi naredne dolaske, detaljan tok dogadjaja, hold istek i placanje bez
              potrebe za zasebnim admin ekranom.
            </p>
          </div>
          <div class="section-actions">
            @if (flashMessage()) {
              <sl-status-pill tone="success">{{ flashMessage() }}</sl-status-pill>
            }
            <sl-ui-button variant="secondary" size="sm" (clicked)="reloadWorkspace()">
              Osvezi portal
            </sl-ui-button>
          </div>
        </div>

        <div class="sl-container bookings-layout">
          <aside class="panel panel--list">
            <div class="panel__header">
              <div>
                <h3>Lista dolazaka</h3>
                <p>{{ bookings().length }} rezervacija u operativnom redu.</p>
              </div>
            </div>

            @if (workspaceLoading()) {
              <div class="stack">
                @for (placeholder of bookingPlaceholders; track placeholder) {
                  <article class="booking-card booking-card--loading">
                    <sl-loading-skeleton width="9rem" height="1rem"></sl-loading-skeleton>
                    <sl-loading-skeleton width="12rem" height="0.9rem"></sl-loading-skeleton>
                    <sl-loading-skeleton width="6rem" height="0.9rem"></sl-loading-skeleton>
                  </article>
                }
              </div>
            } @else if (!bookings().length) {
              <sl-empty-state
                title="Nema dolazecih rezervacija"
                description="Kada backend vrati naredne rezervacije, ovde ce se pojaviti operativna lista za check-in i intervencije."
              ></sl-empty-state>
            } @else {
              <div class="stack">
                @for (booking of bookings(); track booking.id) {
                  <button
                    type="button"
                    class="booking-card"
                    [class.booking-card--selected]="booking.id === selectedReservationId()"
                    (click)="selectBooking(booking.id)"
                  >
                    <div class="booking-card__header">
                      <strong>{{ resourceLabel(booking.resourceId) }}</strong>
                      <sl-status-pill [tone]="statusTone(booking.status)">
                        {{ reservationStatusLabel(booking.status) }}
                      </sl-status-pill>
                    </div>
                    <span>{{ bookingCard(booking).schedule }}</span>
                    <span>{{ bookingCard(booking).amount }}</span>
                    <small>
                      {{ paymentModeLabel(booking.paymentMode) }}
                      @if (booking.paymentExpiresAt) {
                        · hold istice {{ formatDateTime(booking.paymentExpiresAt) }}
                      }
                    </small>
                  </button>
                }
              </div>
            }
          </aside>

          <section class="panel panel--detail">
            <div class="panel__header">
              <div>
                <h3>Detalj rezervacije</h3>
                <p>Tok dogadjaja, placanja i podrska na jednom mestu.</p>
              </div>
            </div>

            @if (detailLoading()) {
              <div class="stack">
                <sl-loading-skeleton width="12rem" height="1.2rem"></sl-loading-skeleton>
                <sl-loading-skeleton width="100%" height="9rem"></sl-loading-skeleton>
                <sl-loading-skeleton width="100%" height="14rem"></sl-loading-skeleton>
              </div>
            } @else if (detailError()) {
              <sl-error-state
                title="Detalj nije dostupan"
                [message]="detailError()!"
                (retry)="retrySelectedBooking()"
              ></sl-error-state>
            } @else if (!detail()) {
              <sl-empty-state
                title="Izaberite rezervaciju"
                description="Kliknite na rezervaciju iz leve kolone da otvorite detaljan prikaz za check-in, no-show ili otkazivanje."
              ></sl-empty-state>
            } @else {
              <article class="detail-sheet">
                <div class="detail-sheet__hero">
                  <div>
                    <div class="detail-sheet__title-row">
                      <h4>{{ resourceLabel(detail()!.reservation.resourceId) }}</h4>
                      <sl-status-pill [tone]="statusTone(detail()!.reservation.status)">
                        {{ reservationStatusLabel(detail()!.reservation.status) }}
                      </sl-status-pill>
                    </div>
                    <p>{{ formatDateTime(detail()!.reservation.startsAt) }} - {{ formatDateTime(detail()!.reservation.endsAt) }}</p>
                  </div>

                  <div class="detail-sheet__meta">
                    <span>{{ paymentModeLabel(detail()!.reservation.paymentMode) }}</span>
                    <strong>{{ formatMoney(detail()!.reservation.totalAmountCents, detail()!.reservation.currency) }}</strong>
                  </div>
                </div>

                <div class="detail-grid">
                  <article class="metric-card">
                    <span>Hold</span>
                    <strong>
                      @if (detail()!.hold) {
                        {{ holdStatusLabel(detail()!.hold!.status) }}
                      } @else {
                        Nema hold zapisa
                      }
                    </strong>
                    <small>
                      @if (detail()!.hold?.expiresAt) {
                        Istice {{ formatDateTime(detail()!.hold!.expiresAt) }}
                      } @else {
                        Rezervacija nema aktivan hold.
                      }
                    </small>
                  </article>
                  <article class="metric-card">
                    <span>Check-in</span>
                    <strong>
                      @if (detail()!.checkin) {
                        {{ checkinStatusLabel(detail()!.checkin!.status) }}
                      } @else {
                        Nije evidentiran
                      }
                    </strong>
                    <small>
                      @if (detail()!.checkin?.checkinAt) {
                        {{ formatDateTime(detail()!.checkin!.checkinAt) }}
                      } @else {
                        Operater jos nije prijavio dolazak.
                      }
                    </small>
                  </article>
                  <article class="metric-card">
                    <span>Podrska</span>
                    <strong>{{ detail()!.supportCases.length }}</strong>
                    <small>Otvoreni ili istorijski slucajevi vezani za ovu rezervaciju.</small>
                  </article>
                </div>

                <div class="action-card">
                  <label for="booking-action-text">Napomena ili razlog</label>
                  <textarea
                    id="booking-action-text"
                    rows="3"
                    [ngModel]="bookingActionText"
                    (ngModelChange)="bookingActionText = $event"
                    placeholder="Unesite internu napomenu za operatera ili razlog intervencije"
                  ></textarea>

                  <div class="action-row">
                    <sl-ui-button
                      [loading]="bookingActionBusy() === 'check-in'"
                      [disabled]="!canCheckIn(detail()!.reservation.status)"
                      (clicked)="runBookingAction('check-in')"
                    >
                      Check-in vozaca
                    </sl-ui-button>
                    <sl-ui-button
                      variant="secondary"
                      [loading]="bookingActionBusy() === 'no-show'"
                      [disabled]="!canMarkNoShow(detail()!.reservation.status)"
                      (clicked)="runBookingAction('no-show')"
                    >
                      Oznaci no-show
                    </sl-ui-button>
                    <sl-ui-button
                      variant="danger"
                      [loading]="bookingActionBusy() === 'cancel'"
                      [disabled]="!canCancel(detail()!.reservation.status)"
                      (clicked)="runBookingAction('cancel')"
                    >
                      Otkazi rezervaciju
                    </sl-ui-button>
                  </div>
                </div>

                <div class="detail-columns">
                  <section class="detail-block">
                    <div class="detail-block__header">
                      <h5>Timeline</h5>
                      <span>{{ detail()!.timeline.length }} dogadjaja</span>
                    </div>
                    @if (!detail()!.timeline.length) {
                      <p class="muted">Timeline je trenutno prazan.</p>
                    } @else {
                      <ol class="timeline">
                        @for (event of detail()!.timeline; track event.id) {
                          <li class="timeline__item">
                            <div class="timeline__marker"></div>
                            <div>
                              <strong>{{ bookingEventLabel(event) }}</strong>
                              <p>{{ formatDateTime(event.occurredAt) }}</p>
                              @if (event.notes) {
                                <small>{{ event.notes }}</small>
                              }
                            </div>
                          </li>
                        }
                      </ol>
                    }
                  </section>

                  <section class="detail-block">
                    <div class="detail-block__header">
                      <h5>Placanja i refundacije</h5>
                      <span>{{ detail()!.paymentAttempts.length }} pokusaja</span>
                    </div>

                    @if (!detail()!.paymentAttempts.length) {
                      <p class="muted">Nema pokusaja placanja za ovu rezervaciju.</p>
                    } @else {
                      <div class="stack stack--compact">
                        @for (attempt of detail()!.paymentAttempts; track attempt.id) {
                          <article class="inline-card">
                            <div class="inline-card__header">
                              <strong>{{ attempt.provider }}</strong>
                              <sl-status-pill [tone]="paymentAttemptTone(attempt)">
                                {{ paymentAttemptLabel(attempt) }}
                              </sl-status-pill>
                            </div>
                            <span>{{ formatMoney(attempt.amountCents, attempt.currency) }}</span>
                            <small>{{ paymentModeLabel(attempt.paymentMode) }}</small>
                            @if (attempt.failureMessage) {
                              <small>{{ attempt.failureMessage }}</small>
                            }
                          </article>
                        }
                      </div>
                    }

                    @if (detail()!.refunds.length) {
                      <div class="detail-block__header detail-block__header--sub">
                        <h5>Refundacije</h5>
                        <span>{{ detail()!.refunds.length }}</span>
                      </div>
                      <div class="stack stack--compact">
                        @for (refund of detail()!.refunds; track refund.id) {
                          <article class="inline-card">
                            <div class="inline-card__header">
                              <strong>{{ formatMoney(refund.amountCents, refund.currency) }}</strong>
                              <sl-status-pill [tone]="refundTone(refund)">
                                {{ refundLabel(refund) }}
                              </sl-status-pill>
                            </div>
                            <small>{{ refund.reason || 'Bez dodatnog razloga' }}</small>
                          </article>
                        }
                      </div>
                    }
                  </section>

                  <section class="detail-block detail-block--full">
                    <div class="detail-block__header">
                      <h5>Podrska i kontakt</h5>
                      <span>{{ detail()!.supportCases.length }} slucajeva</span>
                    </div>
                    @if (!detail()!.supportCases.length) {
                      <p class="muted">
                        Nema vezanih slucajeva. Za eskalaciju koristite {{ appConfig.supportEmail }}.
                      </p>
                    } @else {
                      <div class="stack stack--compact">
                        @for (supportCase of detail()!.supportCases; track supportCase.id) {
                          <article class="inline-card">
                            <div class="inline-card__header">
                              <strong>{{ supportCase.subject }}</strong>
                              <sl-status-pill [tone]="supportTone(supportCase)">
                                {{ supportLabel(supportCase) }}
                              </sl-status-pill>
                            </div>
                            <small>{{ supportCase.category }} · azurirano {{ formatDateTime(supportCase.updatedAt) }}</small>
                          </article>
                        }
                      </div>
                    }
                  </section>
                </div>
              </article>
            }
          </section>
        </div>
      </section>

      <section class="section section--muted" id="kapacitet">
        <div class="sl-container section-heading">
          <div>
            <h2>Kontrola prodaje i kapaciteta</h2>
            <p>
              Operater moze da pauzira prodaju, vrati prodaju i postavi novi prodajni limit po
              resursu bez izlaska iz portala.
            </p>
          </div>
        </div>

        <div class="sl-container inventory-grid">
          @if (workspaceLoading()) {
            @for (placeholder of inventoryPlaceholders; track placeholder) {
              <article class="inventory-card inventory-card--loading">
                <sl-loading-skeleton width="8rem" height="1rem"></sl-loading-skeleton>
                <sl-loading-skeleton width="100%" height="7rem"></sl-loading-skeleton>
              </article>
            }
          } @else if (!resources().length) {
            <sl-empty-state
              title="Nema aktivnih resursa"
              description="Kada operater ima aktivne resurse, ovde ce se pojaviti kontrole za pauziranje prodaje i promenu kapaciteta."
            ></sl-empty-state>
          } @else {
            @for (resource of resources(); track resource.resourceId) {
              <article class="inventory-card">
                <div class="inventory-card__header">
                  <div>
                    <h3>{{ resource.label }}</h3>
                    <p>{{ shortId(resource.resourceId) }}</p>
                  </div>
                  <sl-status-pill [tone]="resourceTone(resource)">
                    {{ resourceStatusLabel(resource) }}
                  </sl-status-pill>
                </div>

                <dl class="resource-meta">
                  <div>
                    <dt>Trenutna rezervacija</dt>
                    <dd>{{ resource.currentReservationId ? shortId(resource.currentReservationId) : 'Nema' }}</dd>
                  </div>
                  <div>
                    <dt>Sledeci dolazak</dt>
                    <dd>{{ resource.nextReservationAt ? formatDateTime(resource.nextReservationAt) : 'Nema zakazanog dolaska' }}</dd>
                  </div>
                  <div>
                    <dt>Poslednja izmena prodaje</dt>
                    <dd>{{ inventoryStateLabel(resource) }}</dd>
                  </div>
                </dl>

                <label [attr.for]="'reason-' + resource.resourceId">Razlog pauze</label>
                <textarea
                  [id]="'reason-' + resource.resourceId"
                  rows="2"
                  [ngModel]="resourceReason(resource.resourceId)"
                  (ngModelChange)="setResourceReason(resource.resourceId, $event)"
                  placeholder="Npr. manuelna pauza zbog zadrzanog kapaciteta"
                ></textarea>

                <label [attr.for]="'capacity-' + resource.resourceId">Novi prodajni limit</label>
                <input
                  [id]="'capacity-' + resource.resourceId"
                  type="number"
                  min="0"
                  [ngModel]="resourceCapacity(resource.resourceId)"
                  (ngModelChange)="setResourceCapacity(resource.resourceId, $event)"
                  placeholder="Unesite broj mesta"
                />

                <div class="action-row action-row--inventory">
                  <sl-ui-button
                    variant="secondary"
                    [loading]="inventoryActionKey() === resource.resourceId + ':pause'"
                    (clicked)="pauseSales(resource)"
                  >
                    Pauziraj prodaju
                  </sl-ui-button>
                  <sl-ui-button
                    variant="ghost"
                    [loading]="inventoryActionKey() === resource.resourceId + ':unpause'"
                    (clicked)="unpauseSales(resource)"
                  >
                    Vrati prodaju
                  </sl-ui-button>
                  <sl-ui-button
                    [loading]="inventoryActionKey() === resource.resourceId + ':capacity'"
                    (clicked)="adjustCapacity(resource)"
                  >
                    Sacuvaj limit
                  </sl-ui-button>
                </div>
              </article>
            }
          }
        </div>
      </section>
    }
  `,
  styles: [
    `
      :host {
        display: block;
      }

      .hero {
        padding: var(--sl-space-7) 0 var(--sl-space-5);
        background:
          radial-gradient(circle at 12% 18%, rgba(14, 116, 144, 0.18), transparent 26%),
          linear-gradient(135deg, #f5f7ef 0%, #eef4ea 46%, #f9f3e8 100%);
      }

      .hero__grid {
        display: grid;
        grid-template-columns: minmax(0, 1.05fr) minmax(320px, 0.95fr);
        gap: var(--sl-space-4);
        align-items: start;
      }

      .hero__copy {
        display: grid;
        gap: var(--sl-space-2);
      }

      .hero__copy p {
        max-width: 54rem;
        font-size: var(--sl-font-size-lg);
      }

      .pilot-meta {
        display: grid;
        grid-template-columns: repeat(3, minmax(0, 1fr));
        gap: var(--sl-space-2);
        margin: 0;
      }

      .pilot-meta div,
      .operator-chip,
      .summary-card,
      .panel,
      .metric-card,
      .action-card,
      .detail-block,
      .inline-card,
      .inventory-card {
        border: 1px solid var(--sl-color-border);
        border-radius: var(--sl-radius-md);
        background: var(--sl-color-surface);
        box-shadow: var(--sl-shadow-xs);
      }

      .pilot-meta div {
        display: grid;
        gap: 0.4rem;
        padding: var(--sl-space-2);
      }

      dt {
        color: var(--sl-color-text-muted);
        font-size: var(--sl-font-size-sm);
        font-weight: 700;
      }

      dd {
        margin: 0;
        color: var(--sl-color-text-strong);
        font-weight: 700;
      }

      .operator-chip {
        display: flex;
        flex-wrap: wrap;
        justify-content: space-between;
        gap: var(--sl-space-1);
        padding: var(--sl-space-2);
      }

      .summary-grid {
        display: grid;
        grid-template-columns: repeat(2, minmax(0, 1fr));
        gap: var(--sl-space-2);
      }

      .summary-card {
        display: grid;
        gap: 0.5rem;
        padding: var(--sl-space-2);
      }

      .summary-card strong {
        font-size: clamp(1.8rem, 5vw, 2.5rem);
        line-height: 1;
      }

      .summary-card small,
      .muted,
      .booking-card span,
      .booking-card small,
      .detail-sheet__hero p,
      .detail-block span,
      .inline-card small,
      .inventory-card p {
        color: var(--sl-color-text-muted);
      }

      .section {
        padding: var(--sl-space-5) 0;
      }

      .section--muted {
        background: color-mix(in srgb, var(--sl-color-surface-muted) 72%, white);
      }

      .section-heading {
        display: flex;
        justify-content: space-between;
        gap: var(--sl-space-3);
        align-items: end;
        margin-bottom: var(--sl-space-3);
      }

      .section-heading p {
        max-width: 52rem;
      }

      .section-actions {
        display: flex;
        flex-wrap: wrap;
        justify-content: end;
        align-items: center;
        gap: var(--sl-space-1);
      }

      .bookings-layout {
        display: grid;
        grid-template-columns: minmax(320px, 0.92fr) minmax(0, 1.08fr);
        gap: var(--sl-space-3);
        align-items: start;
      }

      .panel {
        padding: var(--sl-space-2);
      }

      .panel__header,
      .detail-block__header,
      .inventory-card__header,
      .booking-card__header,
      .inline-card__header,
      .detail-sheet__hero,
      .detail-sheet__title-row,
      .action-row,
      .resource-meta {
        display: flex;
        justify-content: space-between;
        gap: var(--sl-space-2);
      }

      .panel__header,
      .detail-block__header,
      .inventory-card__header,
      .detail-sheet__hero,
      .inline-card__header {
        align-items: start;
      }

      .stack {
        display: grid;
        gap: var(--sl-space-2);
      }

      .stack--compact {
        gap: var(--sl-space-1);
      }

      .booking-card {
        width: 100%;
        display: grid;
        gap: 0.5rem;
        padding: var(--sl-space-2);
        border: 1px solid var(--sl-color-border);
        border-radius: var(--sl-radius-md);
        background: var(--sl-color-surface);
        text-align: left;
        cursor: pointer;
        transition:
          border-color 160ms ease,
          box-shadow 160ms ease,
          transform 160ms ease;
      }

      .booking-card:hover {
        border-color: color-mix(in srgb, var(--sl-color-primary) 35%, var(--sl-color-border));
        box-shadow: var(--sl-shadow-sm);
        transform: translateY(-1px);
      }

      .booking-card--selected {
        border-color: var(--sl-color-primary);
        box-shadow: 0 0 0 1px color-mix(in srgb, var(--sl-color-primary) 35%, transparent);
      }

      .detail-sheet {
        display: grid;
        gap: var(--sl-space-2);
      }

      .detail-sheet__meta {
        text-align: right;
      }

      .detail-grid,
      .detail-columns,
      .inventory-grid {
        display: grid;
        gap: var(--sl-space-2);
      }

      .detail-grid {
        grid-template-columns: repeat(3, minmax(0, 1fr));
      }

      .metric-card,
      .action-card,
      .detail-block,
      .inline-card,
      .inventory-card {
        padding: var(--sl-space-2);
      }

      .metric-card {
        display: grid;
        gap: 0.35rem;
      }

      .metric-card strong {
        font-size: 1.2rem;
      }

      .action-card {
        display: grid;
        gap: 0.75rem;
      }

      label {
        color: var(--sl-color-text-strong);
        font-size: var(--sl-font-size-sm);
        font-weight: 700;
      }

      textarea,
      input {
        width: 100%;
        border: 1px solid var(--sl-color-border-strong);
        border-radius: var(--sl-radius-sm);
        padding: 0.8rem 0.9rem;
        font: inherit;
        background: var(--sl-color-surface);
        color: var(--sl-color-text-strong);
      }

      textarea {
        resize: vertical;
      }

      .action-row {
        flex-wrap: wrap;
      }

      .action-row--inventory sl-ui-button {
        flex: 1 1 160px;
      }

      .detail-columns {
        grid-template-columns: repeat(2, minmax(0, 1fr));
      }

      .detail-block {
        display: grid;
        gap: 0.85rem;
      }

      .detail-block--full {
        grid-column: 1 / -1;
      }

      .detail-block__header--sub {
        padding-top: 0.25rem;
        border-top: 1px solid var(--sl-color-border);
      }

      .timeline {
        list-style: none;
        display: grid;
        gap: var(--sl-space-2);
        margin: 0;
        padding: 0;
      }

      .timeline__item {
        display: grid;
        grid-template-columns: 16px 1fr;
        gap: var(--sl-space-1);
      }

      .timeline__marker {
        width: 12px;
        height: 12px;
        margin-top: 0.35rem;
        border-radius: 50%;
        background: var(--sl-color-primary);
      }

      .inline-card {
        display: grid;
        gap: 0.35rem;
      }

      .inventory-grid {
        grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
      }

      .inventory-card {
        display: grid;
        gap: 0.75rem;
      }

      .resource-meta {
        display: grid;
        grid-template-columns: repeat(3, minmax(0, 1fr));
        gap: 0.75rem;
        margin: 0;
      }

      .resource-meta div {
        display: grid;
        gap: 0.35rem;
      }

      @media (max-width: 1080px) {
        .hero__grid,
        .bookings-layout,
        .detail-grid,
        .detail-columns {
          grid-template-columns: 1fr;
        }

        .detail-block--full {
          grid-column: auto;
        }
      }

      @media (max-width: 760px) {
        .pilot-meta,
        .summary-grid,
        .resource-meta {
          grid-template-columns: 1fr;
        }

        .panel__header,
        .section-heading,
        .detail-sheet__hero,
        .inventory-card__header,
        .inline-card__header {
          flex-direction: column;
        }

        .detail-sheet__meta {
          text-align: left;
        }
      }
    `,
  ],
})
export class OperatorWorkspaceComponent implements OnInit {
  private readonly operatorService = inject(OperatorService);

  readonly appConfig = inject(SPOTLINK_APP_CONFIG);

  readonly summaryPlaceholders = Array.from({ length: 4 }, (_, index) => index);
  readonly bookingPlaceholders = Array.from({ length: 4 }, (_, index) => index);
  readonly inventoryPlaceholders = Array.from({ length: 3 }, (_, index) => index);

  readonly operator = signal<OperatorAccount | null>(null);
  readonly summary = signal<OperatorDashboardSummary | null>(null);
  readonly resources = signal<OperatorResourceHealth[]>([]);
  readonly bookings = signal<Reservation[]>([]);
  readonly detail = signal<OperatorBookingDetail | null>(null);
  readonly selectedReservationId = signal<string | null>(null);

  readonly workspaceLoading = signal(true);
  readonly detailLoading = signal(false);
  readonly workspaceError = signal<string | null>(null);
  readonly detailError = signal<string | null>(null);
  readonly bookingActionBusy = signal<'check-in' | 'no-show' | 'cancel' | null>(null);
  readonly inventoryActionKey = signal<string | null>(null);
  readonly flashMessage = signal<string | null>(null);
  readonly inventoryState = signal<Record<string, InventoryControl>>({});

  readonly resourceLabelMap = computed(
    () => new Map(this.resources().map((resource) => [resource.resourceId, resource.label])),
  );

  bookingActionText = '';
  private readonly resourceReasons: Record<string, string> = {};
  private readonly resourceCapacities: Record<string, number | null> = {};

  ngOnInit(): void {
    this.loadWorkspace();
  }

  reloadWorkspace(): void {
    this.loadWorkspace(this.selectedReservationId() ?? undefined);
  }

  retrySelectedBooking(): void {
    const reservationId = this.selectedReservationId();
    if (!reservationId) {
      return;
    }

    this.loadBookingDetail(reservationId);
  }

  selectBooking(reservationId: string): void {
    if (reservationId === this.selectedReservationId() && this.detail()) {
      return;
    }

    this.loadBookingDetail(reservationId);
  }

  bookingCard(reservation: Reservation) {
    return toReservationCardViewModel(reservation, this.resourceLabel(reservation.resourceId));
  }

  resourceLabel(resourceId: string): string {
    return this.resourceLabelMap().get(resourceId) ?? `Resurs ${this.shortId(resourceId)}`;
  }

  operatorSupportLabel(): string {
    const supportEmail = this.operator()?.supportEmail?.trim();
    return supportEmail || this.appConfig.supportEmail;
  }

  reservationStatusLabel(status: ReservationStatus): string {
    switch (status) {
      case 'DRAFT':
        return 'Nacrt';
      case 'PENDING_PAYMENT':
        return 'Ceka uplatu';
      case 'CONFIRMED':
        return 'Potvrdjena';
      case 'ACTIVE':
        return 'Aktivna';
      case 'COMPLETED':
        return 'Zavrsena';
      case 'CANCELLED':
        return 'Otkazana';
      case 'EXPIRED':
        return 'Istekla';
      case 'DISPUTED':
        return 'Sporna';
      case 'NO_SHOW':
        return 'No-show';
    }
  }

  paymentModeLabel(paymentMode: PaymentMode): string {
    return paymentMode === 'PAY_ON_ARRIVAL' ? 'Placanje po dolasku' : 'Online placanje';
  }

  holdStatusLabel(status: OperatorBookingDetail['hold'] extends infer THold
    ? THold extends { status: infer TStatus }
      ? TStatus
      : never
    : never): string {
    switch (status) {
      case 'ACTIVE':
        return 'Aktivan';
      case 'CONSUMED':
        return 'Potrosen';
      case 'RELEASED':
        return 'Oslobodjen';
      case 'EXPIRED':
        return 'Istekao';
      default:
        return 'Nepoznato';
    }
  }

  checkinStatusLabel(status: OperatorBookingDetail['checkin'] extends infer TCheckin
    ? TCheckin extends { status: infer TStatus }
      ? TStatus
      : never
    : never): string {
    switch (status) {
      case 'CHECKED_IN':
        return 'Prijavljen dolazak';
      case 'COMPLETED':
        return 'Boravak zavrsen';
      case 'NO_SHOW':
        return 'No-show';
      default:
        return 'Nepoznato';
    }
  }

  statusTone(status: ReservationStatus): StatusTone {
    switch (status) {
      case 'CONFIRMED':
      case 'ACTIVE':
      case 'COMPLETED':
        return 'success';
      case 'PENDING_PAYMENT':
        return 'warning';
      case 'DRAFT':
        return 'neutral';
      case 'CANCELLED':
      case 'EXPIRED':
      case 'DISPUTED':
      case 'NO_SHOW':
        return 'danger';
    }
  }

  bookingEventLabel(event: BookingEvent): string {
    switch (event.eventType) {
      case 'LEGACY_IMPORTED':
        return 'Istorijski zapis';
      case 'CREATED':
        return 'Rezervacija kreirana';
      case 'HOLD_CREATED':
        return 'Hold otvoren';
      case 'HOLD_EXPIRED':
        return 'Hold istekao';
      case 'STATUS_CHANGED':
        return 'Status promenjen';
      case 'PAYMENT_AUTHORIZED':
        return 'Placanje autorizovano';
      case 'PAYMENT_FAILED':
        return 'Placanje neuspesno';
      case 'CONFIRMED':
        return 'Rezervacija potvrdjena';
      case 'CANCELLED':
        return 'Rezervacija otkazana';
      case 'OPERATOR_CANCELLED':
        return 'Operater otkazao rezervaciju';
      case 'CHECKED_IN':
        return 'Vozac prijavljen';
      case 'NO_SHOW':
        return 'Evidentiran no-show';
      case 'ADMIN_OVERRIDE':
        return 'Admin override';
      case 'REFUND_MARKED':
        return 'Refundacija oznacena';
    }
  }

  paymentAttemptTone(attempt: PaymentAttempt): StatusTone {
    switch (attempt.status) {
      case 'AUTHORIZED':
        return 'success';
      case 'PENDING':
      case 'REQUIRES_ACTION':
        return 'warning';
      case 'FAILED':
      case 'CANCELLED':
        return 'danger';
      case 'REFUND_MARKED':
        return 'info';
    }
  }

  paymentAttemptLabel(attempt: PaymentAttempt): string {
    switch (attempt.status) {
      case 'AUTHORIZED':
        return 'Autorizovano';
      case 'PENDING':
        return 'U toku';
      case 'REQUIRES_ACTION':
        return 'Ceka akciju';
      case 'FAILED':
        return 'Neuspesno';
      case 'CANCELLED':
        return 'Otkazano';
      case 'REFUND_MARKED':
        return 'Refundacija oznacena';
    }
  }

  refundTone(refund: Refund): StatusTone {
    switch (refund.status) {
      case 'PROCESSED':
        return 'success';
      case 'MARKED':
        return 'info';
      case 'FAILED':
        return 'danger';
    }
  }

  refundLabel(refund: Refund): string {
    switch (refund.status) {
      case 'PROCESSED':
        return 'Obradjena';
      case 'MARKED':
        return 'Oznacena';
      case 'FAILED':
        return 'Neuspesna';
    }
  }

  supportTone(supportCase: SupportCase): StatusTone {
    switch (supportCase.status) {
      case 'OPEN':
      case 'WAITING_ON_OPERATOR':
        return 'warning';
      case 'WAITING_ON_CUSTOMER':
        return 'info';
      case 'RESOLVED':
        return 'success';
      default:
        return 'neutral';
    }
  }

  supportLabel(supportCase: SupportCase): string {
    switch (supportCase.status) {
      case 'OPEN':
        return 'Otvoren';
      case 'WAITING_ON_OPERATOR':
        return 'Ceka operatera';
      case 'WAITING_ON_CUSTOMER':
        return 'Ceka korisnika';
      case 'RESOLVED':
        return 'Resen';
      default:
        return supportCase.status;
    }
  }

  canCheckIn(status: ReservationStatus): boolean {
    return status === 'CONFIRMED';
  }

  canMarkNoShow(status: ReservationStatus): boolean {
    return status === 'CONFIRMED';
  }

  canCancel(status: ReservationStatus): boolean {
    return !['CANCELLED', 'COMPLETED', 'EXPIRED', 'NO_SHOW'].includes(status);
  }

  runBookingAction(action: 'check-in' | 'no-show' | 'cancel'): void {
    const reservation = this.detail()?.reservation;
    if (!reservation) {
      return;
    }

    this.detailError.set(null);
    this.bookingActionBusy.set(action);

    const payload = action === 'check-in'
      ? { notes: this.optionalValue(this.bookingActionText) }
      : { reason: this.optionalValue(this.bookingActionText) };

    const request =
      action === 'check-in'
        ? this.operatorService.checkIn(reservation.id, payload)
        : action === 'no-show'
          ? this.operatorService.markNoShow(reservation.id, payload)
          : this.operatorService.cancelBooking(reservation.id, payload);

    request
      .pipe(finalize(() => this.bookingActionBusy.set(null)))
      .subscribe({
        next: () => {
          this.bookingActionText = '';
          this.flashMessage.set(this.bookingActionMessage(action));
          this.loadWorkspace(reservation.id);
        },
        error: (error) => {
          this.detailError.set(
            this.toErrorMessage(error, 'Operativna akcija nije uspela. Pokusajte ponovo.'),
          );
        },
      });
  }

  pauseSales(resource: OperatorResourceHealth): void {
    this.inventoryActionKey.set(`${resource.resourceId}:pause`);
    this.operatorService
      .pauseSales(resource.resourceId, this.optionalValue(this.resourceReason(resource.resourceId)))
      .pipe(finalize(() => this.inventoryActionKey.set(null)))
      .subscribe({
        next: (control) => {
          this.applyInventoryControl(control);
          this.flashMessage.set('Prodaja je pauzirana.');
        },
        error: (error) => {
          this.flashMessage.set(this.toErrorMessage(error, 'Pauza prodaje nije sacuvana.'));
        },
      });
  }

  unpauseSales(resource: OperatorResourceHealth): void {
    this.inventoryActionKey.set(`${resource.resourceId}:unpause`);
    this.operatorService
      .unpauseSales(resource.resourceId)
      .pipe(finalize(() => this.inventoryActionKey.set(null)))
      .subscribe({
        next: (control) => {
          this.applyInventoryControl(control);
          this.flashMessage.set('Prodaja je ponovo aktivna.');
        },
        error: (error) => {
          this.flashMessage.set(this.toErrorMessage(error, 'Aktivacija prodaje nije uspela.'));
        },
      });
  }

  adjustCapacity(resource: OperatorResourceHealth): void {
    const sellableCapacity = this.resourceCapacity(resource.resourceId);
    if (sellableCapacity === null || Number.isNaN(sellableCapacity)) {
      this.flashMessage.set('Unesite novi prodajni limit pre cuvanja.');
      return;
    }

    this.inventoryActionKey.set(`${resource.resourceId}:capacity`);
    this.operatorService
      .adjustSellableCapacity(resource.resourceId, {
        sellableCapacity,
        reason: this.optionalValue(this.resourceReason(resource.resourceId)),
      })
      .pipe(finalize(() => this.inventoryActionKey.set(null)))
      .subscribe({
        next: (control) => {
          this.applyInventoryControl(control);
          this.flashMessage.set('Prodajni limit je sacuvan.');
        },
        error: (error) => {
          this.flashMessage.set(this.toErrorMessage(error, 'Promena kapaciteta nije uspela.'));
        },
      });
  }

  resourceTone(resource: OperatorResourceHealth): StatusTone {
    const control = this.inventoryState()[resource.resourceId];
    if (control?.paused) {
      return 'warning';
    }

    return resource.online ? 'success' : 'danger';
  }

  resourceStatusLabel(resource: OperatorResourceHealth): string {
    const control = this.inventoryState()[resource.resourceId];
    if (control?.paused) {
      return 'Prodaja pauzirana';
    }

    return resource.online ? 'Prodaja aktivna' : 'Resurs neaktivan';
  }

  inventoryStateLabel(resource: OperatorResourceHealth): string {
    const control = this.inventoryState()[resource.resourceId];
    if (!control) {
      return 'Nema lokalno ucitanog override stanja';
    }

    if (control.paused) {
      return control.pauseReason || 'Pauza bez dodatnog razloga';
    }

    return `Osnovni kapacitet ${control.baseCapacity}`;
  }

  resourceReason(resourceId: string): string {
    return this.resourceReasons[resourceId] ?? '';
  }

  setResourceReason(resourceId: string, value: string): void {
    this.resourceReasons[resourceId] = value;
  }

  resourceCapacity(resourceId: string): number | null {
    return this.resourceCapacities[resourceId] ?? null;
  }

  setResourceCapacity(resourceId: string, value: number | string | null): void {
    if (typeof value === 'number') {
      this.resourceCapacities[resourceId] = value;
      return;
    }

    if (value === null || value === '') {
      this.resourceCapacities[resourceId] = null;
      return;
    }

    const parsed = Number(value);
    this.resourceCapacities[resourceId] = Number.isFinite(parsed) ? parsed : null;
  }

  formatDateTime(value?: string): string {
    if (!value) {
      return 'Nije dostupno';
    }

    const parsed = new Date(value);
    if (Number.isNaN(parsed.getTime())) {
      return 'Nije dostupno';
    }

    return new Intl.DateTimeFormat(this.appConfig.defaultLocale || 'sr-RS', {
      dateStyle: 'medium',
      timeStyle: 'short',
      timeZone: 'Europe/Belgrade',
    }).format(parsed);
  }

  formatMoney(amountCents: number, currency: string): string {
    try {
      return new Intl.NumberFormat(this.appConfig.defaultLocale || 'sr-RS', {
        style: 'currency',
        currency,
        maximumFractionDigits: 2,
      }).format(amountCents / 100);
    } catch {
      return `${(amountCents / 100).toFixed(2)} ${currency}`;
    }
  }

  shortId(value?: string): string {
    return value ? `${value.slice(0, 8)}...` : 'N/A';
  }

  private loadWorkspace(focusReservationId?: string): void {
    this.workspaceLoading.set(true);
    this.workspaceError.set(null);

    forkJoin({
      operator: this.operatorService.getCurrentOperator(),
      summary: this.operatorService.getDashboardSummary(),
      resources: this.operatorService.getResourceHealth(),
      bookings: this.operatorService.getUpcomingBookings(),
    })
      .pipe(finalize(() => this.workspaceLoading.set(false)))
      .subscribe({
        next: ({ operator, summary, resources, bookings }) => {
          this.operator.set(operator);
          this.summary.set(summary);
          this.resources.set(resources);
          this.bookings.set(bookings.content);
          this.seedInventoryForms(resources);

          const nextReservationId = this.resolveSelectedReservationId(
            bookings.content,
            focusReservationId,
          );
          if (nextReservationId) {
            this.loadBookingDetail(nextReservationId);
          } else {
            this.selectedReservationId.set(null);
            this.detail.set(null);
            this.detailError.set(null);
          }
        },
        error: (error) => {
          this.workspaceError.set(
            this.toErrorMessage(error, 'Portal nije uspeo da ucita operativne podatke.'),
          );
        },
      });
  }

  private loadBookingDetail(reservationId: string): void {
    this.selectedReservationId.set(reservationId);
    this.detailLoading.set(true);
    this.detailError.set(null);

    this.operatorService
      .getBookingDetail(reservationId)
      .pipe(finalize(() => this.detailLoading.set(false)))
      .subscribe({
        next: (detail) => {
          this.detail.set(detail);
        },
        error: (error) => {
          this.detail.set(null);
          this.detailError.set(
            this.toErrorMessage(error, 'Detalj rezervacije nije dostupan u ovom trenutku.'),
          );
        },
      });
  }

  private resolveSelectedReservationId(
    bookings: Reservation[],
    preferredReservationId?: string,
  ): string | null {
    if (!bookings.length) {
      return null;
    }

    if (preferredReservationId && bookings.some((booking) => booking.id === preferredReservationId)) {
      return preferredReservationId;
    }

    const currentReservationId = this.selectedReservationId();
    if (currentReservationId && bookings.some((booking) => booking.id === currentReservationId)) {
      return currentReservationId;
    }

    return bookings[0]?.id ?? null;
  }

  private seedInventoryForms(resources: OperatorResourceHealth[]): void {
    for (const resource of resources) {
      if (!(resource.resourceId in this.resourceReasons)) {
        this.resourceReasons[resource.resourceId] = '';
      }
      if (!(resource.resourceId in this.resourceCapacities)) {
        this.resourceCapacities[resource.resourceId] = null;
      }
    }
  }

  private applyInventoryControl(control: InventoryControl): void {
    this.inventoryState.update((current) => ({
      ...current,
      [control.resourceId]: control,
    }));
    this.resourceCapacities[control.resourceId] = control.baseCapacity;
  }

  private optionalValue(value: string): string | undefined {
    const trimmed = value.trim();
    return trimmed ? trimmed : undefined;
  }

  private bookingActionMessage(action: 'check-in' | 'no-show' | 'cancel'): string {
    switch (action) {
      case 'check-in':
        return 'Dolazak vozaca je evidentiran.';
      case 'no-show':
        return 'Rezervacija je oznacena kao no-show.';
      case 'cancel':
        return 'Rezervacija je otkazana.';
    }
  }

  private toErrorMessage(error: unknown, fallback: string): string {
    const candidate = error as {
      error?: { message?: string };
      message?: string;
    };

    return candidate?.error?.message || candidate?.message || fallback;
  }
}