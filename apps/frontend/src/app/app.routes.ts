import { Routes } from '@angular/router';

export const routes: Routes = [
  {
    path: '',
    title: 'SpotLink Foundation',
    loadComponent: () =>
      import('./pages/foundation-dashboard.component').then((m) => m.FoundationDashboardComponent),
  },
  {
    path: '**',
    redirectTo: '',
  },
];
