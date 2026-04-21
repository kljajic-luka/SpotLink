import { ChangeDetectionStrategy, Component, Input } from '@angular/core';

@Component({
  selector: 'sl-optimized-image',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (src) {
      <img
        [src]="src"
        [alt]="alt"
        [loading]="loading"
        [attr.decoding]="decoding"
        [class.sl-image--cover]="objectFit === 'cover'"
      />
    } @else {
      <div class="sl-image-placeholder" role="img" [attr.aria-label]="alt || 'Image placeholder'"></div>
    }
  `,
  styles: [
    `
      :host {
        display: block;
        overflow: hidden;
        border-radius: var(--sl-radius-md);
        background: var(--sl-color-surface-muted);
      }

      img,
      .sl-image-placeholder {
        display: block;
        width: 100%;
        height: 100%;
        min-height: inherit;
      }

      img {
        object-fit: contain;
      }

      .sl-image--cover {
        object-fit: cover;
      }

      .sl-image-placeholder {
        min-height: 10rem;
        background:
          linear-gradient(135deg, rgba(15, 118, 110, 0.16), transparent),
          var(--sl-color-surface-muted);
      }
    `,
  ],
})
export class OptimizedImageComponent {
  @Input() src = '';
  @Input() alt = '';
  @Input() loading: 'lazy' | 'eager' = 'lazy';
  @Input() decoding: 'async' | 'auto' | 'sync' = 'async';
  @Input() objectFit: 'contain' | 'cover' = 'cover';
}
