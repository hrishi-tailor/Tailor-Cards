import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useCart } from '../context/CartContext'
import './Cart.css'

export function Cart() {
  const { cart, loading, error, removeFromCart } = useCart()
  const [removingId, setRemovingId] = useState<number | null>(null)
  const [actionError, setActionError] = useState<string | null>(null)

  const handleRemove = async (itemId: number) => {
    try {
      setRemovingId(itemId)
      setActionError(null)
      await removeFromCart(itemId)
    } catch (err) {
      setActionError(err instanceof Error ? err.message : 'Failed to remove item')
    } finally {
      setRemovingId(null)
    }
  }

  if (loading && !cart) {
    return (
      <div className="tc-cart-wrapper">
        <div className="tc-status-box">
          <div className="tc-spinner" />
          <p>Loading your shopping cart...</p>
        </div>
      </div>
    )
  }

  if (error && !cart) {
    return (
      <div className="tc-cart-wrapper">
        <div className="tc-status-box">
          <h2>Unable to load cart</h2>
          <p>{error}</p>
          <Link to="/" className="tc-browse-btn">
            Return to Catalog
          </Link>
        </div>
      </div>
    )
  }

  const items = cart?.items || []
  const totalItems = cart?.totalItems ?? 0
  const totalPrice = cart?.totalPrice ?? 0

  if (items.length === 0) {
    return (
      <div className="tc-cart-wrapper">
        <div className="tc-empty-cart-card">
          <div className="tc-empty-icon-wrap">
            <svg className="tc-empty-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
              <circle cx="9" cy="21" r="1" />
              <circle cx="20" cy="21" r="1" />
              <path d="M1 1h4l2.68 13.39a2 2 0 0 0 2 1.61h9.72a2 2 0 0 0 2-1.61L23 6H6" />
            </svg>
          </div>
          <h2>Your Cart is Empty</h2>
          <p>You haven't added any cards or collectibles yet. Explore our vault of rare holographic singles, PSA slabs, and supplies!</p>
          <Link to="/" className="tc-browse-btn">
            Browse All Cards
          </Link>
        </div>
      </div>
    )
  }

  return (
    <div className="tc-cart-wrapper">
      {actionError && (
        <div className="tc-error-banner">
          {actionError}
        </div>
      )}

      {/* Two-Column Layout (Left 75%, Right 25%) */}
      <div className="tc-cart-grid-layout">
        {/* Left Column (75%): Cart Items List */}
        <section className="tc-cart-items-card">
          <div className="tc-cart-card-header">
            <h1>Shopping Cart</h1>
            <span className="tc-cart-session-info">
              Guest Session: <code>{cart?.cartSessionId}</code>
            </span>
          </div>

          <div className="tc-cart-items-list">
            {items.map((item) => (
              <article key={item.id} className="tc-cart-item-row">
                {/* Image on Left */}
                <div className="tc-cart-item-img-wrap">
                  {item.imageUrl ? (
                    <img
                      src={item.imageUrl}
                      alt={item.productName}
                      className="tc-cart-item-img"
                    />
                  ) : (
                    <div className="tc-cart-no-img">No Image</div>
                  )}
                </div>

                {/* Details in Middle */}
                <div className="tc-cart-item-info">
                  <h2 className="tc-cart-item-title">{item.productName}</h2>
                  <div className="tc-cart-item-meta">
                    <span className="tc-cart-qty-pill">Qty: {item.quantity}</span>
                    {item.quantity > 1 && (
                      <span className="tc-cart-unit-price">
                        ${typeof item.price === 'number' ? item.price.toFixed(2) : item.price} each
                      </span>
                    )}
                  </div>
                  <button
                    type="button"
                    onClick={() => handleRemove(item.id)}
                    disabled={removingId === item.id}
                    className="tc-cart-remove-btn"
                  >
                    {removingId === item.id ? 'Removing...' : 'Remove item'}
                  </button>
                </div>

                {/* Price on Right */}
                <div className="tc-cart-item-price-wrap">
                  <p className="tc-cart-item-total">
                    ${typeof item.subtotal === 'number' ? item.subtotal.toFixed(2) : item.subtotal}
                  </p>
                </div>
              </article>
            ))}
          </div>

          {/* Left Column Subtotal */}
          <div className="tc-cart-left-footer">
            <Link to="/" className="tc-continue-link">
              ← Continue Shopping
            </Link>
            <div className="tc-cart-left-subtotal">
              Subtotal ({totalItems} {totalItems === 1 ? 'item' : 'items'}):
              <strong>${typeof totalPrice === 'number' ? totalPrice.toFixed(2) : totalPrice}</strong>
            </div>
          </div>
        </section>

        {/* Right Column (25%): Order Summary Card */}
        <aside className="tc-cart-summary-card">
          <h2 className="tc-summary-title">Order Summary</h2>

          <div className="tc-summary-row">
            <span>Items ({totalItems}):</span>
            <span>${typeof totalPrice === 'number' ? totalPrice.toFixed(2) : totalPrice}</span>
          </div>

          <div className="tc-summary-row">
            <span>Insured Shipping:</span>
            <span style={{ color: 'var(--tc-green)', fontWeight: 600 }}>Free</span>
          </div>

          <div className="tc-summary-row total-row">
            <span>Estimated Total:</span>
            <span className="tc-summary-total-amount">
              ${typeof totalPrice === 'number' ? totalPrice.toFixed(2) : totalPrice}
            </span>
          </div>

          {/* Indigo/Purple Proceed to Checkout button */}
          <button
            type="button"
            className="tc-checkout-btn"
            onClick={() => alert('Proceeding to Secure Checkout...')}
          >
            Proceed to Checkout
          </button>

          <div className="tc-security-note">
            🔒 Fully insured & tamper-evident packaging
          </div>
        </aside>
      </div>
    </div>
  )
}
