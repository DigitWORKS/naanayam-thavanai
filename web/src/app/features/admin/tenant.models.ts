/** Wire types of the Super Admin tenant endpoints. */

export type TenantStatus = 'TRIAL' | 'ACTIVE' | 'SUSPENDED';
export type Plan = 'TRIAL' | 'MONTHLY' | 'YEARLY';

/** GET /admin/tenants (page content), GET /admin/tenants/{id} */
export interface Tenant {
  id: number;
  name: string;
  ownerName: string;
  phone: string;
  licenceNo: string | null;
  status: TenantStatus;
  plan: Plan | null;
  planExpiry: string | null;
  /** Set when the financier registered themselves and accepted the terms. */
  termsAcceptedAt: string | null;
  createdAt: string;
}

/** GET /admin/tenants/summary */
export interface TenantSummary {
  all: number;
  trial: number;
  expiringSoon: number;
  suspended: number;
}

export const PLAN_LABEL: Record<Plan, string> = { TRIAL: 'Trial', MONTHLY: 'Monthly', YEARLY: 'Yearly' };

const iso = (d: Date) =>
  `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;

/**
 * Expiry after starting / renewing {@code plan} today — the server's rule (Plan.renewedExpiry): unused days are
 * kept, so a running plan extends from its current expiry; an expired or missing one starts from today.
 * Trial 30 days, monthly +1 month, yearly +1 year.
 */
export function renewedExpiry(plan: Plan, currentExpiry: string | null, today: string): string {
  const base = currentExpiry && currentExpiry >= today ? currentExpiry : today;
  const d = new Date(`${base}T00:00:00`);
  const day = d.getDate();
  if (plan === 'TRIAL') d.setDate(day + 30);
  else {
    // Java plusMonths / plusYears: clamp to the last day of the target month (31 Jan + 1 month = 28/29 Feb).
    d.setDate(1);
    if (plan === 'MONTHLY') d.setMonth(d.getMonth() + 1);
    else d.setFullYear(d.getFullYear() + 1);
    const last = new Date(d.getFullYear(), d.getMonth() + 1, 0).getDate();
    d.setDate(Math.min(day, last));
  }
  return iso(d);
}
