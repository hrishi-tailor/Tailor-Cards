import { useEffect, useRef, useState } from 'react'
import type { Product } from '../types'
import { isGradedOrSealed } from '../types'
import { conditionLabel, displayName, formatCad, gradeOf } from '../catalog'
import { useAddToCart } from '../hooks/useAddToCart'
import { PriceHistoryChart } from './PriceHistoryChart'

type Zoom = 'full' | 'tl' | 'tr' | 'bl' | 'br' | 'center'

const ZOOMS: { id: Zoom; label: string; origin: string }[] = [
  { id: 'full', label: 'Full card', origin: '50% 50%' },
  { id: 'tl', label: 'Top left', origin: '4% 4%' },
  { id: 'tr', label: 'Top right', origin: '96% 4%' },
  { id: 'bl', label: 'Bottom left', origin: '4% 96%' },
  { id: 'br', label: 'Bottom right', origin: '96% 96%' },
  { id: 'center', label: 'Center', origin: '50% 50%' },
]

interface QuickViewProps {
  product: Product
  /** Products the arrow keys step through (the grid the visitor came from). */
  siblings: Product[]
  onNavigate: (product: Product) => void
  onClose: () => void
}

/** Product details in a dialog: photo with corner zoom, specs, add to cart, and raw price history. */
export function ProductQuickView({ product, siblings, onNavigate, onClose }: QuickViewProps) {
  const [zoom, setZoom] = useState<Zoom>('full')
  const [tab, setTab] = useState<'photo' | 'history'>('photo')
  const { add, state } = useAddToCart()
  const closeRef = useRef<HTMLButtonElement>(null)
  const name = displayName(product)
  const sold = product.status === 'SOLD'
  const outOfStock = !sold && product.stock <= 0
  const hasHistory = !isGradedOrSealed(product)
  const origin = ZOOMS.find((z) => z.id === zoom)?.origin ?? '50% 50%'

  useEffect(() => {
    setZoom('full')
    setTab('photo')
  }, [product.id])

  useEffect(() => {
    closeRef.current?.focus()
    const previous = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    return () => { document.body.style.overflow = previous }
  }, [])

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose()
      const i = siblings.findIndex((p) => p.id === product.id)
      if (e.key === 'ArrowRight' && i >= 0 && i < siblings.length - 1) onNavigate(siblings[i + 1])
      if (e.key === 'ArrowLeft' && i > 0) onNavigate(siblings[i - 1])
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [product.id, siblings, onNavigate, onClose])

  const specs: [string, string | undefined][] = [
    ['Set', product.set],
    ['Card number', product.cardNumber ? `#${product.cardNumber.replace(/^#/, '')}` : undefined],
    [gradeOf(product) ? 'Grade' : 'Condition', conditionLabel(product)],
    ['Category', product.category?.name],
    ['In stock', sold ? 'Sold' : outOfStock ? 'Out of stock' : String(product.stock)],
  ]

  return (
    <div className="tc-qv-scrim" onClick={onClose}>
      <div className="tc-qv" role="dialog" aria-modal="true" aria-label={name} onClick={(e) => e.stopPropagation()}>
        <button ref={closeRef} type="button" className="tc-icon-btn tc-qv-close" onClick={onClose} aria-label="Close">
          <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M6 6l12 12M18 6 6 18" /></svg>
        </button>

        <div className="tc-qv-media">
          {hasHistory && (
            <div className="tc-qv-tabs" role="tablist">
              <button type="button" role="tab" aria-selected={tab === 'photo'} className={tab === 'photo' ? 'active' : ''}
                onClick={() => setTab('photo')}>Photo</button>
              <button type="button" role="tab" aria-selected={tab === 'history'} className={tab === 'history' ? 'active' : ''}
                onClick={() => setTab('history')}>Price history</button>
            </div>
          )}
          {tab === 'history' ? (
            <div className="tc-qv-history">
              <PriceHistoryChart productId={product.id} productName={product.name} currentPrice={product.price}
                cardSet={product.set} cardNumber={product.cardNumber} condition={product.condition} grading={product.grading} />
            </div>
          ) : (
            <>
              <div className="tc-qv-photo">
                {product.imageUrl ? (
                  <img src={product.imageUrl} alt={name}
                    style={{ transform: zoom === 'full' ? 'none' : 'scale(2.5)', transformOrigin: origin }} />
                ) : (
                  <span className="tc-product-noimg">Photo coming soon</span>
                )}
              </div>
              {product.imageUrl && (
                <div className="tc-qv-zooms" aria-label="Zoom">
                  {ZOOMS.map((z) => (
                    <button key={z.id} type="button" className={zoom === z.id ? 'active' : ''} aria-pressed={zoom === z.id}
                      onClick={() => setZoom(z.id)}>{z.label}</button>
                  ))}
                </div>
              )}
            </>
          )}
        </div>

        <div className="tc-qv-info">
          <span className="tc-eyebrow">{conditionLabel(product)}</span>
          <h2>{name}</h2>
          <p className="tc-qv-price">{formatCad(product.price)}</p>
          {sold ? (
            <p className="tc-qv-note">This card has sold.</p>
          ) : outOfStock ? (
            <p className="tc-qv-note">Out of stock right now.</p>
          ) : (
            <button type="button" className="tc-button tc-button-solid tc-qv-add" onClick={() => add(product.id)}
              disabled={state === 'adding'}>
              {state === 'adding' ? 'Adding…' : state === 'added' ? 'Added to cart' : 'Add to cart'}
            </button>
          )}
          {state === 'error' && <p className="tc-product-error" role="alert">Couldn't add to cart. Try again.</p>}
          <dl className="tc-qv-specs">
            {specs.filter(([, v]) => v).map(([k, v]) => (
              <div key={k}><dt>{k}</dt><dd>{v}</dd></div>
            ))}
          </dl>
          {product.description && <p className="tc-qv-desc">{product.description}</p>}
          {siblings.length > 1 && <p className="tc-qv-keys">Use the arrow keys to browse, Esc to close.</p>}
        </div>
      </div>
    </div>
  )
}
