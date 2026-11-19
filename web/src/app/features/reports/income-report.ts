import { httpResource } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, inject, input, numberAttribute } from '@angular/core';
import { Router, RouterLink } from '@angular/router';

import { API } from '../../core/api';
import { apiErrorMessage } from '../../core/api-error';
import { Money } from '../../core/money';
import { safeValue } from '../../core/resource';
import { downloadCsv } from '../../shared/csv';

/** GET /reports/interest-income?fy= */
interface Income {
  fy: string;
  totalInterestIncome: string;
  totalPenaltyIncome: string;
  totalPreclosureCharges: string;
  months: { month: string; interest: string; penalty: string; preclosureCharges: string }[];
}

const m = (v: string | null | undefined) => Money.fromJson(v);
/** Financial year (1 April – 31 March) that contains {@code d}, as its starting year. */
const fyOf = (d: Date) => (d.getMonth() >= 3 ? d.getFullYear() : d.getFullYear() - 1);
const fyLabel = (start: number) => `FY ${start}–${String(start + 1).slice(2)}`;
/** Years offered in the picker. */
const YEARS_BACK = 4;

/** Reports › Income for tax (design board "Web income for tax"). ?fy=2026 picks the year. */
@Component({
  selector: 'nt-income-report',
  imports: [RouterLink],
  templateUrl: './income-report.html',
  styleUrl: './reports.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class IncomeReportPage {
  private readonly router = inject(Router);

  private readonly currentFy = fyOf(new Date());
  readonly fy = input(fyOf(new Date()), {
    transform: (v: unknown) => {
      const n = numberAttribute(v, NaN);
      const cur = fyOf(new Date());
      return Number.isInteger(n) && n <= cur && n >= cur - 20 ? n : cur;
    },
  });

  readonly years = Array.from({ length: YEARS_BACK + 1 }, (_, i) => this.currentFy - i)
    .map((y) => ({ value: y, label: fyLabel(y) }));
  readonly isCurrent = computed(() => this.fy() === this.currentFy);

  private readonly res = httpResource<Income>(() => ({ url: `${API}/reports/interest-income`, params: { fy: this.fy() } }));
  readonly loading = computed(() => this.res.isLoading());
  readonly error = computed(() => (this.res.error() ? apiErrorMessage(this.res.error()) : ''));

  readonly totals = computed(() => {
    const r = safeValue(this.res);
    if (!r) return null;
    const i = m(r.totalInterestIncome);
    const p = m(r.totalPenaltyIncome);
    const c = m(r.totalPreclosureCharges);
    return { interest: i.format(), penalty: p.format(), preclosure: c.format(), all: i.plus(p).plus(c).format() };
  });

  readonly months = computed(() => {
    const r = safeValue(this.res);
    if (!r) return [];
    const now = new Date();
    const thisMonth = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`;
    const rows = r.months.map((x) => {
      const total = m(x.interest).plus(m(x.penalty)).plus(m(x.preclosureCharges));
      return { x, total, future: x.month > thisMonth };
    });
    const max = Math.max(1, ...rows.map((y) => y.total.paise));
    return rows.map(({ x, total, future }) => ({
      key: x.month,
      label: new Date(`${x.month}-01T00:00:00`).toLocaleDateString('en-IN', { month: 'short', year: 'numeric' }),
      future,
      current: x.month === thisMonth,
      interest: future ? '—' : m(x.interest).format(),
      penalty: future ? '—' : m(x.penalty).format(),
      preclosure: future ? '—' : m(x.preclosureCharges).format(),
      total: future ? '—' : total.format(),
      barPct: (total.paise / max) * 100,
    }));
  });

  pickFy(v: string): void {
    const n = Number(v);
    this.router.navigate([], { queryParams: { fy: n === this.currentFy ? null : n }, replaceUrl: true });
  }

  retry(): void { this.res.reload(); }

  exportCsv(): void {
    const r = safeValue(this.res);
    if (!r) return;
    const rows: (string | number)[][] = [['Month', 'Interest', 'Late penalty', 'Pre-closure charges', 'Total']];
    for (const x of r.months) {
      const total = m(x.interest).plus(m(x.penalty)).plus(m(x.preclosureCharges));
      rows.push([x.month, x.interest, x.penalty, x.preclosureCharges, total.toApi()]);
    }
    const all = m(r.totalInterestIncome).plus(m(r.totalPenaltyIncome)).plus(m(r.totalPreclosureCharges));
    rows.push([`${fyLabel(this.fy())} total`, r.totalInterestIncome, r.totalPenaltyIncome, r.totalPreclosureCharges, all.toApi()]);
    downloadCsv(`income-${r.fy}.csv`, rows);
  }
}
