import { HttpClient, httpResource } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, inject, input, numberAttribute, signal } from '@angular/core';
import { NgTemplateOutlet } from '@angular/common';
import { RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';

import { API } from '../../core/api';
import { apiErrorCode, apiErrorMessage } from '../../core/api-error';
import { Money } from '../../core/money';
import { BusinessSummary } from '../../layout/business-summary';
import { CollectPayment } from './collect-payment';
import {
  FREQUENCY_LABEL, Installment, LedgerEntry, LoanDetail, STATUS_LABEL,
} from './loan.models';
import { formatPhone } from '../../shared/format';
import { PrecloseDialog } from './preclose-dialog';
import { ReasonDialog } from './reason-dialog';
import { safeValue } from '../../core/resource';

const m = (v: string | null | undefined) => Money.fromJson(v);
const DAY_MS = 86_400_000;
const parseDate = (iso: string) => new Date(`${iso}T00:00:00`);
const longDate = (iso: string) =>
  parseDate(iso).toLocaleDateString('en-IN', { day: 'numeric', month: 'short', year: 'numeric' });
const rowDate = (iso: string) =>
  parseDate(iso).toLocaleDateString('en-IN', { weekday: 'short', day: 'numeric', month: 'short' });
const isoToday = () => {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
};

/** Collapsed schedule: rows from this many days back, this many rows, then the last few. */
const LOOK_BACK_DAYS = 4;
const WINDOW_ROWS = 6;
const TAIL_ROWS = 3;
const PAYMENTS_SHOWN = 5;

type Dialog = 'collect' | 'preclose' | 'default' | { reverse: LedgerEntry } | null;

/** One loan (design board "Web loan detail"). */
@Component({
  selector: 'nt-loan-detail',
  imports: [RouterLink, NgTemplateOutlet, CollectPayment, PrecloseDialog, ReasonDialog],
  templateUrl: './loan-detail.html',
  styleUrl: './loan-detail.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LoanDetailPage {
  private readonly http = inject(HttpClient);
  private readonly summary = inject(BusinessSummary);

  /** Route param :loanId. */
  readonly loanId = input.required({ transform: numberAttribute });

  readonly loanRes = httpResource<LoanDetail>(() => `${API}/loans/${this.loanId()}`);
  private readonly scheduleRes = httpResource<Installment[]>(() => `${API}/loans/${this.loanId()}/installments`);
  private readonly ledgerRes = httpResource<LedgerEntry[]>(() => `${API}/loans/${this.loanId()}/ledger`);

  readonly notFound = computed(() => apiErrorCode(this.loanRes.error()) === 'NOT_FOUND');
  readonly loadError = computed(() => (this.loanRes.error() ? apiErrorMessage(this.loanRes.error()) : ''));
  readonly loan = computed(() => safeValue(this.loanRes));

  readonly dialog = signal<Dialog>(null);
  readonly reverseTarget = computed(() => {
    const d = this.dialog();
    return d && typeof d === 'object' ? d.reverse : null;
  });

  // ------------------------------------------------------------ header + summary

  readonly view = computed(() => {
    const l = this.loan();
    if (!l) return null;
    const outstanding = m(l.outstanding);
    const behind = m(l.overdueAmount);
    const penalty = m(l.pendingPenalty);
    const extra = l.installmentCount - l.tenureCount;
    return {
      active: l.status === 'ACTIVE',
      statusLabel: STATUS_LABEL[l.status],
      statusClass: l.status === 'ACTIVE' ? 'nt-tag--ok' : l.status === 'DEFAULTED' ? 'nt-tag--danger' : '',
      subtitle: [formatPhone(l.clientPhone), l.areaName, `started ${longDate(l.startDate)}`].filter(Boolean),
      outstanding: outstanding.format(),
      paidText: `${l.paidCount} of ${l.installmentCount} paid`,
      paidPct: l.installmentCount ? Math.round((l.paidCount / l.installmentCount) * 100) : 0,
      behindText: behind.isPositive || penalty.isPositive
        ? [behind.isPositive ? `${behind.format()} behind schedule` : '',
          penalty.isPositive ? `penalty ${penalty.format()}` : ''].filter(Boolean).join(' · ')
        : '',
      facts: [
        { label: 'Principal', value: m(l.principal).format() },
        {
          label: 'Interest',
          value: `${Number(l.interestRate)}% p.a. ${l.interestMethod === 'FLAT' ? 'flat' : 'reducing'}`,
        },
        { label: 'Total payable', value: m(l.totalPayable).format() },
        { label: 'Paid', value: m(l.totalPaid).format() },
        { label: FREQUENCY_LABEL[l.frequency], value: m(l.installmentAmount).format() },
        { label: 'Ends', value: longDate(l.currentEndDate) + (extra > 0 ? ` (+${extra})` : '') },
        {
          label: 'Interest collected',
          value: l.interestCollection === 'UPFRONT_DEDUCTED' ? 'Deducted upfront' : 'Added to dues',
        },
        { label: 'Given to client', value: m(l.disbursedAmount).format() },
      ],
      legalNote: l.legalNote,
      closedText: l.closedAt
        ? `${STATUS_LABEL[l.status]} on ${new Date(l.closedAt).toLocaleDateString('en-IN',
          { day: 'numeric', month: 'short', year: 'numeric' })}`
        : '',
    };
  });

  // ------------------------------------------------------------ payments

  readonly showAllPayments = signal(false);
  readonly paymentsLoading = computed(() => this.ledgerRes.isLoading() && !this.ledgerRes.hasValue());
  readonly paymentsError = computed(() => !!this.ledgerRes.error());

  readonly payments = computed(() => {
    const ledger = safeValue(this.ledgerRes) ?? [];
    // The server reverses payments on active and closed loans (a closed loan reopens), not pre-closures.
    const reversible = this.loan()?.status === 'ACTIVE' || this.loan()?.status === 'CLOSED';
    const reversals = new Map(ledger.filter((e) => e.entryType === 'REVERSAL').map((e) => [e.reversalOf, e]));
    // Reversal rows are folded into the payment they undo.
    const rows = ledger.filter((e) => e.entryType === 'PAYMENT').reverse().map((p) => {
      const rev = reversals.get(p.id);
      const when = new Date(p.paidOn).toLocaleString('en-IN',
        { day: 'numeric', month: 'short', hour: 'numeric', minute: '2-digit' });
      const by = p.collectedByName ?? '';
      const detail = rev
        ? [rev.remarks || 'Reversed']
        : [p.mode === 'UPI' ? `UPI ${p.upiRefNo ?? ''}`.trim() : 'Cash',
          p.purpose === 'PRECLOSURE' ? 'pre-closure' : '', by, `balance ${m(p.runningBalance).format()}`,
          p.remarks ? `“${p.remarks}”` : ''].filter(Boolean);
      return {
        source: p,
        when,
        detail: detail.join(' · '),
        amount: m(p.amount).format(),
        reversed: !!rev,
        canReverse: reversible && !rev && p.purpose === 'REGULAR',
      };
    });
    return { total: rows.length, rows: this.showAllPayments() ? rows : rows.slice(0, PAYMENTS_SHOWN) };
  });

  // ------------------------------------------------------------ schedule

  readonly showAllSchedule = signal(false);
  readonly lookBackDays = LOOK_BACK_DAYS;
  readonly scheduleLoading = computed(() => this.scheduleRes.isLoading() && !this.scheduleRes.hasValue());
  readonly scheduleError = computed(() => !!this.scheduleRes.error());

  readonly schedule = computed(() => {
    const list = safeValue(this.scheduleRes) ?? [];
    const today = isoToday();
    const seqById = new Map(list.map((i) => [i.id, i.seqNo]));
    const movedTo = new Map(list.filter((i) => i.extendedFrom !== null).map((i) => [i.extendedFrom, i.seqNo]));
    const lastRegular = Math.max(0, ...list.filter((i) => !i.isExtension).map((i) => i.seqNo));

    const rows = list.map((i) => {
      const due = m(i.dueAmount);
      const paid = m(i.paidAmount);
      const future = i.dueDate > today && paid.isZero && i.status === 'PENDING';
      const penalty = m(i.penaltyAmount);
      const s = statusText(i, today, movedTo.get(i.id), i.extendedFrom ? seqById.get(i.extendedFrom) : undefined,
        i.seqNo === lastRegular);
      return {
        seq: i.seqNo,
        date: (i.dueDate === today ? 'Today · ' : '') + rowDate(i.dueDate),
        isToday: i.dueDate === today,
        due: due.format(),
        paid: future ? '—' : paid.format(),
        paidMuted: future || paid.isZero,
        penalty: penalty.isPositive ? penalty.format() : '—',
        penaltyWarn: penalty.isPositive,
        status: s.text,
        statusClass: s.cls,
        dueDate: i.dueDate,
      };
    });

    if (this.showAllSchedule() || rows.length <= WINDOW_ROWS + TAIL_ROWS + 1) {
      return { total: rows.length, collapsed: false, head: rows, hidden: 0, tail: [] as typeof rows };
    }
    const from = new Date(parseDate(today).getTime() - LOOK_BACK_DAYS * DAY_MS);
    const fromIso = `${from.getFullYear()}-${String(from.getMonth() + 1).padStart(2, '0')}-${String(from.getDate()).padStart(2, '0')}`;
    let start = rows.findIndex((r) => r.dueDate >= fromIso);
    if (start < 0) start = Math.max(0, rows.length - WINDOW_ROWS - TAIL_ROWS);
    const head = rows.slice(start, start + WINDOW_ROWS);
    const tailStart = Math.max(start + WINDOW_ROWS, rows.length - TAIL_ROWS);
    const tail = rows.slice(tailStart);
    return {
      total: rows.length,
      collapsed: true,
      before: start,
      head,
      hidden: tailStart - (start + WINDOW_ROWS),
      tail,
    };
  });

  // ------------------------------------------------------------ actions

  /** After any change: the loan, its rows and payments, and the dashboard totals. */
  refresh(): void {
    this.loanRes.reload();
    this.scheduleRes.reload();
    this.ledgerRes.reload();
    this.summary.reload();
  }

  readonly reverse = async (reason: string) => {
    const p = this.reverseTarget();
    if (!p) return;
    await firstValueFrom(this.http.post(`${API}/payments/${p.id}/reverse`, { reason }));
    this.refresh();
  };

  readonly markDefaulted = async (legalNote: string) => {
    await firstValueFrom(this.http.post(`${API}/loans/${this.loanId()}/default`, { legalNote }));
    this.refresh();
  };

  reverseHeading(p: LedgerEntry): string {
    return `Reverse ${m(p.amount).format()} of ${new Date(p.paidOn).toLocaleDateString('en-IN',
      { day: 'numeric', month: 'short' })}?`;
  }
}

function statusText(i: Installment, today: string, movedTo: number | undefined, from: number | undefined,
                    lastDay: boolean): { text: string; cls: string } {
  switch (i.status) {
    case 'PAID':
      return { text: 'Paid', cls: 'ok' };
    case 'SETTLED':
      return { text: 'Settled', cls: 'muted' };
    case 'MISSED':
      return { text: movedTo ? `Missed → moved to #${movedTo}` : 'Missed', cls: 'warn' };
    case 'PARTIAL':
      return { text: i.dueDate < today ? 'Part paid · behind' : 'Part paid', cls: i.dueDate < today ? 'warn' : '' };
    default:
      if (i.isExtension) return { text: from ? `Extension (from #${from})` : 'Extension', cls: 'muted' };
      if (i.dueDate === today) return { text: 'Due', cls: 'strong' };
      if (i.dueDate < today) return { text: 'Not paid', cls: 'warn' };
      return { text: lastDay ? 'Pending · last day' : 'Pending', cls: 'muted' };
  }
}

