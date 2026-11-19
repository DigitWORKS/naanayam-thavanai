/** Wire types of the endpoints the owner dashboard reads. Money fields are decimal strings ("1250.00"). */

export type Plan = 'TRIAL' | 'MONTHLY' | 'YEARLY';

/** GET /reports/dashboard */
export interface Dashboard {
  date: string;
  businessName: string;
  plan: Plan | null;
  planExpiry: string | null;
  todayTarget: string;
  todayCollected: string;
  todayCash: string;
  todayUpi: string;
  totalOutstanding: string;
  overdueAmount: string;
  overdueLoans: number;
  activeLoans: number;
  pendingHandovers: number;
  activeClients: number;
  penaltyDue: string;
  fy: string;
  fyInterest: string;
}

export type HandoverStatus = 'PENDING' | 'CONFIRMED' | 'DISPUTED';

/** GET /reports/agents-today */
export interface AgentDay {
  userId: number;
  name: string;
  role: 'OWNER' | 'AGENT';
  active: boolean;
  areas: string[];
  /** Null for owners. */
  target: string | null;
  collected: string;
  cash: string;
  upi: string;
  payments: number;
  handover: {
    id: number;
    expectedAmount: string;
    handedAmount: string;
    status: HandoverStatus;
    remarks: string | null;
  } | null;
}

/** GET /reports/collections?groupBy=AREA */
export interface CollectionRow {
  key: string;
  target: string | null;
  collected: string;
  cash: string;
  upi: string;
  paymentCount: number;
}

/** GET /reports/most-behind */
export interface BehindLoan {
  loanId: number;
  loanNo: string;
  clientId: number;
  clientName: string;
  areaName: string;
  arrears: string;
  installmentsBehind: number;
  behindSince: string;
}

/** GET /cash-handovers */
export interface Handover {
  id: number;
  agentId: number;
  agentName: string;
  handoverDate: string;
  expectedAmount: string;
  handedAmount: string;
  /** handed − expected; negative = short. */
  difference: string;
  status: HandoverStatus;
  receivedBy: number | null;
  remarks: string | null;
  confirmedAt: string | null;
}
