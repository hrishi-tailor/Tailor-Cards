import { createContext, useContext, useEffect, useState, type ReactNode } from 'react'
import type { CartResponse } from '../types'
import { getCartSessionId } from '../utils/cartSession'

interface CartContextType {
  cartSessionId: string
  cart: CartResponse | null
  loading: boolean
  error: string | null
  totalItems: number
  totalPrice: number
  addToCart: (productId: number, quantity?: number) => Promise<void>
  removeFromCart: (itemId: number) => Promise<void>
  refreshCart: () => Promise<void>
}

const CartContext = createContext<CartContextType | undefined>(undefined)

export function CartProvider({ children }: { children: ReactNode }) {
  const [cartSessionId] = useState<string>(() => getCartSessionId())
  const [cart, setCart] = useState<CartResponse | null>(null)
  const [loading, setLoading] = useState<boolean>(true)
  const [error, setError] = useState<string | null>(null)

  const fetchCart = async () => {
    try {
      setLoading(true)
      setError(null)
      const response = await fetch(`/api/cart/${cartSessionId}`)
      if (!response.ok) {
        throw new Error(`Failed to fetch cart: ${response.statusText}`)
      }
      const data: CartResponse = await response.json()
      setCart(data)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Error fetching cart')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    fetchCart()
  }, [cartSessionId])

  const addToCart = async (productId: number, quantity = 1) => {
    try {
      setError(null)
      const response = await fetch(`/api/cart/${cartSessionId}`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ productId, quantity }),
      })

      if (!response.ok) {
        const errData = await response.json().catch(() => null)
        const errMsg = errData?.message || `Failed to add item to cart (${response.status})`
        throw new Error(errMsg)
      }

      const updatedCart: CartResponse = await response.json()
      setCart(updatedCart)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Error adding to cart')
      throw err
    }
  }

  const removeFromCart = async (itemId: number) => {
    try {
      setError(null)
      const response = await fetch(`/api/cart/${cartSessionId}/items/${itemId}`, {
        method: 'DELETE',
      })

      if (!response.ok) {
        throw new Error(`Failed to remove item (${response.status})`)
      }

      // Refresh cart state to recalculate total and items
      await fetchCart()
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Error removing item')
      throw err
    }
  }

  const totalItems = cart?.totalItems ?? 0
  const totalPrice = cart?.totalPrice ?? 0

  return (
    <CartContext.Provider
      value={{
        cartSessionId,
        cart,
        loading,
        error,
        totalItems,
        totalPrice,
        addToCart,
        removeFromCart,
        refreshCart: fetchCart,
      }}
    >
      {children}
    </CartContext.Provider>
  )
}

export function useCart(): CartContextType {
  const context = useContext(CartContext)
  if (!context) {
    throw new Error('useCart must be used within a CartProvider')
  }
  return context
}
