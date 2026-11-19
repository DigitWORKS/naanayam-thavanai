import { HttpClient } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, inject, input, output, signal, viewChild } from '@angular/core';
import { firstValueFrom } from 'rxjs';

import { API } from '../../core/api';
import { apiErrorMessage } from '../../core/api-error';
import { Money } from '../../core/money';
import { Dialog } from '../../shared/dialog';
import { isValidUtr, LoanDetail, normaliseUtr, PaymentMode } from './loan.models';

/** Records a payment on a loan from the web (owner collecting at the office). */
@Component({
  selector: 'nt-collect-payment',
  imports: [Dialog],
  template: `
    <nt-dialog [heading]="'Collect payment · ' + loan().loanNo" [busy]="busy()" (closed)="closed.emit()">
      <form class="form" (submit)="$event.preventDefault(); submit()">
        <p class="who">{{ loan().clientName }} · outstanding {{ outstanding().format() }}</p>

        <div class="nt-field">
          <label for="cp-amount">Amount (₹)</label>
          <input id="cp-amount" inputmode="decimal" autocomplete="off" [value]="amountText()"
                 (input)="amountText.set($any($event.target).value)" [attr.aria-invalid]="!!amountError()" />
          <div class="chips">
            @for (c of chips(); track c.label) {
              <button type="button" class="chip" (click)="amountText.set(c.amount.toApi().replace('.00', ''))">
                {{ c.label }} {{ c.amount.format() }}
              </button>
            }
          </div>
          @if (amountError()) { <span class="nt-error-text small">{{ amountError() }}</span> }
        </div>

        <fieldset class="modes">
          <legend>Paid by</legend>
          @for (md of modes; track md) {
            <label [class.on]="mode() === md">
              <input type="radio" name="cp-mode" [value]="md" [checked]="mode() === md" (change)="mode.set(md)" />
              {{ md === 'CASH' ? 'Cash' : 'UPI' }}
            </label>
          }
        </fieldset>

        @if (mode() === 'UPI') {
          <div class="nt-field">
            <label for="cp-utr">UPI transaction ID (UTR)</label>
            <input id="cp-utr" inputmode="numeric" autocomplete="off" maxlength="16" placeholder="12 digits"
                   [value]="utr()" (input)="utr.set($any($event.target).value)"
                   [attr.aria-invalid]="!!utrError()" />
            @if (utrError()) { <span class="nt-error-text small">{{ utrError() }}</span> }
          </div>
        }

        <div class="nt-field">
          <label for="cp-note">Note <span class="opt">(optional)</span></label>
          <input id="cp-note" maxlength="255" [value]="note()" (input)="note.set($any($event.target).value)" />
        </div>

        @if (error()) { <p class="nt-error-text" role="alert">{{ error() }}</p> }

        <div class="actions">
          <button type="button" class="nt-btn nt-btn--outline" [disabled]="busy()" (click)="dialog().close()">Cancel</button>
          <button type="submit" class="nt-btn" [disabled]="busy()">
            {{ busy() ? 'Saving…' : 'Record ' + (amount()?.format() ?? '') }}
          </button>
        </div>
      </form>
    </nt-dialog>
  `,
  styleUrl: './dialog-form.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CollectPayment {
  private readonly http = inject(HttpClient);

  readonly loan = input.required<LoanDetail>();
  readonly closed = output<void>();
  readonly saved = output<void>();

  readonly dialog = viewChild.required(Dialog);
  readonly modes: PaymentMode[] = ['CASH', 'UPI'];

  readonly outstanding = computed(() => Money.fromJson(this.loan().outstanding));
  readonly amountText = signal('');
  readonly mode = signal<PaymentMode>('CASH');
  readonly utr = signal('');
  readonly note = signal('');
  readonly busy = signal(false);
  readonly error = signal('');
  readonly touched = signal(false);
  /** One id per dialog: a retry after a lost response is recognised by the server as the same payment. */
  private readonly clientTxnId = crypto.randomUUID();

  readonly chips = computed(() => {
    const l = this.loan();
    const one = Money.fromJson(l.installmentAmount);
    const behind = Money.fromJson(l.overdueAmount);
    const out: { label: string; amount: Money }[] = [{ label: 'Installment', amount: one }];
    if (behind.isPositive) out.push({ label: 'Behind + today', amount: behind.plus(one) });
    out.push({ label: 'All', amount: this.outstanding() });
    // Never offer more than is outstanding, and no duplicates.
    const capped = out.map((c) => ({
      ...c, amount: c.amount.paise > this.outstanding().paise ? this.outstanding() : c.amount,
    }));
    return capped.filter((c, i) => capped.findIndex((d) => d.amount.equals(c.amount)) === i);
  });

  readonly amount = computed(() => {
    const m = Money.tryParse(this.amountText());
    return m && m.isPositive ? m : null;
  });
  readonly amountError = computed(() => {
    if (!this.touched()) return '';
    const m = this.amount();
    if (!m) return 'Enter an amount above ₹0.';
    if (m.paise > this.outstanding().paise) return `More than the outstanding ${this.outstanding().format()}.`;
    return '';
  });
  readonly utrError = computed(() =>
    this.touched() && this.mode() === 'UPI' && !isValidUtr(this.utr()) ? 'UTR is the 12-digit number in the UPI app.' : '');

  async submit(): Promise<void> {
    this.touched.set(true);
    if (this.amountError() || this.utrError()) return;
    this.busy.set(true);
    this.error.set('');
    try {
      await firstValueFrom(this.http.post(`${API}/payments`, {
        clientTxnId: this.clientTxnId,
        loanId: this.loan().id,
        amount: this.amount()!.toApi(),
        mode: this.mode(),
        upiRefNo: this.mode() === 'UPI' ? normaliseUtr(this.utr()) : null,
        paidOn: new Date().toISOString(),
        remarks: this.note().trim() || null,
      }));
      this.saved.emit();
      this.dialog().close();
    } catch (e) {
      this.error.set(apiErrorMessage(e, 'Could not record the payment. Try again.'));
    } finally {
      this.busy.set(false);
    }
  }
}
