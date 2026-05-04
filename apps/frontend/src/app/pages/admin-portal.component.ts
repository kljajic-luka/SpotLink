import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Observable, finalize } from 'rxjs';

import {
  AdminAuditEvent,
  AdminBookingDetail,
  AdminPaymentAttempt,
  AdminService,
  AdminSupportCase,
  PauseOperationResult,
} from '@foundation/admin';
import { StatusPillComponent, StatusTone, UiButtonComponent } from '@foundation/design-system';
import {
  EmptyStateComponent,
  ErrorStateComponent,
  LoadingSkeletonComponent,
} from '@foundation/shared-components';
import {
  BookingEvent,
  PaymentAttempt,
  Refund,
  Reservation,
  ReservationStatus,
  SupportCase,
} from '@foundation/reservations';

type AdminActionKey = 'confirm' | 'reject' | 'cancel' | 'refund' | 'pause-location' | 'pause-operator';

@Component({
  selector: 'sl-admin-portal',
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
    <section class="admin-shell">
      <div class="sl-container admin-toolbar">
        <div>
          <h1>Admin operativa</h1>
          <p>RSD · Europe/Belgrade · sr-RS</p>
        </div>
        <div class="toolbar-status">
          @if (flashMessage()) {
            <sl-status-pill tone="success">{{ flashMessage() }}</sl-status-pill>
          }
          @if (actionError()) {
            <sl-status-pill tone="danger">{{ actionError() }}</sl-status-pill>
          }
        </div>
      </div>

      <div class="sl-container admin-search">
        <form class="search-form" (ngSubmit)="searchBookings()">
          <label for="booking-query">Booking search</label>
          <input
            id="booking-query"
            name="bookingQuery"
            type="search"
            [(ngModel)]="bookingQuery"
            placeholder="Reservation ID, booking code, operator context"
          />

          <label for="status-filter">Status</label>
          <select id="status-filter" name="statusFilter" [(ngModel)]="statusFilter">
            <option value="">All</option>
            @for (status of reservationStatuses; track status) {
              <option [value]="status">{{ reservationStatusLabel(status) }}</option>
            }
          </select>

          <sl-ui-button type="submit" [loading]="bookingsLoading()">Search</sl-ui-button>
        </form>
      </div>

      <div class="sl-container admin-grid">
        <aside class="panel panel--list">
          <div class="panel__header">
            <h2>Bookings</h2>
            <sl-ui-button variant="secondary" size="sm" (clicked)="searchBookings()">Refresh</sl-ui-button>
          </div>

          @if (bookingsLoading()) {
            <div class="stack">
              @for (placeholder of placeholders; track placeholder) {
                <sl-loading-skeleton width="100%" height="5rem"></sl-loading-skeleton>
              }
            </div>
          } @else if (bookingsError()) {
            <sl-error-state
              title="Bookings unavailable"
              [message]="bookingsError()!"
              (retry)="searchBookings()"
            ></sl-error-state>
          } @else if (!bookings().length) {
            <sl-empty-state title="No bookings" description="No bookings match the active filters."></sl-empty-state>
          } @else {
            <div class="stack">
              @for (booking of bookings(); track booking.id) {
                <button
                  type="button"
                  class="booking-row"
                  [class.booking-row--active]="booking.id === selectedReservationId()"
                  (click)="selectBooking(booking.id)"
                >
                  <span>{{ shortId(booking.id) }}</span>
                  <strong>{{ formatMoney(booking.totalAmountCents, booking.currency) }}</strong>
                  <small>{{ formatDateTime(booking.startsAt) }}</small>
                  <small>{{ booking.bookingCode || 'No code' }}</small>
                  <sl-status-pill [tone]="statusTone(booking.status)">
                    {{ reservationStatusLabel(booking.status) }}
                  </sl-status-pill>
                </button>
              }
            </div>
          }
        </aside>

        <section class="panel panel--detail">
          <div class="panel__header">
            <h2>Booking detail</h2>
            @if (detail()) {
              <span>{{ shortId(detail()!.reservation.id) }}</span>
            }
          </div>

          @if (detailLoading()) {
            <div class="stack">
              <sl-loading-skeleton width="100%" height="7rem"></sl-loading-skeleton>
              <sl-loading-skeleton width="100%" height="12rem"></sl-loading-skeleton>
            </div>
          } @else if (detailError()) {
            <sl-error-state
              title="Detail unavailable"
              [message]="detailError()!"
              (retry)="retrySelectedBooking()"
            ></sl-error-state>
          } @else if (!detail()) {
            <sl-empty-state title="Select a booking" description="Select a booking from the list."></sl-empty-state>
          } @else {
            <article class="detail-card">
              <div class="detail-card__hero">
                <div>
                  <h3>{{ shortId(detail()!.reservation.id) }}</h3>
                  <p>{{ formatDateTime(detail()!.reservation.startsAt) }} - {{ formatDateTime(detail()!.reservation.endsAt) }}</p>
                </div>
                <sl-status-pill [tone]="statusTone(detail()!.reservation.status)">
                  {{ reservationStatusLabel(detail()!.reservation.status) }}
                </sl-status-pill>
              </div>

              <dl class="fact-grid">
                <div>
                  <dt>Customer</dt>
                  <dd>{{ shortId(detail()!.reservation.customerId) }}</dd>
                </div>
                <div>
                  <dt>Operator</dt>
                  <dd>{{ shortId(detail()!.reservation.operatorId) }}</dd>
                </div>
                <div>
                  <dt>Location</dt>
                  <dd>{{ shortId(detail()!.reservation.locationId) }}</dd>
                </div>
                <div>
                  <dt>Resource</dt>
                  <dd>{{ shortId(detail()!.reservation.resourceId) }}</dd>
                </div>
                <div>
                  <dt>Payment mode</dt>
                  <dd>{{ paymentModeLabel(detail()!.reservation.paymentMode) }}</dd>
                </div>
                <div>
                  <dt>Booking code</dt>
                  <dd>{{ detail()!.reservation.bookingCode || 'None' }}</dd>
                </div>
                <div>
                  <dt>Hold</dt>
                  <dd>{{ detail()!.hold ? detail()!.hold!.status : 'None' }}</dd>
                </div>
              </dl>

              <div class="manual-actions">
                <label for="admin-action-reason">Reason</label>
                <textarea
                  id="admin-action-reason"
                  rows="3"
                  [(ngModel)]="actionReason"
                  name="actionReason"
                  placeholder="Reason recorded in timeline and audit log"
                ></textarea>

                <div class="refund-row">
                  <label for="refund-amount">Refund amount cents</label>
                  <input
                    id="refund-amount"
                    name="refundAmountCents"
                    type="number"
                    min="0"
                    [(ngModel)]="refundAmountCents"
                  />
                </div>

                <div class="button-row">
                  <sl-ui-button
                    variant="secondary"
                    [loading]="actionBusy() === 'confirm'"
                    [disabled]="!canConfirmManual(detail()!.reservation.status)"
                    (clicked)="confirmManualBooking()"
                  >
                    Confirm manual
                  </sl-ui-button>
                  <sl-ui-button
                    variant="danger"
                    [loading]="actionBusy() === 'reject'"
                    [disabled]="!canConfirmManual(detail()!.reservation.status)"
                    (clicked)="rejectManualBooking()"
                  >
                    Reject manual
                  </sl-ui-button>
                  <sl-ui-button
                    variant="danger"
                    [loading]="actionBusy() === 'cancel'"
                    [disabled]="!canCancel(detail()!.reservation.status)"
                    (clicked)="cancelBooking()"
                  >
                    Manual cancel
                  </sl-ui-button>
                  <sl-ui-button
                    variant="secondary"
                    [loading]="actionBusy() === 'refund'"
                    (clicked)="markRefund()"
                  >
                    Mark refund
                  </sl-ui-button>
                </div>
              </div>
            </article>

            <div class="detail-columns">
              <section class="subpanel">
                <div class="panel__header">
                  <h3>Timeline</h3>
                  <span>{{ detail()!.timeline.length }}</span>
                </div>
                @if (!detail()!.timeline.length) {
                  <p class="muted">No timeline events.</p>
                } @else {
                  <ol class="timeline">
                    @for (event of detail()!.timeline; track event.id) {
                      <li>
                        <strong>{{ bookingEventLabel(event) }}</strong>
                        <span>{{ formatDateTime(event.occurredAt) }}</span>
                        @if (event.notes) {
                          <small>{{ event.notes }}</small>
                        }
                      </li>
                    }
                  </ol>
                }
              </section>

              <section class="subpanel">
                <div class="panel__header">
                  <h3>Payment attempts</h3>
                  <span>{{ detail()!.paymentAttempts.length }}</span>
                </div>
                <div class="stack stack--compact">
                  @for (attempt of detail()!.paymentAttempts; track attempt.id) {
                    <article class="inline-row">
                      <strong>{{ attempt.provider }}</strong>
                      <span>{{ paymentAttemptLabel(attempt) }}</span>
                      <small>{{ formatMoney(attempt.amountCents, attempt.currency) }}</small>
                    </article>
                  } @empty {
                    <p class="muted">No payment attempts.</p>
                  }
                </div>

                @if (detail()!.refunds.length) {
                  <div class="panel__header panel__header--sub">
                    <h3>Refunds</h3>
                    <span>{{ detail()!.refunds.length }}</span>
                  </div>
                  <div class="stack stack--compact">
                    @for (refund of detail()!.refunds; track refund.id) {
                      <article class="inline-row">
                        <strong>{{ formatMoney(refund.amountCents, refund.currency) }}</strong>
                        <span>{{ refundLabel(refund) }}</span>
                        <small>{{ refund.reason || 'No reason' }}</small>
                      </article>
                    }
                  </div>
                }
              </section>

              <section class="subpanel subpanel--full">
                <div class="panel__header">
                  <h3>Support cases</h3>
                  <span>{{ detail()!.supportCases.length }}</span>
                </div>
                <div class="stack stack--compact">
                  @for (supportCase of detail()!.supportCases; track supportCase.id) {
                    <article class="inline-row">
                      <strong>{{ supportCase.subject }}</strong>
                      <span>{{ supportCase.status }}</span>
                      <small>{{ supportCase.category }} · {{ formatDateTime(supportCase.updatedAt) }}</small>
                    </article>
                  } @empty {
                    <p class="muted">No support cases.</p>
                  }
                </div>
              </section>
            </div>
          }
        </section>

        <aside class="panel panel--side">
          <section class="subpanel">
            <h2>Pause controls</h2>
            <label for="pause-location-id">Location ID</label>
            <input id="pause-location-id" name="pauseLocationId" [(ngModel)]="pauseLocationId" />
            <label for="pause-operator-id">Operator ID</label>
            <input id="pause-operator-id" name="pauseOperatorId" [(ngModel)]="pauseOperatorId" />
            <label for="pause-reason">Reason</label>
            <textarea id="pause-reason" rows="2" name="pauseReason" [(ngModel)]="pauseReason"></textarea>
            @if (pauseResult()) {
              <p class="success">
                {{ shortId(pauseResult()!.targetId) }} · pools affected {{ pauseResult()!.affectedPools }}
              </p>
            }
            <div class="button-row">
              <sl-ui-button
                variant="secondary"
                [loading]="actionBusy() === 'pause-location'"
                (clicked)="pauseLocation()"
              >
                Pause location
              </sl-ui-button>
              <sl-ui-button
                variant="secondary"
                [loading]="actionBusy() === 'pause-operator'"
                (clicked)="pauseOperator()"
              >
                Pause operator
              </sl-ui-button>
            </div>
          </section>

          <section class="subpanel">
            <div class="panel__header">
              <h2>Payment attempts</h2>
              <sl-ui-button variant="ghost" size="sm" (clicked)="loadPaymentAttempts()">Refresh</sl-ui-button>
            </div>
            <div class="stack stack--compact">
              @for (attempt of paymentAttempts(); track attempt.id) {
                <article class="inline-row">
                  <strong>{{ paymentAttemptLabel(attempt) }}</strong>
                  <span>{{ shortId(attempt.reservationId) }}</span>
                  <small>{{ attempt.provider }} · {{ formatMoney(attempt.amountCents, attempt.currency) }}</small>
                </article>
              } @empty {
                <p class="muted">No payment attempts loaded.</p>
              }
            </div>
          </section>

          <section class="subpanel">
            <div class="panel__header">
              <h2>Support cases</h2>
              <sl-ui-button variant="ghost" size="sm" (clicked)="loadSupportCases()">Refresh</sl-ui-button>
            </div>
            <div class="stack stack--compact">
              @for (supportCase of supportCases(); track supportCase.id) {
                <article class="inline-row">
                  <strong>{{ supportCase.subject }}</strong>
                  <span>{{ supportLabel(supportCase) }}</span>
                  <small>{{ formatDateTime(supportCase.updatedAt) }}</small>
                </article>
              } @empty {
                <p class="muted">No support cases loaded.</p>
              }
            </div>
          </section>

          <section class="subpanel">
            <div class="panel__header">
              <h2>Audit events</h2>
              <sl-ui-button variant="ghost" size="sm" (clicked)="loadAuditEvents()">Refresh</sl-ui-button>
            </div>
            <div class="stack stack--compact">
              @for (event of auditEvents(); track event.id) {
                <article class="inline-row">
                  <strong>{{ event.action }}</strong>
                  <span>{{ event.resourceType }} · {{ shortId(event.resourceId) }}</span>
                  <small>{{ formatDateTime(event.createdAt) }} · {{ metadataSummary(event) }}</small>
                </article>
              } @empty {
                <p class="muted">No audit events loaded.</p>
              }
            </div>
          </section>
        </aside>
      </div>
    </section>
  `,
  styles: [
    `
      :host {
        display: block;
      }

      .admin-shell {
        padding: var(--sl-space-4) 0 var(--sl-space-5);
        background: var(--sl-color-surface-muted);
      }

      .admin-toolbar,
      .admin-search,
      .admin-grid,
      .detail-columns,
      .stack {
        display: grid;
        gap: var(--sl-space-2);
      }

      .admin-toolbar {
        grid-template-columns: minmax(0, 1fr) auto;
        align-items: end;
        margin-bottom: var(--sl-space-3);
      }

      h1,
      h2,
      h3,
      p {
        margin: 0;
      }

      h1 {
        font-size: var(--sl-font-size-2xl);
      }

      h2 {
        font-size: var(--sl-font-size-lg);
      }

      h3 {
        font-size: var(--sl-font-size-md);
      }

      .toolbar-status,
      .button-row,
      .panel__header,
      .detail-card__hero {
        display: flex;
        justify-content: space-between;
        align-items: start;
        gap: var(--sl-space-2);
      }

      .toolbar-status,
      .button-row {
        flex-wrap: wrap;
        justify-content: end;
      }

      .admin-search,
      .panel,
      .subpanel,
      .detail-card,
      .booking-row,
      .inline-row {
        border: 1px solid var(--sl-color-border);
        border-radius: var(--sl-radius-md);
        background: var(--sl-color-surface);
        box-shadow: var(--sl-shadow-xs);
      }

      .admin-search,
      .panel,
      .subpanel,
      .detail-card {
        padding: var(--sl-space-2);
      }

      .search-form {
        display: grid;
        grid-template-columns: minmax(240px, 1fr) minmax(160px, 220px) auto;
        gap: var(--sl-space-1);
        align-items: end;
      }

      label {
        display: grid;
        gap: 0.35rem;
        color: var(--sl-color-text-strong);
        font-size: var(--sl-font-size-sm);
        font-weight: 700;
      }

      input,
      select,
      textarea {
        width: 100%;
        border: 1px solid var(--sl-color-border-strong);
        border-radius: var(--sl-radius-sm);
        padding: 0.75rem 0.85rem;
        background: var(--sl-color-surface);
        color: var(--sl-color-text-strong);
        font: inherit;
      }

      .admin-grid {
        grid-template-columns: minmax(260px, 0.82fr) minmax(0, 1.35fr) minmax(300px, 0.9fr);
        align-items: start;
      }

      .booking-row {
        width: 100%;
        display: grid;
        gap: 0.35rem;
        padding: var(--sl-space-2);
        text-align: left;
        cursor: pointer;
      }

      .booking-row--active {
        border-color: var(--sl-color-primary);
        box-shadow: 0 0 0 1px color-mix(in srgb, var(--sl-color-primary) 35%, transparent);
      }

      .detail-card,
      .manual-actions,
      .subpanel,
      .inline-row {
        display: grid;
        gap: var(--sl-space-1);
      }

      .fact-grid {
        display: grid;
        grid-template-columns: repeat(3, minmax(0, 1fr));
        gap: var(--sl-space-1);
        margin: 0;
      }

      .fact-grid div {
        display: grid;
        gap: 0.35rem;
        padding: var(--sl-space-1);
        border-radius: var(--sl-radius-sm);
        background: var(--sl-color-surface-muted);
      }

      dt,
      .muted,
      .inline-row small,
      .inline-row span,
      .booking-row small,
      .admin-toolbar p {
        color: var(--sl-color-text-muted);
      }

      dd {
        margin: 0;
        color: var(--sl-color-text-strong);
        font-weight: 700;
      }

      .refund-row,
      .detail-columns {
        display: grid;
        grid-template-columns: repeat(2, minmax(0, 1fr));
        gap: var(--sl-space-2);
      }

      .subpanel--full {
        grid-column: 1 / -1;
      }

      .panel__header--sub {
        padding-top: var(--sl-space-1);
        border-top: 1px solid var(--sl-color-border);
      }

      .timeline {
        list-style: none;
        display: grid;
        gap: var(--sl-space-1);
        margin: 0;
        padding: 0;
      }

      .timeline li,
      .inline-row {
        padding: var(--sl-space-1);
        border-radius: var(--sl-radius-sm);
        background: var(--sl-color-surface-muted);
      }

      .timeline li {
        display: grid;
        gap: 0.25rem;
      }

      .success {
        color: var(--sl-color-success);
        font-weight: 700;
      }

      @media (max-width: 1180px) {
        .admin-grid,
        .detail-columns,
        .fact-grid {
          grid-template-columns: 1fr;
        }

        .subpanel--full {
          grid-column: auto;
        }
      }

      @media (max-width: 760px) {
        .admin-toolbar,
        .search-form,
        .refund-row {
          grid-template-columns: 1fr;
        }

        .toolbar-status,
        .button-row,
        .panel__header,
        .detail-card__hero {
          justify-content: start;
        }
      }
    `,
  ],
})
export class AdminPortalComponent implements OnInit {
  private readonly adminService = inject(AdminService);

  readonly reservationStatuses: ReservationStatus[] = [
    'DRAFT',
    'PENDING_PAYMENT',
    'PENDING_OPERATOR_CONFIRMATION',
    'CONFIRMED',
    'ACTIVE',
    'COMPLETED',
    'CANCELLED',
    'REJECTED',
    'EXPIRED',
    'DISPUTED',
    'NO_SHOW',
  ];
  readonly placeholders = Array.from({ length: 4 }, (_, index) => index);

  readonly bookings = signal<Reservation[]>([]);
  readonly detail = signal<AdminBookingDetail | null>(null);
  readonly paymentAttempts = signal<AdminPaymentAttempt[]>([]);
  readonly supportCases = signal<AdminSupportCase[]>([]);
  readonly auditEvents = signal<AdminAuditEvent[]>([]);
  readonly pauseResult = signal<PauseOperationResult | null>(null);
  readonly selectedReservationId = signal<string | null>(null);

  readonly bookingsLoading = signal(false);
  readonly detailLoading = signal(false);
  readonly sideLoading = signal(false);
  readonly bookingsError = signal<string | null>(null);
  readonly detailError = signal<string | null>(null);
  readonly actionError = signal<string | null>(null);
  readonly flashMessage = signal<string | null>(null);
  readonly actionBusy = signal<AdminActionKey | null>(null);

  bookingQuery = '';
  statusFilter: ReservationStatus | '' = '';
  actionReason = '';
  refundAmountCents: number | null = null;
  pauseLocationId = '';
  pauseOperatorId = '';
  pauseReason = '';

  ngOnInit(): void {
    this.searchBookings();
    this.loadPaymentAttempts();
    this.loadSupportCases();
    this.loadAuditEvents();
  }

  searchBookings(): void {
    this.bookingsLoading.set(true);
    this.bookingsError.set(null);

    this.adminService
      .searchBookings({
        query: this.optionalValue(this.bookingQuery),
        status: this.statusFilter || undefined,
        page: 0,
        size: 25,
      })
      .pipe(finalize(() => this.bookingsLoading.set(false)))
      .subscribe({
        next: (page) => {
          this.bookings.set(page.content);
          const selectedId = this.selectedReservationId();
          if (selectedId && !page.content.some((booking) => booking.id === selectedId)) {
            this.selectedReservationId.set(null);
            this.detail.set(null);
          }
        },
        error: (error) => {
          this.bookingsError.set(this.toErrorMessage(error, 'Booking search failed.'));
        },
      });
  }

  selectBooking(reservationId: string): void {
    this.selectedReservationId.set(reservationId);
    this.loadBookingDetail(reservationId);
    this.loadPaymentAttempts(reservationId);
  }

  retrySelectedBooking(): void {
    const reservationId = this.selectedReservationId();
    if (reservationId) {
      this.loadBookingDetail(reservationId);
    }
  }

  cancelBooking(): void {
    const reservation = this.detail()?.reservation;
    if (!reservation || !this.confirmManualAction('Manual cancel this booking?')) {
      return;
    }

    this.runManualAction('cancel', () =>
      this.adminService.cancelBooking(reservation.id, this.optionalValue(this.actionReason)),
    );
  }

  confirmManualBooking(): void {
    const reservation = this.detail()?.reservation;
    if (!reservation || !this.confirmManualAction('Confirm this manual booking?')) {
      return;
    }

    this.runManualAction('confirm', () =>
      this.adminService.confirmManualBooking(reservation.id, this.optionalValue(this.actionReason)),
    );
  }

  rejectManualBooking(): void {
    const reservation = this.detail()?.reservation;
    if (!reservation || !this.confirmManualAction('Reject this manual booking?')) {
      return;
    }

    this.runManualAction('reject', () =>
      this.adminService.rejectManualBooking(reservation.id, this.optionalValue(this.actionReason)),
    );
  }

  markRefund(): void {
    const reservation = this.detail()?.reservation;
    if (!reservation || !this.confirmManualAction('Mark this booking for refund?')) {
      return;
    }

    this.runManualAction('refund', () =>
      this.adminService.markRefund(reservation.id, {
        amountCents: this.refundAmountCents ?? undefined,
        reason: this.optionalValue(this.actionReason),
      }),
    );
  }

  pauseLocation(): void {
    const locationId = this.optionalValue(this.pauseLocationId);
    if (!locationId) {
      this.actionError.set('Location ID is required.');
      return;
    }
    if (!this.confirmManualAction('Pause this location?')) {
      return;
    }

    this.actionBusy.set('pause-location');
    this.actionError.set(null);
    this.flashMessage.set(null);

    this.adminService
      .pauseLocation(locationId, this.optionalValue(this.pauseReason))
      .pipe(finalize(() => this.actionBusy.set(null)))
      .subscribe({
        next: (result) => this.handlePauseSuccess(result, 'Location paused.'),
        error: (error) => this.actionError.set(this.toErrorMessage(error, 'Location pause failed.')),
      });
  }

  pauseOperator(): void {
    const operatorId = this.optionalValue(this.pauseOperatorId);
    if (!operatorId) {
      this.actionError.set('Operator ID is required.');
      return;
    }
    if (!this.confirmManualAction('Pause this operator?')) {
      return;
    }

    this.actionBusy.set('pause-operator');
    this.actionError.set(null);
    this.flashMessage.set(null);

    this.adminService
      .pauseOperator(operatorId, this.optionalValue(this.pauseReason))
      .pipe(finalize(() => this.actionBusy.set(null)))
      .subscribe({
        next: (result) => this.handlePauseSuccess(result, 'Operator paused.'),
        error: (error) => this.actionError.set(this.toErrorMessage(error, 'Operator pause failed.')),
      });
  }

  loadPaymentAttempts(reservationId = this.selectedReservationId() ?? undefined): void {
    this.sideLoading.set(true);
    this.adminService
      .listPaymentAttempts(reservationId)
      .pipe(finalize(() => this.sideLoading.set(false)))
      .subscribe({
        next: (page) => this.paymentAttempts.set(page.content),
        error: (error) => this.actionError.set(this.toErrorMessage(error, 'Payment attempts failed.')),
      });
  }

  loadSupportCases(): void {
    this.adminService.listSupportCases().subscribe({
      next: (page) => this.supportCases.set(page.content),
      error: (error) => this.actionError.set(this.toErrorMessage(error, 'Support cases failed.')),
    });
  }

  loadAuditEvents(): void {
    this.adminService.listAuditEvents(0, 25).subscribe({
      next: (page) => this.auditEvents.set(page.content),
      error: (error) => this.actionError.set(this.toErrorMessage(error, 'Audit events failed.')),
    });
  }

  canCancel(status: ReservationStatus): boolean {
    return !['CANCELLED', 'REJECTED', 'COMPLETED', 'EXPIRED', 'NO_SHOW'].includes(status);
  }

  canConfirmManual(status: ReservationStatus): boolean {
    return status === 'PENDING_OPERATOR_CONFIRMATION';
  }

  reservationStatusLabel(status: ReservationStatus): string {
    switch (status) {
      case 'DRAFT':
        return 'Draft';
      case 'PENDING_PAYMENT':
        return 'Pending payment';
      case 'PENDING_OPERATOR_CONFIRMATION':
        return 'Pending operator confirmation';
      case 'CONFIRMED':
        return 'Confirmed';
      case 'ACTIVE':
        return 'Active';
      case 'COMPLETED':
        return 'Completed';
      case 'CANCELLED':
        return 'Cancelled';
      case 'REJECTED':
        return 'Rejected';
      case 'EXPIRED':
        return 'Expired';
      case 'DISPUTED':
        return 'Disputed';
      case 'NO_SHOW':
        return 'No-show';
    }
  }

  statusTone(status: ReservationStatus): StatusTone {
    switch (status) {
      case 'CONFIRMED':
      case 'ACTIVE':
      case 'COMPLETED':
        return 'success';
      case 'PENDING_PAYMENT':
      case 'PENDING_OPERATOR_CONFIRMATION':
        return 'warning';
      case 'DRAFT':
        return 'neutral';
      case 'CANCELLED':
      case 'REJECTED':
      case 'EXPIRED':
      case 'DISPUTED':
      case 'NO_SHOW':
        return 'danger';
    }
  }

  paymentModeLabel(paymentMode: Reservation['paymentMode']): string {
    return paymentMode === 'PAY_ON_ARRIVAL' ? 'Pay on arrival' : 'Online';
  }

  bookingEventLabel(event: BookingEvent): string {
    return event.eventType.replaceAll('_', ' ');
  }

  paymentAttemptLabel(attempt: PaymentAttempt): string {
    return attempt.status.replaceAll('_', ' ');
  }

  refundLabel(refund: Refund): string {
    return refund.status.replaceAll('_', ' ');
  }

  supportLabel(supportCase: SupportCase): string {
    return supportCase.status.replaceAll('_', ' ');
  }

  metadataSummary(event: AdminAuditEvent): string {
    if (!event.metadata) {
      return 'No metadata';
    }
    return Object.entries(event.metadata)
      .slice(0, 2)
      .map(([key, value]) => `${key}: ${String(value)}`)
      .join(', ');
  }

  formatDateTime(value?: string): string {
    if (!value) {
      return 'N/A';
    }
    const parsed = new Date(value);
    if (Number.isNaN(parsed.getTime())) {
      return 'N/A';
    }
    return new Intl.DateTimeFormat('sr-RS', {
      dateStyle: 'medium',
      timeStyle: 'short',
      timeZone: 'Europe/Belgrade',
    }).format(parsed);
  }

  formatMoney(amountCents: number, currency: string): string {
    try {
      return new Intl.NumberFormat('sr-RS', {
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

  private loadBookingDetail(reservationId: string): void {
    this.detailLoading.set(true);
    this.detailError.set(null);

    this.adminService
      .getBookingDetail(reservationId)
      .pipe(finalize(() => this.detailLoading.set(false)))
      .subscribe({
        next: (detail) => this.detail.set(detail),
        error: (error) => {
          this.detail.set(null);
          this.detailError.set(this.toErrorMessage(error, 'Booking detail failed.'));
        },
      });
  }

  private runManualAction(action: AdminActionKey, requestFactory: () => Observable<unknown>): void {
    const reservationId = this.detail()?.reservation.id;
    if (!reservationId) {
      return;
    }

    this.actionBusy.set(action);
    this.actionError.set(null);
    this.flashMessage.set(null);

    requestFactory()
      .pipe(finalize(() => this.actionBusy.set(null)))
      .subscribe({
        next: () => {
          this.flashMessage.set(this.successMessageFor(action));
          this.actionReason = '';
          this.loadBookingDetail(reservationId);
          this.searchBookings();
          this.loadPaymentAttempts(reservationId);
          this.loadSupportCases();
          this.loadAuditEvents();
        },
        error: (error: unknown) => {
          this.actionError.set(this.toErrorMessage(error, 'Manual action failed.'));
        },
      });
  }

  private handlePauseSuccess(result: PauseOperationResult, message: string): void {
    this.pauseResult.set(result);
    this.flashMessage.set(`${message} Audit events refreshed.`);
    this.pauseReason = '';
    this.loadAuditEvents();
    this.searchBookings();
  }

  private successMessageFor(action: AdminActionKey): string {
    switch (action) {
      case 'confirm':
        return 'Booking confirmed. Audit events refreshed.';
      case 'reject':
        return 'Booking rejected. Audit events refreshed.';
      case 'cancel':
        return 'Booking cancelled. Audit events refreshed.';
      case 'refund':
        return 'Refund marker saved. Audit events refreshed.';
      case 'pause-location':
        return 'Location paused. Audit events refreshed.';
      case 'pause-operator':
        return 'Operator paused. Audit events refreshed.';
    }
  }

  private confirmManualAction(message: string): boolean {
    return window.confirm(message);
  }

  private optionalValue(value: string): string | undefined {
    const trimmed = value.trim();
    return trimmed ? trimmed : undefined;
  }

  private toErrorMessage(error: unknown, fallback: string): string {
    const candidate = error as { error?: { message?: string }; message?: string };
    return candidate?.error?.message || candidate?.message || fallback;
  }
}
