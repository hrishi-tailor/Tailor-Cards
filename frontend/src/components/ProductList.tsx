import { useEffect, useState } from 'react'
import { API_BASE_URL } from '../api/config'
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
  const [imgError, setImgError] = useState<Record<number, boolean>>({})

  const { addToCart } = useCart()

  useEffect(() => {
    const fetchProducts = async () => {
      try {
        setLoading(true)
        setError(null)
        // Request page 0 with size=100 so all seeded products load on a single page
        const response = await fetch(`${API_BASE_URL}/api/products?page=0&size=100`)
        if (!response.ok) {
          throw new Error(`Failed to fetch products: ${response.status} ${response.statusText}`)
        }
        const data: PageResponse<Product> = await response.json()
        let allProducts = data.content || []

        // If totalPages > 1, fetch remaining pages to ensure all products are retrieved
        if (data.totalPages > 1) {
          const pagePromises = []
          for (let p = 1; p < data.totalPages; p++) {
            pagePromises.push(
              fetch(`${API_BASE_URL}/api/products?page=${p}&size=100`)
                .then((res) => {
                  if (!res.ok) throw new Error(`Failed to fetch page ${p}`)
                  return res.json()
                })
                .then((pageData: PageResponse<Product>) => pageData.content || [])
            )
          }
          const remainingPages = await Promise.all(pagePromises)
          for (const pageItems of remainingPages) {
            allProducts = allProducts.concat(pageItems)
          }
        }
        setProducts(allProducts)
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
    const catName = product.category?.name?.toLowerCase() || ''

    if (selectedCategory === 'Singles') {
      return (
        catName === 'singles' ||
        catName.includes('single') ||
        catName.includes('trading') ||
        product.name.toLowerCase().includes('card')
      )
    }
    if (selectedCategory === 'Sealed') {
      return (
        catName === 'sealed' ||
        catName.includes('seal')
      )
    }
    if (selectedCategory === 'Slabs') {
      return (
        Boolean(product.grading) ||
        catName.includes('graded') ||
        catName.includes('slab') ||
        product.name.toLowerCase().includes('psa') ||
        product.name.toLowerCase().includes('bgs') ||
        product.name.toLowerCase().includes('cgc')
      )
    }
    if (selectedCategory === 'Buying Requests') {
      return false
    }
    return catName.includes(selectedCategory.toLowerCase())
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
          const isSold = product.status === 'SOLD'
          const isOutOfStock = product.stock <= 0 && !isSold
          const isUnavailable = isSold || isOutOfStock
          const isAdding = addingId === product.id
          const isJustAdded = addedId === product.id

          return (
            <article key={product.id} className="tc-product-card">
              {/* Image taking up top 50% */}
              <div className="tc-card-image-wrap">
                {product.category && (
                  <span className="tc-card-category-tag">{product.category.name}</span>
                )}
                {isSold && (
                  <span className="tc-card-sold-tag">SOLD</span>
                )}
                {product.imageUrl && !imgError[product.id] ? (
                  <img
                    src={product.imageUrl}
                    alt={product.name}
                    className={`tc-card-image ${isSold ? 'sold-image' : ''}`}
                    loading="lazy"
                    onError={() => setImgError((prev) => ({ ...prev, [product.id]: true }))}
                  />
                ) : (
                  <div className="tc-card-placeholder-img">No Image Available</div>
                )}
              </div>

              {/* Product Content */}
              <div className="tc-card-content">
                {/* Meta Chips: Set, Card #, Condition, Grading */}
                {(product.set || product.cardNumber || product.condition || product.grading) && (
                  <div className="tc-card-meta-row">
                    {product.set && <span className="tc-meta-pill tc-meta-set" title={product.set}>{product.set}</span>}
                    {product.cardNumber && <span className="tc-meta-pill tc-meta-num">#{product.cardNumber}</span>}
                    {product.condition && <span className="tc-meta-pill tc-meta-condition">{product.condition}</span>}
                    {product.grading && <span className="tc-meta-pill tc-meta-grading">{product.grading}</span>}
                  </div>
                )}

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

                {/* Status Indicator */}
                <div className={`tc-card-stock ${isSold ? 'sold' : isOutOfStock ? 'out-of-stock' : 'in-stock'}`}>
                  {isSold ? 'SOLD' : isOutOfStock ? 'Out of Stock' : 'In Stock'}
                </div>

                {/* Modern Indigo/Purple Add to Cart Button */}
                <button
                  type="button"
                  onClick={() => handleAddToCart(product)}
                  disabled={isUnavailable || isAdding}
                  className={`tc-add-to-cart-btn ${isSold ? 'sold-btn' : ''} ${isJustAdded ? 'added' : ''}`}
                >
                  {isSold
                    ? 'SOLD'
                    : isOutOfStock
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
