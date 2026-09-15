/**
 * API configuration for Tailor Cards.
 * Uses VITE_API_URL (or VITE_API_BASE_URL) from environment variables,
 * falling back to the Render production URL if undefined.
 */
const rawBaseUrl =
  import.meta.env.VITE_API_URL ||
  import.meta.env.VITE_API_BASE_URL ||
  'https://tailor-cards-api.onrender.com'

export const API_BASE_URL = rawBaseUrl.replace(/\/+$/, '')

