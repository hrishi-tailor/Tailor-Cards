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

