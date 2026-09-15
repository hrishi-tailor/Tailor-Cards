import { API_BASE_URL } from './config';
import type { BuylistSubmission, BuylistSubmissionPayload, BuylistMessage, BuylistUploadResponse } from '../types';

/**
 * Resolves an image URL to an absolute URL if needed (handles relative /uploads paths).
 */
export function resolveImageUrl(url?: string): string {
  if (!url) return '';
  if (url.startsWith('http://') || url.startsWith('https://') || url.startsWith('blob:')) {
    return url;
  }
  if (url.startsWith('/')) {
    return `${API_BASE_URL}${url}`;
  }
  return `${API_BASE_URL}/${url}`;
}

/**
 * Uploads a single card image file to the backend buylist upload endpoint.
 */
export async function uploadBuylistImage(file: File): Promise<string> {
  const formData = new FormData();
  formData.append('file', file);

  const response = await fetch(`${API_BASE_URL}/api/buylist/upload`, {
    method: 'POST',
    body: formData,
  });

  if (!response.ok) {
    let errorMsg = `Upload failed with status ${response.status}`;
    try {
      const data = await response.json();
      if (data && (data.message || data.error)) {
        errorMsg = data.message || data.error;
      }
    } catch {
      // response body was not json
    }
    throw new Error(errorMsg);
  }

  const data: BuylistUploadResponse = await response.json();
  return data.url;
}

/**
 * Submits a new buylist submission request to POST /api/buylist/submit.
 */
export async function submitBuylist(payload: BuylistSubmissionPayload): Promise<BuylistSubmission> {
  const response = await fetch(`${API_BASE_URL}/api/buylist/submit`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(payload),
  });

  if (!response.ok) {
    let errorMsg = `Submission failed with status ${response.status}`;
    try {
      const data = await response.json();
      if (data && (data.message || data.error)) {
        errorMsg = data.message || data.error;
      }
    } catch {
      // response body was not json
    }
    throw new Error(errorMsg);
  }

  return response.json();
}

/**
 * Fetches submission details by trackingToken from GET /api/buylist/track/{token}.
 */
export async function getBuylistSubmission(trackingToken: string): Promise<BuylistSubmission> {
  const cleanToken = trackingToken.trim();
  const response = await fetch(`${API_BASE_URL}/api/buylist/track/${encodeURIComponent(cleanToken)}`);

  if (!response.ok) {
    if (response.status === 404) {
      throw new Error('Submission not found. Please verify your tracking link or token.');
    }
    let errorMsg = `Failed to fetch tracking details (${response.status})`;
    try {
      const data = await response.json();
      if (data && (data.message || data.error)) {
        errorMsg = data.message || data.error;
      }
    } catch {
      // not json
    }
    throw new Error(errorMsg);
  }

  return response.json();
}

/**
 * Posts a customer reply message to POST /api/buylist/{trackingToken}/messages.
 */
export async function postBuylistMessage(
  trackingToken: string,
  message: string,
  senderEmail?: string
): Promise<BuylistMessage> {
  const cleanToken = trackingToken.trim();
  const response = await fetch(`${API_BASE_URL}/api/buylist/${encodeURIComponent(cleanToken)}/messages`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({
      message: message.trim(),
      senderEmail: senderEmail ? senderEmail.trim() : undefined,
      senderRole: 'CUSTOMER',
    }),
  });

  if (!response.ok) {
    let errorMsg = `Failed to post message (${response.status})`;
    try {
      const data = await response.json();
      if (data && (data.message || data.error)) {
        errorMsg = data.message || data.error;
      }
    } catch {
      // not json
    }
    throw new Error(errorMsg);
  }

  return response.json();
}
