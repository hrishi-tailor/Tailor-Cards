import { API_BASE_URL } from './config'

export interface CheckoutSessionResponse {
  url: string
  sessionId: string
}

export async function createCheckoutSession(
  cartId: string,
  productIds?: number[]
): Promise<CheckoutSessionResponse> {
  const response = await fetch(`${API_BASE_URL}/api/checkout/create-session`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({
      cartId,
      productIds,
    }),
  })

  if (!response.ok) {
    const errorData = await response.json().catch(() => null)
    const message = errorData?.message || `Checkout failed with status ${response.status}`
    throw new Error(message)
  }

  return response.json()
}
