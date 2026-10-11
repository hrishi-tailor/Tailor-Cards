export interface Category {
  id: number;
  name: string;
  description?: string;
}

export interface Product {
  id: number;
  name: string;
  description: string;
  price: number;
  imageUrl?: string;
  stock: number;
  category?: Category;
  cardNumber?: string;
  set?: string;
  condition?: string;
  grading?: string;
  status?: 'AVAILABLE' | 'SOLD';
  /** Linked card id (TCGdex), used for the official image and prices. */
  pokemontcgId?: string | null;
  /** The seller's own photos, shown after the default picture (imageUrl). */
  photoUrls?: string[];
}

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
}

export interface CartItem {
  id: number;
  productId: number;
  productName: string;
  price: number;
  imageUrl?: string;
  quantity: number;
  subtotal: number;
}

export interface CartResponse {
  cartSessionId: string;
  items: CartItem[];
  totalPrice: number;
  totalItems: number;
}

export type BuylistStatus = 'PENDING' | 'UNDER_REVIEW' | 'OFFERED' | 'ACCEPTED' | 'REJECTED';

export interface BuylistMessage {
  id: number;
  senderRole: 'CUSTOMER' | 'ADMIN' | string;
  senderEmail: string;
  message: string;
  createdAt: string;
}

export interface BuylistSubmission {
  id: number;
  trackingToken: string;
  customerEmail: string;
  customerName?: string;
  cardName: string;
  cardSet?: string;
  askingPrice?: number;
  additionalComments?: string;
  imageUrls: string[];
  status: BuylistStatus;
  createdAt: string;
  messages: BuylistMessage[];
  /** Present for submissions from the AI buylist chat. Amounts are USD market references. */
  chatDetails?: BuylistChatDetails | null;
}

export interface BuylistChatSubmissionLine {
  lineNo: number;
  kind: 'CARD' | 'BULK';
  name: string;
  setName?: string | null;
  cardNumber?: string | null;
  variant?: string | null;
  condition?: string | null;
  grading?: string | null;
  quantity: number;
  cardId?: string | null;
  matchedName?: string | null;
  matchedSet?: string | null;
  rarity?: string | null;
  imageUrl?: string | null;
  currency: string;
  unitMarketUsd?: number | null;
  lineMarketUsd?: number | null;
  eurTrend?: number | null;
  priceUpdatedAt?: string | null;
  priceBasis?: string | null;
  requestedUnitUsd?: number | null;
  status: string;
  statusReason?: string | null;
  photoUrl?: string | null;
}

export interface BuylistChatDetails {
  source: string;
  currency: string;
  totalMarketUsd?: number | null;
  eligibleMarketUsd?: number | null;
  likelihoodPct?: number | null;
  likelihoodLabel?: string | null;
  likelihoodReasons?: string[];
  quoteExpiresAt?: string | null;
  lineCount?: number | null;
  lines?: BuylistChatSubmissionLine[] | null;
  redFlags?: string[] | null;
  transcript?: { role: string; content: string; at: string }[] | null;
  ownerDecision?: string | null;
  counterAmountUsd?: number | null;
  dealType?: 'SELL' | 'TRADE' | 'PARTIAL' | null;
  requestedCashUsd?: number | null;
  storeCards?: { productId: number; name: string; priceCad: number; priceUsd: number }[] | null;
  storeTotalUsd?: number | null;
  cashOfferUsd?: number | null;
  tradeCreditUsd?: number | null;
  askRatio?: number | null;
  usdCadRate?: number | null;
}

export interface BuylistSubmissionPayload {
  customerEmail: string;
  customerName?: string;
  cardName: string;
  cardSet?: string;
  askingPrice?: number;
  additionalComments?: string;
  imageUrls: string[];
}

export interface BuylistUploadResponse {
  url: string;
}

export type PriceRange = '1M' | '3M' | '1Y';

export interface PricePoint {
  date: string;
  price: number;
  volume?: number;
}

export interface PriceHistoryData {
  productId: number;
  productName: string;
  cardSet?: string;
  cardNumber?: string;
  condition?: string;
  grading?: string;
  range: PriceRange;
  currency: string;
  currentPrice: number;
  periodLow: number;
  periodHigh: number;
  changeAmount: number;
  changePercentage: number;
  sourceLabel?: string;
  isSampleData?: boolean;
  trackingStartDate?: string;
  snapshotCount?: number;
  history: PricePoint[];
}

export function isGradedOrSealed(product?: {
  grading?: string | null;
  condition?: string | null;
  category?: { name?: string | null } | null;
  name?: string | null;
}): boolean {
  if (!product) return false;
  const gradingUpper = (product.grading || '').trim().toUpperCase();
  const isGraded = Boolean(
    (gradingUpper && gradingUpper !== 'RAW' && gradingUpper !== 'UNGRADED') ||
    /\b(PSA\s*\d+|BGS\s*[\d\.]+|CGC\s*[\d\.]+|PSA|BGS|CGC)\b/i.test(product.name || '')
  );

  const condUpper = (product.condition || '').trim().toUpperCase();
  const catUpper = (product.category?.name || '').trim().toUpperCase();
  const isSealed = Boolean(
    condUpper === 'SEALED' ||
    catUpper.includes('SEALED') ||
    /\b(sealed|booster box|etb|elite trainer box|blister pack)\b/i.test(product.name || '')
  );

  return isGraded || isSealed;
}

export type TradeFlowType = 'SELL' | 'TRADE';
export type TradeDecision = 'ACCEPT' | 'COUNTER' | 'DECLINE' | 'NEEDS_REVIEW';

export interface CustomerCardItem {
  name: string;
  set?: string;
  cardNumber?: string;
  pokemontcgId?: string;
  condition?: string;
  grading?: string;
  isSealed?: boolean;
  quantity?: number;
  marketPriceCad?: number;
  imageUrl?: string;
  confirmed?: boolean;
}

export interface StoreCardItem {
  productId: number;
  name: string;
  listPriceCad: number;
  quantity: number;
}

export interface TradeChatMessage {
  role: 'user' | 'assistant';
  content: string;
}

export interface TradeConversationRequest {
  conversationId?: string;
  flowType: TradeFlowType;
  messages: TradeChatMessage[];
  confirmedItems?: CustomerCardItem[];
  storeProductIds?: number[];
}

export interface TradeConversationResponse {
  conversationId: string;
  reply: string;
  extractedItems: CustomerCardItem[];
  requiresConfirmation: boolean;
}

export interface TradeQuoteRequest {
  flowType: TradeFlowType;
  customerCards: CustomerCardItem[];
  storeProductIds?: number[];
}

export interface TradeQuoteResponse {
  flowType: TradeFlowType;
  decision: TradeDecision;
  cashOffer?: number;
  tradeCredit?: number;
  counterTopUp?: number;
  customerTotalMarketCad?: number;
  storeTotalListPriceCad?: number;
  explanation: string;
  ruleTrace?: any;
  customerCards: CustomerCardItem[];
  storeCards: StoreCardItem[];
}

export interface TradeSubmissionPayload {
  quote: TradeQuoteRequest;
  customerName: string;
  customerEmail: string;
  customerPhone?: string;
  customerNotes?: string;
}

export interface TradeSubmissionResponse {
  id: number;
  referenceCode: string;
  status: string;
  flowType: TradeFlowType;
  decision: TradeDecision;
  offeredAmount?: number;
  counterTopUp?: number;
  customerTotalMarketCad?: number;
  storeTotalListPriceCad?: number;
  customerName: string;
  customerEmail: string;
  explanation?: string;
  createdAt: string;
}


// ---- Admin Listing Generator ----

export type ListingCondition =
  | 'NEAR_MINT'
  | 'LIGHTLY_PLAYED'
  | 'MODERATELY_PLAYED'
  | 'HEAVILY_PLAYED'
  | 'DAMAGED'
  | 'UNKNOWN';

export type ListingConfidence = 'LOW' | 'MEDIUM' | 'HIGH';

/** Text-only draft read from photos. Never contains a price. */
export interface ListingDraft {
  cardName: string;
  setName: string | null;
  cardNumber: string | null;
  rarity: string | null;
  language: string | null;
  condition: ListingCondition;
  gradingCompany: string | null;
  grade: string | null;
  isSealed: boolean;
  title: string;
  description: string;
  conditionNotes: string | null;
  confidence: ListingConfidence;
  uncertainties: string[];
}

export interface ListingCardCandidate {
  cardId: string;
  name: string | null;
  setName: string | null;
  cardNumber: string | null;
  imageUrl: string | null;
}

export interface ListingMarketReference {
  cardId: string;
  name: string | null;
  setName: string | null;
  cardNumber: string | null;
  marketReferenceCad: number | null;
  stockImageUrl: string | null;
}

export interface ListingDraftResponse {
  draft: ListingDraft;
  matchStatus: 'MATCHED' | 'AMBIGUOUS' | 'NONE';
  marketReference: ListingMarketReference | null;
  candidates: ListingCardCandidate[];
}

export interface CreateProductPayload {
  name: string;
  description: string;
  price: number;
  imageUrl: string | null;
  stock: number;
  categoryId: number;
  cardNumber: string | null;
  set: string | null;
  condition: string | null;
  grading: string | null;
  pokemontcgId: string | null;
  /** The seller's own photos, shown after the stock image. */
  photoUrls?: string[];
}
