import { HttpClient, httpResource } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, inject, input, output, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';

import { API } from '../../core/api';
import { apiErrorCode, apiErrorMessage } from '../../core/api-error';
import { Money } from '../../core/money';
import { BusinessSummary } from '../../layout/business-summary';
import { formatPhone } from '../../shared/format';
import type { Area } from '../loans/loan.models';
import { FREQUENCY_LABEL } from '../loans/loan.models';
import { ClientForm } from './client-form';
import { ClientDetail, ClientDocument, DOC_LABEL, SettingsLite } from './client.models';

const m = (v: string | null | undefined) => Money.fromJson(v);
const shortDate = (iso: string) =>
  new Date(`${iso}T00:00:00`).toLocaleDateString('en-IN', { day: 'numeric', month: 'short' });
const dateOf = (iso: string, opts: Intl.DateTimeFormatOptions) => new Date(iso).toLocaleDateString('en-IN', opts);

/** The selected client on the Clients page (right-hand panel of "Web clients"). */
@Component({
  selector: 'nt-client-panel',
  imports: [RouterLink, ClientForm],
  templateUrl: './client-panel.html',
  styleUrl: './client-panel.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ClientPanel {
  private readonly http = inject(HttpClient);
  private readonly summary = inject(BusinessSummary);

  readonly clientId = input.required<number>();
  readonly saved = output<number>();
  readonly closed = output<void>();

  private readonly clientRes = httpResource<ClientDetail>(() => `${API}/clients/${this.clientId()}`);
  private readonly docsRes = httpResource<ClientDocument[]>(() => `${API}/clients/${this.clientId()}/documents`);
  private readonly settingsRes = httpResource<SettingsLite>(() => `${API}/settings`);
  private readonly areasRes = httpResource<Area[]>(() => `${API}/areas`);

  readonly areas = computed(() => this.areasRes.value() ?? []);
  readonly client = computed(() => this.clientRes.value());
  readonly notFound = computed(() => apiErrorCode(this.clientRes.error()) === 'NOT_FOUND');
  readonly loadError = computed(() => (this.clientRes.error() ? apiErrorMessage(this.clientRes.error()) : ''));
  /** Keeps the old client on screen (dimmed) while the next one loads. */
  readonly switching = computed(() => this.clientRes.isLoading() && this.client()?.id !== this.clientId());

  readonly editing = signal(false);

  readonly view = computed(() => {
    const c = this.client();
    if (!c) return null;
    const max = this.settingsRes.value()?.maxActiveLoansPerClient;
    const count = c.activeLoans.length;
    const initials = c.name.trim().split(/\s+/).slice(0, 2).map((w) => w[0]?.toUpperCase() ?? '').join('');
    return {
      initials,
      since: c.createdAt ? `client since ${dateOf(c.createdAt, { month: 'short', year: 'numeric' })}` : '',
      phone: formatPhone(c.phone),
      tel: `tel:+91${c.phone}`,
      whatsapp: `https://wa.me/91${c.phone}`,
      outstanding: m(c.totalOutstanding).format(),
      loans: c.activeLoans.map((l) => {
        const behind = m(l.overdueAmount);
        return {
          id: l.id,
          title: `${l.loanNo} · ${m(l.principal).format()}`,
          tag: behind.isPositive ? `${behind.format()} behind` : 'On track',
          tagClass: behind.isPositive ? 'nt-tag--warn' : 'nt-tag--ok',
          pct: l.installmentCount ? Math.round((l.paidCount / l.installmentCount) * 100) : 0,
          line: `${FREQUENCY_LABEL[l.frequency]} ${m(l.installmentAmount).format()} · ${m(l.outstanding).format()} left · ends ${shortDate(l.endDate)}`,
        };
      }),
      limitText: max === undefined ? '' : `${count} of ${max} active ${max === 1 ? 'loan' : 'loans'}${count >= max ? ' · limit reached' : ''}`,
      limitReached: max !== undefined && count >= max,
      canLend: c.isActive && (max === undefined || count < max),
      details: [
        { label: 'Alt. mobile', value: c.altPhone ? formatPhone(c.altPhone) : '' },
        { label: 'Address', value: c.address ?? '' },
        { label: 'Aadhaar', value: c.aadhaarLast4 ? `XXXX XXXX ${c.aadhaarLast4}` : '' },
        {
          label: 'Guarantor',
          value: [c.guarantorName, c.guarantorPhone ? formatPhone(c.guarantorPhone) : ''].filter(Boolean).join(' · '),
        },
        {
          label: 'Consent',
          value: c.consentGivenAt
            ? `Given ${dateOf(c.consentGivenAt, { day: 'numeric', month: 'short', year: 'numeric' })}${c.consentVersion ? ' · ' + c.consentVersion : ''}`
            : 'Not recorded',
        },
      ].filter((d) => d.value),
    };
  });

  readonly docs = computed(() =>
    (this.docsRes.value() ?? []).map((d) => ({
      source: d,
      label: DOC_LABEL[d.docType],
      date: dateOf(d.uploadedAt, { day: 'numeric', month: 'short', year: 'numeric' }),
    })));
  readonly docsError = computed(() => !!this.docsRes.error());
  readonly opening = signal<number | null>(null);
  readonly docError = signal('');

  /** Documents need the bearer token, so they are fetched as a blob and opened from memory. */
  async openDoc(d: ClientDocument): Promise<void> {
    // Open the tab now (inside the click) so pop-up blockers allow it, then point it at the file.
    const tab = window.open('', '_blank');
    this.opening.set(d.id);
    this.docError.set('');
    try {
      const blob = await firstValueFrom(this.http.get(d.url, { responseType: 'blob' }));
      const url = URL.createObjectURL(blob);
      if (tab) tab.location.href = url;
      else window.location.assign(url);
      setTimeout(() => URL.revokeObjectURL(url), 60_000);
    } catch (e) {
      tab?.close();
      this.docError.set(apiErrorMessage(e, 'Could not open the document.'));
    } finally {
      this.opening.set(null);
    }
  }

  onEdited(id: number): void {
    this.clientRes.reload();
    this.summary.reload();
    this.saved.emit(id);
  }

  retry(): void {
    this.clientRes.reload();
    this.docsRes.reload();
  }
}
