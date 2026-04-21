import { ChangeDetectionStrategy, Component, Input } from '@angular/core';

export type StatusTone = 'neutral' | 'info' | 'success' | 'warning' | 'danger';

@Component({
  selector: 'sl-status-pill',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<span [class]="'sl-pill ' + toneClass"><ng-content></ng-content></span>`,
  styles: [
    `
      .sl-pill {
        display: inline-flex;
        align-items: center;
        min-height: 28px;
        border-radius: var(--sl-radius-full);
        padding: 0 0.75rem;
        font-size: var(--sl-font-size-sm);
        font-weight: 700;
        letter-spacing: 0;
        white-space: nowrap;
      }

      .sl-pill--neutral {
        background: var(--sl-color-surface-muted);
        color: var(--sl-color-text-strong);
      }

      .sl-pill--info {
        background: rgba(37, 99, 235, 0.12);
        color: var(--sl-color-info);
      }

      .sl-pill--success {
        background: rgba(21, 128, 61, 0.12);
        color: var(--sl-color-success);
      }

      .sl-pill--warning {
        background: rgba(245, 158, 11, 0.18);
        color: var(--sl-color-warning);
      }

      .sl-pill--danger {
        background: rgba(185, 28, 28, 0.12);
        color: var(--sl-color-danger);
      }
    `,
  ],
})
export class StatusPillComponent {
  @Input() tone: StatusTone = 'neutral';

  get toneClass(): string {
    return `sl-pill--${this.tone}`;
  }
}
