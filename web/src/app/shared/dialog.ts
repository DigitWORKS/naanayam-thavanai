import {
  afterNextRender, ChangeDetectionStrategy, Component, ElementRef, input, output, viewChild,
} from '@angular/core';

/**
 * Modal built on the native <dialog>: focus trap, Esc and backdrop come from the browser. Render it with
 * @if and remove it on (closed). While {@code busy} is true, Esc does nothing.
 */
@Component({
  selector: 'nt-dialog',
  template: `
    <dialog #dlg (close)="closed.emit()" (cancel)="onCancel($event)"
            [attr.aria-labelledby]="titleId" [style.width]="width()">
      <h2 [id]="titleId">{{ heading() }}</h2>
      <ng-content />
    </dialog>
  `,
  styles: `
    dialog {
      max-width: calc(100vw - 32px);
      max-height: calc(100vh - 32px);
      border: 0;
      border-radius: var(--nt-radius-card);
      padding: 22px;
      color: var(--nt-ink);
      &::backdrop { background: rgba(16, 35, 28, 0.45); }
    }
    h2 { font: 700 19px var(--nt-font-display); margin-bottom: 14px; }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Dialog {
  private static seq = 0;

  readonly heading = input.required<string>();
  readonly width = input('460px');
  readonly busy = input(false);
  readonly closed = output<void>();

  readonly titleId = `nt-dialog-${++Dialog.seq}`;
  private readonly dlg = viewChild.required<ElementRef<HTMLDialogElement>>('dlg');

  constructor() {
    afterNextRender(() => this.dlg().nativeElement.showModal());
  }

  /**
   * Esc: blocked while saving. (A template handler like `busy() && $event.preventDefault()` would block it
   * always — Angular treats a listener that returns false as preventDefault.)
   */
  onCancel(e: Event): void {
    if (this.busy()) e.preventDefault();
  }

  close(): void {
    this.dlg().nativeElement.close();
  }
}
