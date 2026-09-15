import { useEffect, useState, useMemo } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { API_BASE_URL } from '../api/config'
import { useCart } from '../context/CartContext'
import type { PageResponse, Product } from '../types'
import { WhySellDirectly } from './WhySellDirectly'
import './ProductList.css'

interface ProductListProps {
  selectedCategory?: string
}

type SortOption = 'curated' | 'price_desc' | 'price_asc' | 'name'
type ZoomCorner = 'FULL' | 'TL' | 'TR' | 'BL' | 'BR' | 'CENTER'

interface ConditionStampInfo {
  stamp: string
  code: 'nm' | 'lp' | 'mp' | 'hp' | 'dmg' | 'slab' | 'raw' | 'sealed'
  fullName: string
}

function parseConditionStamp(condition?: string | null, grading?: string | null, categoryName?: string | null): ConditionStampInfo {
  if (categoryName?.toUpperCase().includes('SEALED') || condition?.toUpperCase().includes('SEALED')) {
    return {
      stamp: '[SEALED]',
      code: 'sealed',
      fullName: 'Factory Sealed Product',
    }
  }

  if (grading && grading.trim()) {
    const clean = grading.trim().toUpperCase()
    return {
      stamp: `[${clean}]`,
      code: 'slab',
      fullName: `Graded Slab (${grading.trim()})`,
    }
  }

  if (!condition || !condition.trim()) {
    return {
      stamp: '[RAW NM]',
      code: 'nm',
      fullName: 'Raw Near Mint',
    }
  }

  const upper = condition.trim().toUpperCase()

  if (upper.includes('NEAR MINT') || upper === 'NM' || upper.includes('GEM') || upper === 'MINT') {
    return { stamp: '[RAW NM]', code: 'nm', fullName: 'Raw Near Mint' }
  }
  if (upper.includes('LIGHTLY PLAYED') || upper === 'LP' || upper.includes('EXCELLENT')) {
    return { stamp: '[LP]', code: 'lp', fullName: 'Lightly Played' }
  }
  if (upper.includes('MODERATELY PLAYED') || upper === 'MP' || upper.includes('VERY GOOD')) {
    return { stamp: '[LP]', code: 'lp', fullName: 'Moderately Played' }
  }
  if (upper.includes('HEAVILY PLAYED') || upper === 'HP' || upper.includes('POOR') || upper.includes('DAMAGED') || upper === 'DMG') {
    return { stamp: '[DMG]', code: 'dmg', fullName: 'Damaged / Played Binder Card' }
  }

  return {
    stamp: `[${upper.slice(0, 8)}]`,
    code: 'raw',
    fullName: condition.trim(),
  }
}

const TEASER_TIERS = [
  { id: 'gem-mint', label: 'PSA 10 / Black Label · 90%', short: 'PSA 10 (90%)', rate: 0.90, category: 'PSA 10 & BGS Black Label' },
  { id: 'graded-slab', label: 'Graded Slabs · 85%', short: 'Slabs (85%)', rate: 0.85, category: 'Graded Slabs (PSA, CGC, BGS)' },
  { id: 'raw-nm', label: 'Raw NM · 80%', short: 'Raw NM (80%)', rate: 0.80, category: 'Raw NM Singles' },
  { id: 'sealed', label: 'Sealed · 75%', short: 'Sealed (75%)', rate: 0.75, category: 'Sealed Products' },
  { id: 'played', label: 'Played Binder · 70%', short: 'Played (70%)', rate: 0.70, category: 'Played & Binder Singles' },
]

const QUICK_VALUES = [100, 300, 500, 1000]

export function ProductList({ selectedCategory = 'All' }: ProductListProps) {
  const navigate = useNavigate()
  const [products, setProducts] = useState<Product[]>([])
  const [loading, setLoading] = useState<boolean>(true)
  const [error, setError] = useState<string | null>(null)
  const [addingId, setAddingId] = useState<number | null>(null)
  const [addedId, setAddedId] = useState<number | null>(null)
  const [cartError, setCartError] = useState<string | null>(null)
  const [imgError, setImgError] = useState<Record<number, boolean>>({})

  // Editorial Home State
  const [showFullCatalog, setShowFullCatalog] = useState(false)
  const [teaserTierId, setTeaserTierId] = useState('gem-mint')
  const [teaserMarketPrice, setTeaserMarketPrice] = useState(300)

  // Local Filter & Sort Controls
  const [searchFilter, setSearchFilter] = useState('')
  const [sortBy, setSortBy] = useState<SortOption>('curated')
  const [showAvailableOnly, setShowAvailableOnly] = useState(false)

  // Quick Inspect Lightbox State
  const [inspectProduct, setInspectProduct] = useState<Product | null>(null)
  const [zoomCorner, setZoomCorner] = useState<ZoomCorner>('FULL')

  const { addToCart } = useCart()

  useEffect(() => {
    const fetchProducts = async () => {
      try {
        setLoading(true)
        setError(null)
        const response = await fetch(`${API_BASE_URL}/api/products?page=0&size=100`)
        if (!response.ok) {
          throw new Error(`Failed to fetch products: ${response.status} ${response.statusText}`)
        }
        const data: PageResponse<Product> = await response.json()
        let allProducts = data.content || []

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

  // Sync showcase/full-catalog view when category selection changes
  useEffect(() => {
    if (selectedCategory !== 'All') {
      setShowFullCatalog(true)
    } else {
      setShowFullCatalog(false)
    }
  }, [selectedCategory])

  // Filter & Sort Products for Full Catalog
  const filteredProducts = useMemo(() => {
    return products
      .filter((product) => {
        if (selectedCategory !== 'All') {
          const catName = product.category?.name?.toLowerCase() || ''
          if (selectedCategory === 'Singles') {
            const isSingle =
              catName === 'singles' ||
              catName.includes('single') ||
              catName.includes('trading') ||
              product.name.toLowerCase().includes('card')
            if (!isSingle) return false
          } else if (selectedCategory === 'Sealed') {
            const isSealed = catName === 'sealed' || catName.includes('seal')
            if (!isSealed) return false
          } else if (selectedCategory === 'Slabs') {
            const isSlab =
              Boolean(product.grading) ||
              catName.includes('graded') ||
              catName.includes('slab') ||
              product.name.toLowerCase().includes('psa') ||
              product.name.toLowerCase().includes('bgs') ||
              product.name.toLowerCase().includes('cgc')
            if (!isSlab) return false
          } else {
            if (!catName.includes(selectedCategory.toLowerCase())) return false
          }
        }

        if (showAvailableOnly && product.status === 'SOLD') {
          return false
        }

        if (searchFilter.trim()) {
          const q = searchFilter.toLowerCase().trim()
          const matchesName = product.name.toLowerCase().includes(q)
          const matchesSet = product.set?.toLowerCase().includes(q) || false
          const matchesNum = product.cardNumber?.toLowerCase().includes(q) || false
          const matchesGrading = product.grading?.toLowerCase().includes(q) || false
          if (!matchesName && !matchesSet && !matchesNum && !matchesGrading) {
            return false
          }
        }

        return true
      })
      .sort((a, b) => {
        if (sortBy === 'price_desc') return b.price - a.price
        if (sortBy === 'price_asc') return a.price - b.price
        if (sortBy === 'name') return a.name.localeCompare(b.name)
        if (a.status === 'SOLD' && b.status !== 'SOLD') return 1
        if (a.status !== 'SOLD' && b.status === 'SOLD') return -1
        return b.price - a.price
      })
  }, [products, selectedCategory, showAvailableOnly, searchFilter, sortBy])

  // Curated 6-Card Showcase for Home View
  const showcaseProducts = useMemo(() => {
    if (products.length === 0) return []
    const slabs = products.filter((p) => Boolean(p.grading))
    const sealed = products.filter(
      (p) => p.category?.name?.toLowerCase().includes('sealed') || p.name.toLowerCase().includes('trainer box')
    )
    const singles = products.filter(
      (p) => !p.grading && !p.name.toLowerCase().includes('trainer box') && p.price >= 20
    )

    const list: Product[] = []
    if (slabs[0]) list.push(slabs[0])
    if (sealed[0]) list.push(sealed[0])
    if (singles[0]) list.push(singles[0])
    if (slabs[1]) list.push(slabs[1])
    if (sealed[1]) list.push(sealed[1])
    if (singles[1]) list.push(singles[1])

    if (list.length < 4) {
      return products.slice(0, 6)
    }
    return list.slice(0, 6)
  }, [products])

  // Calculator Teaser values
  const activeTeaserTier = TEASER_TIERS.find((t) => t.id === teaserTierId) || TEASER_TIERS[0]
  const teaserPayoutAmount = teaserMarketPrice * activeTeaserTier.rate
  const teaserMarketplaceSavings = teaserMarketPrice * 0.1325

  // Keyboard navigation for Quick Inspect Modal
  useEffect(() => {
    if (!inspectProduct) return

    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        setInspectProduct(null)
        setZoomCorner('FULL')
      } else if (e.key === 'ArrowRight') {
        const pool = showFullCatalog || selectedCategory !== 'All' ? filteredProducts : showcaseProducts
        const currentIndex = pool.findIndex((p) => p.id === inspectProduct.id)
        if (currentIndex !== -1 && currentIndex < pool.length - 1) {
          setInspectProduct(pool[currentIndex + 1])
          setZoomCorner('FULL')
        }
      } else if (e.key === 'ArrowLeft') {
        const pool = showFullCatalog || selectedCategory !== 'All' ? filteredProducts : showcaseProducts
        const currentIndex = pool.findIndex((p) => p.id === inspectProduct.id)
        if (currentIndex > 0) {
          setInspectProduct(pool[currentIndex - 1])
          setZoomCorner('FULL')
        }
      }
    }

    window.addEventListener('keydown', handleKeyDown)
    return () => window.removeEventListener('keydown', handleKeyDown)
  }, [inspectProduct, filteredProducts, showcaseProducts, showFullCatalog, selectedCategory])

  const handleAddToCart = async (product: Product, e?: React.MouseEvent) => {
    if (e) e.stopPropagation()
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

  const handleOpenInspect = (product: Product, e?: React.MouseEvent) => {
    if (e) e.stopPropagation()
    setInspectProduct(product)
    setZoomCorner('FULL')
  }

  const getZoomStyle = (corner: ZoomCorner) => {
    switch (corner) {
      case 'TL':
        return { transform: 'scale(2.5)', transformOrigin: '5% 5%' }
      case 'TR':
        return { transform: 'scale(2.5)', transformOrigin: '95% 5%' }
      case 'BL':
        return { transform: 'scale(2.5)', transformOrigin: '5% 95%' }
      case 'BR':
        return { transform: 'scale(2.5)', transformOrigin: '95% 95%' }
      case 'CENTER':
        return { transform: 'scale(2.5)', transformOrigin: '50% 50%' }
      default:
        return { transform: 'scale(1)', transformOrigin: '50% 50%' }
    }
  }

  const scrollToVault = () => {
    document.getElementById('vault-showcase')?.scrollIntoView({ behavior: 'smooth', block: 'start' })
  }

  const handleLockInTeaser = () => {
    navigate(
      `/sell?tier=${encodeURIComponent(activeTeaserTier.category)}&rate=${activeTeaserTier.rate}&market=${teaserMarketPrice}&payout=${teaserPayoutAmount.toFixed(2)}#buylist-form`
    )
  }

  // Helper to render individual product cards
  const renderCard = (product: Product) => {
    const isSold = product.status === 'SOLD'
    const isOutOfStock = product.stock <= 0 && !isSold
    const isAdding = addingId === product.id
    const isJustAdded = addedId === product.id
    const condInfo = parseConditionStamp(product.condition, product.grading, product.category?.name)

    return (
      <article
        key={product.id}
        className={`tc-card ${isSold ? 'is-sold' : ''}`}
        onClick={() => handleOpenInspect(product)}
      >
        <div className="tc-card-stage">
          <div className="tc-card-badges">
            {product.category && (
              <span className="tc-cat-badge tc-mono">[{product.category.name.toUpperCase()}]</span>
            )}
          </div>

          {isSold && (
            <div className="tc-sold-stamp">
              <span className="tc-pixel">[SOLD ARCHIVE]</span>
            </div>
          )}

          {product.imageUrl && !imgError[product.id] ? (
            <img
              src={product.imageUrl}
              alt={product.name}
              className={`tc-card-img ${isSold ? 'img-desaturated' : ''}`}
              loading="lazy"
              onError={() => setImgError((prev) => ({ ...prev, [product.id]: true }))}
            />
          ) : (
            <div className="tc-no-image tc-mono">No Image</div>
          )}

          <button
            type="button"
            className="tc-loupe-btn"
            onClick={(e) => handleOpenInspect(product, e)}
            aria-label={`Inspect ${product.name} at 250% magnification`}
            title="Inspect 250% Optical Loupe"
          >
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" width="14" height="14" aria-hidden="true">
              <circle cx="11" cy="11" r="7" />
              <line x1="21" y1="21" x2="16.65" y2="16.65" />
              <line x1="11" y1="8" x2="11" y2="14" />
              <line x1="8" y1="11" x2="14" y2="11" />
            </svg>
            <span>250% Loupe</span>
          </button>
        </div>

        <div className="tc-card-body">
          <div className="tc-meta-row">
            {product.set && (
              <span className="tc-meta-tag tag-set" title={product.set}>
                {product.set}
              </span>
            )}
            {product.cardNumber && (
              <span className="tc-meta-tag tag-num tc-mono">
                #{product.cardNumber}
              </span>
            )}
            <span className={`tc-meta-tag stamp-${condInfo.code} tc-mono`} title={condInfo.fullName}>
              {condInfo.stamp}
            </span>
          </div>

          <h3 className="tc-card-title" title={product.name}>
            {product.name}
          </h3>

          <div className="tc-price-row">
            <div className="tc-price-group">
              <span className="tc-price-val tc-pixel">
                ${product.price ? product.price.toFixed(2) : '0.00'}
              </span>
              <span className="tc-price-cur tc-pixel">CAD</span>
            </div>

            <span className={`tc-stock-tag ${isSold ? 'tc-pixel tag-sold' : 'tc-mono'} ${isOutOfStock ? 'tag-out' : 'tag-available'}`}>
              {isSold ? '[ARCHIVE]' : isOutOfStock ? '[OUT OF STOCK]' : '[IN STOCK]'}
            </span>
          </div>

          <div className="tc-card-actions">
            {isSold ? (
              <button
                type="button"
                className="tc-btn tc-btn-disabled"
                disabled
              >
                Sold Archive Record
              </button>
            ) : isOutOfStock ? (
              <button
                type="button"
                className="tc-btn tc-btn-disabled"
                disabled
              >
                Out of Stock
              </button>
            ) : (
              <button
                type="button"
                className={`tc-btn tc-btn-primary ${isJustAdded ? 'is-added' : ''}`}
                onClick={(e) => handleAddToCart(product, e)}
                disabled={isAdding}
              >
                {isAdding ? 'Adding...' : isJustAdded ? 'Added to Cart ✓' : 'Add to Cart'}
              </button>
            )}
          </div>
        </div>
      </article>
    )
  }

  const isFilteredCategory = selectedCategory !== 'All'
  const isViewingFullCatalog = showFullCatalog || isFilteredCategory || searchFilter.trim() !== ''

  return (
    <div className="tc-catalog-wrapper">
      {/* =========================================================================
          1. HERO SECTION: "THE TWO DOORS" SPLIT HERO (BUY VS SELL)
          ========================================================================= */}
      {!isFilteredCategory && (
        <section className="tc-split-hero" aria-label="Welcome to Tailor Cards">
          {/* Left Door: Buy / Archive & Inventory */}
          <div className="tc-split-panel tc-door-buy tc-corner-accent">
            <div className="tc-door-content">
              <span className="tc-panel-eyebrow tc-mono">[ARCHIVE &amp; INVENTORY]</span>
              <h1 className="tc-panel-headline">Curated Pokémon Singles &amp; Slabs</h1>
              <p className="tc-panel-copy">
                Calibrated corner macro inspections on vintage WOTC, Japanese promos, and modern chase cards.
                Transparent grading, zero hidden flaws.
              </p>
            </div>
            <div className="tc-door-action">
              <button type="button" className="tc-panel-cta-btn tc-cta-buy" onClick={scrollToVault}>
                <span>Browse Collection</span>
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" width="16" height="16" aria-hidden="true">
                  <path strokeLinecap="round" strokeLinejoin="round" d="M19.5 13.5 12 21m0 0-7.5-7.5M12 21V3" />
                </svg>
              </button>
            </div>
          </div>

          {/* Right Door: Sell / Direct Liquidity */}
          <div className="tc-split-panel tc-door-sell tc-corner-accent">
            <div className="tc-door-content">
              <span className="tc-panel-eyebrow tc-mono">[DIRECT LIQUIDITY]</span>
              <h2 className="tc-panel-headline">Turn Cards Into Instant Cash</h2>
              <p className="tc-panel-copy">
                We acquire collections, grails, and raw singles at up to 90% market value via direct Interac e-Transfer.
                Skip the 13% platform fees and chargeback risks.
              </p>
            </div>
            <div className="tc-door-action">
              <Link to="/sell" className="tc-panel-cta-btn tc-cta-sell">
                <span>Calculate Payout &amp; Submit</span>
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" width="16" height="16" aria-hidden="true">
                  <path strokeLinecap="round" strokeLinejoin="round" d="M13.5 4.5 21 12m0 0-7.5 7.5M21 12H3" />
                </svg>
              </Link>
            </div>
          </div>
        </section>
      )}

      {/* =========================================================================
          2. AUTHENTICITY & LOUPE TRUST STRIP
          ========================================================================= */}
      {!isFilteredCategory && (
        <div className="tc-trust-strip" aria-label="Vault Authentication Standards">
          <div className="tc-trust-item">
            <div className="tc-trust-icon-box">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" className="tc-trust-svg" aria-hidden="true">
                <circle cx="11" cy="11" r="7" />
                <line x1="21" y1="21" x2="16.65" y2="16.65" />
                <path strokeLinecap="round" d="M11 8v6M8 11h6" />
              </svg>
            </div>
            <div className="tc-trust-text">
              <h3 className="tc-trust-title">250% Macro Loupe</h3>
              <p className="tc-trust-desc">Inspect corners, whitening, and surfaces before purchase.</p>
            </div>
          </div>

          <div className="tc-trust-item">
            <div className="tc-trust-icon-box">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" className="tc-trust-svg" aria-hidden="true">
                <path strokeLinecap="round" strokeLinejoin="round" d="M9 12.75 11.25 15 15 9.75M21 12c0 1.268-.63 2.39-1.593 3.068a3.745 3.745 0 0 1-1.043 3.296 3.745 3.745 0 0 1-3.296 1.043A3.745 3.745 0 0 1 12 21c-1.268 0-2.39-.63-3.068-1.593a3.746 3.746 0 0 1-3.296-1.043 3.745 3.745 0 0 1-1.043-3.296A3.745 3.745 0 0 1 3 12c0-1.268.63-2.39 1.593-3.068a3.745 3.745 0 0 1 1.043-3.296 3.746 3.746 0 0 1 3.296-1.043A3.746 3.746 0 0 1 12 3c1.268 0 2.39.63 3.068 1.593a3.746 3.746 0 0 1 3.296 1.043 3.746 3.746 0 0 1 1.043 3.296A3.745 3.745 0 0 1 21 12Z" />
              </svg>
            </div>
            <div className="tc-trust-text">
              <h3 className="tc-trust-title">Guaranteed Authentic</h3>
              <p className="tc-trust-desc">Zero counterfeit tolerance, verified holos and seals.</p>
            </div>
          </div>

          <div className="tc-trust-item">
            <div className="tc-trust-icon-box">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" className="tc-trust-svg" aria-hidden="true">
                <path strokeLinecap="round" strokeLinejoin="round" d="M9 12.75 11.25 15 15 9.75m-3-7.036A11.959 11.959 0 0 1 3.598 6 11.99 11.99 0 0 0 3 9.749c0 5.592 3.824 10.29 9 11.623 5.176-1.332 9-6.03 9-11.622 0-1.31-.21-2.571-.598-3.751h-.152c-3.196 0-6.1-1.248-8.25-3.285Z" />
              </svg>
            </div>
            <div className="tc-trust-text">
              <h3 className="tc-trust-title">Protected Transactions</h3>
              <p className="tc-trust-desc">Instant e-Transfer for sellers, tracked shipping for buyers.</p>
            </div>
          </div>
        </div>
      )}

      {/* =========================================================================
          3. COMPACT INSTANT CASH CALCULATOR TEASER
          ========================================================================= */}
      {!isFilteredCategory && (
        <section className="tc-teaser-calc tc-corner-accent" aria-labelledby="teaser-calc-heading">
          <div className="tc-teaser-calc-header">
            <div className="tc-teaser-calc-title-box">
              <span className="tc-teaser-calc-badge tc-mono">[DIRECT LIQUIDITY ESTIMATOR]</span>
              <h2 id="teaser-calc-heading" className="tc-teaser-calc-heading">Instant Cash Payout Estimator</h2>
            </div>
            <span className="tc-teaser-calc-sub tc-mono">Live e-Transfer Payout Preview</span>
          </div>

          {/* Tier Pills */}
          <div className="tc-teaser-pills-row" role="tablist" aria-label="Buylist Category Tier">
            {TEASER_TIERS.map((tier) => (
              <button
                key={tier.id}
                type="button"
                className={`tc-teaser-pill ${teaserTierId === tier.id ? 'active' : ''}`}
                onClick={() => setTeaserTierId(tier.id)}
              >
                <span>{tier.label.split(' · ')[0]}</span>
                <span className="tc-pill-separator" aria-hidden="true"> · </span>
                <span className="tc-pixel">{(tier.rate * 100).toFixed(0)}%</span>
              </button>
            ))}
          </div>

          {/* Values Row: Slider + Quick Preset Buttons */}
          <div className="tc-teaser-input-row">
            <div className="tc-teaser-slider-col">
              <div className="tc-teaser-slider-header">
                <span className="tc-teaser-label">Card Market Price (CAD):</span>
                <span className="tc-teaser-slider-val tc-pixel">${teaserMarketPrice} CAD</span>
              </div>
              <input
                type="range"
                min="50"
                max="2000"
                step="25"
                value={teaserMarketPrice}
                onChange={(e) => setTeaserMarketPrice(Number(e.target.value))}
                className="tc-teaser-slider"
                aria-label="Market value slider"
              />
            </div>

            <div className="tc-teaser-presets-col">
              <span className="tc-teaser-label">Quick Values:</span>
              <div className="tc-teaser-preset-btns">
                {QUICK_VALUES.map((val) => (
                  <button
                    key={val}
                    type="button"
                    className={`tc-teaser-quick-btn tc-pixel ${teaserMarketPrice === val ? 'active' : ''}`}
                    onClick={() => setTeaserMarketPrice(val)}
                  >
                    ${val}
                  </button>
                ))}
              </div>
            </div>
          </div>

          {/* Instant Payout Breakdown */}
          <div className="tc-teaser-results-bar">
            <div className="tc-teaser-metric">
              <span className="tc-metric-label">Your Market Value</span>
              <span className="tc-metric-value tc-pixel">${teaserMarketPrice.toFixed(2)} CAD</span>
            </div>

            <div className="tc-teaser-metric-arrow" aria-hidden="true">→</div>

            <div className="tc-teaser-metric highlight">
              <div className="tc-metric-label-row">
                <span className="tc-metric-label gold">TailorCards Instant Cash</span>
                <span className="tc-rate-tag tc-pixel">{(activeTeaserTier.rate * 100).toFixed(0)}% RATE</span>
              </div>
              <span className="tc-metric-value gold tc-pixel">${teaserPayoutAmount.toFixed(2)} CAD</span>
              <span className="tc-metric-sub">Direct Interac e-Transfer</span>
            </div>

            <div className="tc-teaser-metric fee-col">
              <span className="tc-metric-label">Marketplace Fee Savings</span>
              <span className="tc-metric-value green tc-pixel">+${teaserMarketplaceSavings.toFixed(2)} CAD</span>
              <span className="tc-metric-sub">Saved vs. eBay (~13.25%) + Zero Fraud Risk</span>
            </div>

            <button
              type="button"
              className="tc-teaser-lock-cta"
              onClick={handleLockInTeaser}
            >
              <span>Lock In Rate &amp; Sell</span>
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" width="16" height="16" aria-hidden="true">
                <path strokeLinecap="round" strokeLinejoin="round" d="M13.5 4.5 21 12m0 0-7.5 7.5M21 12H3" />
              </svg>
            </button>
          </div>
        </section>
      )}

      {/* =========================================================================
          Progressive Disclosure: Why Sell Directly Tabbed Reading Pane
          ========================================================================= */}
      {!isFilteredCategory && !isViewingFullCatalog && (
        <WhySellDirectly />
      )}


      {/* =========================================================================
          4. CURATED "VAULT SHOWCASE" ROW
          ========================================================================= */}
      {!isFilteredCategory && !isViewingFullCatalog && (
        <section id="vault-showcase" className="tc-showcase-section" aria-labelledby="showcase-heading">
          <div className="tc-showcase-header">
            <div>
              <span className="tc-showcase-eyebrow tc-mono">[VAULT SHOWCASE]</span>
              <h2 id="showcase-heading" className="tc-showcase-title">Curated Slabs &amp; Singles</h2>
              <p className="tc-showcase-subtitle">
                Hand-inspected cards outside of binder sleeves with calibrated corner loupe photography.
              </p>
            </div>
            <span className="tc-showcase-count tc-mono">{showcaseProducts.length} FEATURED</span>
          </div>

          {/* 6-Card Showcase Grid */}
          <div className="tc-product-grid">
            {showcaseProducts.map(renderCard)}
          </div>

          {/* Secondary CTA: Explore Full 60-Card Catalog */}
          <div className="tc-showcase-cta-bar">
            <button
              type="button"
              className="tc-expand-catalog-btn"
              onClick={() => {
                setShowFullCatalog(true)
                setTimeout(() => {
                  document.getElementById('full-catalog')?.scrollIntoView({ behavior: 'smooth', block: 'start' })
                }, 50)
              }}
            >
              <span>Explore Full 60-Card Catalog</span>
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" width="16" height="16" aria-hidden="true">
                <path strokeLinecap="round" strokeLinejoin="round" d="M19.5 8.25l-7.5 7.5-7.5-7.5" />
              </svg>
            </button>
          </div>
        </section>
      )}

      {/* =========================================================================
          5. FULL CATALOG VIEW (When Expanded or Filtered)
          ========================================================================= */}
      {isViewingFullCatalog && (
        <section id="full-catalog" className="tc-full-catalog-section" aria-labelledby="full-catalog-heading">
          <div className="tc-catalog-heading-bar">
            <div>
              <span className="tc-catalog-eyebrow tc-mono">
                {isFilteredCategory ? `[CATEGORY ARCHIVE: ${selectedCategory.toUpperCase()}]` : '[FULL ARCHIVE INVENTORY]'}
              </span>
              <h2 id="full-catalog-heading" className="tc-catalog-heading-title">
                {isFilteredCategory ? `${selectedCategory} Collection` : 'Full 60-Card Inventory Archive'}
              </h2>
            </div>
            <div className="tc-catalog-heading-actions">
              <span className="tc-catalog-heading-count tc-mono">
                {filteredProducts.length} {filteredProducts.length === 1 ? 'item' : 'items'}
              </span>
              {!isFilteredCategory && (
                <button
                  type="button"
                  className="tc-collapse-btn"
                  onClick={() => setShowFullCatalog(false)}
                >
                  Collapse to Showcase ↑
                </button>
              )}
            </div>
          </div>

          {/* Control Bar: Search & Sort */}
          <div className="tc-control-bar">
            <div className="tc-search-box">
              <svg
                className="tc-search-box-icon"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                strokeWidth="2"
                width="16"
                height="16"
                aria-hidden="true"
              >
                <circle cx="11" cy="11" r="7" />
                <line x1="21" y1="21" x2="16.65" y2="16.65" />
              </svg>
              <input
                type="text"
                className="tc-search-box-input"
                placeholder="Filter by card name, set, or #..."
                value={searchFilter}
                onChange={(e) => setSearchFilter(e.target.value)}
              />
              {searchFilter && (
                <button
                  type="button"
                  className="tc-search-box-clear"
                  onClick={() => setSearchFilter('')}
                  aria-label="Clear search input"
                >
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" width="14" height="14">
                    <line x1="18" y1="6" x2="6" y2="18" />
                    <line x1="6" y1="6" x2="18" y2="18" />
                  </svg>
                </button>
              )}
            </div>

            <div className="tc-controls-right">
              <button
                type="button"
                className={`tc-toggle-btn ${showAvailableOnly ? 'active' : ''}`}
                onClick={() => setShowAvailableOnly(!showAvailableOnly)}
              >
                <span className={`tc-toggle-indicator ${showAvailableOnly ? 'on' : ''}`} />
                <span>Available Only</span>
              </button>

              <div className="tc-sort-wrap">
                <label htmlFor="tc-sort-select" className="tc-sort-label">SORT:</label>
                <select
                  id="tc-sort-select"
                  className="tc-sort-select tc-mono"
                  value={sortBy}
                  onChange={(e) => setSortBy(e.target.value as SortOption)}
                >
                  <option value="curated">Curated Vault Order</option>
                  <option value="price_desc">Price: High to Low</option>
                  <option value="price_asc">Price: Low to High</option>
                  <option value="name">Card Name (A-Z)</option>
                </select>
              </div>
            </div>
          </div>

          {/* Error Banner */}
          {cartError && (
            <div className="tc-cart-error-banner" role="alert">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" className="tc-alert-icon">
                <circle cx="12" cy="12" r="10" />
                <line x1="12" y1="8" x2="12" y2="12" />
                <line x1="12" y1="16" x2="12.01" y2="16" />
              </svg>
              <span>{cartError}</span>
            </div>
          )}

          {/* Loading Skeleton Grid */}
          {loading ? (
            <div className="tc-product-grid" aria-label="Loading catalog">
              {Array.from({ length: 8 }).map((_, i) => (
                <div key={i} className="tc-skeleton-card">
                  <div className="tc-skeleton-stage" />
                  <div className="tc-skeleton-body">
                    <div className="tc-skeleton-pills" />
                    <div className="tc-skeleton-title" />
                    <div className="tc-skeleton-footer" />
                  </div>
                </div>
              ))}
            </div>
          ) : error ? (
            <div className="tc-empty-state-box">
              <h3 className="tc-empty-title">Failed to load inventory</h3>
              <p className="tc-empty-text">{error}</p>
              <button type="button" onClick={() => window.location.reload()} className="tc-primary-action-btn">
                Retry
              </button>
            </div>
          ) : filteredProducts.length === 0 ? (
            <div className="tc-empty-state-box">
              <h3 className="tc-empty-title">No cards found</h3>
              <p className="tc-empty-text">Try adjusting your filters or disabling &quot;Available Only&quot; to view sold archive cards.</p>
              <button
                type="button"
                className="tc-primary-action-btn"
                onClick={() => {
                  setSearchFilter('')
                  setShowAvailableOnly(false)
                }}
              >
                Reset Filters
              </button>
            </div>
          ) : (
            <div className="tc-product-grid">
              {filteredProducts.map(renderCard)}
            </div>
          )}
        </section>
      )}

      {/* =========================================================================
          QUICK INSPECT CORNER LOUPE MODAL (250% MAGNIFICATION)
          ========================================================================= */}
      {inspectProduct && (() => {
        const modalCond = parseConditionStamp(inspectProduct.condition, inspectProduct.grading, inspectProduct.category?.name)

        return (
          <div
            className="tc-modal-backdrop"
            onClick={() => {
              setInspectProduct(null)
              setZoomCorner('FULL')
            }}
            role="dialog"
            aria-modal="true"
            aria-label={`Inspect ${inspectProduct.name}`}
          >
            <div className="tc-modal-window" onClick={(e) => e.stopPropagation()}>
              <div className="tc-modal-header">
                <div>
                  <span className="tc-modal-eyebrow tc-mono">INSPECTION DOSSIER &middot; #{inspectProduct.id}</span>
                  <h2 className="tc-modal-title">{inspectProduct.name}</h2>
                </div>

                <div className="tc-modal-actions">
                  <span className="tc-key-hint tc-mono"><kbd>Esc</kbd> to close &middot; <kbd>&larr;</kbd> <kbd>&rarr;</kbd> navigate</span>
                  <button
                    type="button"
                    className="tc-modal-close-btn"
                    onClick={() => {
                      setInspectProduct(null)
                      setZoomCorner('FULL')
                    }}
                    aria-label="Close Inspector"
                  >
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" width="16" height="16">
                      <line x1="18" y1="6" x2="6" y2="18" />
                      <line x1="6" y1="6" x2="18" y2="18" />
                    </svg>
                  </button>
                </div>
              </div>

              <div className="tc-modal-body">
                {/* Left: Loupe Stage */}
                <div className="tc-loupe-col">
                  <div className="tc-loupe-viewport">
                    {inspectProduct.imageUrl && !imgError[inspectProduct.id] ? (
                      <img
                        src={inspectProduct.imageUrl}
                        alt={inspectProduct.name}
                        className="tc-loupe-img"
                        style={getZoomStyle(zoomCorner)}
                      />
                    ) : (
                      <div className="tc-no-image tc-mono">No image available for loupe inspection</div>
                    )}

                    {inspectProduct.status === 'SOLD' && (
                      <div className="tc-modal-sold-tag tc-pixel">[SOLD ARCHIVE]</div>
                    )}
                  </div>

                  {/* Corner Loupe Presets */}
                  <div className="tc-loupe-controls">
                    <span className="tc-loupe-label tc-mono">CORNER LOUPE:</span>
                    <div className="tc-loupe-buttons">
                      <button
                        type="button"
                        className={`tc-loupe-preset ${zoomCorner === 'FULL' ? 'active' : ''}`}
                        onClick={() => setZoomCorner('FULL')}
                      >
                        Full Card
                      </button>
                      <button
                        type="button"
                        className={`tc-loupe-preset ${zoomCorner === 'TL' ? 'active' : ''}`}
                        onClick={() => setZoomCorner('TL')}
                        title="Top-Left Corner (Whitening & Edge Silvering)"
                      >
                        Top-Left
                      </button>
                      <button
                        type="button"
                        className={`tc-loupe-preset ${zoomCorner === 'TR' ? 'active' : ''}`}
                        onClick={() => setZoomCorner('TR')}
                        title="Top-Right Corner (Edge Cut & Centering)"
                      >
                        Top-Right
                      </button>
                      <button
                        type="button"
                        className={`tc-loupe-preset ${zoomCorner === 'BL' ? 'active' : ''}`}
                        onClick={() => setZoomCorner('BL')}
                        title="Bottom-Left Corner (Back Scratches & Edgewear)"
                      >
                        Bottom-Left
                      </button>
                      <button
                        type="button"
                        className={`tc-loupe-preset ${zoomCorner === 'BR' ? 'active' : ''}`}
                        onClick={() => setZoomCorner('BR')}
                        title="Bottom-Right Corner"
                      >
                        Bottom-Right
                      </button>
                      <button
                        type="button"
                        className={`tc-loupe-preset ${zoomCorner === 'CENTER' ? 'active' : ''}`}
                        onClick={() => setZoomCorner('CENTER')}
                        title="Holo Surface & Foil Centering"
                      >
                        Center Holo
                      </button>
                    </div>
                  </div>

                  <div className="tc-loupe-guide">
                    <span className="tc-guide-bold tc-mono">250% OPTICAL MAGNIFICATION:</span>
                    <span>Direct high-resolution scan. Corner presets isolate edge silvering, whitening, and holo foil scratches.</span>
                  </div>
                </div>

                {/* Right: Spec Dossier Column */}
                <div className="tc-dossier-col">
                  <div className="tc-dossier-header">
                    <div className="tc-dossier-price-wrap">
                      <span className="tc-dossier-label tc-mono">ACQUISITION PRICE</span>
                      <span className="tc-dossier-price tc-pixel">
                        ${inspectProduct.price ? inspectProduct.price.toFixed(2) : '0.00'}
                        <span className="tc-dossier-cur tc-pixel">CAD</span>
                      </span>
                    </div>

                    <span className={`tc-meta-tag stamp-${modalCond.code} tc-mono`}>
                      {modalCond.stamp}
                    </span>
                  </div>

                  {/* Specs Table */}
                  <div className="tc-spec-table">
                    <span className="tc-spec-heading tc-mono">CARD ARCHIVE SPECIFICATIONS</span>
                    <dl className="tc-spec-grid">
                      <div className="tc-spec-row">
                        <dt className="tc-spec-term">Card Title</dt>
                        <dd className="tc-spec-desc">{inspectProduct.name}</dd>
                      </div>

                      <div className="tc-spec-row">
                        <dt className="tc-spec-term">Set / Expansion</dt>
                        <dd className="tc-spec-desc">{inspectProduct.set || 'Unrecorded'}</dd>
                      </div>

                      <div className="tc-spec-row">
                        <dt className="tc-spec-term">Card Number</dt>
                        <dd className="tc-spec-desc tc-mono">{inspectProduct.cardNumber ? `#${inspectProduct.cardNumber}` : 'N/A'}</dd>
                      </div>

                      <div className="tc-spec-row">
                        <dt className="tc-spec-term">Condition Tier</dt>
                        <dd className="tc-spec-desc">{modalCond.fullName}</dd>
                      </div>

                      {inspectProduct.grading && (
                        <div className="tc-spec-row">
                          <dt className="tc-spec-term">Grading Authority</dt>
                          <dd className="tc-spec-desc tc-mono">{inspectProduct.grading}</dd>
                        </div>
                      )}

                      <div className="tc-spec-row">
                        <dt className="tc-spec-term">Archive Status</dt>
                        <dd className="tc-spec-desc tc-mono">
                          {inspectProduct.status === 'SOLD' ? 'SOLD ARCHIVE (Historical Record)' : 'AVAILABLE IN VAULT'}
                        </dd>
                      </div>
                    </dl>
                  </div>

                  {/* Transparent Collector Notes */}
                  {inspectProduct.description && (
                    <div className="tc-collector-notes">
                      <span className="tc-notes-label tc-mono">INSPECTION &amp; CONDITION NOTES</span>
                      <p className="tc-notes-text">{inspectProduct.description}</p>
                    </div>
                  )}

                  {/* Modal Action CTA */}
                  <div className="tc-dossier-footer">
                    {inspectProduct.status === 'SOLD' ? (
                      <div className="tc-sold-notice">
                        <span className="tc-sold-badge tc-pixel">[ARCHIVE]</span>
                        <span>This verified card has been sold to a private collector. Recorded in permanent ledger.</span>
                      </div>
                    ) : inspectProduct.stock <= 0 ? (
                      <button type="button" className="tc-btn tc-btn-disabled" disabled>
                        Out of Stock
                      </button>
                    ) : (
                      <button
                        type="button"
                        className={`tc-dossier-add-btn ${addedId === inspectProduct.id ? 'is-added' : ''}`}
                        onClick={(e) => handleAddToCart(inspectProduct, e)}
                        disabled={addingId === inspectProduct.id}
                      >
                        {addingId === inspectProduct.id
                          ? 'Adding to Cart...'
                          : addedId === inspectProduct.id
                          ? 'Added to Cart ✓'
                          : `Add to Cart — $${inspectProduct.price ? inspectProduct.price.toFixed(2) : '0.00'} CAD`}
                      </button>
                    )}
                  </div>
                </div>
              </div>
            </div>
          </div>
        )
      })()}
    </div>
  )
}
