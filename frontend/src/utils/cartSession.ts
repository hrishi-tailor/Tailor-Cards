const CART_SESSION_KEY = 'tc_cart_session_id'

/**
 * Retrieves the guest cartSessionId from localStorage.
 * If one does not exist, generates a new UUID via crypto.randomUUID()
 * and persists it to localStorage.
 */
export function getCartSessionId(): string {
  let sessionId = localStorage.getItem(CART_SESSION_KEY)
  if (!sessionId) {
    sessionId = crypto.randomUUID()
    localStorage.setItem(CART_SESSION_KEY, sessionId)
  }
  return sessionId
}
