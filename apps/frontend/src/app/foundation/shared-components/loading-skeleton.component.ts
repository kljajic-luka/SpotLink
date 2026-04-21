import { ChangeDetectionStrategy, Component, Input } from '@angular/core';

@Component({
  selector: 'sl-loading-skeleton',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div
      class="sl-skeleton"
      [style.width]="width"
      [style.height]="height"
      [style.border-radius]="radius"
      aria-hidden="true"
    ></div>
  `,
  styles: [
    `
      .sl-skeleton {
        min-width: 1rem;
        background: linear-gradient(
          90deg,
          var(--sl-color-surface-muted),
          var(--sl-color-border),
          var(--sl-color-surface-muted)
        );
        background-size: 220% 100%;
        animation: sl-skeleton 1.2s ease-in-out infinite;
      }

      @keyframes sl-skeleton {
        0% {
          background-position: 220% 0;
        }
        100% {
          background-position: -220% 0;
        }
      }
    `,
  ],
})
export class LoadingSkeletonComponent {
  @Input() width = '100%';
  @Input() height = '1rem';
  @Input() radius = '6px';
}
