import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';

import { pageWindow } from './format';

export interface PageMeta {
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

/** "Showing 1–20 of 186" and page buttons. Pages are 1-based in the output. */
@Component({
  selector: 'nt-pager',
  template: `
    @if (view(); as p) {
      <span>{{ p.text }}</span>
      @if (p.last > 1) {
        <nav aria-label="Pages">
          @if (p.current > 1) {
            <button type="button" (click)="pageChange.emit(p.current - 1)">← Prev</button>
          }
          @for (n of p.pages; track $index) {
            @if (n === 0) {
              <span class="gap">…</span>
            } @else {
              <button type="button" [class.on]="n === p.current" [attr.aria-current]="n === p.current ? 'page' : null"
                      (click)="pageChange.emit(n)">{{ n }}</button>
            }
          }
          @if (p.current < p.last) {
            <button type="button" (click)="pageChange.emit(p.current + 1)">Next →</button>
          }
        </nav>
      }
    }
  `,
  styles: `
    :host {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      justify-content: space-between;
      gap: 10px;
      font: 500 13px var(--nt-font);
      color: var(--nt-muted);
    }
    nav { display: flex; gap: 6px; align-items: center; }
    button {
      height: 34px;
      min-width: 34px;
      padding: 0 10px;
      border: 0;
      border-radius: 9px;
      background: var(--nt-surface);
      color: var(--nt-ink);
      font: 500 13px var(--nt-font);
      cursor: pointer;
      &.on { background: var(--nt-brand); color: #fff; font-weight: 600; }
    }
    .gap { padding: 0 4px; }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Pager {
  readonly meta = input<PageMeta | undefined>();
  readonly pageChange = output<number>();

  readonly view = computed(() => {
    const meta = this.meta();
    if (!meta || meta.totalElements === 0) return null;
    const from = meta.page * meta.size + 1;
    const to = Math.min(meta.totalElements, from + meta.size - 1);
    return {
      text: `Showing ${from}–${to} of ${meta.totalElements}`,
      current: meta.page + 1,
      last: meta.totalPages,
      pages: pageWindow(meta.page + 1, meta.totalPages),
    };
  });
}
