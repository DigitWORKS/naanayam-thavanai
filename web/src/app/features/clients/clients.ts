import { HttpParams, httpResource } from '@angular/common/http';
import {
  ChangeDetectionStrategy, Component, computed, DestroyRef, effect, inject, input, numberAttribute, signal,
} from '@angular/core';
import { Router } from '@angular/router';

import { API } from '../../core/api';
import { apiErrorMessage } from '../../core/api-error';
import { Money } from '../../core/money';
import { formatPhone } from '../../shared/format';
import { Pager } from '../../shared/pager';
import type { Area, Page } from '../loans/loan.models';
import { ClientForm } from './client-form';
import { ClientFilter, ClientListItem } from './client.models';
import { ClientPanel } from './client-panel';
import { safeValue } from '../../core/resource';

const PAGE_SIZE = 20;
const FILTERS: { id: ClientFilter; label: string }[] = [
  { id: 'ALL', label: 'All' },
  { id: 'BEHIND', label: 'Behind' },
  { id: 'NO_ACTIVE_LOAN', label: 'No active loan' },
];
const asFilter = (v: unknown): ClientFilter => (v === 'BEHIND' || v === 'NO_ACTIVE_LOAN' ? v : 'ALL');
const m = (v: string | null | undefined) => Money.fromJson(v);

/**
 * Clients (design board "Web clients"): list on the left, the selected client on the right.
 * /clients/:clientId selects one; filters are in the query (?q=&filter=&area=&page=) and kept when
 * moving between clients.
 */
@Component({
  selector: 'nt-clients',
  imports: [Pager, ClientPanel, ClientForm],
  templateUrl: './clients.html',
  styleUrl: './clients.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ClientsPage {
  private readonly router = inject(Router);

  readonly clientId = input(null, { transform: (v: unknown) => (v ? numberAttribute(v) : null) });
  readonly q = input('', { transform: (v: string | undefined) => v ?? '' });
  readonly filter = input<ClientFilter, unknown>('ALL', { transform: asFilter });
  readonly area = input(null, { transform: (v: unknown) => (v ? numberAttribute(v) : null) });
  readonly page = input(1, { transform: (v: unknown) => Math.max(1, numberAttribute(v, 1)) });

  readonly filters = FILTERS;
  readonly searchText = signal('');
  private searchTimer: ReturnType<typeof setTimeout> | undefined;
  readonly creating = signal(false);

  constructor() {
    effect(() => this.searchText.set(this.q()));
    inject(DestroyRef).onDestroy(() => clearTimeout(this.searchTimer));
  }

  private readonly params = computed(() => {
    let p = new HttpParams().set('filter', this.filter()).set('page', this.page() - 1).set('size', PAGE_SIZE);
    if (this.q().trim()) p = p.set('q', this.q().trim());
    if (this.area() !== null) p = p.set('areaId', this.area()!);
    return p;
  });

  private readonly clientsRes = httpResource<Page<ClientListItem>>(() => ({
    url: `${API}/clients`, params: this.params(),
  }));
  private readonly areasRes = httpResource<Area[]>(() => `${API}/areas`);

  readonly areas = computed(() => safeValue(this.areasRes) ?? []);
  readonly meta = computed(() => safeValue(this.clientsRes)?.meta);
  readonly loading = computed(() => this.clientsRes.isLoading());
  readonly error = computed(() => (this.clientsRes.error() ? apiErrorMessage(this.clientsRes.error()) : ''));
  readonly hasFilters = computed(() => !!this.q().trim() || this.filter() !== 'ALL' || this.area() !== null);

  readonly countText = computed(() => {
    const n = this.meta()?.totalElements;
    if (n === undefined) return '';
    return this.hasFilters() ? `${n} matching` : `${n} ${n === 1 ? 'client' : 'clients'}`;
  });

  readonly rows = computed(() =>
    (safeValue(this.clientsRes)?.content ?? []).map((c) => {
      const behind = m(c.overdueAmount);
      return {
        id: c.id,
        name: c.name,
        phone: formatPhone(c.phone),
        area: c.areaName,
        loans: c.activeLoans,
        outstanding: c.activeLoans ? m(c.outstanding).format() : '—',
        tag: !c.isActive ? 'Inactive' : behind.isPositive ? `${behind.format()} behind` : c.activeLoans ? '' : 'No active loan',
        tagClass: !c.isActive ? 'nt-tag--danger' : behind.isPositive ? 'nt-tag--warn' : '',
        selected: c.id === this.clientId(),
      };
    }));

  // ------------------------------------------------------------ navigation

  /** Same page (list or a selected client), new query; filter changes go back to page 1. */
  private go(changes: Record<string, string | number | null>, replaceUrl = false): void {
    const resetPage = !('page' in changes);
    this.router.navigate([], {
      queryParams: { ...changes, ...(resetPage ? { page: null } : {}) },
      queryParamsHandling: 'merge',
      replaceUrl,
    });
  }

  open(id: number): void {
    this.router.navigate(['/clients', id], { queryParamsHandling: 'preserve' });
  }

  closePanel(): void {
    this.router.navigate(['/clients'], { queryParamsHandling: 'preserve' });
  }

  onSearch(text: string): void {
    this.searchText.set(text);
    clearTimeout(this.searchTimer);
    this.searchTimer = setTimeout(() => this.go({ q: text.trim() || null }, true), 300);
  }

  setFilter(f: ClientFilter): void { this.go({ filter: f === 'ALL' ? null : f }); }
  setArea(v: string): void { this.go({ area: v || null }); }
  goPage(p: number): void { this.go({ page: p === 1 ? null : p }); }

  clearFilters(): void {
    clearTimeout(this.searchTimer);
    this.go({ q: null, filter: null, area: null });
  }

  /** After create / edit: refresh the list (outstanding, names) and show the client. */
  onSaved(id: number): void {
    this.clientsRes.reload();
    if (id !== this.clientId()) this.open(id);
  }

  retry(): void { this.clientsRes.reload(); }
}
