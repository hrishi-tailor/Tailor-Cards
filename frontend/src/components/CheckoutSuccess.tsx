import { useEffect } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { useCart } from '../context/CartContext'
import './CheckoutSuccess.css'

export function CheckoutSuccess() {
  const [searchParams] = useSearchParams()
  const sessionId = searchParams.get('session_id')
  const { clearCart } = useCart()
  const isDemo = sessionId?.startsWith('demo_')

  useEffect(() => {
    // Clear the local cart state upon successful checkout
    clearCart()
  }, [clearCart])

  return (
    <div className="tc-success-page">
      <div className="tc-success-card tc-corner-accent">
        {/* Verification Icon Badge */}
        <div className="tc-success-icon-wrap">
          <svg
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            strokeWidth="2"
            className="tc-success-check-svg"
            aria-hidden="true"
          >
            <path strokeLinecap="round" strokeLinejoin="round" d="m4.5 12.75 6 6 9-13.5" />
          </svg>
        </div>

        <div className="tc-success-badge tc-mono">
          {isDemo ? 'Demo order confirmed' : 'Order confirmed'}
        </div>

        <h1 className="tc-success-title">
          {isDemo ? 'Demo Order Verified & Logged' : 'Payment Verified & Secured'}
        </h1>

        <p className="tc-success-subtitle">
          {isDemo ? (
            <>
              Thank you for evaluating <strong>Tailor Cards</strong>! This was a demo order, so no payment was taken.
            </>
          ) : (
            <>
              Thank you for your order. We'll pack your cards and email you when they ship.
            </>
          )}
        </p>

        {/* Order Dossier Details */}
        <div className="tc-success-dossier">
          <div className="tc-dossier-row">
            <span className="tc-dossier-label">{isDemo ? 'Evaluation Session ID' : 'Stripe Session ID'}</span>
            <span className="tc-dossier-value tc-mono">{sessionId || 'cs_verified_payment'}</span>
          </div>

          <div className="tc-dossier-row">
            <span className="tc-dossier-label">Payment Status</span>
            <span className="tc-dossier-value status-paid tc-mono">
              {isDemo ? '● DEMO SIMULATION (NO CHARGE)' : '● PAID IN FULL (CAD)'}
            </span>
          </div>

          <div className="tc-dossier-row">
            <span className="tc-dossier-label">Packing</span>
            <span className="tc-dossier-value">Checked and packed in a top loader</span>
          </div>

          <div className="tc-dossier-row">
            <span className="tc-dossier-label">Shipping &amp; Tracking</span>
            <span className="tc-dossier-value">Fully insured tracked courier dispatch</span>
          </div>
        </div>

        {/* Trust Notice */}
        <div className="tc-success-note">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" className="tc-note-icon" aria-hidden="true">
            <path strokeLinecap="round" strokeLinejoin="round" d="M9 12.75 11.25 15 15 9.75m-3-7.036A11.959 11.959 0 0 1 3.598 6 11.99 11.99 0 0 0 3 9.749c0 5.592 3.824 10.29 9 11.623 5.176-1.332 9-6.03 9-11.622 0-1.31-.21-2.571-.598-3.751h-.152c-3.196 0-6.1-1.248-8.25-3.285Z" />
          </svg>
          <span>A receipt and tracking confirmation will be delivered to your billing email address.</span>
        </div>

        {/* Actions */}
        <div className="tc-success-actions">
          <Link to="/shop" className="tc-success-primary-btn">
            Continue shopping
          </Link>
          <Link to="/sell" className="tc-success-secondary-btn">
            Sell to Us &rarr;
          </Link>
        </div>
      </div>
    </div>
  )
}
