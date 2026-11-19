import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import {
  ChangeDetectionStrategy, Component, computed, inject, input, OnInit, output, signal, viewChild,
} from '@angular/core';
import { firstValueFrom } from 'rxjs';

import { API } from '../../core/api';
import { apiErrorCode, apiErrorMessage } from '../../core/api-error';
import { Dialog } from '../../shared/dialog';
import type { Area } from '../loans/loan.models';
import {
  ClientDetail, CONSENT_TEXT_EN, CONSENT_TEXT_TA, CONSENT_VERSION, ExistingClient,
} from './client.models';

/** Same rule as the server (UserDtos.PHONE): 10 digits starting 6–9. */
const PHONE = /^[6-9]\d{9}$/;
const digits = (s: string) => s.replace(/\D/g, '');

/** New client, or edit one when [client] is set (phone and Aadhaar are fixed after creation). */
@Component({
  selector: 'nt-client-form',
  imports: [Dialog],
  templateUrl: './client-form.html',
  styleUrl: './client-form.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ClientForm implements OnInit {
  private readonly http = inject(HttpClient);

  readonly areas = input.required<Area[]>();
  readonly client = input<ClientDetail | null>(null);
  readonly saved = output<number>();
  readonly closed = output<void>();
  /** "Open existing client" after a duplicate phone. */
  readonly openExisting = output<number>();

  readonly dialog = viewChild.required(Dialog);
  readonly consentEn = CONSENT_TEXT_EN;
  readonly consentTa = CONSENT_TEXT_TA;

  readonly areaId = signal<number | null>(null);
  readonly name = signal('');
  readonly phone = signal('');
  readonly altPhone = signal('');
  readonly address = signal('');
  readonly aadhaar = signal('');
  readonly guarantorName = signal('');
  readonly guarantorPhone = signal('');
  readonly consent = signal(false);
  readonly active = signal(true);

  readonly touched = signal(false);
  readonly busy = signal(false);
  readonly error = signal('');
  readonly existing = signal<ExistingClient | null>(null);

  readonly isEdit = computed(() => !!this.client());
  /** Active areas, plus the client's current one if it was deactivated. */
  readonly areaOptions = computed(() =>
    this.areas().filter((a) => a.isActive || a.id === this.client()?.areaId));

  ngOnInit(): void {
    const c = this.client();
    if (c) {
      this.areaId.set(c.areaId);
      this.name.set(c.name);
      this.altPhone.set(c.altPhone ?? '');
      this.address.set(c.address ?? '');
      this.guarantorName.set(c.guarantorName ?? '');
      this.guarantorPhone.set(c.guarantorPhone ?? '');
      this.active.set(c.isActive);
    } else {
      const active = this.areas().filter((a) => a.isActive);
      if (active.length === 1) this.areaId.set(active[0].id);
    }
  }

  readonly errors = computed(() => {
    const e: Record<string, string> = {};
    if (this.areaId() === null) e['area'] = 'Pick an area.';
    if (!this.name().trim()) e['name'] = 'Enter the name.';
    if (!this.isEdit() && !PHONE.test(digits(this.phone()))) e['phone'] = 'Enter a 10-digit mobile number.';
    if (this.altPhone().trim() && !PHONE.test(digits(this.altPhone()))) e['altPhone'] = 'Enter a 10-digit mobile number, or leave it empty.';
    if (this.guarantorPhone().trim() && !PHONE.test(digits(this.guarantorPhone()))) e['guarantorPhone'] = 'Enter a 10-digit mobile number, or leave it empty.';
    if (!this.isEdit() && this.aadhaar().trim() && !/^\d{4}$/.test(this.aadhaar().trim())) e['aadhaar'] = 'Only the last 4 digits.';
    if (!this.isEdit() && !this.consent()) e['consent'] = "The client's consent is needed to save their details.";
    return e;
  });
  readonly shown = computed(() => (this.touched() ? this.errors() : {}));

  async submit(): Promise<void> {
    this.touched.set(true);
    this.existing.set(null);
    if (Object.keys(this.errors()).length) return;
    this.busy.set(true);
    this.error.set('');
    const opt = (s: string) => s.trim() || null;
    try {
      const c = this.client();
      const res = c
        ? await firstValueFrom(this.http.patch<{ id: number }>(`${API}/clients/${c.id}`, {
          areaId: this.areaId(),
          name: this.name().trim(),
          // "" clears an optional field on PATCH.
          altPhone: digits(this.altPhone()),
          address: this.address().trim(),
          guarantorName: this.guarantorName().trim(),
          guarantorPhone: digits(this.guarantorPhone()),
          isActive: this.active(),
        }))
        : await firstValueFrom(this.http.post<{ id: number }>(`${API}/clients`, {
          areaId: this.areaId(),
          name: this.name().trim(),
          phone: digits(this.phone()),
          altPhone: opt(digits(this.altPhone())),
          address: opt(this.address()),
          aadhaarLast4: opt(this.aadhaar()),
          guarantorName: opt(this.guarantorName()),
          guarantorPhone: opt(digits(this.guarantorPhone())),
          consentGiven: true,
          consentVersion: CONSENT_VERSION,
        }));
      this.saved.emit(res.id);
      this.dialog().close();
    } catch (e) {
      if (apiErrorCode(e) === 'CLIENT_EXISTS') {
        this.existing.set((e as HttpErrorResponse).error.details as ExistingClient);
      } else {
        this.error.set(apiErrorMessage(e, 'Could not save. Try again.'));
      }
    } finally {
      this.busy.set(false);
    }
  }

  goToExisting(id: number): void {
    this.openExisting.emit(id);
    this.dialog().close();
  }
}
