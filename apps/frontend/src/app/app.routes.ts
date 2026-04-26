import { Routes } from '@angular/router';

export const routes: Routes = [
  {
    path: '',
    title: 'SpotLink Operativa',
    loadComponent: () =>
      import('./pages/operator-workspace.component').then((m) => m.OperatorWorkspaceComponent),
  },
  {
    path: 'foundation',
    title: 'SpotLink Foundation',
    loadComponent: () =>
      import('./pages/foundation-dashboard.component').then((m) => m.FoundationDashboardComponent),
  },
  {
    path: '**',
    redirectTo: '',
  },
];
