import {
  ChangeDetectionStrategy,
  Component,
  EventEmitter,
  forwardRef,
  Input,
  Output,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';

@Component({
  selector: 'sl-text-field',
  standalone: true,
  imports: [CommonModule],
  providers: [
    {
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => TextFieldComponent),
      multi: true,
    },
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <label class="sl-field" [class.sl-field--disabled]="disabled">
      <span class="sl-field__label">{{ label }}</span>
      @if (multiline) {
        <textarea
          class="sl-field__control"
          [id]="inputId"
          [rows]="rows"
          [value]="value"
          [placeholder]="placeholder"
          [disabled]="disabled"
          [required]="required"
          (input)="handleInput($event)"
          (blur)="onTouched()"
        ></textarea>
      } @else {
        <input
          class="sl-field__control"
          [id]="inputId"
          [type]="type"
          [value]="value"
          [placeholder]="placeholder"
          [disabled]="disabled"
          [required]="required"
          (input)="handleInput($event)"
          (blur)="onTouched()"
        />
      }
      @if (hint) {
        <span class="sl-field__hint">{{ hint }}</span>
      }
    </label>
  `,
  styles: [
    `
      :host {
        display: block;
      }

      .sl-field {
        display: grid;
        gap: 0.375rem;
      }

      .sl-field__label {
        color: var(--sl-color-text-strong);
        font-size: var(--sl-font-size-sm);
        font-weight: 700;
      }

      .sl-field__control {
        width: 100%;
        border: 1px solid var(--sl-color-border);
        border-radius: var(--sl-radius-sm);
        background: var(--sl-color-surface);
        color: var(--sl-color-text);
        min-height: 44px;
        padding: 0.75rem 0.875rem;
        resize: vertical;
      }

      .sl-field__control:focus {
        border-color: var(--sl-color-focus);
      }

      .sl-field__hint {
        color: var(--sl-color-text-muted);
        font-size: var(--sl-font-size-sm);
      }

      .sl-field--disabled {
        opacity: 0.6;
      }
    `,
  ],
})
export class TextFieldComponent implements ControlValueAccessor {
  @Input({ required: true }) label = '';
  @Input() inputId = `sl-field-${Math.random().toString(36).slice(2, 8)}`;
  @Input() type = 'text';
  @Input() placeholder = '';
  @Input() hint = '';
  @Input() required = false;
  @Input() disabled = false;
  @Input() multiline = false;
  @Input() rows = 4;

  @Output() valueChanged = new EventEmitter<string>();

  value = '';

  onChange: (value: string) => void = () => undefined;
  onTouched: () => void = () => undefined;

  writeValue(value: string | null | undefined): void {
    this.value = value ?? '';
  }

  registerOnChange(fn: (value: string) => void): void {
    this.onChange = fn;
  }

  registerOnTouched(fn: () => void): void {
    this.onTouched = fn;
  }

  setDisabledState(isDisabled: boolean): void {
    this.disabled = isDisabled;
  }

  handleInput(event: Event): void {
    const value = (event.target as HTMLInputElement | HTMLTextAreaElement).value;
    this.value = value;
    this.onChange(value);
    this.valueChanged.emit(value);
  }
}
