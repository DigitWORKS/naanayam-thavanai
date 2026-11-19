import { inject } from '@angular/core';
import { CanMatchFn, Router } from '@angular/router';

import { Role } from './auth.models';
import { AuthService } from './auth.service';

/** Signed in, otherwise to /login (remembering where they were going). */
export const authGuard: CanMatchFn = (_route, segments) => {
  const auth = inject(AuthService);
  if (auth.isSignedIn()) return true;
  const returnUrl = '/' + segments.map((s) => s.path).join('/');
  return inject(Router).createUrlTree(['/login'], { queryParams: returnUrl !== '/' ? { returnUrl } : {} });
};

/** Only for signed-out users (the login page); signed-in users go to their home. */
export const guestGuard: CanMatchFn = () => {
  const auth = inject(AuthService);
  return auth.isSignedIn() ? inject(Router).parseUrl(auth.homeUrl()) : true;
};

/**
 * Role check. Redirects to the user's own home instead of returning false, so a wrong-role URL never
 * falls through to the wildcard route (which would loop).
 */
export const roleGuard =
  (role: Role): CanMatchFn =>
  () => {
    const auth = inject(AuthService);
    return auth.role() === role ? true : inject(Router).parseUrl(auth.homeUrl());
  };
