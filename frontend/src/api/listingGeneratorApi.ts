import { API_BASE_URL } from './config';
import { clearAdminAuth, getAdminAuthHeader } from './buylistApi';
import type {
  Category,
  CreateProductPayload,
  ListingCondition,
  ListingDraftResponse,
  ListingMarketReference,
  Product,
} from '../types';

/** Error carrying the HTTP status so the UI can tell upstream failures (502/503) from bad input. */
export class ListingApiError extends Error {
  readonly status: number;

  constructor(message: string, status: number) {
    super(message);
    this.status = status;
  }
}

function authHeader(): string {
  const auth = getAdminAuthHeader();
  if (!auth) {
    throw new ListingApiError('Unauthorized: Admin credentials required.', 401);
  }
  return `Basic ${auth}`;
}

async function readError(response: Response, fallback: string): Promise<ListingApiError> {
  if (response.status === 401) {
    clearAdminAuth();
    return new ListingApiError('Your session expired. Please log in again.', 401);
  }
  if (response.status === 403) {
    return new ListingApiError('Only admins can do this.', 403);
  }
  let message = fallback;
  try {
    const data = await response.json();
    if (data && (data.message || data.error)) {
      message = data.message || data.error;
    }
  } catch {
    // body was not json
  }
  return new ListingApiError(message, response.status);
}

/** POST /api/admin/listing-generator/draft. Photos are sent for reading only; the server does not store them. */
export async function generateListingDraft(files: File[], note: string): Promise<ListingDraftResponse> {
  const formData = new FormData();
  files.forEach((file) => formData.append('images', file));
  if (note.trim()) {
    formData.append('note', note.trim());
  }

  const response = await fetch(`${API_BASE_URL}/api/admin/listing-generator/draft`, {
    method: 'POST',
    headers: { Authorization: authHeader() },
    body: formData,
  });
  if (!response.ok) {
    throw await readError(response, `Draft failed (${response.status})`);
  }
  return response.json();
}

export async function getListingMarketReference(params: {
  cardId: string;
  condition: ListingCondition;
  gradingCompany: string;
  grade: string;
  sealed: boolean;
}): Promise<ListingMarketReference> {
  const query = new URLSearchParams({ cardId: params.cardId, sealed: String(params.sealed) });
  if (params.condition) query.set('condition', params.condition);
  if (params.gradingCompany.trim() && params.grade.trim()) {
    query.set('gradingCompany', params.gradingCompany.trim());
    query.set('grade', params.grade.trim());
  }

  const response = await fetch(`${API_BASE_URL}/api/admin/listing-generator/market-reference?${query}`, {
    headers: { Authorization: authHeader() },
  });
  if (!response.ok) {
    throw await readError(response, `Market reference failed (${response.status})`);
  }
  return response.json();
}

export async function getCategories(): Promise<Category[]> {
  const response = await fetch(`${API_BASE_URL}/api/categories`);
  if (!response.ok) {
    throw await readError(response, 'Could not load categories');
  }
  const data = await response.json();
  return Array.isArray(data) ? data : data.content ?? [];
}

/** Stores one product photo (location data stripped) and returns its URL: POST /api/products/photos (ADMIN). */
export async function uploadProductPhoto(file: File): Promise<string> {
  const formData = new FormData();
  formData.append('file', file);
  const response = await fetch(`${API_BASE_URL}/api/products/photos`, {
    method: 'POST',
    headers: { Authorization: authHeader() },
    body: formData,
  });
  if (!response.ok) {
    throw await readError(response, `Photo upload failed (${response.status})`);
  }
  const data: { url: string } = await response.json();
  return data.url;
}

/** Existing product endpoint: POST /api/products (ADMIN). */
export async function createProduct(payload: CreateProductPayload): Promise<Product> {
  const response = await fetch(`${API_BASE_URL}/api/products`, {
    method: 'POST',
    headers: { Authorization: authHeader(), 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  });
  if (!response.ok) {
    throw await readError(response, `Could not create product (${response.status})`);
  }
  return response.json();
}

/** Adds photos to an existing product, after its current ones: POST /api/products/{id}/photos (ADMIN). */
export async function addProductPhotos(productId: number, files: File[]): Promise<Product> {
  const formData = new FormData();
  files.forEach((f) => formData.append('files', f));
  const response = await fetch(`${API_BASE_URL}/api/products/${productId}/photos`, {
    method: 'POST',
    headers: { Authorization: authHeader() },
    body: formData,
  });
  if (!response.ok) {
    throw await readError(response, `Photo upload failed (${response.status})`);
  }
  return response.json();
}

/** Removes the photo at a position: DELETE /api/products/{id}/photos/{index} (ADMIN). */
export async function removeProductPhoto(productId: number, index: number): Promise<Product> {
  const response = await fetch(`${API_BASE_URL}/api/products/${productId}/photos/${index}`, {
    method: 'DELETE',
    headers: { Authorization: authHeader() },
  });
  if (!response.ok) {
    throw await readError(response, `Could not remove the photo (${response.status})`);
  }
  return response.json();
}

/** Makes the official card image the default picture: POST /api/products/{id}/official-image (ADMIN). */
export async function applyOfficialProductImage(productId: number): Promise<Product> {
  const response = await fetch(`${API_BASE_URL}/api/products/${productId}/official-image`, {
    method: 'POST',
    headers: { Authorization: authHeader() },
  });
  if (!response.ok) {
    throw await readError(response, `No official image found (${response.status})`);
  }
  return response.json();
}

export interface OfficialImageBackfill {
  updated: number;
  unmatched: string[];
}

/** Official images for every product that doesn't show one yet: POST /api/products/official-images (ADMIN). */
export async function applyOfficialImagesForAll(): Promise<OfficialImageBackfill> {
  const response = await fetch(`${API_BASE_URL}/api/products/official-images`, {
    method: 'POST',
    headers: { Authorization: authHeader() },
  });
  if (!response.ok) {
    throw await readError(response, `Could not update images (${response.status})`);
  }
  return response.json();
}
