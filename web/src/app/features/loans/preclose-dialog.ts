import { HttpClient, httpResource } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, inject, input, output, signal, viewChild } from '@angular/core';
import { firstValueFrom } from 'rxjs';

import { API } from '../../core/api';
import { apiErrorCode, apiErrorMessage } from '../../core/api-error';
import { Money } from '../../core/money';
import { Dialog } from '../../shared/dialog';
import { isValidUtr, LoanDetail, normaliseUtr, PaymentMode, PreclosureQuote } from './loan.models';
import { safeValue } from '../../core/resource';

/** Pre-closure quote as of today; the owner can settle the loan with it. */
@Component({
  selector: 'nt-preclose-dialog',
  imports: [Dialog],
  template: `
    <nt-dialog [heading]="'Pre-close · ' + loan().loanNo" [busy]="busy()" (closed)="closed.emit()">
      @if (quote(); as q) {
        <form class="form" (submit)="$event.preventDefault(); submit()">
          <p class="who">{{ loan().clientName }} · quote for today ({{ q.asOf }})</p>
          <dl class="lines">
            <dt>Principal outstanding</dt><dd>{{ q.principal }}</dd>
            <dt>Interest outstanding</dt><dd>{{ q.interest }}</dd>
            @if (q.hasPenalty) { <dt>Penalty due</dt><dd>{{ q.penalty }}</dd> }
            @if (q.hasRebate) { <dt>Interest rebate</dt><dd class="minus">− {{ q.rebate }}</dd> }
            @if (q.hasCharge) { <dt>Pre-closure charge</dt><dd>{{ q.charge }}</dd> }
            <dt class="total">To settle</dt><dd class="total">{{ q.settlement }}</dd>
          </dl>

          <fieldset class="modes">
            <legend>Paid by</legend>
            @for (md of modes; track md) {
              <label [class.on]="mode() === md">
                <input type="radio" name="pc-mode" [value]="md" [checked]="mode() === md" (change)="mode.set(md)" />
                {{ md === 'CASH' ? 'Cash' : 'UPI' }}
              </label>
            }
          </fieldset>
          @if (mode() === 'UPI') {
            <div class="nt-field">
              <label for="pc-utr">UPI transaction ID (UTR)</label>
              <input id="pc-utr" inputmode="numeric" autocomplete="off" maxlength="16" placeholder="12 digits"
                     [value]="utr()" (input)="utr.set($any($event.target).value)" />
              @if (touched() && !utrOk()) {
                <span class="nt-error-text small">UTR is the 12-digit number in the UPI app.</span>
              }
            </div>
          }

          <p class="nt-help">This closes the loan. Pending installments are marked settled.</p>
          @if (error()) { <p class="nt-error-text" role="alert">{{ error() }}</p> }
          <div class="actions">
            <button type="button" class="nt-btn nt-btn--outline" [disabled]="busy()" (click)="dialog().close()">Cancel</button>
            <button type="submit" class="nt-btn" [disabled]="busy()">
              {{ busy() ? 'Closing…' : 'Received ' + q.settlement + ' · close loan' }}
            </button>
          </div>
        </form>
      } @else if (quoteRes.error()) {
        <p class="nt-error-text" role="alert">{{ quoteError() }}</p>
        <div class="actions">
          <button type="button" class="nt-btn nt-btn--outline" (click)="dialog().close()">Close</button>
          <button type="button" class="nt-btn" (click)="quoteRes.reload()">Retry</button>
        </div>
      } @else {
        <p class="who">Working out the quote…</p>
      }
    </nt-dialog>
  `,
  styleUrl: './dialog-form.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PrecloseDialog {
  private readonly http = inject(HttpClient);

  readonly loan = input.required<LoanDetail>();
  readonly closed = output<void>();
  readonly saved = output<void>();

  readonly dialog = viewChild.required(Dialog);
  readonly modes: PaymentMode[] = ['CASH', 'UPI'];

  readonly quoteRes = httpResource<PreclosureQuote>(() => `${API}/loans/${this.loan().id}/preclosure-quote`);
  readonly quoteError = computed(() => apiErrorMessage(this.quoteRes.error(), 'Could not get the quote.'));
  readonly quote = computed(() => {
    const q = safeValue(this.quoteRes);
    if (!q) return null;
    const m = (v: string) => Money.fromJson(v);
    return {
      asOf: new Date(`${q.asOf}T00:00:00`).toLocaleDateString('en-IN', { day: 'numeric', month: 'short' }),
      principal: m(q.outstandingPrincipal).format(),
      interest: m(q.outstandingInterest).format(),
      penalty: m(q.pendingPenalty).format(),
      hasPenalty: m(q.pendingPenalty).isPositive,
      rebate: m(q.interestRebate).format(),
      hasRebate: m(q.interestRebate).isPositive,
      charge: m(q.preclosureCharge).format(),
      hasCharge: m(q.preclosureCharge).isPositive,
      settlement: m(q.settlementAmount).format(),
      settlementApi: q.settlementAmount,
    };
  });

  readonly mode = signal<PaymentMode>('CASH');
  readonly utr = signal('');
  readonly utrOk = computed(() => this.mode() === 'CASH' || isValidUtr(this.utr()));
  readonly touched = signal(false);
  readonly busy = signal(false);
  readonly error = signal('');
  private readonly clientTxnId = crypto.randomUUID();

  async submit(): Promise<void> {
    this.touched.set(true);
    const q = this.quote();
    if (!q || !this.utrOk()) return;
    this.busy.set(true);
    this.error.set('');
    try {
      await firstValueFrom(this.http.post(`${API}/loans/${this.loan().id}/preclose`, {
        clientTxnId: this.clientTxnId,
        settlementAmount: Money.fromJson(q.settlementApi).toApi(),
        mode: this.mode(),
        upiRefNo: this.mode() === 'UPI' ? normaliseUtr(this.utr()) : null,
        paidOn: new Date().toISOString(),
      }));
      this.saved.emit();
      this.dialog().close();
    } catch (e) {
      if (apiErrorCode(e) === 'QUOTE_OUTDATED') {
        // A payment or a new day changed the amount: show the fresh quote and let the owner confirm again.
        this.quoteRes.reload();
        this.error.set('The amount changed since this quote. Check the new amount and confirm again.');
      } else {
        this.error.set(apiErrorMessage(e, 'Could not pre-close. Try again.'));
      }
    } finally {
      this.busy.set(false);
    }
  }
}
