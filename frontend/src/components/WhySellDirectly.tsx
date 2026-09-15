import { useState } from 'react'
import './WhySellDirectly.css'

export interface WhySellTab {
  id: string
  label: string
  title: string
  subtitle: string
  copy: string
  badge: string
}

export const WHY_SELL_TABS: WhySellTab[] = [
  {
    id: 'zero-fees',
    label: 'Zero Seller Fees',
    title: 'Zero Seller Fees',
    subtitle: 'Beat the ~13% Marketplace Cut',
    copy: 'Keep 100% of your payout quote. No eBay, TCGplayer, or payment processing deductions.',
    badge: '0% FEES',
  },
  {
    id: 'fast-payouts',
    label: 'Guaranteed Fast Payouts',
    title: 'Guaranteed Fast Payouts',
    subtitle: 'Direct Interac e-Transfer or Cash',
    copy: 'Immediate payment issued within hours of appraisal verification. Never wait weeks for funds.',
    badge: 'INSTANT CASH',
  },
  {
    id: 'corner-loupe',
    label: '250% Corner Loupe',
    title: '250% Corner Loupe Evaluation',
    subtitle: 'Transparent, Calibrated Grading',
    copy: 'Every card inspected corner-by-corner on calibrated optical loupe with live photo tracking in your seller portal.',
    badge: 'OPTICAL MACRO',
  },
  {
    id: 'zero-fraud',
    label: 'Zero Return Fraud',
    title: 'Zero Return / Chargeback Fraud',
    subtitle: 'Protected Seller Sovereignty',
    copy: 'No mail return scams, counterfeit swap claims, or hold periods. Once accepted, your transaction is closed.',
    badge: 'SOVEREIGN SALE',
  },
]

export function WhySellDirectly() {
  const [activeTabId, setActiveTabId] = useState<string>(WHY_SELL_TABS[0].id)
  const activeTab = WHY_SELL_TABS.find((t) => t.id === activeTabId) || WHY_SELL_TABS[0]

  return (
    <section className="tc-why-pane-section" aria-labelledby="why-sell-heading">
      <div className="tc-why-pane-header">
        <div className="tc-why-pane-eyebrow">
          <span className="tc-why-bullet" aria-hidden="true" />
          <span className="tc-mono">[LIQUIDITY ARCHITECTURE]</span>
        </div>
        <h3 id="why-sell-heading" className="tc-why-pane-title">
          Why Sell Directly to TailorCards?
        </h3>
        <p className="tc-why-pane-subtitle">
          Direct private liquidity vs. public marketplace friction. Select an advantage below to view appraisal &amp; payout standards.
        </p>
      </div>

      {/* Two-Column Interactive Tabbed Reading Pane */}
      <div className="tc-why-tabbed-container">
        {/* Left Column (The Menu): 4 Clickable Minimalist Tabs */}
        <div className="tc-why-menu-col" role="tablist" aria-label="Why Sell Advantages">
          {WHY_SELL_TABS.map((tab) => {
            const isActive = tab.id === activeTabId
            return (
              <button
                key={tab.id}
                type="button"
                role="tab"
                aria-selected={isActive}
                className={`tc-why-menu-tab ${isActive ? 'active' : ''}`}
                onClick={() => setActiveTabId(tab.id)}
              >
                <span className="tc-why-tab-indicator" aria-hidden="true" />
                <span className="tc-why-tab-label">{tab.label}</span>
                <span className="tc-why-tab-chevron" aria-hidden="true">→</span>
              </button>
            )
          })}
        </div>

        {/* Right Column (The Display): Single Gen 5 Styled Text Box */}
        <div className="tc-why-display-col" role="tabpanel" aria-live="polite">
          <div className="tc-why-display-box tc-corner-accent">
            <div className="tc-why-display-top">
              <span className="tc-why-badge tc-mono">[{activeTab.badge}]</span>
              <span className="tc-why-step-indicator tc-mono">
                {WHY_SELL_TABS.findIndex((t) => t.id === activeTab.id) + 1} / {WHY_SELL_TABS.length}
              </span>
            </div>

            <h4 className="tc-why-display-title">{activeTab.title}</h4>
            <span className="tc-why-display-sub">{activeTab.subtitle}</span>

            <p className="tc-why-display-paragraph">
              {activeTab.copy}
            </p>
          </div>
        </div>
      </div>
    </section>
  )
}
