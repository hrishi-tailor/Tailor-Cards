import { useState } from 'react'
import type { Product } from '../types'
import { conditionLabel, displayName, formatCad, isOfficialImage, isSealed, isSlab, productImages } from '../catalog'
import { useAddToCart } from '../hooks/useAddToCart'
import './Shop.css'

interface ProductCardProps {
  product: Product
  onQuickView: (product: Product) => void
}

/**
 * Shop grid card: the default picture (official card image), with arrows on hover to step through the
 * seller's own photos; grade or condition badge, name, set and number, price, add to cart.
 */
export function ProductCard({ product, onQuickView }: ProductCardProps) {
  const images = productImages(product)
  const [index, setIndex] = useState(0)
  const [failed, setFailed] = useState<Record<string, boolean>>({})
  const { add, state } = useAddToCart()
  const sold = product.status === 'SOLD'
  const outOfStock = !sold && product.stock <= 0
  const name = displayName(product)
  const meta = [product.set, product.cardNumber ? `#${product.cardNumber.replace(/^#/, '')}` : null].filter(Boolean).join(' · ')
  const featured = isSlab(product) || isSealed(product)
  const current = images[index]
  const many = images.length > 1

  const step = (e: React.MouseEvent, delta: number) => {
    e.stopPropagation()
    setIndex((i) => (i + delta + images.length) % images.length)
  }

  return (
    <article className={`tc-product ${sold ? 'is-sold' : ''}`}>
      <div className="tc-product-media">
        <button type="button" className="tc-product-open" onClick={() => onQuickView(product)}
          aria-label={`Quick view: ${name}`}>
          {current && !failed[current] ? (
            <img key={current} src={current} alt={index === 0 ? name : `${name}, photo ${index}`} loading="lazy"
              className={isOfficialImage(current) ? 'is-official' : 'is-photo'}
              onError={() => setFailed((f) => ({ ...f, [current]: true }))} />
          ) : (
            <span className="tc-product-noimg">Photo coming soon</span>
          )}
          <span className="tc-product-quick">Quick view</span>
        </button>
        <span className={`tc-product-badge ${featured ? 'is-foil' : ''}`}>{conditionLabel(product)}</span>
        {(sold || outOfStock) && <span className="tc-product-flag">{sold ? 'Sold' : 'Out of stock'}</span>}
        {index > 0 && !isOfficialImage(current) && <span className="tc-product-actual">Actual card</span>}
        {many && (
          <>
            <button type="button" className="tc-gallery-arrow is-prev" onClick={(e) => step(e, -1)}
              aria-label={`Previous photo of ${name}`}>
              <svg viewBox="0 0 24 24" aria-hidden="true"><path d="m15 6-6 6 6 6" /></svg>
            </button>
            <button type="button" className="tc-gallery-arrow is-next" onClick={(e) => step(e, 1)}
              aria-label={`Next photo of ${name}`}>
              <svg viewBox="0 0 24 24" aria-hidden="true"><path d="m9 6 6 6-6 6" /></svg>
            </button>
            <span className="tc-gallery-dots" aria-hidden="true">
              {images.map((src, i) => <span key={src} className={i === index ? 'on' : ''} />)}
            </span>
          </>
        )}
      </div>
      <div className="tc-product-body">
        <h3 className="tc-product-name">
          <button type="button" onClick={() => onQuickView(product)}>{name}</button>
        </h3>
        {meta && <p className="tc-product-meta">{meta}</p>}
        <p className="tc-product-price">{formatCad(product.price)}</p>
      </div>
      {!sold && !outOfStock && (
        <button type="button" className="tc-button tc-button-ghost tc-product-add" onClick={() => add(product.id)}
          disabled={state === 'adding'}>
          {state === 'adding' ? 'Adding…' : state === 'added' ? 'Added to cart' : 'Add to cart'}
        </button>
      )}
      {state === 'error' && <p className="tc-product-error" role="alert">Couldn't add to cart. Try again.</p>}
    </article>
  )
}
