import { HttpErrorResponse } from '@angular/common/http';
import {
  afterNextRender, ChangeDetectionStrategy, Component, computed, ElementRef, input, output, signal, viewChild,
} from '@angular/core';

import { Money } from '../../core/money';
import type { Handover, HandoverStatus } from './dashboard.models';

type Decide = (id: number, status: Exclude<HandoverStatus, 'PENDING'>, remarks: string | null) => Promise<void>;

/** Modal to confirm or dispute a handover whose amount differs from the cash collected. */
@Component({
  selector: 'nt-handover-review',
  template: `
    <dialog #dlg (close)="closed.emit()" (cancel)="busy() && $event.preventDefault()" aria-labelledby="hr-title">
      <h2 id="hr-title">{{ handover().agentName }}'s cash · {{ dateText() }}</h2>

      <dl class="amounts">
        <dt>Cash collected</dt><dd>{{ expected().format() }}</dd>
        <dt>Handed over</dt><dd>{{ handed().format() }}</dd>
        <dt>Difference</dt><dd [class.warn]="!diff().isZero">{{ diffText() }}</dd>
      </dl>

      @if (handover().remarks; as note) {
        <p class="note"><span>Agent's note</span>“{{ note }}”</p>
      }

      <div class="nt-field">
        <label for="hr-remarks">Your note <span class="opt">(needed to dispute)</span></label>
        <textarea id="hr-remarks" rows="2" maxlength="255" [value]="remarks()"
                  (input)="remarks.set($any($event.target).value)"></textarea>
        <span class="help">A note you add replaces the agent's note on this handover.</span>
      </div>

      @if (error()) {
        <p class="nt-error-text" role="alert">{{ error() }}</p>
      }

      <div class="actions">
        <button type="button" class="nt-btn nt-btn--outline" [disabled]="busy()" (click)="dlg.close()">Cancel</button>
        <button type="button" class="nt-btn nt-btn--outline danger" [disabled]="busy() || !remarks().trim()"
                (click)="submit('DISPUTED')">Dispute</button>
        <button type="button" class="nt-btn" [disabled]="busy()" (click)="submit('CONFIRMED')">
          Confirm {{ handed().format() }}
        </button>
      </div>
    </dialog>
  `,
  styles: `
    dialog {
      width: min(460px, calc(100vw - 32px));
      border: 0;
      border-radius: var(--nt-radius-card);
      padding: 22px;
      color: var(--nt-ink);
      &::backdrop { background: rgba(16, 35, 28, 0.45); }
    }
    h2 { font: 700 19px var(--nt-font-display); margin-bottom: 14px; }
    .amounts {
      display: grid; grid-template-columns: 1fr auto; gap: 8px 16px; margin: 0 0 14px;
      font: 500 14px var(--nt-font);
      dt { color: var(--nt-muted); }
      dd { margin: 0; text-align: right; font-weight: 600; font-variant-numeric: tabular-nums; }
    }
    .warn { color: var(--nt-warning); }
    .note {
      margin: 0 0 14px; padding: 10px 12px; border-radius: 10px; background: var(--nt-bg); font-size: 14px;
      span { display: block; font: 600 12px var(--nt-font); color: var(--nt-muted); margin-bottom: 2px; }
    }
    textarea {
      border: 1.5px solid var(--nt-border); border-radius: var(--nt-radius); padding: 10px 12px;
      font: 500 15px var(--nt-font); resize: vertical; outline: none;
      &:focus { border-color: var(--nt-brand); }
    }
    .opt { font-weight: 500; color: var(--nt-muted); }
    .help { font: 500 12px var(--nt-font); color: var(--nt-muted); }
    .nt-error-text { margin: 12px 0 0; }
    .actions { display: flex; flex-wrap: wrap; justify-content: flex-end; gap: 10px; margin-top: 18px; }
    .danger { color: var(--nt-error); border-color: var(--nt-error); }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class HandoverReview {
  readonly handover = input.required<Handover>();
  readonly decide = input.required<Decide>();
  readonly closed = output<void>();

  private readonly dialog = viewChild.required<ElementRef<HTMLDialogElement>>('dlg');

  readonly remarks = signal('');
  readonly busy = signal(false);
  readonly error = signal('');

  readonly expected = computed(() => Money.fromJson(this.handover().expectedAmount));
  readonly handed = computed(() => Money.fromJson(this.handover().handedAmount));
  readonly diff = computed(() => Money.fromJson(this.handover().difference));
  readonly diffText = computed(() => {
    const d = this.diff();
    if (d.isZero) return 'Matches';
    return d.isPositive ? `${d.format()} extra` : `${new Money(-d.paise).format()} short`;
  });
  readonly dateText = computed(() =>
    new Date(`${this.handover().handoverDate}T00:00:00`)
      .toLocaleDateString('en-IN', { day: 'numeric', month: 'short', year: 'numeric' }));

  constructor() {
    afterNextRender(() => this.dialog().nativeElement.showModal());
  }

  async submit(status: Exclude<HandoverStatus, 'PENDING'>): Promise<void> {
    const note = this.remarks().trim();
    this.busy.set(true);
    this.error.set('');
    try {
      await this.decide()(this.handover().id, status, note || null);
      this.dialog().nativeElement.close();
    } catch (e) {
      const code = e instanceof HttpErrorResponse ? e.error?.code : null;
      this.error.set(code === 'ALREADY_DECIDED'
        ? 'This handover was already decided. The list has been refreshed.'
        : 'Could not save. Check your connection and try again.');
    } finally {
      this.busy.set(false);
    }
  }
}
