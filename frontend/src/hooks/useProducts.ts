import { useCallback, useEffect, useState } from 'react'
import { API_BASE_URL } from '../api/config'
import type { PageResponse, Product } from '../types'

const PAGE_SIZE = 100

/** All products, every page of /api/products, loaded once per mount (and on reload()). */
export function useProducts() {
  const [products, setProducts] = useState<Product[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [version, setVersion] = useState(0)

  useEffect(() => {
    let cancelled = false
    const load = async () => {
      try {
        const first = await fetchPage(0)
        let all = first.content ?? []
        if (first.totalPages > 1) {
          const rest = await Promise.all(
            Array.from({ length: first.totalPages - 1 }, (_, i) => fetchPage(i + 1)))
          all = all.concat(...rest.map((p) => p.content ?? []))
        }
        if (!cancelled) setProducts(all)
      } catch {
        if (!cancelled) setError("We couldn't load the shop right now. Refresh the page to try again.")
      } finally {
        if (!cancelled) setLoading(false)
      }
    }
    load()
    return () => { cancelled = true }
  }, [version])

  /** Loads the products again (e.g. after an admin change). */
  const reload = useCallback(() => setVersion((v) => v + 1), [])

  return { products, loading, error, reload }
}

async function fetchPage(page: number): Promise<PageResponse<Product>> {
  const res = await fetch(`${API_BASE_URL}/api/products?page=${page}&size=${PAGE_SIZE}`)
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
  return res.json()
}
