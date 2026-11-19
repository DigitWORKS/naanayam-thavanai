import { HttpParams, httpResource } from '@angular/common/http';
import {
  ChangeDetectionStrategy, Component, computed, DestroyRef, effect, inject, input, numberAttribute, signal,
} from '@angular/core';
import { Router } from '@angular/router';

import { API } from '../../core/api';
import { apiErrorMessage } from '../../core/api-error';
import { safeValue } from '../../core/resource';
import { formatPhone } from '../../shared/format';
import { Pager } from '../../shared/pager';
import type { Page } from '../loans/loan.models';
import { BusinessForm } from './business-form';
import { BusinessPanel } from './business-panel';
import { PLAN_LABEL, Tenant, TenantStatus, TenantSummary } from './tenant.models';

type Filter = 'ALL' | TenantStatus | 'EXPIRING';
const FILTERS: { id: Filter; label: string }[] = [
  { id: 'ALL', label: 'All' },
  { id: 'TRIAL', label: 'Trial' },
  { id: 'ACTIVE', label: 'Active' },
  { id: 'SUSPENDED', label: 'Suspended' },
  { id: 'EXPIRING', label: 'Expiring in 7 days' },
];
const asFilter = (v: unknown): Filter => (FILTERS.some((f) => f.id === v) ? (v as Filter) : 'ALL');
const PAGE_SIZE = 20;
const longDate = (s: string) =>
  new Date(s.length === 10 ? `${s}T00:00:00` : s).toLocaleDateString('en-IN', { day: 'numeric', month: 'short', year: 'numeric' });
const todayIso = () => {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
};

/**
 * Super Admin › Businesses (design board "WebAdminTenants"). State in the URL: ?q=&show=&page=&t=
 * (t = business open in the side panel).
 */
@Component({
  selector: 'nt-businesses',
  imports: [Pager, BusinessPanel, BusinessForm],
  templateUrl: './businesses.html',
  styleUrl: './businesses.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class BusinessesPage {
  private readonly router = inject(Router);

  readonly q = input('', { transform: (v: string | undefined) => v ?? '' });
  readonly show = input<Filter, unknown>('ALL', { transform: asFilter });
  readonly page = input(1, { transform: (v: unknown) => Math.max(1, numberAttribute(v, 1)) });
  readonly t = input(null, { transform: (v: unknown) => (v ? numberAttribute(v) : null) });

  readonly filters = FILTERS;
  readonly searchText = signal('');
  private searchTimer: ReturnType<typeof setTimeout> | undefined;
  readonly adding = signal(false);

  constructor() {
    effect(() => this.searchText.set(this.q()));
    inject(DestroyRef).onDestroy(() => clearTimeout(this.searchTimer));
  }

  private readonly listRes = httpResource<Page<Tenant>>(() => {
    let p = new HttpParams().set('page', this.page() - 1).set('size', PAGE_SIZE);
    const f = this.show();
    if (f === 'EXPIRING') p = p.set('expiringSoon', 'true');
    else if (f !== 'ALL') p = p.set('status', f);
    if (this.q().trim()) p = p.set('q', this.q().trim());
    return { url: `${API}/admin/tenants`, params: p };
  });
  private readonly summaryRes = httpResource<TenantSummary>(() => `${API}/admin/tenants/summary`);
  /** The open business, fetched on its own so it shows even when the list's filter hides it. */
  private readonly selectedRes = httpResource<Tenant>(() => (this.t() ? `${API}/admin/tenants/${this.t()}` : undefined));

  readonly summary = computed(() => safeValue(this.summaryRes));
  readonly meta = computed(() => safeValue(this.listRes)?.meta);
  readonly loading = computed(() => this.listRes.isLoading());
  readonly error = computed(() => (this.listRes.error() ? apiErrorMessage(this.listRes.error()) : ''));
  readonly selected = computed(() => (this.t() ? safeValue(this.selectedRes) : undefined));
  readonly hasFilters = computed(() => !!this.q().trim() || this.show() !== 'ALL');

  readonly rows = computed(() => {
    const today = todayIso();
    return (safeValue(this.listRes)?.content ?? []).map((x) => {
      const expired = !!x.planExpiry && x.planExpiry < today;
      const soon = !!x.planExpiry && !expired && daysBetween(today, x.planExpiry) < 7;
      return {
        id: x.id,
        name: x.name,
        since: `Since ${longDate(x.createdAt)}`,
        owner: x.ownerName,
        phone: formatPhone(x.phone),
        plan: x.plan ? PLAN_LABEL[x.plan] : '—',
        expires: x.planExpiry ? longDate(x.planExpiry) : '—',
        expiresClass: x.status === 'SUSPENDED' ? '' : expired ? 'danger' : soon ? 'warn' : '',
        status: x.status === 'SUSPENDED' ? 'Suspended' : expired ? 'Expired' : x.status === 'TRIAL' ? 'Trial' : 'Active',
        statusClass: x.status === 'SUSPENDED' || expired ? 'nt-tag--danger' : soon ? 'nt-tag--warn' : 'nt-tag--ok',
        selected: x.id === this.t(),
      };
    });
  });

  private go(q: Record<string, string | number | null>, replaceUrl = false): void {
    const resetPage = !('page' in q) && !('t' in q);
    this.router.navigate([], {
      queryParams: { ...q, ...(resetPage ? { page: null } : {}) }, queryParamsHandling: 'merge', replaceUrl,
    });
  }

  onSearch(text: string): void {
    this.searchText.set(text);
    clearTimeout(this.searchTimer);
    this.searchTimer = setTimeout(() => this.go({ q: text.trim() || null }, true), 300);
  }

  setFilter(f: Filter): void { this.go({ show: f === 'ALL' ? null : f }); }
  goPage(p: number): void { this.go({ page: p === 1 ? null : p }); }
  open(id: number): void { this.go({ t: id }); }
  close(): void { this.go({ t: null }); }

  clearFilters(): void {
    clearTimeout(this.searchTimer);
    this.go({ q: null, show: null });
  }

  /** After a change in the panel or a new business: refresh everything and show it. */
  onSaved(id: number): void {
    this.listRes.reload();
    this.summaryRes.reload();
    this.selectedRes.reload();
    if (id !== this.t()) this.open(id);
  }

  retry(): void {
    this.listRes.reload();
    this.summaryRes.reload();
  }
}

/** Whole days from a to b (ISO dates). */
export function daysBetween(a: string, b: string): number {
  return Math.round((new Date(`${b}T00:00:00`).getTime() - new Date(`${a}T00:00:00`).getTime()) / 86_400_000);
}
