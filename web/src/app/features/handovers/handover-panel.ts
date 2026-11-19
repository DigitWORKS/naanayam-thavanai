import { HttpClient, httpResource } from '@angular/common/http';
import {
  ChangeDetectionStrategy, Component, computed, effect, inject, input, output, signal, untracked,
} from '@angular/core';
import { RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';

import { API } from '../../core/api';
import { apiErrorCode, apiErrorMessage } from '../../core/api-error';
import { Money } from '../../core/money';
import { safeValue } from '../../core/resource';
import type { Handover, HandoverStatus } from '../dashboard/dashboard.models';
import type { Agent } from './handovers';

/** GET /cash-handovers/{id}/payments */
interface HandoverPayment {
  paymentId: number;
  loanId: number;
  loanNo: string;
  clientName: string;
  amount: string;
  entryType: 'PAYMENT' | 'REVERSAL';
  paidOn: string;
  remarks: string | null;
}

const m = (v: string | null | undefined) => Money.fromJson(v);

/** Right-hand panel of Cash handovers: the numbers, the cash entries behind them, confirm / dispute. */
@Component({
  selector: 'nt-handover-panel',
  imports: [RouterLink],
  templateUrl: './handover-panel.html',
  styleUrl: './handover-panel.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class HandoverPanel {
  private readonly http = inject(HttpClient);

  readonly handover = input.required<Handover>();
  readonly agent = input<Agent | null>(null);
  readonly decided = output<void>();
  readonly closed = output<void>();

  private readonly paymentsRes = httpResource<HandoverPayment[]>(() =>
    `${API}/cash-handovers/${this.handover().id}/payments`);

  readonly showPayments = signal(false);
  readonly remarks = signal('');
  readonly busy = signal<HandoverStatus | null>(null);
  readonly error = signal('');

  constructor() {
    // A different handover: start clean.
    effect(() => {
      this.handover().id;
      untracked(() => {
        this.showPayments.set(false);
        this.remarks.set('');
        this.error.set('');
      });
    });
  }

  readonly view = computed(() => {
    const h = this.handover();
    const diff = m(h.difference);
    const pays = safeValue(this.paymentsRes);
    const count = pays?.filter((p) => p.entryType === 'PAYMENT').length;
    const areas = this.agent()?.areas.map((a) => a.name).join(', ');
    return {
      pending: h.status === 'PENDING',
      date: new Date(`${h.handoverDate}T00:00:00`).toLocaleDateString('en-IN',
        { weekday: 'short', day: 'numeric', month: 'short' }),
      title: [h.agentName, areas].filter(Boolean).join(' · '),
      collectedLabel: count === undefined ? 'Cash collected' : `Cash collected (${count} ${count === 1 ? 'payment' : 'payments'})`,
      expected: m(h.expectedAmount).format(),
      handed: m(h.handedAmount).format(),
      diffLabel: diff.isZero ? 'Difference' : diff.isPositive ? 'Extra' : 'Short',
      diff: diff.isZero ? 'Matches' : new Money(Math.abs(diff.paise)).format(),
      diffWarn: !diff.isZero,
      agentNote: h.remarks,
      ownerNote: h.ownerRemarks,
      statusText: h.status === 'CONFIRMED' ? 'Confirmed' : h.status === 'DISPUTED' ? 'Disputed' : '',
      decidedAt: h.confirmedAt
        ? new Date(h.confirmedAt).toLocaleString('en-IN', { day: 'numeric', month: 'short', hour: 'numeric', minute: '2-digit' })
        : '',
      firstName: h.agentName.split(/\s+/)[0],
    };
  });

  readonly payments = computed(() => {
    const list = safeValue(this.paymentsRes) ?? [];
    const rows = list.map((p) => ({
      id: p.paymentId,
      loanId: p.loanId,
      time: new Date(p.paidOn).toLocaleTimeString('en-IN', { hour: 'numeric', minute: '2-digit' }),
      who: `${p.clientName} · ${p.loanNo}`,
      amount: m(p.amount).format(),
      reversal: p.entryType === 'REVERSAL',
      note: p.remarks ?? '',
    }));
    const total = Money.sum(list.map((p) => m(p.amount)));
    return {
      rows,
      total: total.format(),
      // The list is recomputed now; if entries changed after the handover it no longer matches.
      differs: !!list.length && !total.equals(m(this.handover().expectedAmount)),
    };
  });
  readonly paymentsLoading = computed(() => this.paymentsRes.isLoading() && !this.paymentsRes.hasValue());
  readonly paymentsError = computed(() => !!this.paymentsRes.error());

  async decide(status: Exclude<HandoverStatus, 'PENDING'>): Promise<void> {
    const note = this.remarks().trim();
    if (status === 'DISPUTED' && !note) {
      this.error.set(`Write what is wrong so ${this.view().firstName} sees it.`);
      return;
    }
    this.busy.set(status);
    this.error.set('');
    try {
      await firstValueFrom(this.http.post(`${API}/cash-handovers/${this.handover().id}/confirm`,
        { status, remarks: note || null }));
      this.decided.emit();
    } catch (e) {
      if (apiErrorCode(e) === 'ALREADY_DECIDED') {
        this.decided.emit();
      } else {
        this.error.set(apiErrorMessage(e, 'Could not save. Try again.'));
      }
    } finally {
      this.busy.set(null);
    }
  }

  retryPayments(): void { this.paymentsRes.reload(); }
}
