import { HttpClient, httpResource } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, effect, inject, signal, untracked } from '@angular/core';
import { firstValueFrom } from 'rxjs';

import { API } from '../../core/api';
import { apiErrorMessage } from '../../core/api-error';
import { AuthService } from '../../core/auth/auth.service';
import { Money } from '../../core/money';
import { safeValue } from '../../core/resource';
import { BusinessSummary } from '../../layout/business-summary';
import { formatPhone } from '../../shared/format';

type Method = 'FLAT' | 'REDUCING';
type PenaltyType = 'FIXED' | 'PERCENT';
type ChargeType = 'NONE' | 'FIXED' | 'PERCENT';

/** GET / PUT /settings */
interface SettingsDto {
  maxInterestRate: string;
  allowedInterestMethods: Method[];
  maxActiveLoansPerClient: number;
  penaltyEnabled: boolean;
  penaltyType: PenaltyType | null;
  penaltyValue: string | null;
  penaltyGraceDays: number;
  penaltyMaxCap: string | null;
  penaltyEffectiveFrom: string | null;
  recordRetentionYears: number;
  allocationOrder: 'PENALTY_FIRST' | 'INSTALLMENT_FIRST';
  advanceAllocation: 'NEXT_INSTALLMENTS' | 'LAST_INSTALLMENTS';
  preclosureEnabled: boolean;
  preclosureRebate: 'NONE' | 'UNEARNED_INTEREST';
  preclosureChargeType: ChargeType;
  preclosureChargeValue: string | null;
}

/** GET /settings/tenant */
interface TenantInfo {
  name: string;
  ownerName: string;
  phone: string;
  licenceNo: string | null;
  plan: 'TRIAL' | 'MONTHLY' | 'YEARLY' | null;
  planExpiry: string | null;
}

/** Editable form: numbers as typed text, so a half-typed value is not lost. */
interface Form {
  rate: string;
  methods: Method[];
  maxLoans: number;
  penaltyOn: boolean;
  penaltyType: PenaltyType;
  penaltyValue: string;
  grace: string;
  cap: string;
  order: SettingsDto['allocationOrder'];
  advance: SettingsDto['advanceAllocation'];
  preOn: boolean;
  rebate: boolean;
  chargeType: ChargeType;
  chargeValue: string;
}

const PLAN_LABEL = { TRIAL: 'Free trial', MONTHLY: 'Monthly', YEARLY: 'Yearly' } as const;
const DAY_MS = 86_400_000;
const plain = (v: string | null) => (v === null ? '' : String(Number(v)));
const longDate = (s: string) =>
  new Date(`${s}T00:00:00`).toLocaleDateString('en-IN', { day: 'numeric', month: 'short', year: 'numeric' });

function toForm(s: SettingsDto): Form {
  return {
    rate: plain(s.maxInterestRate),
    methods: [...s.allowedInterestMethods].sort(),
    maxLoans: s.maxActiveLoansPerClient,
    penaltyOn: s.penaltyEnabled,
    penaltyType: s.penaltyType ?? 'FIXED',
    penaltyValue: plain(s.penaltyValue),
    grace: String(s.penaltyGraceDays),
    cap: plain(s.penaltyMaxCap),
    order: s.allocationOrder,
    advance: s.advanceAllocation,
    preOn: s.preclosureEnabled,
    rebate: s.preclosureRebate === 'UNEARNED_INTEREST',
    chargeType: s.preclosureChargeType,
    chargeValue: plain(s.preclosureChargeValue),
  };
}

/** Settings (design board "Web settings"), plus changing your own PIN. */
@Component({
  selector: 'nt-settings',
  templateUrl: './settings.html',
  host: { '(window:beforeunload)': 'onUnload($event)' },
  styleUrl: './settings.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SettingsPage {
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthService);
  private readonly summary = inject(BusinessSummary);

  private readonly settingsRes = httpResource<SettingsDto>(() => `${API}/settings`);
  private readonly tenantRes = httpResource<TenantInfo>(() => `${API}/settings/tenant`);

  readonly loadError = computed(() => (this.settingsRes.error() ? apiErrorMessage(this.settingsRes.error()) : ''));
  readonly saved = computed(() => safeValue(this.settingsRes));
  readonly form = signal<Form | null>(null);

  constructor() {
    // Load (and reload after save) into the form.
    effect(() => {
      const s = this.saved();
      if (s) untracked(() => this.form.set(toForm(s)));
    });
  }

  readonly dirty = computed(() => {
    const f = this.form();
    const s = this.saved();
    return !!f && !!s && JSON.stringify(f) !== JSON.stringify(toForm(s));
  });

  /** Closing or reloading the tab with unsaved changes: let the browser ask. */
  onUnload(e: BeforeUnloadEvent): void {
    if (this.dirty()) e.preventDefault();
  }

  /** Called by the route's canDeactivate. */
  canLeave(): boolean {
    return !this.dirty() || window.confirm('Leave without saving your settings changes?');
  }

  set<K extends keyof Form>(key: K, value: Form[K]): void {
    this.form.update((f) => (f ? { ...f, [key]: value } : f));
    this.saveMessage.set('');
  }

  toggleMethod(m: Method, on: boolean): void {
    const f = this.form();
    if (!f) return;
    this.set('methods', (on ? [...f.methods, m] : f.methods.filter((x) => x !== m)).sort());
  }

  // ------------------------------------------------------------ plan card

  readonly plan = computed(() => {
    const t = safeValue(this.tenantRes);
    if (!t) return null;
    const left = t.planExpiry
      ? Math.max(0, Math.ceil((new Date(`${t.planExpiry}T00:00:00`).getTime() - Date.now()) / DAY_MS))
      : null;
    return {
      label: t.plan ? PLAN_LABEL[t.plan] : 'No plan',
      ends: t.planExpiry ? `Ends ${longDate(t.planExpiry)}${left !== null ? ` · ${left} ${left === 1 ? 'day' : 'days'} left` : ''}` : '',
      business: t.name,
      owner: `${t.ownerName} · ${formatPhone(t.phone)}`,
      licence: t.licenceNo || 'Not recorded',
    };
  });
  readonly retention = computed(() => {
    const y = this.saved()?.recordRetentionYears;
    return y ? `${y} ${y === 1 ? 'year' : 'years'}` : '';
  });

  // ------------------------------------------------------------ validation + save

  readonly errors = computed(() => {
    const f = this.form();
    const e: Record<string, string> = {};
    if (!f) return e;
    const rate = Money.tryParse(f.rate);
    if (!rate || rate.paise < 0 || rate.paise > 99_999) e['rate'] = 'Between 0 and 999.99.';
    if (!f.methods.length) e['methods'] = 'Allow at least one method.';
    if (f.penaltyOn) {
      const v = Money.tryParse(f.penaltyValue);
      if (!v || !v.isPositive) e['penaltyValue'] = 'Enter an amount above 0.';
      else if (f.penaltyType === 'PERCENT' && v.paise > 10_000) e['penaltyValue'] = 'At most 100%.';
      if (!/^\d{1,3}$/.test(f.grace.trim())) e['grace'] = 'Whole days, 0 or more.';
      if (f.cap.trim() && !Money.tryParse(f.cap)) e['cap'] = 'Enter an amount, or leave empty for no limit.';
    }
    if (f.preOn && f.chargeType !== 'NONE') {
      const v = Money.tryParse(f.chargeValue);
      if (!v || !v.isPositive) e['chargeValue'] = 'Enter a value above 0.';
      else if (f.chargeType === 'PERCENT' && v.paise > 10_000) e['chargeValue'] = 'At most 100%.';
    }
    return e;
  });
  readonly touched = signal(false);
  readonly shown = computed(() => (this.touched() ? this.errors() : {}));

  readonly penaltyNote = computed(() => {
    const f = this.form();
    const s = this.saved();
    if (!f?.penaltyOn) return '';
    const from = s?.penaltyEnabled && s.penaltyEffectiveFrom ? longDate(s.penaltyEffectiveFrom) : 'today';
    return `Charged once per missed day, only if still unpaid after the grace days. Applies to days missed from ${from}.`;
  });

  readonly saving = signal(false);
  readonly saveError = signal('');
  readonly saveMessage = signal('');

  async save(): Promise<void> {
    this.touched.set(true);
    const f = this.form();
    const s = this.saved();
    if (!f || !s || Object.keys(this.errors()).length) return;
    const money = (v: string) => Money.parse(v).toApi();
    const body: SettingsDto = {
      ...s,
      maxInterestRate: money(f.rate),
      allowedInterestMethods: f.methods,
      maxActiveLoansPerClient: f.maxLoans,
      penaltyEnabled: f.penaltyOn,
      penaltyType: f.penaltyOn ? f.penaltyType : s.penaltyType,
      penaltyValue: f.penaltyOn ? money(f.penaltyValue) : s.penaltyValue,
      penaltyGraceDays: f.penaltyOn ? Number(f.grace.trim()) : s.penaltyGraceDays,
      penaltyMaxCap: f.penaltyOn ? (f.cap.trim() ? money(f.cap) : null) : s.penaltyMaxCap,
      allocationOrder: f.order,
      advanceAllocation: f.advance,
      preclosureEnabled: f.preOn,
      preclosureRebate: f.rebate ? 'UNEARNED_INTEREST' : 'NONE',
      preclosureChargeType: f.chargeType,
      preclosureChargeValue: f.chargeType === 'NONE' ? null : money(f.chargeValue),
    };
    this.saving.set(true);
    this.saveError.set('');
    try {
      await firstValueFrom(this.http.put(`${API}/settings`, body));
      this.settingsRes.reload();
      this.summary.reload();
      this.touched.set(false);
      this.saveMessage.set('Settings saved. They apply to new loans and to days from today.');
    } catch (e) {
      this.saveError.set(apiErrorMessage(e, 'Could not save the settings.'));
    } finally {
      this.saving.set(false);
    }
  }

  discard(): void {
    const s = this.saved();
    if (s) this.form.set(toForm(s));
    this.touched.set(false);
    this.saveError.set('');
  }

  // ------------------------------------------------------------ your PIN

  readonly curPin = signal('');
  readonly newPin = signal('');
  readonly newPin2 = signal('');
  readonly pinBusy = signal(false);
  readonly pinError = signal('');
  readonly pinDone = signal(false);

  async changePin(): Promise<void> {
    this.pinDone.set(false);
    if (!/^\d{4,6}$/.test(this.newPin())) return this.pinError.set('New PIN must be 4 to 6 digits.');
    if (this.newPin() !== this.newPin2()) return this.pinError.set('The two new PINs do not match.');
    if (!this.curPin()) return this.pinError.set('Enter your current PIN.');
    this.pinBusy.set(true);
    this.pinError.set('');
    try {
      await this.auth.changePin(this.curPin(), this.newPin());
      this.curPin.set('');
      this.newPin.set('');
      this.newPin2.set('');
      this.pinDone.set(true);
    } catch (e) {
      this.pinError.set(apiErrorMessage(e, 'Could not change the PIN.'));
    } finally {
      this.pinBusy.set(false);
    }
  }
}
