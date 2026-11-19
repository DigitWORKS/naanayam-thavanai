import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { computed, inject, Injectable, signal } from '@angular/core';
import { Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';

import { API, ApiErrorBody, skipAuth } from '../api';
import { AppUser, HOME_BY_ROLE, TokenResponse } from './auth.models';
import { TokenStore } from './token-store';

export type RefreshOutcome = 'ok' | 'offline' | 'rejected';

/** Login failures with a message fit for the login page. */
export class LoginError extends Error {
  constructor(readonly code: string, message: string) {
    super(message);
  }
}

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly store = inject(TokenStore);
  private readonly router = inject(Router);

  private readonly _user = signal<AppUser | null>(this.store.user);

  /** Signed-in user, or null. */
  readonly user = this._user.asReadonly();
  readonly isSignedIn = computed(() => this._user() !== null);
  readonly role = computed(() => this._user()?.role ?? null);

  /** One refresh at a time: parallel 401s share it. */
  private refreshing: Promise<RefreshOutcome> | null = null;

  get accessToken(): string | null {
    return this.store.accessToken;
  }

  async login(phone: string, pin: string): Promise<AppUser> {
    let tokens: TokenResponse;
    try {
      tokens = await firstValueFrom(
        this.http.post<TokenResponse>(`${API}/auth/login`, { phone, pin }, { context: skipAuth() }),
      );
    } catch (e) {
      throw toLoginError(e);
    }
    if (tokens.user.role === 'AGENT') {
      throw new LoginError('AGENT_WEB', 'Agents use the Naanayam Thavanai mobile app. Please log in there.');
    }
    this.store.save(tokens);
    this._user.set(tokens.user);
    return tokens.user;
  }

  /** Exchanges the refresh token. Offline keeps the session; only a definite rejection ends it. */
  refresh(): Promise<RefreshOutcome> {
    this.refreshing ??= this.doRefresh().finally(() => (this.refreshing = null));
    return this.refreshing;
  }

  private async doRefresh(): Promise<RefreshOutcome> {
    const refreshToken = this.store.refreshToken;
    if (!refreshToken) return 'rejected';
    try {
      const tokens = await firstValueFrom(
        this.http.post<TokenResponse>(`${API}/auth/refresh`, { refreshToken }, { context: skipAuth() }),
      );
      this.store.save(tokens);
      this._user.set(tokens.user);
      return 'ok';
    } catch (e) {
      return e instanceof HttpErrorResponse && e.status === 0 ? 'offline' : 'rejected';
    }
  }

  logout(reason?: 'expired'): void {
    this.store.clear();
    this._user.set(null);
    void this.router.navigate(['/login'], { queryParams: reason ? { reason } : {} });
  }

  /** Landing page for the signed-in role. */
  homeUrl(): string {
    const role = this._user()?.role;
    return role && role !== 'AGENT' ? HOME_BY_ROLE[role] : '/login';
  }
}

function toLoginError(e: unknown): LoginError {
  if (e instanceof HttpErrorResponse) {
    if (e.status === 0) return new LoginError('NETWORK', "Can't reach the server. Check the internet connection.");
    if (e.status === 401) return new LoginError('BAD_CREDENTIALS', 'Wrong mobile number or PIN.');
    const body = e.error as Partial<ApiErrorBody> | null;
    if (body?.message) return new LoginError(body.code ?? `HTTP_${e.status}`, body.message);
  }
  return new LoginError('UNKNOWN', 'Something went wrong. Please try again.');
}
