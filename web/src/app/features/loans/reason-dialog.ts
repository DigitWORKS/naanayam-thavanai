import { ChangeDetectionStrategy, Component, input, output, signal, viewChild } from '@angular/core';

import { apiErrorMessage } from '../../core/api-error';
import { Dialog } from '../../shared/dialog';

/** Destructive action that needs a written reason (reverse a payment, mark a loan defaulted). */
@Component({
  selector: 'nt-reason-dialog',
  imports: [Dialog],
  template: `
    <nt-dialog [heading]="heading()" [busy]="busy()" (closed)="closed.emit()">
      <form class="form" (submit)="$event.preventDefault(); submit()">
        <p class="warn-box">{{ warning() }}</p>
        <div class="nt-field">
          <label for="rd-reason">{{ label() }}</label>
          <textarea id="rd-reason" rows="3" [attr.maxlength]="maxLength()" [value]="reason()"
                    (input)="reason.set($any($event.target).value)"></textarea>
        </div>
        @if (error()) { <p class="nt-error-text" role="alert">{{ error() }}</p> }
        <div class="actions">
          <button type="button" class="nt-btn nt-btn--outline" [disabled]="busy()" (click)="dialog().close()">Cancel</button>
          <button type="submit" class="nt-btn danger" [disabled]="busy() || !reason().trim()">
            {{ busy() ? 'Saving…' : action() }}
          </button>
        </div>
      </form>
    </nt-dialog>
  `,
  styleUrl: './dialog-form.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ReasonDialog {
  readonly heading = input.required<string>();
  readonly warning = input.required<string>();
  readonly label = input('Reason');
  readonly action = input.required<string>();
  readonly maxLength = input(200);
  /** Does the call; throws to show the server's message. */
  readonly run = input.required<(reason: string) => Promise<void>>();
  readonly closed = output<void>();

  readonly dialog = viewChild.required(Dialog);
  readonly reason = signal('');
  readonly busy = signal(false);
  readonly error = signal('');

  async submit(): Promise<void> {
    const r = this.reason().trim();
    if (!r) return;
    this.busy.set(true);
    this.error.set('');
    try {
      await this.run()(r);
      this.dialog().close();
    } catch (e) {
      this.error.set(apiErrorMessage(e));
    } finally {
      this.busy.set(false);
    }
  }
}
