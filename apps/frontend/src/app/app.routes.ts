import { Routes } from '@angular/router';
import { requireRole } from '@foundation/auth';

export const routes: Routes = [
  {
    path: '',
    pathMatch: 'full',
    redirectTo: 'operator',
  },
  {
    path: 'login',
    title: 'SpotLink Login',
    loadComponent: () => import('./pages/login-page.component').then((m) => m.LoginPageComponent),
  },
  {
    path: 'unauthorized',
    title: 'SpotLink Unauthorized',
    loadComponent: () =>
      import('./pages/access-state-page.component').then((m) => m.UnauthorizedPageComponent),
  },
  {
    path: 'forbidden',
    title: 'SpotLink Forbidden',
    loadComponent: () =>
      import('./pages/access-state-page.component').then((m) => m.ForbiddenPageComponent),
  },
  {
    path: 'operator',
    title: 'SpotLink Operativa',
    canActivate: [requireRole(['OPERATOR', 'ADMIN'])],
    loadComponent: () =>
      import('./pages/operator-workspace.component').then((m) => m.OperatorWorkspaceComponent),
  },
  {
    path: 'admin',
    title: 'SpotLink Admin',
    canActivate: [requireRole(['ADMIN'])],
    loadComponent: () => import('./pages/admin-portal.component').then((m) => m.AdminPortalComponent),
  },
  {
    path: 'foundation',
    title: 'SpotLink Foundation',
    canActivate: [requireRole(['ADMIN'])],
    loadComponent: () =>
      import('./pages/foundation-dashboard.component').then((m) => m.FoundationDashboardComponent),
  },
  {
    path: '**',
    redirectTo: '',
  },
];
