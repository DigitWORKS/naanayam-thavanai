import { HttpClient, httpResource } from '@angular/common/http';
import {
  ChangeDetectionStrategy, Component, computed, DestroyRef, effect, inject, input, numberAttribute, signal,
  untracked, viewChild,
} from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';

import { API } from '../../core/api';
import { apiErrorCode, apiErrorMessage } from '../../core/api-error';
import { Money } from '../../core/money';
import { BusinessSummary } from '../../layout/business-summary';
import { Dialog } from '../../shared/dialog';
import { formatPhone } from '../../shared/format';
import type { ClientDetail, ClientListItem } from '../clients/client.models';
import { Frequency, FREQUENCY_LABEL, FREQUENCY_UNIT, Page } from './loan.models';
import { safeValue } from '../../core/resource';

type Method = 'FLAT' | 'REDUCING';
type Collection = 'ADDED_TO_REPAYMENT' | 'UPFRONT_DEDUCTED';

/** GET /settings — the loan rules the form enforces before the server does. */
interface LoanRules {
  maxInterestRate: string;
  allowedInterestMethods: Method[];
  maxActiveLoansPerClient: number;
}

/** POST /loans/preview */
interface Preview {
  interestAmount: string;
  disbursedAmount: string;
  totalPayable: string;
  installmentAmount: string;
  endDate: string;
  installments: { seqNo: number; dueDate: string; dueAmount: string; principalBalanceAfter: string }[];
}

interface LoanBody {
  clientId: number;
  principal: string;
  interestRate: string;
  interestMethod: Method;
  interestCollection: Collection;
  frequency: Frequency;
  tenureCount: number;
  startDate: string;
}

const m = (v: string | null | undefined) => Money.fromJson(v);
const iso = (d: Date) =>
  `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
const addDays = (d: Date, n: number) => new Date(d.getFullYear(), d.getMonth(), d.getDate() + n);
const longDate = (s: string) =>
  new Date(`${s}T00:00:00`).toLocaleDateString('en-IN', { day: 'numeric', month: 'short', year: 'numeric' });
const rowDate = (s: string) =>
  new Date(`${s}T00:00:00`).toLocaleDateString('en-IN', { weekday: 'short', day: 'numeric', month: 'short' });

/** Start date may be up to 30 days either side of today (same as the mobile app). */
const START_WINDOW_DAYS = 30;
const PREVIEW_HEAD = 5;
const PREVIEW_TAIL = 2;

/** Give a loan (no web design board: the mobile "New loan" screen laid out for a wide page). */
@Component({
  selector: 'nt-new-loan',
  imports: [RouterLink, Dialog],
  templateUrl: './new-loan.html',
  styleUrl: './new-loan.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class NewLoanPage {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly summary = inject(BusinessSummary);

  /** ?clientId= preselects the client (from the client panel). */
  readonly clientIdParam = input(null, {
    alias: 'clientId', transform: (v: unknown) => (v ? numberAttribute(v) : null),
  });

  readonly frequencies = Object.entries(FREQUENCY_LABEL) as [Frequency, string][];
  readonly today = iso(new Date());
  readonly minStart = iso(addDays(new Date(), -START_WINDOW_DAYS));
  readonly maxStart = iso(addDays(new Date(), START_WINDOW_DAYS));

  // ------------------------------------------------------------ form state

  readonly clientId = signal<number | null>(null);
  readonly principal = signal('');
  readonly rate = signal('');
  readonly method = signal<Method>('FLAT');
  readonly collection = signal<Collection>('ADDED_TO_REPAYMENT');
  readonly frequency = signal<Frequency>('DAILY');
  readonly tenure = signal('100');
  readonly startDate = signal(this.today);

  private readonly rulesRes = httpResource<LoanRules>(() => `${API}/settings`);
  readonly rules = computed(() => safeValue(this.rulesRes));
  readonly maxRateText = computed(() => {
    const r = this.rules()?.maxInterestRate;
    return r ? String(Number(r)) : '';
  });

  constructor() {
    effect(() => {
      const id = this.clientIdParam();
      if (id) untracked(() => this.clientId.set(id));
    });
    // A method the business does not allow cannot stay selected.
    effect(() => {
      const allowed = this.rules()?.allowedInterestMethods;
      if (allowed?.length && !allowed.includes(untracked(this.method))) this.method.set(allowed[0]);
    });
    inject(DestroyRef).onDestroy(() => {
      clearTimeout(this.searchTimer);
      clearTimeout(this.previewTimer);
    });
  }

  // ------------------------------------------------------------ client picker

  private readonly clientRes = httpResource<ClientDetail>(() =>
    this.clientId() ? `${API}/clients/${this.clientId()}` : undefined);
  readonly client = computed(() => (this.clientId() ? safeValue(this.clientRes) : undefined));
  readonly clientLoading = computed(() => !!this.clientId() && this.clientRes.isLoading() && !this.client());
  readonly clientError = computed(() =>
    this.clientId() && this.clientRes.error()
      ? apiErrorCode(this.clientRes.error()) === 'NOT_FOUND'
        ? 'This client was not found.'
        : apiErrorMessage(this.clientRes.error(), 'Could not load the client.')
      : '');

  readonly clientCheck = computed(() => {
    const c = this.client();
    const max = this.rules()?.maxActiveLoansPerClient;
    if (!c) return null;
    const count = c.activeLoans.length;
    return {
      name: c.name,
      line: [formatPhone(c.phone), c.areaName].join(' · '),
      loansText: `${count} active ${count === 1 ? 'loan' : 'loans'}${max ? ` of ${max} allowed` : ''}`,
      blocker: !c.isActive
        ? 'This client is inactive. Make them active from Clients to give a loan.'
        : max !== undefined && count >= max
          ? `This client already has ${count} active ${count === 1 ? 'loan' : 'loans'}, the limit set in Settings.`
          : '',
    };
  });

  readonly search = signal('');
  private readonly searchQuery = signal('');
  private searchTimer: ReturnType<typeof setTimeout> | undefined;
  private readonly searchRes = httpResource<Page<ClientListItem>>(() =>
    this.searchQuery()
      ? { url: `${API}/clients`, params: { q: this.searchQuery(), size: 8 } }
      : undefined);
  readonly results = computed(() =>
    this.searchQuery() ? (safeValue(this.searchRes)?.content ?? []).map((c) => ({
      id: c.id,
      name: c.name,
      line: [formatPhone(c.phone), c.areaName, c.isActive ? '' : 'inactive'].filter(Boolean).join(' · '),
    })) : []);
  readonly searching = computed(() => !!this.searchQuery() && this.searchRes.isLoading());

  onSearch(text: string): void {
    this.search.set(text);
    clearTimeout(this.searchTimer);
    this.searchTimer = setTimeout(() => this.searchQuery.set(text.trim()), 300);
  }

  pickClient(id: number): void {
    this.clientId.set(id);
    this.search.set('');
    this.searchQuery.set('');
  }

  changeClient(): void {
    this.clientId.set(null);
  }

  // ------------------------------------------------------------ validation

  readonly touched = signal(false);

  readonly fieldErrors = computed(() => {
    const e: Record<string, string> = {};
    const p = Money.tryParse(this.principal());
    if (!p || !p.isPositive) e['principal'] = 'Enter the loan amount.';
    const r = Money.tryParse(this.rate());
    const max = this.rules() ? Money.fromJson(this.rules()!.maxInterestRate) : null;
    if (!r || r.paise < 0) e['rate'] = 'Enter the interest rate per year.';
    else if (max && r.paise > max.paise) e['rate'] = `Above your maximum of ${max.toApi().replace(/\.00$/, '')}% a year.`;
    const t = Number(this.tenure());
    if (!Number.isInteger(t) || t < 1 || t > 1000) e['tenure'] = 'Between 1 and 1000.';
    const s = this.startDate();
    if (!s || s < this.minStart || s > this.maxStart) e['start'] = 'Within 30 days of today.';
    return e;
  });
  readonly shown = computed(() => (this.touched() ? this.fieldErrors() : {}));

  /** The request body, or null while anything is missing or invalid. */
  readonly body = computed<LoanBody | null>(() => {
    const id = this.clientId();
    if (!id || Object.keys(this.fieldErrors()).length) return null;
    return {
      clientId: id,
      principal: Money.parse(this.principal()).toApi(),
      interestRate: Money.parse(this.rate()).toApi(),
      interestMethod: this.method(),
      interestCollection: this.collection(),
      frequency: this.frequency(),
      tenureCount: Number(this.tenure()),
      startDate: this.startDate(),
    };
  });

  // ------------------------------------------------------------ preview (debounced)

  private readonly previewBody = signal<LoanBody | null>(null);
  private previewTimer: ReturnType<typeof setTimeout> | undefined;
  private readonly debounce = effect(() => {
    const b = this.body();
    clearTimeout(this.previewTimer);
    this.previewTimer = setTimeout(() => this.previewBody.set(b), 400);
  });

  private readonly previewRes = httpResource<Preview>(() => {
    const b = this.previewBody();
    return b ? { url: `${API}/loans/preview`, method: 'POST', body: b } : undefined;
  });
  /** The preview matches the form only when the debounced body is the current one. */
  readonly previewCurrent = computed(() =>
    JSON.stringify(this.previewBody()) === JSON.stringify(this.body()) && !this.previewRes.isLoading());
  readonly previewError = computed(() =>
    this.previewRes.error() && this.previewBody() ? apiErrorMessage(this.previewRes.error()) : '');
  readonly showAllRows = signal(false);

  readonly preview = computed(() => {
    const p = this.previewBody() ? safeValue(this.previewRes) : undefined;
    if (!p) return null;
    const freq = this.previewBody()!.frequency;
    const rows = p.installments.map((i) => ({
      seq: i.seqNo, date: rowDate(i.dueDate), due: m(i.dueAmount).format(),
    }));
    const collapse = !this.showAllRows() && rows.length > PREVIEW_HEAD + PREVIEW_TAIL + 1;
    return {
      give: m(p.disbursedAmount).format(),
      interest: m(p.interestAmount).format(),
      total: m(p.totalPayable).format(),
      installment: `${FREQUENCY_LABEL[freq]} ${m(p.installmentAmount).format()}`,
      count: `${rows.length} ${FREQUENCY_UNIT[freq][rows.length === 1 ? 0 : 1]}`,
      ends: longDate(p.endDate),
      head: collapse ? rows.slice(0, PREVIEW_HEAD) : rows,
      hidden: collapse ? rows.length - PREVIEW_HEAD - PREVIEW_TAIL : 0,
      tail: collapse ? rows.slice(-PREVIEW_TAIL) : [],
    };
  });

  readonly canCreate = computed(() =>
    !!this.body() && !!this.client() && !!this.preview() && this.previewCurrent() && !this.clientCheck()?.blocker
    && !this.busy());

  // ------------------------------------------------------------ create

  readonly confirming = signal(false);
  readonly busy = signal(false);
  readonly error = signal('');
  readonly confirmDialog = viewChild(Dialog);

  review(): void {
    this.touched.set(true);
    if (this.canCreate()) this.confirming.set(true);
  }

  async create(): Promise<void> {
    const b = this.body();
    if (!b) return;
    this.busy.set(true);
    this.error.set('');
    try {
      const loan = await firstValueFrom(this.http.post<{ id: number }>(`${API}/loans`, b));
      this.summary.reload();
      this.confirmDialog()?.close();
      await this.router.navigate(['/loans', loan.id]);
    } catch (e) {
      this.error.set(apiErrorMessage(e, 'Could not create the loan. Try again.'));
    } finally {
      this.busy.set(false);
    }
  }
}
