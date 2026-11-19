import { HttpContext, HttpContextToken } from '@angular/common/http';

/**
 * API root. Same-origin path: `ng serve` proxies it to the Spring Boot service (proxy.conf.json),
 * and in production the web server routes /v1 to the service. No CORS needed in either case.
 */
export const API = '/v1';

/** Request flag: send without the bearer token and skip the refresh-on-401 handling (login, refresh). */
export const SKIP_AUTH = new HttpContextToken<boolean>(() => false);

export const skipAuth = () => new HttpContext().set(SKIP_AUTH, true);

/** The API's Error schema ({code, message, details}). */
export interface ApiErrorBody {
  code: string;
  message: string;
  details?: Record<string, unknown>;
}
