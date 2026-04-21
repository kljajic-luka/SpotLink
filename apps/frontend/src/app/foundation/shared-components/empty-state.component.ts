import { ChangeDetectionStrategy, Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';

import { UiButtonComponent } from '@foundation/design-system';

@Component({
  selector: 'sl-empty-state',
  standalone: true,
  imports: [CommonModule, UiButtonComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="sl-empty" role="status">
      <div class="sl-empty__icon" aria-hidden="true">
        <svg viewBox="0 0 64 64" focusable="false">
          <rect x="12" y="16" width="40" height="32" rx="6" fill="none" stroke="currentColor" stroke-width="4" />
          <path d="M20 30h24M20 38h14" fill="none" stroke="currentColor" stroke-width="4" stroke-linecap="round" />
        </svg>
      </div>
      <h2>{{ title }}</h2>
      @if (description) {
        <p>{{ description }}</p>
      }
      @if (actionLabel) {
        <sl-ui-button variant="secondary" (clicked)="action.emit()">{{ actionLabel }}</sl-ui-button>
      }
    </section>
  `,
  styles: [
    `
      .sl-empty {
        display: grid;
        justify-items: center;
        gap: 0.85rem;
        padding: var(--sl-space-6) var(--sl-space-2);
        text-align: center;
      }

      .sl-empty__icon {
        display: grid;
        place-items: center;
        width: 72px;
        height: 72px;
        border-radius: var(--sl-radius-md);
        background: var(--sl-color-surface-muted);
        color: var(--sl-color-primary);
      }

      svg {
        width: 44px;
        height: 44px;
      }

      p {
        max-width: 38rem;
      }
    `,
  ],
})
export class EmptyStateComponent {
  @Input() title = 'Nothing here yet';
  @Input() description = '';
  @Input() actionLabel = '';

  @Output() action = new EventEmitter<void>();
}
