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

