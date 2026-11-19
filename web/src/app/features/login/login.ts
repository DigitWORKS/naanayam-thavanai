import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { Router } from '@angular/router';

import { AuthService, LoginError } from '../../core/auth/auth.service';

/** Design W1: brand panel + phone / PIN card. Owners and Super Admin only; agents are sent to the app. */
@Component({
  selector: 'nt-login',
  templateUrl: './login.html',
  styleUrl: './login.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Login {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  /** Query params (component input binding). */
  readonly returnUrl = input<string>();
  readonly reason = input<string>();

  protected readonly phone = signal('');
  protected readonly pin = signal('');
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly notice = computed(() =>
    this.reason() === 'expired' ? 'Your session has expired. Please log in again.' : null,
  );

  protected readonly phoneDigits = computed(() => this.phone().replace(/\D/g, ''));
  protected readonly canSubmit = computed(
    () => /^[6-9]\d{9}$/.test(this.phoneDigits()) && /^\d{4,6}$/.test(this.pin()) && !this.busy(),
  );

  /** Keeps up to 10 digits, shown as "98765 43210". */
  protected onPhone(value: string): void {
    const d = value.replace(/\D/g, '').slice(0, 10);
    this.phone.set(d.length > 5 ? `${d.slice(0, 5)} ${d.slice(5)}` : d);
    this.error.set(null);
  }

  protected onPin(value: string): void {
    this.pin.set(value.replace(/\D/g, '').slice(0, 6));
    this.error.set(null);
  }

  protected async submit(event: Event): Promise<void> {
    event.preventDefault();
    if (!this.canSubmit()) return;
    this.busy.set(true);
    this.error.set(null);
    try {
      await this.auth.login(this.phoneDigits(), this.pin());
      const target = this.safeReturnUrl() ?? this.auth.homeUrl();
      await this.router.navigateByUrl(target);
    } catch (e) {
      this.error.set(e instanceof LoginError ? e.message : 'Something went wrong. Please try again.');
      this.pin.set('');
    } finally {
      this.busy.set(false);
    }
  }

  /** Only same-app paths, never another origin. */
  private safeReturnUrl(): string | null {
    const url = this.returnUrl();
    return url && url.startsWith('/') && !url.startsWith('//') ? url : null;
  }
}
