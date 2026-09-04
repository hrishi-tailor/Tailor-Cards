import { useState } from 'react'
import { getCartSessionId } from '../utils/cartSession'

/**
 * Custom hook to access the persistent guest cartSessionId.
 */
export function useCartSession(): string {
  const [sessionId] = useState<string>(() => getCartSessionId())
  return sessionId
}
