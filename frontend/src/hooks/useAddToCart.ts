import { useCallback, useEffect, useRef, useState } from 'react'
import { useCart } from '../context/CartContext'

type AddState = 'idle' | 'adding' | 'added' | 'error'

/** Add-to-cart with a short "Added to cart" confirmation. */
export function useAddToCart() {
  const { addToCart } = useCart()
  const [state, setState] = useState<AddState>('idle')
  const timer = useRef<number | undefined>(undefined)

  useEffect(() => () => window.clearTimeout(timer.current), [])

  const add = useCallback(async (productId: number) => {
    setState('adding')
    try {
      await addToCart(productId, 1)
      setState('added')
      window.clearTimeout(timer.current)
      timer.current = window.setTimeout(() => setState('idle'), 1800)
    } catch {
      setState('error')
    }
  }, [addToCart])

  return { add, state }
}
