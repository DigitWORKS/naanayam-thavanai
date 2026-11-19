import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/** Stand-in for pages not built yet. `title` / `note` come from the route's data (component input binding). */
@Component({
  selector: 'nt-placeholder',
  template: `
    <h1>{{ title() }}</h1>
    <div class="nt-card note">
      <p>{{ note() }}</p>
    </div>
  `,
  styles: `
    h1 { font: 700 28px var(--nt-font-display); margin-bottom: 16px; }
    .note p { margin: 0; color: var(--nt-muted); }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Placeholder {
  readonly title = input('');
  readonly note = input('This page is built in the next step.');
}
