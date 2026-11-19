import type { LoanSummary } from '../loans/loan.models';

/** Wire types of the client endpoints. Money fields are decimal strings ("1250.00"). */

export type ClientFilter = 'ALL' | 'BEHIND' | 'NO_ACTIVE_LOAN';

/** GET /clients (page content) */
export interface ClientListItem {
  id: number;
  areaId: number;
  areaName: string;
  name: string;
  phone: string;
  isActive: boolean;
  activeLoans: number;
  outstanding: string;
  overdueAmount: string;
}

/** GET /clients/{id} */
export interface ClientDetail {
  id: number;
  areaId: number;
  areaName: string;
  name: string;
  phone: string;
  altPhone: string | null;
  address: string | null;
  aadhaarLast4: string | null;
  guarantorName: string | null;
  guarantorPhone: string | null;
  consentGivenAt: string | null;
  consentVersion: string | null;
  createdAt: string | null;
  isActive: boolean;
  activeLoans: LoanSummary[];
  totalOutstanding: string;
}

export type DocType = 'PHOTO' | 'AADHAAR' | 'PAN' | 'GUARANTOR_ID' | 'OTHER';

/** GET /clients/{id}/documents */
export interface ClientDocument {
  id: number;
  docType: DocType;
  /** Authenticated download path (under /v1). */
  url: string;
  uploadedAt: string;
}

export const DOC_LABEL: Record<DocType, string> = {
  PHOTO: 'Photo',
  AADHAAR: 'Aadhaar',
  PAN: 'PAN',
  GUARANTOR_ID: 'Guarantor ID',
  OTHER: 'Other',
};

/** Details of a 409 CLIENT_EXISTS. accessible=false: an agent outside that area (owners always true). */
export interface ExistingClient {
  clientId: number;
  name: string;
  areaName: string;
  isActive: boolean;
  accessible: boolean;
}

/** GET /settings — only what the client screens use. */
export interface SettingsLite {
  maxActiveLoansPerClient: number;
}

/** Consent notice shown on the new-client form; same text and version as the mobile app. */
export const CONSENT_VERSION = 'v1';
export const CONSENT_TEXT_EN =
  'I allow this finance to store my details and documents for my loans and to send me payment SMS.';
export const CONSENT_TEXT_TA =
  'இந்தக் கடனுக்காக என் விவரங்களையும் ஆவணங்களையும் சேமிக்கவும், பணம் செலுத்திய SMS அனுப்பவும் ஒப்புக்கொள்கிறேன்.';
