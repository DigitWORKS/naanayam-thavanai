import { HttpClient } from '@angular/common/http';
import {
  ChangeDetectionStrategy, Component, computed, effect, inject, input, output, signal, untracked, viewChild,
} from '@angular/core';
import { firstValueFrom } from 'rxjs';

import { API } from '../../core/api';
import { apiErrorMessage } from '../../core/api-error';
import { Dialog } from '../../shared/dialog';
import { formatPhone } from '../../shared/format';
import { Plan, PLAN_LABEL, renewedExpiry, Tenant } from './tenant.models';

const longDate = (s: string) =>
  new Date(s.length === 10 ? `${s}T00:00:00` : s).toLocaleDateString('en-IN', { day: 'numeric', month: 'short', year: 'numeric' });
const todayIso = () => {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
};

/** Right-hand panel of Financiers: plan, expiry, licence, suspend / lift suspension. */
@Component({
  selector: 'nt-financier-panel',
  imports: [Dialog],
  templateUrl: './financier-panel.html',
  styleUrl: './financier-panel.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FinancierPanel {
  private readonly http = inject(HttpClient);

  readonly tenant = input.required<Tenant>();
  readonly saved = output<number>();
  readonly closed = output<void>();

  readonly plans: Plan[] = ['TRIAL', 'MONTHLY', 'YEARLY'];
  readonly today = todayIso();

  /** Plan to start / renew from today; null = leave the plan alone. */
  readonly plan = signal<Plan | null>(null);
  readonly expiry = signal('');
  readonly licence = signal('');
  readonly busy = signal(false);
  readonly error = signal('');
  readonly done = signal('');

  constructor() {
    // Another financier (or the same one reloaded after saving): reset the form to it.
    effect(() => {
      const t = this.tenant();
      untracked(() => {
        this.plan.set(null);
        this.expiry.set('');
        this.licence.set(t.licenceNo ?? '');
        this.error.set('');
      });
    });
  }

  readonly head = computed(() => {
    const t = this.tenant();
    return {
      origin: t.termsAcceptedAt
        ? `Self-registered · terms accepted ${new Date(t.termsAcceptedAt).toLocaleString('en-IN',
          { day: 'numeric', month: 'short', year: 'numeric', hour: 'numeric', minute: '2-digit' })}`
        : `Added by Super Admin · ${longDate(t.createdAt)}`,
      owner: `${t.ownerName} · ${formatPhone(t.phone)}`,
      suspended: t.status === 'SUSPENDED',
      current: `${t.plan ? PLAN_LABEL[t.plan] : 'No plan'}${t.planExpiry ? ` · ends ${longDate(t.planExpiry)}` : ''}`,
      expired: !!t.planExpiry && t.planExpiry < this.today,
    };
  });

  /** What saving will do to the expiry (plan first, then the override, as the server applies them). */
  readonly preview = computed(() => {
    const t = this.tenant();
    const p = this.plan();
    const override = this.expiry();
    if (!p && !override) return '';
    if (override) return `New expiry ${longDate(override)} (set directly)`;
    const from = t.planExpiry && t.planExpiry >= this.today ? `current expiry ${longDate(t.planExpiry)}` : 'today';
    return `${PLAN_LABEL[p!]} from ${from} → new expiry ${longDate(renewedExpiry(p!, t.planExpiry, this.today))}`;
  });

  readonly dirty = computed(() =>
    !!this.plan() || !!this.expiry() || this.licence().trim() !== (this.tenant().licenceNo ?? ''));

  async save(): Promise<void> {
    this.busy.set(true);
    this.error.set('');
    this.done.set('');
    try {
      const t = this.tenant();
      const body: Record<string, unknown> = {};
      if (this.plan()) body['plan'] = this.plan();
      if (this.expiry()) body['planExpiry'] = this.expiry();
      if (this.licence().trim() !== (t.licenceNo ?? '')) body['licenceNo'] = this.licence().trim();
      await firstValueFrom(this.http.patch(`${API}/admin/tenants/${t.id}`, body));
      this.done.set('Saved.');
      this.saved.emit(t.id);
    } catch (e) {
      this.error.set(apiErrorMessage(e, 'Could not save.'));
    } finally {
      this.busy.set(false);
    }
  }

  // ------------------------------------------------------------ suspend / lift

  readonly confirming = signal(false);
  readonly dialog = viewChild(Dialog);
  readonly statusBusy = signal(false);
  readonly statusError = signal('');

  async setSuspended(suspend: boolean): Promise<void> {
    this.statusBusy.set(true);
    this.statusError.set('');
    try {
      const t = this.tenant();
      // Any non-SUSPENDED value lifts a suspension; the server then sets the status from the plan.
      await firstValueFrom(this.http.patch(`${API}/admin/tenants/${t.id}`, { status: suspend ? 'SUSPENDED' : 'ACTIVE' }));
      this.dialog()?.close();
      this.saved.emit(t.id);
    } catch (e) {
      this.statusError.set(apiErrorMessage(e, 'Could not change the status.'));
    } finally {
      this.statusBusy.set(false);
    }
  }
}
