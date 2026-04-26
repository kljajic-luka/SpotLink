import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { Router, RouterLink, RouterOutlet } from '@angular/router';

import { AuthService } from '@foundation/auth';
import { StatusPillComponent, UiButtonComponent } from '@foundation/design-system';

@Component({
  selector: 'sl-root',
  standalone: true,
  imports: [RouterLink, RouterOutlet, StatusPillComponent, UiButtonComponent],
  templateUrl: './app.html',
  styleUrl: './app.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AppComponent {
  readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  canOpenOperator(): boolean {
    return this.auth.hasAnyRole(['OPERATOR', 'ADMIN']);
  }

  canOpenAdmin(): boolean {
    return this.auth.hasAnyRole(['ADMIN']);
  }

  logout(): void {
    this.auth.logout().subscribe(() => {
      void this.router.navigate(['/login']);
    });
  }
}
