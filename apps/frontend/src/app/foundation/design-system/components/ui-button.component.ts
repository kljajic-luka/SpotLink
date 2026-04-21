import { ChangeDetectionStrategy, Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';

export type UiButtonVariant = 'primary' | 'secondary' | 'ghost' | 'danger';
export type UiButtonSize = 'sm' | 'md' | 'lg';

@Component({
  selector: 'sl-ui-button',
  standalone: true,
  imports: [CommonModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <button
      class="sl-button"
      [ngClass]="classList"
      [type]="type"
      [disabled]="disabled || loading"
      [attr.aria-busy]="loading"
      [attr.aria-label]="ariaLabel || null"
      (click)="handleClick($event)"
    >
      @if (loading) {
        <span class="sl-button__spinner" aria-hidden="true"></span>
      }
      <span class="sl-button__content">
        <ng-content></ng-content>
      </span>
    </button>
  `,
  styles: [
    `
      :host {
        display: inline-flex;
      }

      .sl-button {
        display: inline-flex;
        align-items: center;
        justify-content: center;
        gap: 0.5rem;
        min-height: 44px;
        border: 1px solid transparent;
        border-radius: var(--sl-radius-sm);
        padding: 0 1rem;
        cursor: pointer;
        font-weight: 700;
        letter-spacing: 0;
        transition:
          background 160ms ease,
          border-color 160ms ease,
          color 160ms ease,
          transform 160ms ease;
      }

      .sl-button:hover:not(:disabled) {
        transform: translateY(-1px);
      }

      .sl-button:disabled {
        cursor: not-allowed;
        opacity: 0.58;
      }

      .sl-button--primary {
        background: var(--sl-color-primary);
        color: var(--sl-color-text-inverse);
      }

      .sl-button--primary:hover:not(:disabled) {
        background: var(--sl-color-primary-hover);
      }

      .sl-button--secondary {
        background: var(--sl-color-surface);
        border-color: var(--sl-color-border-strong);
        color: var(--sl-color-text-strong);
      }

      .sl-button--ghost {
        background: transparent;
        color: var(--sl-color-primary);
      }

      .sl-button--danger {
        background: var(--sl-color-danger);
        color: var(--sl-color-text-inverse);
      }

      .sl-button--sm {
        min-height: 36px;
        padding-inline: 0.75rem;
        font-size: var(--sl-font-size-sm);
      }

      .sl-button--lg {
        min-height: 52px;
        padding-inline: 1.25rem;
      }

      .sl-button--full {
        width: 100%;
      }

      .sl-button__spinner {
        width: 1rem;
        height: 1rem;
        border: 2px solid currentColor;
        border-right-color: transparent;
        border-radius: 50%;
        animation: sl-spin 750ms linear infinite;
      }

      @keyframes sl-spin {
        to {
          transform: rotate(360deg);
        }
      }
    `,
  ],
})
export class UiButtonComponent {
  @Input() variant: UiButtonVariant = 'primary';
  @Input() size: UiButtonSize = 'md';
  @Input() type: 'button' | 'submit' | 'reset' = 'button';
  @Input() loading = false;
  @Input() disabled = false;
  @Input() fullWidth = false;
  @Input() ariaLabel = '';

  @Output() clicked = new EventEmitter<MouseEvent>();

  get classList(): Record<string, boolean> {
    return {
      [`sl-button--${this.variant}`]: true,
      [`sl-button--${this.size}`]: true,
      'sl-button--full': this.fullWidth,
    };
  }

  handleClick(event: MouseEvent): void {
    if (this.disabled || this.loading) {
      event.preventDefault();
      event.stopPropagation();
      return;
    }

    this.clicked.emit(event);
  }
}
