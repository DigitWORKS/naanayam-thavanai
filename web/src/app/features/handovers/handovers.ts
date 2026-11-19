import { HttpParams, httpResource } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, inject, input, numberAttribute } from '@angular/core';
import { Router } from '@angular/router';

import { API } from '../../core/api';
import { apiErrorMessage } from '../../core/api-error';
import { Money } from '../../core/money';
import { safeValue } from '../../core/resource';
import { BusinessSummary } from '../../layout/business-summary';
import type { Handover, HandoverStatus } from '../dashboard/dashboard.models';
import { HandoverPanel } from './handover-panel';

/** GET /users?role=AGENT — what this page needs. */
export interface Agent {
  id: number;
  name: string;
  isActive: boolean;
  areas: { id: number; name: string }[];
}

const TABS: { id: HandoverStatus; label: string }[] = [
  { id: 'PENDING', label: 'To confirm' },
  { id: 'CONFIRMED', label: 'Confirmed' },
  { id: 'DISPUTED', label: 'Disputed' },
];
const asStatus = (v: unknown): HandoverStatus => (v === 'CONFIRMED' || v === 'DISPUTED' ? v : 'PENDING');
const isoDate = (v: unknown) => (typeof v === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(v) ? v : '');
const m = (v: string | null | undefined) => Money.fromJson(v);
const iso = (d: Date) =>
  `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
const rowDate = (s: string) =>
  new Date(`${s}T00:00:00`).toLocaleDateString('en-IN', { weekday: 'short', day: 'numeric', month: 'short' });

/**
 * Cash handovers (design board "Web cash handovers"). State in the URL query: ?status=&agent=&from=&to=&h=
 * (h = handover open in the side panel). "To confirm" shows every pending one; the other tabs default to
 * this month.
 */
@Component({
  selector: 'nt-handovers',
  imports: [HandoverPanel],
  templateUrl: './handovers.html',
  styleUrl: './handovers.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class HandoversPage {
  private readonly router = inject(Router);
  private readonly summary = inject(BusinessSummary);

  readonly status = input<HandoverStatus, unknown>('PENDING', { transform: asStatus });
  readonly agent = input(null, { transform: (v: unknown) => (v ? numberAttribute(v) : null) });
  readonly from = input('', { transform: isoDate });
  readonly to = input('', { transform: isoDate });
  readonly h = input(null, { transform: (v: unknown) => (v ? numberAttribute(v) : null) });

  readonly tabs = TABS;
  readonly today = iso(new Date());
  readonly monthStart = iso(new Date(new Date().getFullYear(), new Date().getMonth(), 1));

  /** Range in effect: the URL's, else this month for decided tabs, else none (all pending). */
  readonly range = computed(() => {
    if (this.from() || this.to()) return { from: this.from(), to: this.to() };
    return this.status() === 'PENDING' ? { from: '', to: '' } : { from: this.monthStart, to: this.today };
  });

  private readonly listRes = httpResource<Handover[]>(() => {
    let p = new HttpParams().set('status', this.status());
    if (this.agent() !== null) p = p.set('agentId', this.agent()!);
    if (this.range().from) p = p.set('from', this.range().from);
    if (this.range().to) p = p.set('to', this.range().to);
    return { url: `${API}/cash-handovers`, params: p };
  });
  /** This month, every status — for the totals under the table. */
  private readonly monthRes = httpResource<Handover[]>(() => ({
    url: `${API}/cash-handovers`, params: { from: this.monthStart, to: this.today },
  }));
  private readonly agentsRes = httpResource<Agent[]>(() => ({ url: `${API}/users`, params: { role: 'AGENT' } }));

  readonly agents = computed(() => safeValue(this.agentsRes) ?? []);
  readonly pendingCount = computed(() => this.summary.pendingHandovers());
  readonly loading = computed(() => this.listRes.isLoading());
  readonly error = computed(() => (this.listRes.error() ? apiErrorMessage(this.listRes.error()) : ''));

  readonly rows = computed(() =>
    (safeValue(this.listRes) ?? []).map((h) => {
      const diff = m(h.difference);
      return {
        source: h,
        date: rowDate(h.handoverDate),
        agent: h.agentName,
        expected: m(h.expectedAmount).format(),
        handed: m(h.handedAmount).format(),
        diff: diff.isZero ? 'Matches' : diff.isPositive ? `${diff.format()} extra` : `${new Money(-diff.paise).format()} short`,
        diffClass: diff.isZero ? 'ok' : 'warn',
        matches: diff.isZero,
        selected: h.id === this.h(),
      };
    }));

  readonly selected = computed(() => {
    const id = this.h();
    return id === null ? null : ((safeValue(this.listRes) ?? []).find((x) => x.id === id) ?? null);
  });

  readonly agentForSelected = computed(() => {
    const s = this.selected();
    return s ? (this.agents().find((a) => a.id === s.agentId) ?? null) : null;
  });

  readonly monthStats = computed(() => {
    const list = safeValue(this.monthRes);
    if (!list) return null;
    const received = Money.sum(list.filter((x) => x.status === 'CONFIRMED').map((x) => m(x.handedAmount)));
    const short = Money.sum(list.map((x) => m(x.difference)).filter((d) => d.paise < 0).map((d) => new Money(-d.paise)));
    return {
      received: received.format(),
      short: short.format(),
      hasShort: short.isPositive,
      disputed: list.filter((x) => x.status === 'DISPUTED').length,
    };
  });

  readonly emptyText = computed(() =>
    this.status() === 'PENDING' ? 'Nothing to confirm.' : `No ${this.status() === 'CONFIRMED' ? 'confirmed' : 'disputed'} handovers in this period.`);

  // ------------------------------------------------------------ navigation

  private go(changes: Record<string, string | number | null>): void {
    this.router.navigate([], { queryParams: changes, queryParamsHandling: 'merge' });
  }

  pickTab(s: HandoverStatus): void {
    this.go({ status: s === 'PENDING' ? null : s, h: null, from: null, to: null });
  }

  setAgent(v: string): void { this.go({ agent: v || null, h: null }); }
  setFrom(v: string): void { this.go({ from: v || null, to: this.range().to || null, h: null }); }
  setTo(v: string): void { this.go({ to: v || null, from: this.range().from || null, h: null }); }
  open(id: number): void { this.go({ h: id }); }
  close(): void { this.go({ h: null }); }

  /** After confirm / dispute: refresh lists and the menu badge; the panel closes (the row left this tab). */
  onDecided(): void {
    this.listRes.reload();
    this.monthRes.reload();
    this.summary.reload();
    this.close();
  }

  retry(): void { this.listRes.reload(); }
}
