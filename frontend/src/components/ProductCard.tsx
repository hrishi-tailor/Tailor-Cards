import { useState } from 'react'
import type { Product } from '../types'
import { conditionLabel, displayName, formatCad, isSealed, isSlab } from '../catalog'
import { useAddToCart } from '../hooks/useAddToCart'
import './Shop.css'

interface ProductCardProps {
  product: Product
  onQuickView: (product: Product) => void
}

/** Shop grid card: photo, grade or condition badge, name, set and number, price, add to cart. */
export function ProductCard({ product, onQuickView }: ProductCardProps) {
  const [imgFailed, setImgFailed] = useState(false)
  const { add, state } = useAddToCart()
  const sold = product.status === 'SOLD'
  const outOfStock = !sold && product.stock <= 0
  const name = displayName(product)
  const meta = [product.set, product.cardNumber ? `#${product.cardNumber.replace(/^#/, '')}` : null].filter(Boolean).join(' · ')
  const featured = isSlab(product) || isSealed(product)

  return (
    <article className={`tc-product ${sold ? 'is-sold' : ''}`}>
      <button type="button" className="tc-product-media" onClick={() => onQuickView(product)}
        aria-label={`Quick view: ${name}`}>
        <span className={`tc-product-badge ${featured ? 'is-foil' : ''}`}>{conditionLabel(product)}</span>
        {product.imageUrl && !imgFailed ? (
          <img src={product.imageUrl} alt={name} loading="lazy" onError={() => setImgFailed(true)} />
        ) : (
          <span className="tc-product-noimg">Photo coming soon</span>
        )}
        {(sold || outOfStock) && <span className="tc-product-flag">{sold ? 'Sold' : 'Out of stock'}</span>}
        <span className="tc-product-quick">Quick view</span>
      </button>
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
