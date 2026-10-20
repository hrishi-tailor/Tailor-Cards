import { API_BASE_URL } from './config';
import { adminHeaders, clearAdminAuth, getAdminAuthHeader } from './buylistApi';
import type {
  LedgerEntrySource,
  LedgerImportReport,
  LedgerLot,
  LedgerLotRequest,
  LedgerSale,
  LedgerSaleRequest,
  LedgerSummary,
} from '../types';

const BASE = `${API_BASE_URL}/api/admin/ledger`;

/** Error with the HTTP status, so callers can tell "no connection" (0) from a refused request. */
export class LedgerApiError extends Error {
  readonly status: number;

  constructor(message: string, status: number) {
    super(message);
    this.status = status;
  }
}

function headers(json = false): Record<string, string> {
  const auth = getAdminAuthHeader();
  if (!auth) {
    throw new LedgerApiError('Unauthorized: Admin credentials required.', 401);
  }
  return json ? { ...adminHeaders(auth), 'Content-Type': 'application/json' } : adminHeaders(auth);
}

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  let response: Response;
  try {
    response = await fetch(`${BASE}${path}`, init);
  } catch {
    throw new LedgerApiError('No connection to the server.', 0);
  }
  if (response.status === 401) {
    clearAdminAuth();
    throw new LedgerApiError('Your session expired. Please log in again.', 401);
  }
  if (!response.ok && response.status !== 422) {
    let message = `Request failed (${response.status}).`;
    try {
      const data = await response.json();
      message = data?.message || data?.error || message;
    } catch {
      // body was not json
    }
    throw new LedgerApiError(message, response.status);
  }
  return response.status === 204 ? (undefined as T) : ((await response.json()) as T);
}

export function fetchLots(): Promise<LedgerLot[]> {
  return request('/lots', { headers: headers() });
}

export function createLot(lot: LedgerLotRequest, source: LedgerEntrySource = 'MANUAL'): Promise<LedgerLot> {
  return request(`/lots?source=${source}`, { method: 'POST', headers: headers(true), body: JSON.stringify(lot) });
}

export function deleteLot(id: number): Promise<void> {
  return request(`/lots/${id}`, { method: 'DELETE', headers: headers() });
}

export function recordSale(lotId: number, sale: LedgerSaleRequest, source: LedgerEntrySource = 'MANUAL'): Promise<LedgerSale> {
  return request(`/lots/${lotId}/sales?source=${source}`, { method: 'POST', headers: headers(true), body: JSON.stringify(sale) });
}

export function deleteSale(id: number): Promise<void> {
  return request(`/sales/${id}`, { method: 'DELETE', headers: headers() });
}

export function fetchSummary(from?: string, to?: string): Promise<LedgerSummary> {
  const params = new URLSearchParams();
  if (from) params.set('from', from);
  if (to) params.set('to', to);
  const query = params.toString();
  return request(`/summary${query ? `?${query}` : ''}`, { headers: headers() });
}

/** Checks a CSV (dryRun) or saves it. A rejected real run resolves with the report and its errors (HTTP 422). */
export function importLedgerCsv(file: File, dryRun: boolean): Promise<LedgerImportReport> {
  const form = new FormData();
  form.append('file', file);
  return request(`/import?dryRun=${dryRun}`, { method: 'POST', headers: headers(), body: form });
}
