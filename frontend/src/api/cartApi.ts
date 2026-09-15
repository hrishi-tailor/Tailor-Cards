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
    const message =
      errorData?.message ||
      errorData?.error ||
      `Checkout failed with status ${response.status}`
    throw new Error(message)
  }

  const data: CheckoutSessionResponse = await response.json()
  if (!data || !data.url || typeof data.url !== 'string' || data.url.trim() === '') {
    throw new Error('No checkout URL returned from payment server.')
  }

  return data
}

