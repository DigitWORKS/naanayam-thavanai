/** Wire types of the loan endpoints. Money fields are decimal strings ("1250.00"). */

export type LoanStatus = 'ACTIVE' | 'CLOSED' | 'PRECLOSED' | 'DEFAULTED';
export type Frequency = 'DAILY' | 'WEEKLY';
export type InstallmentStatus = 'PENDING' | 'PARTIAL' | 'PAID' | 'MISSED' | 'SETTLED';
export type PaymentMode = 'CASH' | 'UPI';

/** GET /loans (page content) */
export interface LoanSummary {
  id: number;
  loanNo: string;
  principal: string;
  frequency: Frequency;
  installmentAmount: string;
  outstanding: string;
  /** Arrears: amount behind schedule. */
  overdueAmount: string;
  status: LoanStatus;
  clientId: number;
  clientName: string;
  clientPhone: string;
  areaName: string;
  totalPayable: string;
  startDate: string;
  endDate: string;
  paidCount: number;
  installmentCount: number;
}

export interface Page<T> {
  content: T[];
  meta: { page: number; size: number; totalElements: number; totalPages: number };
}

/** GET /loans/{id} */
export interface LoanDetail {
  id: number;
  loanNo: string;
  principal: string;
  frequency: Frequency;
  installmentAmount: string;
  outstanding: string;
  overdueAmount: string;
  status: LoanStatus;
  clientId: number;
  clientName: string;
  clientPhone: string;
  areaName: string;
  interestRate: string;
  interestMethod: 'FLAT' | 'REDUCING';
  interestCollection: 'UPFRONT_DEDUCTED' | 'ADDED_TO_REPAYMENT';
  interestAmount: string;
  disbursedAmount: string;
  totalPayable: string;
  totalPaid: string;
  pendingPenalty: string;
  tenureCount: number;
  startDate: string;
  originalEndDate: string;
  currentEndDate: string;
  legalNote: string | null;
  closedAt: string | null;
  paidCount: number;
  installmentCount: number;
}

/** GET /loans/{id}/installments */
export interface Installment {
  id: number;
  seqNo: number;
  dueDate: string;
  dueAmount: string;
  paidAmount: string;
  penaltyAmount: string;
  penaltyPaid: string;
  status: InstallmentStatus;
  isExtension: boolean;
  /** For an extension: id of the missed installment it carries forward. */
  extendedFrom: number | null;
}

/** GET /loans/{id}/ledger (oldest first) */
export interface LedgerEntry {
  id: number;
  amount: string;
  entryType: 'PAYMENT' | 'REVERSAL';
  purpose: 'REGULAR' | 'PRECLOSURE';
  /** For a reversal: id of the payment it reverses. */
  reversalOf: number | null;
  mode: PaymentMode;
  upiRefNo: string | null;
  paidOn: string;
  collectedByName: string | null;
  runningBalance: string;
  remarks: string | null;
}

/** GET /loans/{id}/preclosure-quote */
export interface PreclosureQuote {
  loanId: number;
  asOf: string;
  outstandingPrincipal: string;
  outstandingInterest: string;
  pendingPenalty: string;
  interestRebate: string;
  preclosureCharge: string;
  settlementAmount: string;
}

/** GET /areas */
export interface Area {
  id: number;
  name: string;
  isActive: boolean;
}

export const FREQUENCY_LABEL: Record<Frequency, string> = { DAILY: 'Daily', WEEKLY: 'Weekly' };
export const FREQUENCY_UNIT: Record<Frequency, [string, string]> = {
  DAILY: ['day', 'days'],
  WEEKLY: ['week', 'weeks'],
};
export const STATUS_LABEL: Record<LoanStatus, string> = {
  ACTIVE: 'Active',
  CLOSED: 'Closed',
  PRECLOSED: 'Pre-closed',
  DEFAULTED: 'Defaulted',
};

/** UPI transaction ID (UTR): 12 digits, spaces ignored — same rule as the server (UpiRefCheck). */
export const normaliseUtr = (text: string) => text.replace(/\s+/g, '');
export const isValidUtr = (text: string) => /^\d{12}$/.test(normaliseUtr(text));
