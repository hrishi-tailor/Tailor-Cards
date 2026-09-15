import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { WhySellDirectly } from './WhySellDirectly'
import './BuylistPayoutMatrix.css'

export interface PayoutTier {
  id: string
  percentage: string
  rate: number
  label: string
  category: string
  pillLabel: string
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
    label: 'Market Rate',
    category: 'PSA 10 & BGS Black Label',
    pillLabel: 'PSA 10 - 90%',
    shortLabel: 'PSA 10 / Black Label (90%)',
    description: 'Pristine & Gem Mint authenticated slabs. Top market pricing guaranteed.',
    highlight: true,
    badge: 'GEM MINT',
  },
  {
    id: 'graded-slab',
    percentage: '85%',
    rate: 0.85,
    label: 'Market Rate',
    category: 'Graded Slabs (PSA, CGC, BGS)',
    pillLabel: 'Graded Slabs - 85%',
    shortLabel: 'Graded Slab (85%)',
    description: 'Authenticated modern & vintage graded slabs (Grades 8 through 9.5).',
    highlight: false,
    badge: 'GRADED',
  },
  {
    id: 'raw-nm',
    percentage: '80%',
    rate: 0.80,
    label: 'Market Rate',
    category: 'Raw NM Singles',
    pillLabel: 'Raw NM - 80%',
    shortLabel: 'Raw NM Single (80%)',
    description: 'Unpeeled, clean pack-fresh singles, vintage holos, and modern chase cards.',
    highlight: false,
    badge: 'NEAR MINT',
  },
  {
    id: 'sealed',
    percentage: '75%',
    rate: 0.75,
    label: 'Market Rate',
    category: 'Sealed Products',
    pillLabel: 'Sealed - 75%',
    shortLabel: 'Sealed Box (75%)',
    description: 'Factory-sealed booster boxes, Elite Trainer Boxes (ETBs), and collection cases.',
    highlight: false,
    badge: 'SEALED',
  },
  {
    id: 'played',
    percentage: '70%',
    rate: 0.70,
    label: 'Base Rate',
    category: 'Played & Binder Singles',
    pillLabel: 'Played - 70%',
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

  return (
    <section className="tc-matrix-section tc-corner-accent" aria-labelledby="buylist-matrix-heading">
      <div className="tc-matrix-header">
        <div className="tc-matrix-eyebrow">
          <span className="tc-matrix-bullet" aria-hidden="true" />
          <span className="tc-mono">[BUYLIST DISCIPLINE · INSTANT CASH RATES]</span>
        </div>
        <div className="tc-matrix-title-row">
          <div>
            <h2 id="buylist-matrix-heading" className="tc-matrix-title">Buylist Rates &amp; Payout Matrix</h2>
            <p className="tc-matrix-subtitle">
              Transparent, competitive cash percentages based on live market pricing. Click any tier pill to inspect grading criteria and calculate instant cash.
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

      {/* =========================================================================
          Progressive Disclosure: Horizontal Pill Navigation for Payout Tiers
          ========================================================================= */}
      <div className="tc-matrix-pills-container">
        <div className="tc-matrix-pills-scroll" role="tablist" aria-label="Select buylist tier">
          {PAYOUT_TIERS.map((tier) => {
            const isSelected = selectedTierId === tier.id
            return (
              <button
                type="button"
                key={tier.id}
                role="tab"
                aria-selected={isSelected}
                className={`tc-matrix-pill-btn ${tier.highlight ? 'highlight' : ''} ${isSelected ? 'active' : ''}`}
                onClick={() => setSelectedTierId(tier.id)}
              >
                <span className="tc-pill-text">{tier.pillLabel.split(' - ')[0]}</span>
                <span className="tc-pill-separator" aria-hidden="true">—</span>
                <span className="tc-pixel tc-pill-rate">{tier.percentage}</span>
              </button>
            )
          })}
        </div>

        {/* Single Details Panel (Injected dynamically for the active pill) */}
        <div className="tc-tier-details-panel" role="tabpanel" aria-live="polite">
          <div className="tc-tier-details-inner">
            <div className="tc-tier-details-header">
              <div className="tc-tier-meta-left">
                <span className="tc-tier-badge tc-mono">[{selectedTier.badge}]</span>
                <h3 className="tc-tier-title">{selectedTier.category}</h3>
              </div>
              <div className="tc-tier-rate-tag">
                <span className="tc-tier-rate-num tc-pixel">{selectedTier.percentage}</span>
                <span className="tc-tier-rate-sub">{selectedTier.label}</span>
              </div>
            </div>

            <p className="tc-tier-description-paragraph">
              {selectedTier.description}
            </p>
          </div>
        </div>
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
            <span className="tc-result-value tc-pixel">
              ${parsedPrice.toLocaleString('en-CA', { minimumFractionDigits: 2, maximumFractionDigits: 2 })} CAD
            </span>
            <span className="tc-result-sub">Based on verified sales</span>
          </div>

          <div className="tc-calc-result-box tc-result-payout-box">
            <div className="tc-result-badge-row">
              <span className="tc-result-label highlight">TailorCards Cash Payout</span>
              <span className="tc-payout-rate-badge tc-pixel">{selectedTier.percentage} RATE</span>
            </div>
            <span className="tc-result-value payout tc-pixel">
              ${cashPayout.toLocaleString('en-CA', { minimumFractionDigits: 2, maximumFractionDigits: 2 })} CAD
            </span>
            <span className="tc-result-sub highlight">
              Direct Interac e-Transfer or Cash upon inspection
            </span>
          </div>

          <div className="tc-calc-result-box">
            <span className="tc-result-label">Marketplace Fee Protection</span>
            <span className="tc-result-value savings tc-pixel">
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

      {/* Tabbed Reading Pane for Why Sell Directly (Progressive Disclosure) */}
      <WhySellDirectly />
    </section>
  )
}
