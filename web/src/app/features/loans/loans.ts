import { HttpClient, HttpParams, httpResource } from '@angular/common/http';
import {
  ChangeDetectionStrategy, Component, computed, DestroyRef, effect, inject, input, numberAttribute, signal,
} from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';

import { API } from '../../core/api';
import { apiErrorMessage } from '../../core/api-error';
import { Money } from '../../core/money';
import { BusinessSummary } from '../../layout/business-summary';
import { formatPhone } from '../../shared/format';
import { Pager } from '../../shared/pager';
import {
  Area, Frequency, FREQUENCY_LABEL, FREQUENCY_UNIT, LoanStatus, LoanSummary, Page, STATUS_LABEL,
} from './loan.models';

const PAGE_SIZE = 20;
const TABS: LoanStatus[] = ['ACTIVE', 'CLOSED', 'PRECLOSED', 'DEFAULTED'];
const m = (v: string | null | undefined) => Money.fromJson(v);
const longDate = (iso: string) =>
  new Date(`${iso}T00:00:00`).toLocaleDateString('en-IN', { day: 'numeric', month: 'short', year: 'numeric' });
const asStatus = (v: unknown): LoanStatus => (TABS.includes(v as LoanStatus) ? (v as LoanStatus) : 'ACTIVE');
const asFrequency = (v: unknown): Frequency | null => (v === 'DAILY' || v === 'WEEKLY' ? v : null);

/**
 * Loan list (design board "Web loans list"). Filters live in the URL query (?status=&q=&area=&frequency=
 * &behind=true&page=), bound to inputs, so links, Back and reload keep them.
 */
@Component({
  selector: 'nt-loans',
  imports: [RouterLink, Pager],
  templateUrl: './loans.html',
  styleUrl: './loans.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LoansPage {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly summary = inject(BusinessSummary);

  // ------------------------------------------------------------ URL state (query params)

  readonly status = input(asStatus(undefined), { transform: asStatus });
  readonly q = input('', { transform: (v: string | undefined) => v ?? '' });
  readonly area = input(null, { transform: (v: unknown) => (v ? numberAttribute(v) : null) });
  readonly frequency = input(null, { transform: asFrequency });
  readonly behind = input(false, { transform: (v: unknown) => v === 'true' || v === true });
  readonly page = input(1, { transform: (v: unknown) => Math.max(1, numberAttribute(v, 1)) });

  /** Search box text; pushed to the URL 300 ms after typing stops. */
  readonly searchText = signal('');
  private searchTimer: ReturnType<typeof setTimeout> | undefined;

  constructor() {
    // Keep the box in step with the URL (Back / Forward, links from elsewhere).
    effect(() => this.searchText.set(this.q()));
    inject(DestroyRef).onDestroy(() => clearTimeout(this.searchTimer));
  }

  // ------------------------------------------------------------ data

  private readonly params = computed(() => {
    let p = new HttpParams().set('status', this.status()).set('page', this.page() - 1).set('size', PAGE_SIZE);
    if (this.q().trim()) p = p.set('q', this.q().trim());
    if (this.area() !== null) p = p.set('areaId', this.area()!);
    if (this.frequency()) p = p.set('frequency', this.frequency()!);
    if (this.behind() && this.status() === 'ACTIVE') p = p.set('behind', 'true');
    return p;
  });

  private readonly loansRes = httpResource<Page<LoanSummary>>(() => ({ url: `${API}/loans`, params: this.params() }));
  private readonly countsRes = httpResource<Record<LoanStatus, number>>(() => `${API}/loans/counts`);
  private readonly areasRes = httpResource<Area[]>(() => `${API}/areas`);

  readonly areas = computed(() => this.areasRes.value() ?? []);
  readonly frequencies = Object.entries(FREQUENCY_LABEL) as [Frequency, string][];

  readonly headerText = computed(() => {
    const d = this.summary.dashboard();
    if (!d) return '';
    return `${d.activeLoans} active · ${m(d.totalOutstanding).format()} outstanding`;
  });

  readonly tabs = computed(() => {
    const counts = this.countsRes.value();
    return TABS.map((s) => ({ id: s, label: STATUS_LABEL[s], count: counts?.[s] }));
  });

  readonly loading = computed(() => this.loansRes.isLoading());
  readonly error = computed(() => (this.loansRes.error() ? apiErrorMessage(this.loansRes.error()) : ''));

  readonly rows = computed(() =>
    (this.loansRes.value()?.content ?? []).map((l) => {
      const behind = m(l.overdueAmount);
      const unit = FREQUENCY_UNIT[l.frequency][l.installmentCount === 1 ? 0 : 1];
      return {
        id: l.id,
        loanNo: l.loanNo,
        client: l.clientName,
        phone: formatPhone(l.clientPhone),
        area: l.areaName,
        principal: m(l.principal).format(),
        installment: `${FREQUENCY_LABEL[l.frequency]} ${m(l.installmentAmount).format()}`,
        paidPct: l.installmentCount ? Math.round((l.paidCount / l.installmentCount) * 100) : 0,
        paidText: `${l.paidCount} of ${l.installmentCount} ${unit}`,
        outstanding: m(l.outstanding).format(),
        ...statusTag(l.status, behind),
        ends: longDate(l.endDate),
      };
    }));

  readonly meta = computed(() => this.loansRes.value()?.meta);

  readonly hasFilters = computed(() =>
    !!this.q().trim() || this.area() !== null || !!this.frequency() || this.behind());

  // ------------------------------------------------------------ actions

  /** Updates the URL query; any filter change goes back to page 1. */
  private go(changes: Record<string, string | number | boolean | null>, replaceUrl = false): void {
    const resetPage = !('page' in changes);
    this.router.navigate([], {
      queryParams: { ...changes, ...(resetPage ? { page: null } : {}) },
      queryParamsHandling: 'merge',
      replaceUrl,
    });
  }

  pickTab(s: LoanStatus): void {
    this.go({ status: s === 'ACTIVE' ? null : s, behind: s === 'ACTIVE' ? (this.behind() || null) : null });
  }

  onSearch(text: string): void {
    this.searchText.set(text);
    clearTimeout(this.searchTimer);
    this.searchTimer = setTimeout(() => this.go({ q: text.trim() || null }, true), 300);
  }

  setArea(v: string): void { this.go({ area: v || null }); }
  setFrequency(v: string): void { this.go({ frequency: v || null }); }
  setBehind(on: boolean): void { this.go({ behind: on || null }); }
  goPage(p: number): void { this.go({ page: p === 1 ? null : p }); }

  clearFilters(): void {
    clearTimeout(this.searchTimer);
    this.go({ q: null, area: null, frequency: null, behind: null });
  }

  readonly exporting = signal(false);
  readonly exportError = signal('');

  /** Downloads loans.csv for the current tab and filters (all pages). */
  async exportCsv(): Promise<void> {
    this.exporting.set(true);
    this.exportError.set('');
    try {
      const params = this.params().delete('page').delete('size');
      const blob = await firstValueFrom(this.http.get(`${API}/loans/export`, { params, responseType: 'blob' }));
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `loans-${STATUS_LABEL[this.status()].toLowerCase()}-${new Date().toISOString().slice(0, 10)}.csv`;
      a.click();
      setTimeout(() => URL.revokeObjectURL(url), 1000);
    } catch (e) {
      this.exportError.set(apiErrorMessage(e, 'Could not export. Try again.'));
    } finally {
      this.exporting.set(false);
    }
  }

  retry(): void {
    this.loansRes.reload();
    this.countsRes.reload();
  }
}

function statusTag(status: LoanStatus, behind: Money): { tag: string; tagClass: string } {
  switch (status) {
    case 'ACTIVE':
      return behind.isPositive
        ? { tag: `${behind.format()} behind`, tagClass: 'nt-tag--warn' }
        : { tag: 'On track', tagClass: 'nt-tag--ok' };
    case 'DEFAULTED':
      return { tag: 'Defaulted', tagClass: 'nt-tag--danger' };
    default:
      return { tag: STATUS_LABEL[status], tagClass: '' };
  }
}
