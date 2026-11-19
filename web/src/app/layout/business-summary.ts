import { httpResource } from '@angular/common/http';
import { computed, Injectable } from '@angular/core';

import { API } from '../core/api';

/** Fields of GET /reports/dashboard the shell needs (the dashboard page reads the rest). */
interface DashboardHeader {
  businessName: string;
  plan: 'TRIAL' | 'MONTHLY' | 'YEARLY' | null;
  planExpiry: string | null;
  pendingHandovers: number;
}

const PLAN_LABEL = { TRIAL: 'Free trial', MONTHLY: 'Monthly plan', YEARLY: 'Yearly plan' } as const;
const DAY_MS = 86_400_000;

/** Business name, plan and handover count for the owner's side menu. Reload after confirming handovers. */
@Injectable({ providedIn: 'root' })
export class BusinessSummary {
  private readonly res = httpResource<DashboardHeader>(() => `${API}/reports/dashboard`);

  readonly businessName = computed(() => this.res.value()?.businessName ?? '');
  readonly pendingHandovers = computed(() => this.res.value()?.pendingHandovers ?? 0);

  private readonly expiry = computed(() => {
    const e = this.res.value()?.planExpiry;
    return e ? new Date(`${e}T00:00:00`) : null;
  });

  /** Whole days until the plan ends (negative once it has ended), or null. */
  readonly daysLeft = computed(() => {
    const e = this.expiry();
    if (!e) return null;
    const today = new Date();
    today.setHours(0, 0, 0, 0);
    return Math.round((e.getTime() - today.getTime()) / DAY_MS);
  });

  readonly expiringSoon = computed(() => {
    const d = this.daysLeft();
    return d !== null && d <= 7;
  });

  /** "Free trial · 28 days left", "Monthly plan · ends 16 Jan 2027" or "Plan ended 3 Oct 2026". */
  readonly planText = computed(() => {
    const plan = this.res.value()?.plan;
    if (!plan) return '';
    const label = PLAN_LABEL[plan];
    const days = this.daysLeft();
    const e = this.expiry();
    if (days === null || !e) return label;
    if (days < 0) return `Plan ended ${fmt(e)}`;
    return days <= 31 ? `${label} · ${days} ${days === 1 ? 'day' : 'days'} left` : `${label} · ends ${fmt(e)}`;
  });

  reload(): void {
    this.res.reload();
  }
}

const fmt = (d: Date) => d.toLocaleDateString('en-IN', { day: 'numeric', month: 'short', year: 'numeric' });
