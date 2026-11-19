import { Routes, UrlMatchResult, UrlSegment } from '@angular/router';

import { authGuard, guestGuard, roleGuard } from './core/auth/auth.guards';

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
      {
        path: 'financiers',
        loadComponent: () => import('./features/admin/financiers').then((m) => m.FinanciersPage),
        title: 'Financiers · Naanayam Thavanai',
      },
      { path: '', pathMatch: 'full', redirectTo: 'financiers' },
    ],
  },
  {
    path: '',
    canMatch: [authGuard, roleGuard('OWNER')],
    loadComponent: () => import('./layout/owner-shell/owner-shell').then((m) => m.OwnerShell),
    children: [
      {
        path: 'dashboard',
        loadComponent: () => import('./features/dashboard/dashboard').then((m) => m.DashboardPage),
        title: 'Dashboard · Naanayam Thavanai',
      },
      {
        // /clients and /clients/:clientId share one route, so picking another client keeps the list (same
        // component instance) instead of rebuilding the page.
        matcher: clientsMatcher,
        loadComponent: () => import('./features/clients/clients').then((m) => m.ClientsPage),
        title: 'Clients · Naanayam Thavanai',
      },
      {
        path: 'loans/new',
        loadComponent: () => import('./features/loans/new-loan').then((m) => m.NewLoanPage),
        title: 'New loan · Naanayam Thavanai',
      },
      {
        path: 'loans/:loanId',
        loadComponent: () => import('./features/loans/loan-detail').then((m) => m.LoanDetailPage),
        title: 'Loan · Naanayam Thavanai',
      },
      {
        path: 'loans',
        loadComponent: () => import('./features/loans/loans').then((m) => m.LoansPage),
        title: 'Loans · Naanayam Thavanai',
      },
      {
        path: 'handovers',
        loadComponent: () => import('./features/handovers/handovers').then((m) => m.HandoversPage),
        title: 'Cash handovers · Naanayam Thavanai',
      },
      {
        path: 'reports/income',
        loadComponent: () => import('./features/reports/income-report').then((m) => m.IncomeReportPage),
        title: 'Income for tax · Naanayam Thavanai',
      },
      {
        path: 'reports',
        loadComponent: () => import('./features/reports/collections-report').then((m) => m.CollectionsReportPage),
        title: 'Reports · Naanayam Thavanai',
      },
      {
        path: 'agents',
        loadComponent: () => import('./features/agents/agents').then((m) => m.AgentsPage),
        title: 'Agents & areas · Naanayam Thavanai',
      },
      {
        path: 'settings',
        loadComponent: () => import('./features/settings/settings').then((m) => m.SettingsPage),
        canDeactivate: [(page: { canLeave(): boolean }) => page.canLeave()],
        title: 'Settings · Naanayam Thavanai',
      },
      { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
    ],
  },
  { path: '**', redirectTo: '' },
];

/** Matches "clients" and "clients/<id>" (id → :clientId). */
export function clientsMatcher(segments: UrlSegment[]): UrlMatchResult | null {
  if (segments[0]?.path !== 'clients' || segments.length > 2) return null;
  if (segments.length === 2 && !/^\d+$/.test(segments[1].path)) return null;
  return { consumed: segments, posParams: segments.length === 2 ? { clientId: segments[1] } : {} };
}
