import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import './BuylistPayoutMatrix.css'

export interface PayoutTier {
  id: string
  percentage: string
  rate: number
  label: string
  category: string
  shortLabel: string
  description: string
  highlight: boolean
  badge: string
}

export const PAYOUT_TIERS: PayoutTier[] = [
  {
    id: 'gem-mint',
    percentage: '90%',
    rate: 0.90,
    label: 'Market',
    category: 'PSA 10 & BGS Black Label',
    shortLabel: 'PSA 10 / Black Label (90%)',
    description: 'Pristine & Gem Mint authenticated slabs. Top market pricing guaranteed.',
    highlight: true,
    badge: 'GEM MINT',
  },
  {
    id: 'graded-slab',
    percentage: '85%',
    rate: 0.85,
    label: 'Market',
    category: 'Graded Slabs (PSA, CGC, BGS)',
    shortLabel: 'Graded Slab (85%)',
    description: 'Authenticated modern & vintage graded slabs (Grades 8 through 9.5).',
    highlight: false,
    badge: 'GRADED',
  },
  {
    id: 'raw-nm',
    percentage: '80%',
    rate: 0.80,
    label: 'Market',
    category: 'Raw NM Singles',
    shortLabel: 'Raw NM Single (80%)',
    description: 'Unpeeled, clean pack-fresh singles, vintage holos, and modern chase cards.',
    highlight: false,
    badge: 'NEAR MINT',
  },
  {
    id: 'sealed',
    percentage: '75%',
    rate: 0.75,
    label: 'Market',
    category: 'Sealed Products',
    shortLabel: 'Sealed Box (75%)',
    description: 'Factory-sealed booster boxes, Elite Trainer Boxes (ETBs), and collection cases.',
    highlight: false,
    badge: 'SEALED',
  },
  {
    id: 'played',
    percentage: '70%',
    rate: 0.70,
    label: 'Base',
    category: 'Played & Binder Singles',
    shortLabel: 'Played / Binder (70%)',
    description: 'Lightly played to damaged collection binder singles (LP, MP, HP, DMG).',
    highlight: false,
    badge: 'PLAYED',
  },
]

const PRICE_PRESETS = [100, 250, 500, 1000]

export interface BuylistPayoutMatrixProps {
  showCta?: boolean
  onLockRate?: (tierName: string, rate: number, marketPrice: number, payoutAmount: number) => void
}

export function BuylistPayoutMatrix({ showCta = true, onLockRate }: BuylistPayoutMatrixProps) {
  const navigate = useNavigate()
  const [selectedTierId, setSelectedTierId] = useState<string>('raw-nm')
  const [marketPriceInput, setMarketPriceInput] = useState<string>('500')
  const [lockedNotice, setLockedNotice] = useState<string | null>(null)

  const selectedTier = PAYOUT_TIERS.find((t) => t.id === selectedTierId) || PAYOUT_TIERS[2]
  const parsedPrice = Math.max(0, parseFloat(marketPriceInput) || 0)
  const cashPayout = parsedPrice * selectedTier.rate
  const marketplaceSavings = parsedPrice * 0.1325

  const handlePriceChange = (val: string) => {
    // Only permit digits and single decimal point
    if (/^\d*\.?\d{0,2}$/.test(val)) {
      setMarketPriceInput(val)
    }
  }

  const handleSelectPreset = (preset: number) => {
    setMarketPriceInput(preset.toString())
  }

  const handleLockRateClick = () => {
    const finalMarket = parsedPrice > 0 ? parsedPrice : 250
    const finalPayout = finalMarket * selectedTier.rate

    if (onLockRate) {
      onLockRate(selectedTier.category, selectedTier.rate, finalMarket, finalPayout)
      setLockedNotice(`Locked in ${selectedTier.percentage} payout of $${finalPayout.toFixed(2)} CAD. Payout amount pre-filled below.`)
      setTimeout(() => setLockedNotice(null), 4500)
    } else {
      navigate(`/sell?tier=${encodeURIComponent(selectedTier.category)}&rate=${selectedTier.rate}&market=${finalMarket}&payout=${finalPayout.toFixed(2)}#buylist-form`)
    }
  }

  const sellBenefits = [
    {
      title: 'Zero Seller Fees',
      subtitle: 'Beat the ~13% Marketplace Cut',
      text: 'Keep 100% of your payout quote. No eBay, TCGplayer, or payment processing deductions.',
      icon: (
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" className="tc-matrix-benefit-svg" aria-hidden="true">
          <path strokeLinecap="round" strokeLinejoin="round" d="M12 6v12m-3-2.818.879.659c1.171.879 3.07.879 4.242 0 1.172-.879 1.172-2.303 0-3.182C13.536 12.219 12.768 12 12 12c-.725 0-1.45-.22-2.003-.659-1.106-.879-1.106-2.303 0-3.182s2.9-.879 4.006 0l.415.33M21 12a9 9 0 1 1-18 0 9 9 0 0 1 18 0Z" />
        </svg>
      ),
    },
    {
      title: 'Guaranteed Fast Payouts',
      subtitle: 'Direct Interac e-Transfer or Cash',
      text: 'Immediate payment issued within hours of appraisal verification. Never wait weeks for funds.',
      icon: (
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" className="tc-matrix-benefit-svg" aria-hidden="true">
          <path strokeLinecap="round" strokeLinejoin="round" d="M9 12.75 11.25 15 15 9.75M21 12c0 1.268-.63 2.39-1.593 3.068a3.745 3.745 0 0 1-1.043 3.296 3.745 3.745 0 0 1-3.296 1.043A3.745 3.745 0 0 1 12 21c-1.268 0-2.39-.63-3.068-1.593a3.746 3.746 0 0 1-3.296-1.043 3.745 3.745 0 0 1-1.043-3.296A3.745 3.745 0 0 1 3 12c0-1.268.63-2.39 1.593-3.068a3.745 3.745 0 0 1 1.043-3.296 3.746 3.746 0 0 1 3.296-1.043A3.746 3.746 0 0 1 12 3c1.268 0 2.39.63 3.068 1.593a3.746 3.746 0 0 1 3.296 1.043 3.746 3.746 0 0 1 1.043 3.296A3.745 3.745 0 0 1 21 12Z" />
        </svg>
      ),
    },
    {
      title: '250% Corner Loupe Evaluation',
      subtitle: 'Transparent, Calibrated Grading',
      text: 'Every card inspected corner-by-corner on calibrated optical loupe with live photo tracking in your seller portal.',
      icon: (
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" className="tc-matrix-benefit-svg" aria-hidden="true">
          <circle cx="11" cy="11" r="7" />
          <line x1="21" y1="21" x2="16.65" y2="16.65" />
          <path strokeLinecap="round" d="M11 8v6M8 11h6" />
        </svg>
      ),
    },
    {
      title: 'Zero Return / Chargeback Fraud',
      subtitle: 'Protected Seller Sovereignty',
      text: 'No mail return scams, counterfeit swap claims, or hold periods. Once accepted, your transaction is closed.',
      icon: (
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" className="tc-matrix-benefit-svg" aria-hidden="true">
          <path strokeLinecap="round" strokeLinejoin="round" d="M9 12.75 11.25 15 15 9.75m-3-7.036A11.959 11.959 0 0 1 3.598 6 11.99 11.99 0 0 0 3 9.749c0 5.592 3.824 10.29 9 11.623 5.176-1.332 9-6.03 9-11.622 0-1.31-.21-2.571-.598-3.751h-.152c-3.196 0-6.1-1.248-8.25-3.285Z" />
        </svg>
      ),
    },
  ]

  return (
    <section className="tc-matrix-section tc-corner-accent" aria-labelledby="buylist-matrix-heading">
      <div className="tc-matrix-header">
        <div className="tc-matrix-eyebrow">
          <span className="tc-matrix-bullet" aria-hidden="true" />
          <span>BOUTIQUE BUYLIST DISCIPLINE · DIRECT CASH RATES</span>
        </div>
        <div className="tc-matrix-title-row">
          <div>
            <h2 id="buylist-matrix-heading" className="tc-matrix-title">Buylist Rates &amp; Payout Matrix</h2>
            <p className="tc-matrix-subtitle">
              Transparent, competitive cash percentages based on live market pricing. Click any tier to calculate your instant cash offer.
            </p>
          </div>
          {showCta && (
            <Link to="/sell" className="tc-matrix-cta-btn">
              <span>Submit to Buylist</span>
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" width="14" height="14" aria-hidden="true">
                <path strokeLinecap="round" strokeLinejoin="round" d="M5 12h14M12 5l7 7-7 7" />
              </svg>
            </Link>
          )}
        </div>
      </div>

      {/* 5 Interactive Payout Tier Cards */}
      <div className="tc-matrix-grid" role="radiogroup" aria-label="Select buylist tier">
        {PAYOUT_TIERS.map((tier) => {
          const isSelected = selectedTierId === tier.id
          return (
            <button
              type="button"
              key={tier.id}
              className={`tc-matrix-card ${tier.highlight ? 'tier-highlight' : ''} ${isSelected ? 'tier-selected' : ''}`}
              onClick={() => setSelectedTierId(tier.id)}
              role="radio"
              aria-checked={isSelected}
            >
              <div className="tc-matrix-card-top">
                <span className="tc-matrix-badge tc-mono">{tier.badge}</span>
                <div className="tc-matrix-percent-wrap">
                  <span className="tc-matrix-percent tc-mono">{tier.percentage}</span>
                  <span className="tc-matrix-label">{tier.label}</span>
                </div>
              </div>
              <h3 className="tc-matrix-tier-name">{tier.category}</h3>
              <p className="tc-matrix-tier-desc">{tier.description}</p>
              <div className="tc-tier-card-footer">
                <span className={`tc-tier-select-indicator ${isSelected ? 'selected' : ''}`}>
                  {isSelected ? '● Active Tier' : 'Select'}
                </span>
              </div>
            </button>
          )
        })}
      </div>

      {/* Dynamic Cash Offer Calculator (Interactive Feature) */}
      <div className="tc-calculator-card">
        <div className="tc-calculator-top">
          <div className="tc-calculator-badge-wrap">
            <span className="tc-calc-badge tc-mono">[ESTIMATOR]</span>
            <h3 className="tc-calculator-heading">
              Instant Cash Calculator &middot; <span className="tc-calc-selected-tier">{selectedTier.shortLabel}</span>
            </h3>
          </div>
          <span className="tc-calc-note tc-mono">Selected: {selectedTier.percentage} Direct Payout Rate</span>
        </div>

        {/* Input & Presets Row */}
        <div className="tc-calc-controls-row">
          <div className="tc-calc-input-group">
            <label htmlFor="calc-market-price" className="tc-calc-label">
              Estimated Market Value (CAD):
            </label>
            <div className="tc-calc-input-wrapper">
              <span className="tc-calc-currency tc-mono">$</span>
              <input
                id="calc-market-price"
                type="text"
                inputMode="decimal"
                className="tc-calc-input tc-mono"
                value={marketPriceInput}
                onChange={(e) => handlePriceChange(e.target.value)}
                placeholder="500"
              />
              <span className="tc-calc-currency-code tc-mono">CAD</span>
            </div>
          </div>

          <div className="tc-calc-presets-group">
            <span className="tc-calc-presets-label">Quick Presets:</span>
            <div className="tc-calc-presets-buttons">
              {PRICE_PRESETS.map((p) => (
                <button
                  key={p}
                  type="button"
                  className={`tc-preset-btn tc-mono ${marketPriceInput === p.toString() ? 'active' : ''}`}
                  onClick={() => handleSelectPreset(p)}
                >
                  ${p}
                </button>
              ))}
            </div>
          </div>
        </div>

        {/* Live Calculated Output Grid */}
        <div className="tc-calc-results-grid">
          <div className="tc-calc-result-box">
            <span className="tc-result-label">Market Value</span>
            <span className="tc-result-value tc-mono">
              ${parsedPrice.toLocaleString('en-CA', { minimumFractionDigits: 2, maximumFractionDigits: 2 })} CAD
            </span>
            <span className="tc-result-sub">Based on verified sales</span>
          </div>

          <div className="tc-calc-result-box tc-result-payout-box">
            <div className="tc-result-badge-row">
              <span className="tc-result-label highlight">TailorCards Cash Payout</span>
              <span className="tc-payout-rate-badge tc-mono">{selectedTier.percentage} RATE</span>
            </div>
            <span className="tc-result-value payout tc-mono">
              ${cashPayout.toLocaleString('en-CA', { minimumFractionDigits: 2, maximumFractionDigits: 2 })} CAD
            </span>
            <span className="tc-result-sub highlight">
              Direct Interac e-Transfer or Cash upon inspection
            </span>
          </div>

          <div className="tc-calc-result-box">
            <span className="tc-result-label">Marketplace Fee Protection</span>
            <span className="tc-result-value savings tc-mono">
              +${marketplaceSavings.toLocaleString('en-CA', { minimumFractionDigits: 2, maximumFractionDigits: 2 })} CAD
            </span>
            <span className="tc-result-sub">
              Saved vs. ~13.25% eBay cut + zero return fraud risk
            </span>
          </div>
        </div>

        {/* Calculator Action CTA */}
        <div className="tc-calc-cta-row">
          <button
            type="button"
            className="tc-calc-lock-btn"
            onClick={handleLockRateClick}
          >
            <span>{onLockRate ? 'Lock in Rate & Pre-fill Form Below' : 'Lock in This Rate & Submit Card'}</span>
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" width="16" height="16" aria-hidden="true">
              <path strokeLinecap="round" strokeLinejoin="round" d="M13.5 4.5 21 12m0 0-7.5 7.5M21 12H3" />
            </svg>
          </button>
          {lockedNotice && (
            <div className="tc-lock-notice" role="status">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" width="14" height="14" aria-hidden="true">
                <path strokeLinecap="round" strokeLinejoin="round" d="m4.5 12.75 6 6 9-13.5" />
              </svg>
              <span>{lockedNotice}</span>
            </div>
          )}
        </div>
      </div>

      {/* Why Sell to TailorCards Callout */}
      <div className="tc-matrix-why-box">
        <div className="tc-matrix-why-header">
          <span className="tc-matrix-why-eyebrow tc-mono">[ADVANTAGE]</span>
          <h3 className="tc-matrix-why-title">Why Sell Directly to TailorCards?</h3>
        </div>
        <div className="tc-matrix-benefits-grid">
          {sellBenefits.map((b) => (
            <div key={b.title} className="tc-matrix-benefit-item">
              <div className="tc-benefit-icon-wrap">{b.icon}</div>
              <div className="tc-benefit-text-wrap">
                <h4 className="tc-benefit-title">{b.title}</h4>
                <span className="tc-benefit-sub">{b.subtitle}</span>
                <p className="tc-benefit-desc">{b.text}</p>
              </div>
            </div>
          ))}
        </div>
      </div>
    </section>
  )
}
