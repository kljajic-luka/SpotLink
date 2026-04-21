import { ChangeDetectionStrategy, Component, EventEmitter, Input, Output } from '@angular/core';

import { UiButtonComponent } from '@foundation/design-system';

@Component({
  selector: 'sl-error-state',
  standalone: true,
  imports: [UiButtonComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="sl-error" role="alert">
      <strong>{{ title }}</strong>
      <p>{{ message }}</p>
      @if (retryable) {
        <sl-ui-button variant="secondary" (clicked)="retry.emit()">Retry</sl-ui-button>
      }
    </section>
  `,
  styles: [
    `
      .sl-error {
        display: grid;
        gap: 0.75rem;
        padding: var(--sl-space-3);
        border: 1px solid rgba(185, 28, 28, 0.24);
        border-radius: var(--sl-radius-md);
        background: rgba(185, 28, 28, 0.08);
      }

      strong {
        color: var(--sl-color-danger);
      }
    `,
  ],
})
export class ErrorStateComponent {
  @Input() title = 'Something went wrong';
  @Input() message = 'Try again in a moment.';
  @Input() retryable = true;

  @Output() retry = new EventEmitter<void>();
}
