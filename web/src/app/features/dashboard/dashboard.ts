import { HttpClient, httpResource } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';

import { API } from '../../core/api';
import { AuthService } from '../../core/auth/auth.service';
import { Money } from '../../core/money';
import { BusinessSummary } from '../../layout/business-summary';
import type { AgentDay, BehindLoan, CollectionRow, Handover, HandoverStatus } from './dashboard.models';
import { HandoverReview } from './handover-review';

const DAY_MS = 86_400_000;
const m = (v: string | null | undefined) => Money.fromJson(v);
const pct = (part: Money, whole: Money) =>
  whole.isPositive ? Math.min(100, Math.round((part.paise / whole.paise) * 100)) : 0;
const parseDate = (iso: string) => new Date(`${iso}T00:00:00`);

/** Owner home (design board "Web owner dashboard"). */
@Component({
  selector: 'nt-dashboard',
  imports: [RouterLink, HandoverReview],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DashboardPage {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly auth = inject(AuthService);
  private readonly summary = inject(BusinessSummary);

  private readonly dash = this.summary.dashboard;
  /** Server's business date (IST); the per-day queries wait for it so they never disagree with the cards. */
  private readonly day = computed(() => this.dash()?.date);

  private readonly agentsRes = httpResource<AgentDay[]>(() =>
    this.day() ? { url: `${API}/reports/agents-today`, params: { date: this.day()! } } : undefined);
  private readonly areasRes = httpResource<CollectionRow[]>(() =>
    this.day()
      ? { url: `${API}/reports/collections`, params: { from: this.day()!, to: this.day()!, groupBy: 'AREA' } }
      : undefined);
  private readonly handoversRes = httpResource<Handover[]>(() => ({
    url: `${API}/cash-handovers`, params: { status: 'PENDING' },
  }));
  private readonly behindRes = httpResource<BehindLoan[]>(() => ({
    url: `${API}/reports/most-behind`, params: { limit: 5 },
  }));

  readonly loadError = computed(() => !!this.summary.error());

  // ------------------------------------------------------------ header

  readonly dateText = computed(() => {
    const d = this.day();
    return (d ? parseDate(d) : new Date())
      .toLocaleDateString('en-IN', { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' });
  });
  readonly firstName = computed(() => (this.auth.user()?.name ?? '').trim().split(/\s+/)[0]);
  readonly search = signal('');

  // ------------------------------------------------------------ cards

  readonly cards = computed(() => {
    const d = this.dash();
    if (!d) return null;
    const collected = m(d.todayCollected);
    const target = m(d.todayTarget);
    return {
      collected: collected.format(),
      collectedPct: pct(collected, target),
      target: target.format(),
      hasTarget: target.isPositive,
      cash: m(d.todayCash).format(),
      upi: m(d.todayUpi).format(),
      outstanding: m(d.totalOutstanding).format(),
      activeLoans: d.activeLoans,
      activeClients: d.activeClients,
      behind: m(d.overdueAmount).format(),
      behindLoans: d.overdueLoans,
      penaltyDue: m(d.penaltyDue),
      fy: d.fy.replace('-', '–'),
      fyInterest: m(d.fyInterest).format(),
      fyMonths: fyMonthsText(parseDate(d.date)),
    };
  });

  // ------------------------------------------------------------ today by agent

  readonly agents = computed(() => {
    const me = this.auth.user()?.id;
    return (this.agentsRes.value() ?? []).map((a) => {
      const owner = a.role === 'OWNER';
      const cash = m(a.cash);
      const h = a.handover;
      // Cash the agent still holds: what was collected in cash, less a handover the owner has confirmed.
      const inHand = h?.status === 'CONFIRMED' ? cash.minus(m(h.handedAmount)) : cash;
      return {
        id: a.userId,
        name: a.userId === me ? 'You' : a.name,
        inactive: !a.active,
        areas: owner ? 'All areas' : a.areas.join(', ') || 'No areas',
        collected: m(a.collected).format(),
        target: a.target === null ? '—' : m(a.target).format(),
        inHand: owner ? '—' : inHand.format(),
        handoverNote: owner || !h ? '' : HANDOVER_NOTE[h.status],
      };
    });
  });
  readonly agentsLoading = computed(() => this.agentsRes.isLoading() && !this.agentsRes.hasValue());
  readonly agentsError = computed(() => !!this.agentsRes.error());

  // ------------------------------------------------------------ today by area

  readonly areas = computed(() =>
    (this.areasRes.value() ?? [])
      .map((r) => ({ name: r.key, collected: m(r.collected), target: m(r.target) }))
      .filter((r) => r.target.isPositive || r.collected.isPositive)
      .sort((a, b) => b.target.paise - a.target.paise || a.name.localeCompare(b.name))
      .map((r) => ({
        name: r.name,
        collected: r.collected.format(),
        pct: pct(r.collected, r.target),
        hasTarget: r.target.isPositive,
        target: r.target.format(),
      })));
  readonly areasLoading = computed(() => this.areasRes.isLoading() && !this.areasRes.hasValue());
  readonly areasError = computed(() => !!this.areasRes.error());

  // ------------------------------------------------------------ cash handovers

  readonly handovers = computed(() => {
    const today = this.day();
    return (this.handoversRes.value() ?? []).map((h) => {
      const diff = m(h.difference);
      return {
        source: h,
        title: `${h.agentName} · ${h.handoverDate === today ? 'today' : shortDate(h.handoverDate)}`,
        matches: diff.isZero,
        detail: `Handed ${m(h.handedAmount).format()} · ${
          diff.isZero ? 'matches' : diff.isPositive ? `${diff.format()} extra` : `${new Money(-diff.paise).format()} short`
        }${h.remarks ? ` · “${h.remarks}”` : ''}`,
      };
    });
  });
  readonly handoversLoading = computed(() => this.handoversRes.isLoading() && !this.handoversRes.hasValue());
  readonly handoversError = computed(() => !!this.handoversRes.error());

  /** Handover being confirmed with one click (matches), to disable its button. */
  readonly confirming = signal<number | null>(null);
  readonly confirmError = signal('');
  /** Handover open in the review dialog. */
  readonly reviewing = signal<Handover | null>(null);

  async confirm(h: Handover): Promise<void> {
    this.confirming.set(h.id);
    this.confirmError.set('');
    try {
      await this.decide(h.id, 'CONFIRMED', null);
    } catch {
      this.confirmError.set(`Could not confirm ${h.agentName}'s handover. Try again.`);
    } finally {
      this.confirming.set(null);
    }
  }

  /** Used by the review dialog; it shows its own error if this throws. */
  readonly decide = async (id: number, status: Exclude<HandoverStatus, 'PENDING'>, remarks: string | null) => {
    try {
      await firstValueFrom(this.http.post(`${API}/cash-handovers/${id}/confirm`, { status, remarks }));
    } finally {
      // Refresh even on failure: a 409 ALREADY_DECIDED means someone else decided it meanwhile.
      this.handoversRes.reload();
      this.agentsRes.reload();
      this.summary.reload();
    }
  };

  // ------------------------------------------------------------ most behind

  readonly behind = computed(() => {
    const d = this.day();
    const today = d ? parseDate(d) : new Date();
    return (this.behindRes.value() ?? []).map((l) => {
      const days = Math.max(1, Math.round((today.getTime() - parseDate(l.behindSince).getTime()) / DAY_MS));
      return {
        loanId: l.loanId,
        client: l.clientName,
        loanNo: l.loanNo,
        area: l.areaName,
        text: `${m(l.arrears).format()} · ${days} ${days === 1 ? 'day' : 'days'}`,
      };
    });
  });
  readonly behindLoading = computed(() => this.behindRes.isLoading() && !this.behindRes.hasValue());
  readonly behindError = computed(() => !!this.behindRes.error());

  // ------------------------------------------------------------ actions

  submitSearch(event: Event): void {
    event.preventDefault();
    const q = this.search().trim();
    this.router.navigate(['/clients'], { queryParams: q ? { q } : {} });
  }

  reloadAll(): void {
    this.summary.reload();
    this.agentsRes.reload();
    this.areasRes.reload();
    this.handoversRes.reload();
    this.behindRes.reload();
  }
}

const HANDOVER_NOTE: Record<HandoverStatus, string> = {
  PENDING: 'handed, to confirm',
  CONFIRMED: 'handed over',
  DISPUTED: 'disputed',
};

const shortDate = (iso: string) => parseDate(iso).toLocaleDateString('en-IN', { day: 'numeric', month: 'short' });

/** "Apr–Nov" for the financial year (1 April – 31 March) up to {@code day}; "Apr" in April. */
function fyMonthsText(day: Date): string {
  const month = day.toLocaleDateString('en-IN', { month: 'short' });
  return day.getMonth() === 3 ? 'Apr' : `Apr–${month}`;
}
