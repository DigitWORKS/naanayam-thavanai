import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink } from '@angular/router';

import { SUPPORT_EMAIL } from '../../core/support';

/**
 * Public page linked from Google Play's Data safety form ("Delete account URL"): how to delete an account and
 * its data without the app. No login needed.
 */
@Component({
  selector: 'nt-delete-account',
  imports: [RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <main class="page">
      <header class="brand">
        <span class="coin" aria-hidden="true">நா</span>
        <span class="brand-text">
          <span class="brand-ta">நாணயம் தவணை</span>
          <span class="brand-en">NAANAYAM THAVANAI</span>
        </span>
      </header>

      <h1>Delete your account and data</h1>
      <p class="lead">
        Naanayam Thavanai is used by money-lending businesses. A business account belongs to its owner;
        agent logins are created by the owner.
      </p>

      <section>
        <h2>Business owners: delete in the app</h2>
        <ol>
          <li>Open the Naanayam Thavanai app and log in with your mobile number and PIN.</li>
          <li>Go to <strong>More</strong> and tap <strong>Delete business account</strong>.</li>
          <li>Read what will be deleted, tick the box, enter your PIN and confirm.</li>
        </ol>
      </section>

      <section>
        <h2>Without the app</h2>
        <p>
          Email <a [href]="mailto">{{ email }}</a> from any address with the subject
          <strong>Delete my account</strong>, your business name and the owner's registered mobile number.
          We call that number to confirm it is you, then close the account within 7 days; the timeline below
          applies from then.
        </p>
      </section>

      <section>
        <h2>Agents</h2>
        <p>
          Ask your business owner to remove your login. If you no longer work with that business and cannot
          reach the owner, email <a [href]="mailto">{{ email }}</a> with your mobile number.
        </p>
      </section>

      <section>
        <h2>What is deleted, and when</h2>
        <ul>
          <li>
            <strong>Straight away:</strong> the business is closed, everyone is signed out and nobody can log in.
          </li>
          <li>
            <strong>After 30 days:</strong> everything is erased permanently: owner and agent logins, areas,
            clients and their documents, loans, schedules, payments, cash handovers and business settings.
            Until then the owner can ask us to restore the account.
          </li>
          <li>
            <strong>Backups:</strong> copies in our daily backups are overwritten within 14 days after
            that.
          </li>
        </ul>
        <p class="muted">
          Money-lending laws may require a business to keep loan records for some years. Download or print any
          records you need before deleting the account.
        </p>
      </section>

      <footer>
        <a routerLink="/login">Log in</a>
        <span>·</span>
        <span>DigitWORKS</span>
      </footer>
    </main>
  `,
  styles: `
    :host { display: block; background: var(--nt-bg, #f4f6f5); min-height: 100vh; }
    .page { max-width: 760px; margin: 0 auto; padding: 40px 24px 64px; color: var(--nt-ink); font: 400 16px/1.6 var(--nt-font); }
    .brand { display: flex; align-items: center; gap: 12px; margin-bottom: 32px; }
    .coin {
      width: 44px; height: 44px; border-radius: 50%; background: var(--nt-gold); border: 3px solid #c99a2e;
      display: flex; align-items: center; justify-content: center; font: 700 17px var(--nt-font-tamil);
    }
    .brand-text { display: flex; flex-direction: column; }
    .brand-ta { font: 700 19px var(--nt-font-tamil); color: var(--nt-brand); }
    .brand-en { font: 600 11px var(--nt-font-display); letter-spacing: 1.4px; color: var(--nt-muted); }
    h1 { font: 700 32px/1.2 var(--nt-font-display); margin: 0 0 12px; }
    h2 { font: 700 19px/1.3 var(--nt-font-display); margin: 0 0 8px; }
    .lead { color: var(--nt-muted); margin: 0 0 24px; }
    section { background: #fff; border-radius: 16px; padding: 20px 24px; margin-bottom: 16px; }
    ol, ul { margin: 0; padding-left: 22px; }
    li { margin-bottom: 6px; }
    p { margin: 0 0 8px; }
    .muted { color: var(--nt-muted); font-size: 14px; margin-top: 12px; }
    a { color: var(--nt-brand); font-weight: 600; }
    footer { display: flex; gap: 8px; color: var(--nt-muted); font-size: 14px; margin-top: 24px; }
  `,
})
export class DeleteAccountPage {
  readonly email = SUPPORT_EMAIL;
  readonly mailto = `mailto:${SUPPORT_EMAIL}?subject=${encodeURIComponent('Delete my account')}`;
}
