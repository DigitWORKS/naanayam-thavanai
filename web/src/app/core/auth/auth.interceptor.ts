import { HttpErrorResponse, HttpInterceptorFn, HttpRequest } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, from, switchMap, throwError } from 'rxjs';

import { SKIP_AUTH } from '../api';
import { AuthService } from './auth.service';

const withToken = (req: HttpRequest<unknown>, token: string | null) =>
  token ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } }) : req;

/**
 * Adds the bearer token. On 401 refreshes once (shared by parallel requests) and replays the request;
 * a definite refresh rejection signs the user out, a network failure just fails this request.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  if (req.context.get(SKIP_AUTH)) return next(req);

  const auth = inject(AuthService);
  const sentWith = auth.accessToken;

  return next(withToken(req, sentWith)).pipe(
    catchError((err: unknown) => {
      if (!(err instanceof HttpErrorResponse) || err.status !== 401 || !sentWith) {
        return throwError(() => err);
      }
      // Another request may already have refreshed while this one was in flight.
      const refreshed = auth.accessToken !== sentWith ? Promise.resolve('ok' as const) : auth.refresh();
      return from(refreshed).pipe(
        switchMap((outcome) => {
          if (outcome === 'ok') return next(withToken(req, auth.accessToken));
          if (outcome === 'rejected') auth.logout('expired');
          return throwError(() => err);
        }),
      );
    }),
  );
};
