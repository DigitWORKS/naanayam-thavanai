import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';

import { authInterceptor } from './auth.interceptor';
import { TokenResponse } from './auth.models';
import { TokenStore } from './token-store';

const owner = { id: 1, name: 'Udhay', phone: '9789044120', role: 'OWNER' as const, areas: [] };
const session = (access: string, refresh = 'r1'): TokenResponse => ({ accessToken: access, refreshToken: refresh, user: owner });

/** The replay goes out several async hops after the refresh resolves; one macrotask covers them. */
const settle = () => new Promise<void>((resolve) => setTimeout(resolve, 0));

describe('authInterceptor', () => {
  let http: HttpClient;
  let backend: HttpTestingController;
  let store: TokenStore;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
        provideRouter([]),
      ],
    });
    store = TestBed.inject(TokenStore);
    store.save(session('a1'));
    http = TestBed.inject(HttpClient);
    backend = TestBed.inject(HttpTestingController);
  });

  afterEach(() => backend.verify());

  it('adds the bearer token', async () => {
    const done = firstValueFrom(http.get('/v1/areas'));
    const req = backend.expectOne('/v1/areas');
    expect(req.request.headers.get('Authorization')).toBe('Bearer a1');
    req.flush([]);
    await done;
  });

  it('refreshes once on 401 and replays with the new token', async () => {
    const done = firstValueFrom(http.get<string[]>('/v1/areas'));
    backend.expectOne('/v1/areas').flush(null, { status: 401, statusText: 'Unauthorized' });

    const refresh = backend.expectOne('/v1/auth/refresh');
    expect(refresh.request.headers.has('Authorization')).toBe(false);
    expect(refresh.request.body).toEqual({ refreshToken: 'r1' });
    refresh.flush(session('a2', 'r2'));
    await settle();

    const replay = backend.expectOne('/v1/areas');
    expect(replay.request.headers.get('Authorization')).toBe('Bearer a2');
    replay.flush(['Tambaram']);
    expect(await done).toEqual(['Tambaram']);
    expect(store.refreshToken).toBe('r2');
  });

  it('signs out when the refresh token is rejected', async () => {
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    const done = firstValueFrom(http.get('/v1/areas')).catch((e) => e);
    backend.expectOne('/v1/areas').flush(null, { status: 401, statusText: 'Unauthorized' });
    backend.expectOne('/v1/auth/refresh').flush(null, { status: 401, statusText: 'Unauthorized' });
    await settle();

    const err = await done;
    expect(err.status).toBe(401);
    expect(store.accessToken).toBeNull();
    expect(navigate).toHaveBeenCalledWith(['/login'], { queryParams: { reason: 'expired' } });
  });
});
