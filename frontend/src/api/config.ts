/**
 * API configuration for Tailor Cards.
 * In production (e.g. deployed on Render), set VITE_API_BASE_URL to your backend URL
 * (e.g. https://tailor-cards-api.onrender.com).
 * In development, leaving it unset defaults to '' (empty string), allowing Vite's dev proxy
 * to route `/api` calls directly to http://localhost:8080.
 */
const rawBaseUrl = import.meta.env.VITE_API_BASE_URL || ''
export const API_BASE_URL = rawBaseUrl.replace(/\/+$/, '')
