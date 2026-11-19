import { httpResource } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';

import { API } from '../../core/api';
import { apiErrorMessage } from '../../core/api-error';
import { Money } from '../../core/money';
import { safeValue } from '../../core/resource';
import { downloadCsv } from '../../shared/csv';

type Period = 'today' | 'week' | 'month' | 'custom';
type Group = 'DAY' | 'AGENT' | 'AREA';

/** GET /reports/collections */
interface Row {
  key: string;
  target: string | null;
  collected: string;
  cash: string;
  upi: string;
  paymentCount: number;
}

const PERIODS: { id: Period; label: string }[] = [
  { id: 'today', label: 'Today' },
  { id: 'week', label: 'This week' },
  { id: 'month', label: 'This month' },
  { id: 'custom', label: 'Custom' },
];
const GROUPS: { id: Group; label: string }[] = [
  { id: 'DAY', label: 'Day' },
  { id: 'AGENT', label: 'Agent' },
  { id: 'AREA', label: 'Area' },
];
/** Day bars stay readable up to about two months; longer ranges show the table only. */
const MAX_CHART_DAYS = 62;
const MAX_RANGE_DAYS = 366;

const iso = (d: Date) =>
  `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
const parse = (s: string) => new Date(`${s}T00:00:00`);
const isIso = (v: unknown): v is string => typeof v === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(v);
const m = (v: string | null | undefined) => Money.fromJson(v);
const pct = (part: Money, whole: Money) => (whole.isPositive ? Math.round((part.paise / whole.paise) * 100) : null);

/** Monday-start week, as in the mobile app. */
function periodRange(p: Exclude<Period, 'custom'>, today = new Date()): { from: string; to: string } {
  const t = new Date(today.getFullYear(), today.getMonth(), today.getDate());
  if (p === 'today') return { from: iso(t), to: iso(t) };
  if (p === 'week') {
    const monday = new Date(t);
    monday.setDate(t.getDate() - ((t.getDay() + 6) % 7));
    return { from: iso(monday), to: iso(t) };
  }
  return { from: iso(new Date(t.getFullYear(), t.getMonth(), 1)), to: iso(t) };
}

/** Compact axis label: ₹50k, ₹1.2L. */
function shortRupees(paise: number): string {
  const r = paise / 100;
  if (r >= 100_000) return `₹${+(r / 100_000).toFixed(1)}L`;
  if (r >= 1_000) return `₹${+(r / 1_000).toFixed(r >= 10_000 ? 0 : 1)}k`;
  return `₹${Math.round(r)}`;
}

/** A round axis maximum just above {@code paise}. */
function niceMax(paise: number): number {
  if (paise <= 0) return 100_00;
  const r = paise / 100;
  const pow = 10 ** Math.floor(Math.log10(r));
  const step = [1, 2, 2.5, 5, 10].find((s) => s * pow >= r)! * pow;
  return step * 100;
}

/** Reports › Collections (design board "Web reports"). State in the URL: ?period=&from=&to=&group=. */
@Component({
  selector: 'nt-collections-report',
  imports: [RouterLink],
  templateUrl: './collections-report.html',
  styleUrl: './reports.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CollectionsReportPage {
  private readonly router = inject(Router);

  readonly period = input<Period, unknown>('month', {
    transform: (v: unknown) => (PERIODS.some((p) => p.id === v) ? (v as Period) : 'month'),
  });
  readonly from = input('', { transform: (v: unknown) => (isIso(v) ? v : '') });
  readonly to = input('', { transform: (v: unknown) => (isIso(v) ? v : '') });
  readonly group = input<Group, unknown>('DAY', {
    transform: (v: unknown) => (GROUPS.some((g) => g.id === v) ? (v as Group) : 'DAY'),
  });

  readonly periods = PERIODS;
  readonly groups = GROUPS;
  readonly today = iso(new Date());

  /** Range in effect; Custom without dates falls back to this month. */
  readonly range = computed(() => {
    if (this.period() === 'custom' && this.from() && this.to()) return { from: this.from(), to: this.to() };
    return periodRange(this.period() === 'custom' ? 'month' : (this.period() as Exclude<Period, 'custom'>));
  });
  readonly rangeDays = computed(() =>
    Math.round((parse(this.range().to).getTime() - parse(this.range().from).getTime()) / 86_400_000) + 1);
  readonly rangeError = computed(() => {
    const r = this.range();
    if (r.to < r.from) return 'The end date is before the start date.';
    if (this.rangeDays() > MAX_RANGE_DAYS) return 'Pick at most a year.';
    return '';
  });
  readonly rangeText = computed(() => {
    const { from, to } = this.range();
    const f = parse(from);
    const t = parse(to);
    const long = (d: Date) => d.toLocaleDateString('en-IN', { day: 'numeric', month: 'short', year: 'numeric' });
    if (from === to) return long(t);
    const sameMonth = f.getMonth() === t.getMonth() && f.getFullYear() === t.getFullYear();
    return sameMonth ? `${f.getDate()} – ${long(t)}` : `${long(f)} – ${long(t)}`;
  });

  private readonly res = httpResource<Row[]>(() =>
    this.rangeError()
      ? undefined
      : { url: `${API}/reports/collections`, params: { from: this.range().from, to: this.range().to, groupBy: this.group() } });

  readonly loading = computed(() => this.res.isLoading());
  readonly error = computed(() => (this.res.error() ? apiErrorMessage(this.res.error()) : ''));

  /** Rows with every day of the range present when grouped by day (days with nothing are zero). */
  private readonly rows = computed(() => {
    const list = safeValue(this.res) ?? [];
    if (this.group() !== 'DAY') {
      return [...list].sort((a, b) => m(b.collected).paise - m(a.collected).paise || a.key.localeCompare(b.key));
    }
    const byDay = new Map(list.map((r) => [r.key, r]));
    const out: Row[] = [];
    for (let d = parse(this.range().from); iso(d) <= this.range().to; d.setDate(d.getDate() + 1)) {
      const k = iso(d);
      out.push(byDay.get(k) ?? { key: k, target: '0.00', collected: '0.00', cash: '0.00', upi: '0.00', paymentCount: 0 });
    }
    return out;
  });

  readonly totals = computed(() => {
    const rows = safeValue(this.res);
    if (!rows) return null;
    const target = Money.sum(rows.map((r) => m(r.target)));
    const collected = Money.sum(rows.map((r) => m(r.collected)));
    const p = pct(collected, target);
    return {
      // By agent, targets of shared areas add up more than once, so no overall target there.
      target: this.group() === 'AGENT' ? '—' : target.format(),
      collected: collected.format(),
      pct: this.group() === 'AGENT' || p === null ? '' : `${p}% of target`,
      cash: Money.sum(rows.map((r) => m(r.cash))).format(),
      upi: Money.sum(rows.map((r) => m(r.upi))).format(),
      count: rows.reduce((n, r) => n + r.paymentCount, 0),
    };
  });

  readonly keyHeader = computed(() => GROUPS.find((g) => g.id === this.group())!.label);

  readonly tableRows = computed(() => this.rows().map((r) => {
    const target = r.target === null ? null : m(r.target);
    const collected = m(r.collected);
    const p = target ? pct(collected, target) : null;
    return {
      key: r.key,
      label: this.group() === 'DAY'
        ? parse(r.key).toLocaleDateString('en-IN', { weekday: 'short', day: 'numeric', month: 'short' })
        : r.key,
      target: target ? target.format() : '—',
      collected: collected.format(),
      pct: p === null ? '—' : `${p}%`,
      low: p !== null && p < 80,
      cash: m(r.cash).format(),
      upi: m(r.upi).format(),
      count: r.paymentCount,
    };
  }));

  // ------------------------------------------------------------ charts

  readonly chartTitle = computed(() => `Collected by ${this.keyHeader().toLowerCase()}`);
  readonly showDayChart = computed(() => this.group() === 'DAY' && this.rangeDays() > 1 && this.rangeDays() <= MAX_CHART_DAYS);
  readonly chartTooLong = computed(() => this.group() === 'DAY' && this.rangeDays() > MAX_CHART_DAYS);

  readonly dayChart = computed(() => {
    if (!this.showDayChart()) return null;
    const rows = this.rows();
    const max = niceMax(Math.max(0, ...rows.map((r) => Math.max(m(r.collected).paise, m(r.target).paise))));
    const every = rows.length > 20 ? Math.ceil(rows.length / 10) : rows.length > 10 ? 2 : 1;
    return {
      ticks: [max, max / 2, 0].map(shortRupees),
      bars: rows.map((r, i) => {
        const d = parse(r.key);
        return {
          key: r.key,
          collectedPct: (m(r.collected).paise / max) * 100,
          targetPct: (m(r.target).paise / max) * 100,
          axis: i % every === 0 ? String(d.getDate()) : '',
          isToday: r.key === this.today,
        };
      }),
    };
  });

  /** Hovered / focused day bar. */
  readonly tipKey = signal<string | null>(null);
  readonly tip = computed(() => {
    const k = this.tipKey();
    if (!k) return null;
    const rows = this.rows();
    const i = rows.findIndex((r) => r.key === k);
    if (i < 0) return null;
    const r = rows[i];
    const p = pct(m(r.collected), m(r.target));
    return {
      label: parse(k).toLocaleDateString('en-IN', { weekday: 'long', day: 'numeric', month: 'short' }),
      collected: m(r.collected).format(),
      pct: p === null ? '' : ` · ${p}%`,
      target: m(r.target).format(),
      cash: m(r.cash).format(),
      upi: m(r.upi).format(),
      // Keep the box inside the plot: anchor left on the first half, right on the second.
      leftPct: ((i + 0.5) / rows.length) * 100,
      flip: i > rows.length / 2,
    };
  });

  readonly groupChart = computed(() => {
    if (this.group() === 'DAY') return null;
    const rows = this.rows();
    const max = Math.max(1, ...rows.map((r) => Math.max(m(r.collected).paise, m(r.target).paise)));
    return rows.map((r) => {
      const target = r.target === null ? null : m(r.target);
      const p = target ? pct(m(r.collected), target) : null;
      return {
        key: r.key,
        collectedPct: (m(r.collected).paise / max) * 100,
        targetPct: target ? (target.paise / max) * 100 : 0,
        collected: m(r.collected).format(),
        of: target ? `of ${target.format()}${p === null ? '' : ` · ${p}%`}` : 'no target',
      };
    });
  });

  // ------------------------------------------------------------ actions

  private go(q: Record<string, string | null>): void {
    this.router.navigate([], { queryParams: q, queryParamsHandling: 'merge', replaceUrl: true });
  }

  pickPeriod(p: Period): void {
    if (p === 'custom') this.go({ period: 'custom', from: this.range().from, to: this.range().to });
    else this.go({ period: p === 'month' ? null : p, from: null, to: null });
  }
  setFrom(v: string): void { if (isIso(v)) this.go({ period: 'custom', from: v, to: this.range().to }); }
  setTo(v: string): void { if (isIso(v)) this.go({ period: 'custom', from: this.range().from, to: v }); }
  pickGroup(g: Group): void { this.go({ group: g === 'DAY' ? null : g }); }
  retry(): void { this.res.reload(); }

  exportCsv(): void {
    const head = [this.keyHeader(), 'Target', 'Collected', 'Cash', 'UPI', 'Payments'];
    const body = this.rows().map((r) => [r.key, r.target ?? '', r.collected, r.cash, r.upi, r.paymentCount]);
    const { from, to } = this.range();
    downloadCsv(`collections-${this.group().toLowerCase()}-${from}-to-${to}.csv`, [head, ...body]);
  }
}
