import { API_BASE_URL } from './config';
import type { BuylistSubmission, BuylistSubmissionPayload, BuylistMessage, BuylistUploadResponse, BuylistStatus } from '../types';

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

const ADMIN_AUTH_KEY = 'tc_admin_auth';

/**
 * Admin credentials helper for Basic Auth.
 * Reads dynamically from sessionStorage or localStorage without any hardcoded credentials.
 */
export function getAdminAuthHeader(): string | null {
  return sessionStorage.getItem(ADMIN_AUTH_KEY) || localStorage.getItem(ADMIN_AUTH_KEY) || null;
}

export function setAdminAuth(username: string, password: string, rememberMe = true): string {
  const encoded = btoa(`${username.trim()}:${password.trim()}`);
  if (rememberMe) {
    localStorage.setItem(ADMIN_AUTH_KEY, encoded);
    sessionStorage.removeItem(ADMIN_AUTH_KEY);
  } else {
    sessionStorage.setItem(ADMIN_AUTH_KEY, encoded);
    localStorage.removeItem(ADMIN_AUTH_KEY);
  }
  return encoded;
}

export function setAdminAuthHeader(authHeader: string, rememberMe = true): void {
  if (rememberMe) {
    localStorage.setItem(ADMIN_AUTH_KEY, authHeader);
    sessionStorage.removeItem(ADMIN_AUTH_KEY);
  } else {
    sessionStorage.setItem(ADMIN_AUTH_KEY, authHeader);
    localStorage.removeItem(ADMIN_AUTH_KEY);
  }
}

export function clearAdminAuth(): void {
  localStorage.removeItem(ADMIN_AUTH_KEY);
  sessionStorage.removeItem(ADMIN_AUTH_KEY);
}

export function isAdminAuthenticated(): boolean {
  return Boolean(getAdminAuthHeader());
}

/**
 * Verifies admin credentials against GET /api/auth/verify using Basic Auth.
 */
export async function verifyAdminAuth(
  username?: string,
  password?: string
): Promise<{ authenticated: boolean; username: string; role?: string }> {
  let authHeader: string | null = null;
  if (username !== undefined && password !== undefined) {
    authHeader = btoa(`${username.trim()}:${password.trim()}`);
  } else {
    authHeader = getAdminAuthHeader();
  }

  if (!authHeader) {
    throw new Error('No admin credentials provided.');
  }

  const response = await fetch(`${API_BASE_URL}/api/auth/verify`, {
    headers: {
      'Authorization': `Basic ${authHeader}`,
    },
  });

  if (!response.ok) {
    if (response.status === 401 || response.status === 403) {
      throw new Error('Invalid credentials');
    }
    throw new Error(`Authentication verification failed (${response.status})`);
  }

  return response.json();
}

/**
 * Fetches buylist submissions for admin dashboard.
 */
export async function getAdminBuylistSubmissions(
  status?: string,
  page = 0,
  size = 100
): Promise<{ content: BuylistSubmission[]; totalElements: number; totalPages: number }> {
  const auth = getAdminAuthHeader();
  if (!auth) {
    throw new Error('Unauthorized: Admin credentials required.');
  }

  const params = new URLSearchParams();
  if (status && status !== 'ALL') {
    params.set('status', status);
  }
  params.set('page', page.toString());
  params.set('size', size.toString());
  params.set('sort', 'createdAt,desc');

  const response = await fetch(`${API_BASE_URL}/api/buylist/admin/submissions?${params.toString()}`, {
    headers: {
      'Authorization': `Basic ${auth}`,
    },
  });

  if (!response.ok) {
    if (response.status === 401 || response.status === 403) {
      clearAdminAuth();
      throw new Error('Unauthorized: Admin credentials required.');
    }
    let errorMsg = `Failed to fetch submissions (${response.status})`;
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
 * Updates submission status via PATCH /api/buylist/admin/submissions/{id}/status.
 */
export async function updateBuylistStatus(
  submissionId: number,
  status: BuylistStatus
): Promise<BuylistSubmission> {
  const auth = getAdminAuthHeader();
  if (!auth) {
    throw new Error('Unauthorized: Admin credentials required.');
  }

  const response = await fetch(`${API_BASE_URL}/api/buylist/admin/submissions/${submissionId}/status`, {
    method: 'PATCH',
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Basic ${auth}`,
    },
    body: JSON.stringify({ status }),
  });

  if (!response.ok) {
    if (response.status === 401 || response.status === 403) {
      clearAdminAuth();
      throw new Error('Unauthorized: Admin credentials required.');
    }
    let errorMsg = `Failed to update status (${response.status})`;
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
 * Posts an admin reply message to POST /api/buylist/admin/submissions/{id}/messages.
 */
export async function postAdminBuylistMessage(
  submissionId: number,
  message: string,
  senderEmail = 'admin@tailorcards.com'
): Promise<BuylistMessage> {
  const auth = getAdminAuthHeader();
  if (!auth) {
    throw new Error('Unauthorized: Admin credentials required.');
  }

  const response = await fetch(`${API_BASE_URL}/api/buylist/admin/submissions/${submissionId}/messages`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Basic ${auth}`,
    },
    body: JSON.stringify({
      message: message.trim(),
      senderEmail: senderEmail.trim(),
      senderRole: 'ADMIN',
    }),
  });

  if (!response.ok) {
    if (response.status === 401 || response.status === 403) {
      clearAdminAuth();
      throw new Error('Unauthorized: Admin credentials required.');
    }
    let errorMsg = `Failed to post message (${response.status})`;
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


