import { HttpErrorResponse } from '@angular/common/http';

/** The API's error body: { code, message, details? }. */
export interface ApiErrorBody {
  code?: string;
  message?: string;
  details?: Record<string, unknown>;
}

/** Error code of a failed call, or null (network error, non-API error). */
export function apiErrorCode(e: unknown): string | null {
  return e instanceof HttpErrorResponse ? ((e.error as ApiErrorBody | null)?.code ?? null) : null;
}

/**
 * Message to show for a failed call: the server's message for rule / validation errors (they are written
 * for people), otherwise a generic one.
 */
export function apiErrorMessage(e: unknown, fallback = 'Something went wrong. Try again.'): string {
  if (!(e instanceof HttpErrorResponse)) return fallback;
  if (e.status === 0) return 'Cannot reach the server. Check your connection and try again.';
  const body = e.error as ApiErrorBody | null;
  return e.status < 500 && body?.message ? body.message : fallback;
}
