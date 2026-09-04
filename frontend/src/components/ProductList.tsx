import { useEffect, useState } from 'react'
import { useCart } from '../context/CartContext'
import type { PageResponse, Product } from '../types'
import './ProductList.css'

export function ProductList() {
  const [products, setProducts] = useState<Product[]>([])
  const [loading, setLoading] = useState<boolean>(true)
  const [error, setError] = useState<string | null>(null)
  const [addingId, setAddingId] = useState<number | null>(null)
  const [addedId, setAddedId] = useState<number | null>(null)
  const [cartError, setCartError] = useState<string | null>(null)

  const { addToCart } = useCart()

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

  const handleAddToCart = async (product: Product) => {
    try {
      setAddingId(product.id)
      setCartError(null)
      await addToCart(product.id, 1)
      setAddedId(product.id)
      setTimeout(() => {
        setAddedId((current) => (current === product.id ? null : current))
      }, 1500)
    } catch (err) {
      setCartError(err instanceof Error ? err.message : 'Failed to add item to cart')
    } finally {
      setAddingId(null)
    }
  }

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

      {cartError && (
        <div style={{ color: '#dc2626', background: '#fef2f2', padding: 12, borderRadius: 8, marginBottom: 20 }}>
          {cartError}
        </div>
      )}

      <div className="product-grid">
        {products.map((product) => {
          const isOutOfStock = product.stock <= 0
          const isAdding = addingId === product.id
          const isJustAdded = addedId === product.id

          return (
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
                  <span className={`stock-badge ${!isOutOfStock ? 'in-stock' : 'out-of-stock'}`}>
                    {!isOutOfStock ? `${product.stock} in stock` : 'Out of stock'}
                  </span>
                </div>
                <button
                  type="button"
                  onClick={() => handleAddToCart(product)}
                  disabled={isOutOfStock || isAdding}
                  className={`add-to-cart-btn ${isJustAdded ? 'added' : ''}`}
                >
                  {isOutOfStock
                    ? 'Out of Stock'
                    : isAdding
                    ? 'Adding...'
                    : isJustAdded
                    ? '✓ Added to Cart'
                    : 'Add to Cart'}
                </button>
              </div>
            </article>
          )
        })}
      </div>
    </div>
  )
}
