import { Routes } from '@angular/router';

import { authGuard, guestGuard, roleGuard } from './core/auth/auth.guards';

const placeholder = () => import('./features/placeholder/placeholder').then((m) => m.Placeholder);

/** Owner pages from the web design; each becomes a real feature in the next steps. */
const ownerPage = (path: string, title: string) => ({ path, loadComponent: placeholder, data: { title }, title });

export const routes: Routes = [
  {
    path: 'login',
    canMatch: [guestGuard],
    loadComponent: () => import('./features/login/login').then((m) => m.Login),
    title: 'Log in · Naanayam Thavanai',
  },
  {
    path: 'admin',
    canMatch: [authGuard, roleGuard('SUPER_ADMIN')],
    loadComponent: () => import('./layout/admin-shell/admin-shell').then((m) => m.AdminShell),
    children: [
      { path: 'financiers', loadComponent: placeholder, data: { title: 'Financiers' }, title: 'Financiers' },
      { path: '', pathMatch: 'full', redirectTo: 'financiers' },
    ],
  },
  {
    path: '',
    canMatch: [authGuard, roleGuard('OWNER')],
    loadComponent: () => import('./layout/owner-shell/owner-shell').then((m) => m.OwnerShell),
    children: [
      ownerPage('dashboard', 'Dashboard'),
      ownerPage('today', "Today's collections"),
      ownerPage('clients', 'Clients'),
      ownerPage('loans', 'Loans'),
      ownerPage('handovers', 'Cash handovers'),
      ownerPage('reports', 'Reports'),
      ownerPage('agents', 'Agents & areas'),
      ownerPage('settings', 'Settings'),
      { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
    ],
  },
  { path: '**', redirectTo: '' },
];
