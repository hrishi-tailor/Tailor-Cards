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
      <div className="cart-container">
        <div className="cart-empty">
          <h2>Loading cart...</h2>
        </div>
      </div>
    )
  }

  if (error && !cart) {
    return (
      <div className="cart-container">
        <div className="cart-empty">
          <h2>Unable to load your cart</h2>
          <p>{error}</p>
          <Link to="/" className="shop-link">Return to Catalog</Link>
        </div>
      </div>
    )
  }

  const items = cart?.items || []

  if (items.length === 0) {
    return (
      <div className="cart-container">
        <div className="cart-header">
          <h1>Your Shopping Cart</h1>
        </div>
        <div className="cart-empty">
          <h2>Your cart is currently empty</h2>
          <p>Explore our catalog and find your next favorite card or accessory!</p>
          <Link to="/" className="shop-link">Browse Products</Link>
        </div>
      </div>
    )
  }

  return (
    <div className="cart-container">
      <div className="cart-header">
        <h1>Your Shopping Cart</h1>
        <p>Guest Session: <code>{cart?.cartSessionId}</code></p>
      </div>

      {actionError && (
        <div className="error-banner" style={{ color: '#dc2626', marginBottom: 16 }}>
          {actionError}
        </div>
      )}

      <div className="cart-table-wrapper">
        <table className="cart-table">
          <thead>
            <tr>
              <th>Product</th>
              <th>Price</th>
              <th>Quantity</th>
              <th>Subtotal</th>
              <th>Action</th>
            </tr>
          </thead>
          <tbody>
            {items.map((item) => (
              <tr key={item.id}>
                <td>
                  <div className="cart-item-info">
                    {item.imageUrl && (
                      <img
                        src={item.imageUrl}
                        alt={item.productName}
                        className="cart-item-image"
                      />
                    )}
                    <div>
                      <p className="cart-item-name">{item.productName}</p>
                    </div>
                  </div>
                </td>
                <td className="cart-item-price">
                  ${typeof item.price === 'number' ? item.price.toFixed(2) : item.price}
                </td>
                <td>{item.quantity}</td>
                <td className="cart-item-subtotal">
                  ${typeof item.subtotal === 'number' ? item.subtotal.toFixed(2) : item.subtotal}
                </td>
                <td>
                  <button
                    type="button"
                    onClick={() => handleRemove(item.id)}
                    disabled={removingId === item.id}
                    className="remove-btn"
                  >
                    {removingId === item.id ? 'Removing...' : 'Remove'}
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <div className="cart-summary">
        <div className="cart-summary-total">
          <span className="summary-label">Total Amount:</span>
          <span className="summary-amount">
            ${typeof cart?.totalPrice === 'number' ? cart.totalPrice.toFixed(2) : '0.00'}
          </span>
        </div>
        <div className="cart-actions">
          <Link to="/" className="continue-btn">
            Continue Shopping
          </Link>
          <button type="button" className="checkout-btn" onClick={() => alert('Proceeding to Checkout!')}>
            Checkout
          </button>
        </div>
      </div>
    </div>
  )
}
