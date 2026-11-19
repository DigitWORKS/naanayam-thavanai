import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';

import { AuthService } from '../../core/auth/auth.service';
import { BusinessSummary } from '../business-summary';

interface NavItem {
  path: string;
  label: string;
  icon: string;
  section?: string;
  badge?: () => number;
}

/** Owner layout from the web design (side menu W-series boards): menu + routed page. */
@Component({
  selector: 'nt-owner-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  templateUrl: './owner-shell.html',
  styleUrl: './owner-shell.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class OwnerShell {
  private readonly auth = inject(AuthService);
  protected readonly business = inject(BusinessSummary);

  protected readonly user = this.auth.user;
  protected readonly initials = computed(() =>
    (this.user()?.name ?? '?')
      .split(/\s+/)
      .filter(Boolean)
      .map((p) => p[0])
      .slice(0, 2)
      .join('')
      .toUpperCase(),
  );

  protected readonly nav: NavItem[] = [
    { path: '/dashboard', label: 'Dashboard', icon: '⌂' },
    { path: '/clients', label: 'Clients', icon: '☺' },
    { path: '/loans', label: 'Loans', icon: '▤' },
    { path: '/handovers', label: 'Cash handovers', icon: '₹', badge: () => this.business.pendingHandovers() },
    { path: '/reports', label: 'Reports', icon: '▥', section: 'Business' },
    { path: '/agents', label: 'Agents & areas', icon: '⚑' },
    { path: '/settings', label: 'Settings', icon: '⚙' },
  ];

  protected logout(): void {
    this.auth.logout();
  }
}
