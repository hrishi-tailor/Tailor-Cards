import { useEffect, useState } from 'react'
import type { PageResponse, Product } from '../types'
import './ProductList.css'

export function ProductList() {
  const [products, setProducts] = useState<Product[]>([])
  const [loading, setLoading] = useState<boolean>(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    const fetchProducts = async () => {
      try {
        setLoading(true)
        setError(null)
        const response = await fetch('/api/products')
        if (!response.ok) {
          throw new Error(`Failed to fetch products: ${response.status} ${response.statusText}`)
        }
        const data: PageResponse<Product> = await response.json()
        setProducts(data.content || [])
      } catch (err) {
        setError(err instanceof Error ? err.message : 'An unexpected error occurred')
      } finally {
        setLoading(false)
      }
    }

    fetchProducts()
  }, [])

  if (loading) {
    return (
      <div className="status-container">
        <p>Loading products...</p>
      </div>
    )
  }

  if (error) {
    return (
      <div className="status-container error">
        <h2>Unable to load products</h2>
        <p>{error}</p>
        <button type="button" onClick={() => window.location.reload()} className="retry-btn">
          Retry
        </button>
      </div>
    )
  }

  if (products.length === 0) {
    return (
      <div className="status-container">
        <p>No products available.</p>
      </div>
    )
  }

  return (
    <div className="product-list-container">
      <header className="product-list-header">
        <h1>Tailor Cards Catalog</h1>
        <p>Browse our collection of trading cards, accessories, and graded items.</p>
      </header>

      <div className="product-grid">
        {products.map((product) => (
          <article key={product.id} className="product-card">
            {product.imageUrl && (
              <div className="product-image-wrapper">
                <img
                  src={product.imageUrl}
                  alt={product.name}
                  className="product-image"
                  loading="lazy"
                />
              </div>
            )}
            <div className="product-content">
              {product.category && (
                <span className="category-tag">{product.category.name}</span>
              )}
              <h2 className="product-title">{product.name}</h2>
              <p className="product-description">{product.description}</p>
              <div className="product-footer">
                <span className="product-price">
                  ${typeof product.price === 'number' ? product.price.toFixed(2) : product.price}
                </span>
                <span className={`stock-badge ${product.stock > 0 ? 'in-stock' : 'out-of-stock'}`}>
                  {product.stock > 0 ? `${product.stock} in stock` : 'Out of stock'}
                </span>
              </div>
            </div>
          </article>
        ))}
      </div>
    </div>
  )
}
