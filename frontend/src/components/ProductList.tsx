import { useEffect, useState } from 'react'
import { useCart } from '../context/CartContext'
import type { PageResponse, Product } from '../types'
import './ProductList.css'

interface ProductListProps {
  selectedCategory?: string
}

export function ProductList({ selectedCategory = 'All' }: ProductListProps) {
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

  // Filter products by selected category
  const filteredProducts = products.filter((product) => {
    if (selectedCategory === 'All') return true
    if (selectedCategory === 'Singles') {
      return (
        product.category?.name.toLowerCase().includes('trading') ||
        product.name.toLowerCase().includes('card')
      )
    }
    if (selectedCategory === 'Slabs') {
      return (
        product.category?.name.toLowerCase().includes('graded') ||
        product.name.toLowerCase().includes('psa') ||
        product.name.toLowerCase().includes('bgs')
      )
    }
    if (selectedCategory === 'Accessories') {
      return (
        product.category?.name.toLowerCase().includes('accessories') ||
        product.name.toLowerCase().includes('sleeves') ||
        product.name.toLowerCase().includes('box') ||
        product.name.toLowerCase().includes('binder')
      )
    }
    return product.category?.name.toLowerCase().includes(selectedCategory.toLowerCase())
  })

  if (loading) {
    return (
      <div className="tc-catalog-wrapper">
        <div className="tc-status-box">
          <div className="tc-spinner" />
          <p>Loading curated trading cards & collectibles...</p>
        </div>
      </div>
    )
  }

  if (error) {
    return (
      <div className="tc-catalog-wrapper">
        <div className="tc-status-box">
          <h2>Unable to load catalog</h2>
          <p>{error}</p>
          <button type="button" onClick={() => window.location.reload()} className="tc-retry-btn">
            Try Again
          </button>
        </div>
      </div>
    )
  }

  return (
    <div className="tc-catalog-wrapper">
      {/* Boutique Hero Header */}
      <section className="tc-hero-header">
        <div className="tc-hero-title-group">
          <h1>Curated Trading Cards & Graded Slabs</h1>
          <p>Authentic singles, certified collectibles, and archival-grade protection supplies.</p>
        </div>
        <div className="tc-hero-pill">
          ✦ Guaranteed Authenticity
        </div>
      </section>

      {/* Catalog Meta / Filter Bar */}
      <div className="tc-catalog-meta-bar">
        <div className="tc-catalog-count">
          Showing <strong>{filteredProducts.length}</strong> of <strong>{products.length}</strong> items
          {selectedCategory !== 'All' && (
            <span> in <span className="tc-category-active-tag">{selectedCategory}</span></span>
          )}
        </div>
      </div>

      {cartError && (
        <div className="tc-error-banner">
          {cartError}
        </div>
      )}

      {/* Responsive CSS Grid (repeat auto-fill, minmax 250px) */}
      <div className="tc-product-grid">
        {filteredProducts.map((product) => {
          const isOutOfStock = product.stock <= 0
          const isAdding = addingId === product.id
          const isJustAdded = addedId === product.id

          return (
            <article key={product.id} className="tc-product-card">
              {/* Image taking up top 50% */}
              <div className="tc-card-image-wrap">
                {product.category && (
                  <span className="tc-card-category-tag">{product.category.name}</span>
                )}
                {product.imageUrl ? (
                  <img
                    src={product.imageUrl}
                    alt={product.name}
                    className="tc-card-image"
                    loading="lazy"
                  />
                ) : (
                  <div className="tc-card-placeholder-img">No Image Available</div>
                )}
              </div>

              {/* Product Content */}
              <div className="tc-card-content">
                {/* Clean Title Clamped to 2 lines */}
                <h2 className="tc-card-title" title={product.name}>
                  {product.name}
                </h2>

                {/* Bold Price in Dark Slate */}
                <div className="tc-card-price-row">
                  <span className="tc-card-price">
                    ${typeof product.price === 'number' ? product.price.toFixed(2) : product.price}
                  </span>
                </div>

                {/* Clean In Stock Status */}
                <div className={`tc-card-stock ${!isOutOfStock ? 'in-stock' : 'out-of-stock'}`}>
                  {!isOutOfStock ? 'In Stock' : 'Out of Stock'}
                </div>

                {/* Modern Indigo/Purple Add to Cart Button */}
                <button
                  type="button"
                  onClick={() => handleAddToCart(product)}
                  disabled={isOutOfStock || isAdding}
                  className={`tc-add-to-cart-btn ${isJustAdded ? 'added' : ''}`}
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
