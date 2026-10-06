import { API_BASE_URL } from './config';
import type {
  Product,
  TradeConversationRequest,
  TradeConversationResponse,
  TradeQuoteRequest,
  TradeQuoteResponse,
  TradeSubmissionPayload,
  TradeSubmissionResponse,
} from '../types';

/**
 * Sends a conversation message turn to the Trade Assistant backend.
 */
export async function sendTradeMessage(
  payload: TradeConversationRequest
): Promise<TradeConversationResponse> {
  const response = await fetch(`${API_BASE_URL}/api/trade-assistant/messages`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(payload),
  });

  if (!response.ok) {
    let errorMsg = `Assistant unavailable (${response.status})`;
    try {
      const data = await response.json();
      if (data && (data.message || data.error)) {
        errorMsg = data.message || data.error;
      }
    } catch {
      // not JSON
    }
    throw new Error(errorMsg);
  }

  return response.json();
}

/**
 * Computes deterministic cash offer or trade quote via pure pricing engine.
 */
export async function getTradeQuote(
  payload: TradeQuoteRequest
): Promise<TradeQuoteResponse> {
  const response = await fetch(`${API_BASE_URL}/api/trade-assistant/quote`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(payload),
  });

  if (!response.ok) {
    let errorMsg = `Failed to compute quote (${response.status})`;
    try {
      const data = await response.json();
      if (data && (data.message || data.error)) {
        errorMsg = data.message || data.error;
      }
    } catch {}
    throw new Error(errorMsg);
  }

  return response.json();
}

/**
 * Submits the customer's confirmed quote for store review and appraisal.
 */
export async function submitTradeRequest(
  payload: TradeSubmissionPayload
): Promise<TradeSubmissionResponse> {
  const response = await fetch(`${API_BASE_URL}/api/trade-assistant/requests`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(payload),
  });

  if (!response.ok) {
    let errorMsg = `Submission failed (${response.status})`;
    try {
      const data = await response.json();
      if (data && (data.message || data.error)) {
        errorMsg = data.message || data.error;
      }
    } catch {}
    throw new Error(errorMsg);
  }

  return response.json();
}

/**
 * Fetches store inventory items for trade selection.
 */
export async function fetchStoreProducts(): Promise<Product[]> {
  const response = await fetch(`${API_BASE_URL}/api/products`);
  if (!response.ok) {
    return [];
  }
  const data = await response.json();
  if (Array.isArray(data)) {
    return data;
  }
  if (data && Array.isArray(data.content)) {
    return data.content;
  }
  return [];
}
