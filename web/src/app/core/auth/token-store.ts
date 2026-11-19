import { Injectable } from '@angular/core';

import { AppUser, TokenResponse } from './auth.models';

const KEY = 'nt.session';

interface StoredSession {
  accessToken: string;
  refreshToken: string;
  user: AppUser;
}

/**
 * Session in localStorage so a reload keeps the owner signed in.
 * Tokens are bearer credentials: the app must not render untrusted HTML (Angular sanitises bindings;
 * never use bypassSecurityTrust* with API data). Moving the refresh token to an httpOnly cookie is in
 * the backlog (docs/PROJECT_STATUS.md).
 */
@Injectable({ providedIn: 'root' })
export class TokenStore {
  private session: StoredSession | null = this.read();

  get accessToken(): string | null { return this.session?.accessToken ?? null; }
  get refreshToken(): string | null { return this.session?.refreshToken ?? null; }
  get user(): AppUser | null { return this.session?.user ?? null; }

  save(t: TokenResponse): void {
    this.session = { accessToken: t.accessToken, refreshToken: t.refreshToken, user: t.user };
    try {
      localStorage.setItem(KEY, JSON.stringify(this.session));
    } catch {
      // Private mode / storage full: the session still works until the tab is closed.
    }
  }

  clear(): void {
    this.session = null;
    try {
      localStorage.removeItem(KEY);
    } catch {
      /* ignore */
    }
  }

  private read(): StoredSession | null {
    try {
      const raw = localStorage.getItem(KEY);
      return raw ? (JSON.parse(raw) as StoredSession) : null;
    } catch {
      return null;
    }
  }
}
