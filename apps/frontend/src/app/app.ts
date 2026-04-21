import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink, RouterOutlet } from '@angular/router';

import { StatusPillComponent, UiButtonComponent } from '@foundation/design-system';

@Component({
  selector: 'sl-root',
  standalone: true,
  imports: [RouterLink, RouterOutlet, StatusPillComponent, UiButtonComponent],
  templateUrl: './app.html',
  styleUrl: './app.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AppComponent {}
