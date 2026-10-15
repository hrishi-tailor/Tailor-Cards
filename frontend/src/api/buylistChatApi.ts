import { API_BASE_URL } from './config';

/** Customer API for the AI buylist chat. Amounts are USD market references; null means "no price". */

const SESSION_KEY = 'tc_buylist_chat_session';

export interface ChatStatus {
  enabled: boolean;
  /** False while email codes are temporarily turned off: chat starts anonymously, email is asked at Confirm. */
  emailVerificationRequired: boolean;
  turnstileSiteKey: string | null;
  maxLines: number;
  maxMessageLength: number;
  maxNoteLength: number;
  /** Photo card scanner; missing on older backends. */
  scannerEnabled?: boolean;
}

/** A QR-code link that lets the customer's phone scan cards onto the list open on their computer. */
export interface PhoneLink {
  token: string
  expiresAt: string
}

export interface PhoneLinkStatus {
  connected: boolean
  /** Names of the cards added from the phone, newest last. */
  added: string[]
  expiresAt: string
  expired: boolean
}

export interface ScanCard {
  cardId: string;
  name: string;
  setName: string | null;
  cardNumber: string | null;
  imageUrl: string | null;
  marketUsd: number | null;
}

/** What the scanner read from the photos. Nothing is on the list until the customer confirms it. */
export interface ScanResult {
  scanId: string | null;
  readable: boolean;
  retakeTips: string[];
  photoIssues: string[];
  sealed: boolean;
  productName: string | null;
  card: ScanCard | null;
  candidates: ScanCard[];
  condition: string;
  conditionLabel: string;
  conditionNotes: Record<string, string>;
  whiteDots: number | null;
  needsBackPhoto: boolean;
  grading: string | null;
  confidence: 'LOW' | 'MEDIUM' | 'HIGH';
  offerUnitUsd: number | null;
  frontPhotoUrl: string | null;
  backPhotoUrl: string | null;
}

export interface ChatCandidate {
  cardId: string;
  name: string;
  setName: string;
  cardNumber: string;
}

export type LineStatus = 'PENDING' | 'ELIGIBLE' | 'BELOW_MINIMUM' | 'NEEDS_PHOTO' | 'NEEDS_REVIEW' | 'UNIDENTIFIED';

export interface ChatLine {
  id: number;
  lineNo: number;
  kind: 'CARD' | 'BULK' | 'SEALED';
  name: string;
  setName: string | null;
  cardNumber: string | null;
  variant: string | null;
  condition: string | null;
  /** Slab grade such as "PSA 10"; see priceBasis for whether the price is for that grade. */
  grading: string | null;
  quantity: number;
  cardId: string | null;
  matchedName: string | null;
  matchedSet: string | null;
  matchedNumber: string | null;
  rarity: string | null;
  imageUrl: string | null;
  currency: string;
  unitMarketUsd: number | null;
  lineMarketUsd: number | null;
  eurTrend: number | null;
  priceUpdatedAt: string | null;
  priceSource: string | null;
  /** RAW = ungraded market price; GRADED = price for this exact grade; SEALED = sealed market price. */
  priceBasis: 'RAW' | 'GRADED' | 'SEALED' | null;
  /** Graded prices: recent eBay sales behind the median. */
  priceSampleSize?: number | null;
  /** Customer's asking price per card (sell deals). */
  requestedUnitUsd: number | null;
  /** Our cash offer per card at the published rates; null when we would not offer on it. */
  offerUnitUsd?: number | null;
  status: LineStatus;
  statusReason: string;
  photoRequired: boolean;
  photoUrl: string | null;
  candidates: ChatCandidate[];
}

export type DealType = 'SELL' | 'TRADE' | 'PARTIAL';

export interface StoreCard {
  productId: number;
  name: string;
  setName: string | null;
  cardNumber: string | null;
  condition: string | null;
  grading: string | null;
  imageUrl: string | null;
  priceCad: number | null;
  priceUsd: number | null;
  stock: number;
  available: boolean;
}

/** Deal in USD market terms, at the shop's published rates. Not an offer. */
export interface ChatDeal {
  dealType: DealType;
  currency: string;
  ratesText: string;
  offerableMarketUsd: number | null;
  cashOfferUsd: number | null;
  tradeCreditUsd: number | null;
  storeCards: StoreCard[];
  storeTotalUsd: number | null;
  storeTotalCad: number | null;
  usdCadRate: number | null;
  requestedCashUsd: number | null;
  askTotalUsd: number | null;
  askRatio: number | null;
  withinRules: boolean;
  message: string;
  needsStoreCards: boolean;
  overByUsd: number | null;
}

export interface ChatSummary {
  currency: string;
  totalMarketUsd: number | null;
  eligibleMarketUsd: number | null;
  statusCounts: Record<string, number>;
  likelihoodPct: number;
  likelihoodLabel: string;
  /** Missing only when talking to an older backend. */
  likelihoodDisclaimer?: string;
  likelihoodReasons: string[];
  contentHash: string;
  ready: boolean;
  canSubmitToday: boolean;
  dailyLimitNotice: string;
  quoteNotice: string;
  /** Missing only when talking to an older backend; the page must still render without it. */
  deal?: ChatDeal;
}

export interface ChatMessage {
  role: 'CUSTOMER' | 'ASSISTANT';
  content: string;
  createdAt: string;
}

export interface ChatDraft {
  draftId: string;
  status: 'OPEN' | 'SUBMITTED';
  maxLines: number;
  progress: { total: number; pending: number };
  lines: ChatLine[];
  summary: ChatSummary;
  messages: ChatMessage[];
}

export interface ConfirmResult {
  submissionId: number;
  trackingToken: string;
  likelihoodPct: number;
  currency: string;
  totalMarketUsd: number | null;
  quoteExpiresAt: string;
  quoteNotice: string;
}

export class ChatApiError extends Error {
  readonly status: number;

  constructor(message: string, status: number) {
    super(message);
    this.status = status;
  }
}

export function getSessionToken(): string | null {
  try {
    return sessionStorage.getItem(SESSION_KEY);
  } catch {
    return null;
  }
}

export function setSessionToken(token: string | null): void {
  try {
    if (token) sessionStorage.setItem(SESSION_KEY, token);
    else sessionStorage.removeItem(SESSION_KEY);
  } catch {
    // storage unavailable: the session lives only in memory for this page
  }
}

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers);
  const token = getSessionToken();
  if (token) headers.set('X-Buylist-Session', token);
  if (init.body && !(init.body instanceof FormData)) headers.set('Content-Type', 'application/json');

  const response = await fetch(`${API_BASE_URL}/api/buylist-chat${path}`, { ...init, headers });
  if (!response.ok) {
    if (response.status === 401) setSessionToken(null);
    let message = `Request failed (${response.status})`;
    try {
      const data = await response.json();
      if (data && (data.message || data.error)) message = data.message || data.error;
    } catch {
      // not json
    }
    throw new ChatApiError(message, response.status);
  }
  if (response.status === 202 || response.status === 204) return undefined as T;
  return response.json();
}

export const buylistChatApi = {
  status: () => request<ChatStatus>('/status'),
  requestCode: (email: string, turnstileToken?: string) =>
    request<void>('/otp/request', { method: 'POST', body: JSON.stringify({ email, turnstileToken }) }),
  verifyCode: async (email: string, code: string) => {
    const session = await request<{ sessionToken: string; email: string; expiresAt: string }>('/otp/verify', {
      method: 'POST',
      body: JSON.stringify({ email, code }),
    });
    setSessionToken(session.sessionToken);
    return session;
  },
  guestSession: async (turnstileToken?: string) => {
    const session = await request<{ sessionToken: string; expiresAt: string }>('/session/guest', {
      method: 'POST',
      body: JSON.stringify({ turnstileToken }),
    });
    setSessionToken(session.sessionToken);
    return session;
  },
  openDraft: () => request<ChatDraft>('/drafts', { method: 'POST' }),
  getDraft: (draftId: string) => request<ChatDraft>(`/drafts/${draftId}`),
  sendMessage: (draftId: string, message: string) =>
    request<{ reply: string; draft: ChatDraft }>(`/drafts/${draftId}/messages`, {
      method: 'POST',
      body: JSON.stringify({ message }),
    }),
  paste: (draftId: string, text: string) =>
    request<ChatDraft>(`/drafts/${draftId}/paste`, { method: 'POST', body: JSON.stringify({ text }) }),
  uploadCsv: (draftId: string, file: File) => {
    const form = new FormData();
    form.append('file', file);
    return request<ChatDraft>(`/drafts/${draftId}/csv`, { method: 'POST', body: form });
  },
  updateLine: (draftId: string, lineId: number,
    changes: { quantity?: number; condition?: string; variant?: string; requestedUnitUsd?: number; grading?: string }) =>
    request<ChatDraft>(`/drafts/${draftId}/lines/${lineId}`, { method: 'PATCH', body: JSON.stringify(changes) }),
  storeCards: (query: string) =>
    request<StoreCard[]>(`/store-cards?q=${encodeURIComponent(query)}`),
  setDeal: (draftId: string, dealType: DealType, requestedCashUsd?: number) =>
    request<ChatDraft>(`/drafts/${draftId}/deal`, {
      method: 'PATCH',
      body: JSON.stringify({ dealType, requestedCashUsd }),
    }),
  addTradeItem: (draftId: string, productId: number) =>
    request<ChatDraft>(`/drafts/${draftId}/trade-items`, { method: 'POST', body: JSON.stringify({ productId }) }),
  removeTradeItem: (draftId: string, productId: number) =>
    request<ChatDraft>(`/drafts/${draftId}/trade-items/${productId}`, { method: 'DELETE' }),
  removeLine: (draftId: string, lineId: number) =>
    request<ChatDraft>(`/drafts/${draftId}/lines/${lineId}`, { method: 'DELETE' }),
  uploadPhoto: (draftId: string, lineId: number, file: File) => {
    const form = new FormData();
    form.append('file', file);
    return request<ChatDraft>(`/drafts/${draftId}/lines/${lineId}/photo`, { method: 'POST', body: form });
  },
  scan: (draftId: string, front: Blob, back?: Blob | null) => {
    const form = new FormData();
    form.append('front', front, 'front.jpg');
    if (back) form.append('back', back, 'back.jpg');
    return request<ScanResult>(`/drafts/${draftId}/scan`, { method: 'POST', body: form });
  },
  addScan: (draftId: string, scanId: string, cardId: string | null, condition: string, quantity: number) =>
    request<ChatDraft>(`/drafts/${draftId}/scan/${scanId}/add`, {
      method: 'POST',
      body: JSON.stringify({ cardId, condition, quantity }),
    }),
  createPhoneLink: (draftId: string) =>
    request<PhoneLink>(`/drafts/${draftId}/phone-link`, { method: 'POST' }),
  phoneLinkStatus: (draftId: string, token: string) =>
    request<PhoneLinkStatus>(`/drafts/${draftId}/phone-link/${encodeURIComponent(token)}`),
  /** Phone side: these use only the link token, never a chat session. */
  phoneOpen: (token: string) =>
    request<{ usdCadRate: number | null; expiresAt: string }>(`/phone/${encodeURIComponent(token)}`),
  phoneScan: (token: string, front: Blob, back?: Blob | null) => {
    const form = new FormData()
    form.append('front', front, 'front.jpg')
    if (back) form.append('back', back, 'back.jpg')
    return request<ScanResult>(`/phone/${encodeURIComponent(token)}/scan`, { method: 'POST', body: form })
  },
  phoneAddScan: (token: string, scanId: string, cardId: string | null, condition: string, quantity: number) =>
    request<{ added: string }>(`/phone/${encodeURIComponent(token)}/scan/${scanId}/add`, {
      method: 'POST',
      body: JSON.stringify({ cardId, condition, quantity }),
    }),
  confirm: (draftId: string, contentHash: string, customerName: string, notes: string, email?: string) =>
    request<ConfirmResult>(`/drafts/${draftId}/confirm`, {
      method: 'POST',
      body: JSON.stringify({ contentHash, customerName, notes, email }),
    }),
};
