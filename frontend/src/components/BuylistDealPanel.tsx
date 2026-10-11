import { useEffect, useState } from 'react'
import { buylistChatApi } from '../api/buylistChatApi'
import type { ChatDeal, ChatDraft, DealType, StoreCard } from '../api/buylistChatApi'

const usd = new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' })
const cad = new Intl.NumberFormat('en-CA', { style: 'currency', currency: 'CAD' })
const money = (value: number | null | undefined) => (value == null ? '—' : usd.format(value))

const DEAL_LABELS: Record<DealType, string> = { SELL: 'Sell for cash', TRADE: 'Trade for shop cards', PARTIAL: 'Trade + cash' }

/** Sell / trade / partial: rates, shop-card picker, cash ask and the numbers the meter is based on. */
export function BuylistDealPanel({ draft, deal, onDraft, onError }: {
  draft: ChatDraft
  deal: ChatDeal
  onDraft: (draft: ChatDraft) => void
  onError: (err: unknown) => void
}) {
  const [query, setQuery] = useState('')
  const [results, setResults] = useState<StoreCard[]>([])
  const [cash, setCash] = useState(deal.requestedCashUsd != null ? String(deal.requestedCashUsd) : '')
  const trading = deal.dealType !== 'SELL'

  // Shop search (debounced)
  useEffect(() => {
    if (!trading) return
    const timer = setTimeout(() => {
      buylistChatApi.storeCards(query).then(setResults).catch(onError)
    }, 300)
    return () => clearTimeout(timer)
  }, [query, trading, onError])

  const run = (action: Promise<ChatDraft>) => action.then(onDraft).catch(onError)
  const picked = new Set(deal.storeCards.map((c) => c.productId))

  return (
    <div className="tc-bc-deal">
      <div className="tc-bc-deal-types" role="radiogroup" aria-label="How do you want to be paid?">
        {(Object.keys(DEAL_LABELS) as DealType[]).map((t) => (
          <button key={t} type="button" role="radio" aria-checked={deal.dealType === t}
            className={`tc-bc-deal-type ${deal.dealType === t ? 'active' : ''}`}
            onClick={() => run(buylistChatApi.setDeal(draft.draftId, t))}>
            {DEAL_LABELS[t]}
          </button>
        ))}
      </div>
      <p className="tc-bc-rates">{deal.ratesText}</p>

      <div className="tc-bc-offer-grid">
        <div><span>Our cash offer</span><strong>{money(deal.cashOfferUsd)}</strong></div>
        <div><span>Our trade credit</span><strong>{money(deal.tradeCreditUsd)}</strong></div>
        {deal.dealType === 'SELL' && <div><span>Your asking total</span><strong>{money(deal.askTotalUsd)}</strong></div>}
        {trading && <div><span>Shop cards you picked</span><strong>{money(deal.storeTotalUsd)}</strong>
          {deal.storeTotalCad != null && deal.storeCards.length > 0 && <small>{cad.format(deal.storeTotalCad)} CAD</small>}</div>}
      </div>
      {deal.dealType === 'SELL' && <p className="tc-bc-muted">Set your price per card in the list above to ask for more or less.</p>}

      {deal.dealType === 'PARTIAL' && (
        <label className="tc-bc-field">
          <span>Cash you want on top (USD)</span>
          <input type="number" min={0} step="0.01" inputMode="decimal" value={cash}
            placeholder={deal.requestedCashUsd != null ? String(deal.requestedCashUsd) : '0.00'}
            onChange={(e) => setCash(e.target.value)}
            onBlur={() => run(buylistChatApi.setDeal(draft.draftId, 'PARTIAL', cash === '' ? 0 : Number(cash)))} />
        </label>
      )}

      {trading && (
        <div className="tc-bc-shop">
          {deal.storeCards.length > 0 && (
            <ul className="tc-bc-picked">
              {deal.storeCards.map((c) => (
                <li key={c.productId} className={c.available ? '' : 'unavailable'}>
                  <span>{c.name}</span>
                  <span>{c.available ? `${money(c.priceUsd)} (${cad.format(c.priceCad ?? 0)} CAD)` : 'No longer available'}</span>
                  <button type="button" className="tc-bc-link" onClick={() => run(buylistChatApi.removeTradeItem(draft.draftId, c.productId))}>Remove</button>
                </li>
              ))}
            </ul>
          )}
          <label className="tc-bc-field">
            <span>Find cards in our shop</span>
            <input value={query} placeholder="e.g. Charizard" onChange={(e) => setQuery(e.target.value)} />
          </label>
          <ul className="tc-bc-shop-results">
            {results.filter((c) => !picked.has(c.productId)).slice(0, 8).map((c) => (
              <li key={c.productId}>
                {c.imageUrl ? <img src={c.imageUrl} alt="" loading="lazy" /> : <div className="tc-bc-line-thumb" />}
                <span className="tc-bc-shop-name">{c.name}<small className="tc-bc-muted">{[c.setName, c.grading ?? c.condition].filter(Boolean).join(' · ')}</small></span>
                <span className="tc-bc-price">{money(c.priceUsd)}</span>
                <button type="button" className="tc-bc-btn tc-bc-btn-ghost tc-bc-small" onClick={() => run(buylistChatApi.addTradeItem(draft.draftId, c.productId))}>Add</button>
              </li>
            ))}
            {results.length === 0 && <li className="tc-bc-muted">No shop cards match.</li>}
          </ul>
          {deal.usdCadRate != null && <p className="tc-bc-muted">Shop prices are in CAD, shown in USD at {deal.usdCadRate} CAD per USD (Bank of Canada).</p>}
        </div>
      )}

      <p className={`tc-bc-deal-msg ${deal.withinRules ? 'ok' : deal.askRatio != null ? 'over' : ''}`}>{deal.message}</p>
    </div>
  )
}
