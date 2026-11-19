import { HttpClient } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, inject, output, signal, viewChild } from '@angular/core';
import { firstValueFrom } from 'rxjs';

import { API } from '../../core/api';
import { apiErrorMessage } from '../../core/api-error';
import { Money } from '../../core/money';
import { Dialog } from '../../shared/dialog';
import { Plan, PLAN_LABEL, renewedExpiry } from './tenant.models';

/** Same rules as the server (UserDtos.PHONE / PIN). */
const PHONE = /^[6-9]\d{9}$/;
const PIN = /^\d{4,6}$/;
const digits = (s: string) => s.replace(/\D/g, '');
const todayIso = () => {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
};

/** Super Admin adds a financier: the business, its owner login and default settings (POST /admin/tenants). */
@Component({
  selector: 'nt-financier-form',
  imports: [Dialog],
  template: `
    <nt-dialog heading="Add financier" width="560px" [busy]="busy()" (closed)="closed.emit()">
      <form class="form" novalidate (submit)="$event.preventDefault(); submit()">
        <div class="grid">
          <div class="nt-field wide">
            <label for="ff-name">Business name</label>
            <input id="ff-name" maxlength="150" autocomplete="off" [value]="name()" (input)="name.set($any($event.target).value)" [attr.aria-invalid]="!!shown()['name']" />
            @if (shown()['name']; as e) { <span class="err">{{ e }}</span> }
          </div>
          <div class="nt-field">
            <label for="ff-owner">Owner name</label>
            <input id="ff-owner" maxlength="100" autocomplete="off" [value]="owner()" (input)="owner.set($any($event.target).value)" [attr.aria-invalid]="!!shown()['owner']" />
            @if (shown()['owner']; as e) { <span class="err">{{ e }}</span> }
          </div>
          <div class="nt-field">
            <label for="ff-phone">Owner mobile</label>
            <input id="ff-phone" inputmode="numeric" autocomplete="off" maxlength="14" [value]="phone()" (input)="phone.set($any($event.target).value)" [attr.aria-invalid]="!!shown()['phone']" />
            @if (shown()['phone']; as e) { <span class="err">{{ e }}</span> } @else { <span class="hint">The owner logs in with this number.</span> }
          </div>
          <div class="nt-field">
            <label for="ff-pin">Owner's first PIN</label>
            <input id="ff-pin" type="password" inputmode="numeric" autocomplete="new-password" maxlength="6" [value]="pin()" (input)="pin.set($any($event.target).value)" [attr.aria-invalid]="!!shown()['pin']" />
            @if (shown()['pin']; as e) { <span class="err">{{ e }}</span> } @else { <span class="hint">4–6 digits; they can change it in Settings.</span> }
          </div>
          <div class="nt-field">
            <label for="ff-rate">Maximum interest (% per year)</label>
            <input id="ff-rate" inputmode="decimal" autocomplete="off" [value]="rate()" (input)="rate.set($any($event.target).value)" [attr.aria-invalid]="!!shown()['rate']" />
            @if (shown()['rate']; as e) { <span class="err">{{ e }}</span> }
          </div>
          <div class="nt-field wide">
            <label for="ff-lic">Money-lending licence <span class="opt">(optional)</span></label>
            <input id="ff-lic" maxlength="50" autocomplete="off" [value]="licence()" (input)="licence.set($any($event.target).value)" />
          </div>
          <fieldset class="seg wide">
            <legend>Plan</legend>
            <div class="seg-row">
              @for (p of plans; track p) {
                <button type="button" role="radio" [attr.aria-checked]="plan() === p" [class.on]="plan() === p" (click)="plan.set(p)">{{ label(p) }}</button>
              }
            </div>
            <span class="hint">Ends {{ endsText() }}.</span>
          </fieldset>
        </div>
        @if (error()) { <p class="nt-error-text small" role="alert">{{ error() }}</p> }
        <div class="actions">
          <button type="button" class="nt-btn nt-btn--outline" [disabled]="busy()" (click)="dialog().close()">Cancel</button>
          <button type="submit" class="nt-btn" [disabled]="busy()">{{ busy() ? 'Adding…' : 'Add financier' }}</button>
        </div>
      </form>
    </nt-dialog>
  `,
  styles: `
    .form { display: flex; flex-direction: column; gap: 16px; }
    .grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 14px 12px; .wide { grid-column: 1 / -1; } }
    @media (max-width: 560px) { .grid { grid-template-columns: 1fr; } }
    .nt-field { gap: 6px; input { height: 46px; font-size: 15px; } }
    .err { font: 600 12px var(--nt-font); color: var(--nt-error); }
    .hint, .opt { font: 500 12px var(--nt-font); color: var(--nt-muted); }
    .seg { margin: 0; padding: 0; border: 0; display: flex; flex-direction: column; gap: 6px; legend { font: 600 14px var(--nt-font); margin-bottom: 6px; padding: 0; } }
    .seg-row { display: flex; gap: 6px; }
    .seg-row button {
      flex: 1; height: 40px; border: 1.5px solid var(--nt-border); border-radius: 10px; background: var(--nt-surface);
      font: 600 13px var(--nt-font); color: var(--nt-ink); cursor: pointer;
      &.on { border-color: var(--nt-brand); background: var(--nt-brand-tint); color: var(--nt-brand); }
    }
    .actions { display: flex; justify-content: flex-end; gap: 10px; }
    p { margin: 0; }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FinancierForm {
  private readonly http = inject(HttpClient);

  readonly saved = output<number>();
  readonly closed = output<void>();
  readonly dialog = viewChild.required(Dialog);

  readonly plans: Plan[] = ['TRIAL', 'MONTHLY', 'YEARLY'];
  readonly name = signal('');
  readonly owner = signal('');
  readonly phone = signal('');
  readonly pin = signal('');
  readonly rate = signal('');
  readonly licence = signal('');
  readonly plan = signal<Plan>('TRIAL');
  readonly touched = signal(false);
  readonly busy = signal(false);
  readonly error = signal('');

  label(p: Plan): string { return PLAN_LABEL[p]; }

  readonly endsText = computed(() =>
    new Date(`${renewedExpiry(this.plan(), null, todayIso())}T00:00:00`)
      .toLocaleDateString('en-IN', { day: 'numeric', month: 'short', year: 'numeric' }));

  readonly errors = computed(() => {
    const e: Record<string, string> = {};
    if (!this.name().trim()) e['name'] = 'Enter the business name.';
    if (!this.owner().trim()) e['owner'] = "Enter the owner's name.";
    if (!PHONE.test(digits(this.phone()))) e['phone'] = 'Enter a 10-digit mobile number.';
    if (!PIN.test(this.pin())) e['pin'] = 'PIN must be 4 to 6 digits.';
    const r = Money.tryParse(this.rate());
    if (!r || r.paise < 0 || r.paise > 99_999) e['rate'] = 'Between 0 and 999.99.';
    return e;
  });
  readonly shown = computed(() => (this.touched() ? this.errors() : {}));

  async submit(): Promise<void> {
    this.touched.set(true);
    if (Object.keys(this.errors()).length) return;
    this.busy.set(true);
    this.error.set('');
    try {
      const res = await firstValueFrom(this.http.post<{ id: number }>(`${API}/admin/tenants`, {
        name: this.name().trim(),
        ownerName: this.owner().trim(),
        phone: digits(this.phone()),
        ownerPin: this.pin(),
        maxInterestRate: Money.parse(this.rate()).toApi(),
        licenceNo: this.licence().trim() || null,
        plan: this.plan(),
      }));
      this.saved.emit(res.id);
      this.dialog().close();
    } catch (e) {
      this.error.set(apiErrorMessage(e, 'Could not add the financier.'));
    } finally {
      this.busy.set(false);
    }
  }
}
