import { useEffect, useState } from 'react'
import { getAdminBuylistSubmission, isDemoRole, resetBuylistChatDailyLimit, updateBuylistStatus } from '../api/buylistApi'
import type { BuylistSubmission } from '../types'

const usd = new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' })
const money = (value: number | null | undefined) => (value == null ? 'No price' : usd.format(value))

/** Chatbot details inside the admin buylist drawer: value, likelihood, red flags, lines, transcript, counter. */
export function AdminChatSubmissionPanel({ submission, onUpdated }: {
  submission: BuylistSubmission
  onUpdated: (updated: BuylistSubmission) => void
}) {
  const [detail, setDetail] = useState<BuylistSubmission | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [counter, setCounter] = useState('')
  const [busy, setBusy] = useState(false)
  const [notice, setNotice] = useState<string | null>(null)
  const demo = isDemoRole()

  useEffect(() => {
    let active = true
    getAdminBuylistSubmission(submission.id)
      .then((d) => { if (active) setDetail(d) })
      .catch((e: unknown) => { if (active) setError(e instanceof Error ? e.message : 'Failed to load details') })
    return () => { active = false }
  }, [submission.id, submission.status])

  const chat = (detail ?? submission).chatDetails
  if (!chat) return null

  const sendCounter = async () => {
    const amount = Number(counter)
    if (!(amount > 0)) return
    setBusy(true)
    try {
      onUpdated(await updateBuylistStatus(submission.id, 'OFFERED', amount))
      setNotice(`Counter of ${usd.format(amount)} USD recorded`)
      setCounter('')
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : 'Counter failed')
    } finally {
      setBusy(false)
    }
  }

  const resetLimit = async () => {
    setBusy(true)
    try {
      const released = await resetBuylistChatDailyLimit(submission.customerEmail)
      setNotice(released > 0 ? 'Daily limit reset: the customer can submit again today' : 'Nothing to reset for today')
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : 'Reset failed')
    } finally {
      setBusy(false)
    }
  }

  return (
    <section className="tc-chat-admin">
      <div className="tc-chat-admin-stats">
        <div><span>Market reference</span><strong>{money(chat.totalMarketUsd)} USD</strong></div>
        <div><span>Eligible</span><strong>{money(chat.eligibleMarketUsd)} USD</strong></div>
        <div><span>Estimated chance</span><strong>{chat.likelihoodPct ?? '—'}%</strong></div>
        {chat.ownerDecision && <div><span>Your decision</span><strong>{chat.ownerDecision}{chat.counterAmountUsd != null ? ` (${usd.format(chat.counterAmountUsd)})` : ''}</strong></div>}
      </div>
      {chat.dealType && (
        <div className="tc-chat-admin-deal">
          <strong>{chat.dealType === 'SELL' ? 'Sell for cash' : chat.dealType === 'TRADE' ? 'Trade for shop cards' : 'Trade + cash'}</strong>
          <span>Cash offer at your rates: {money(chat.cashOfferUsd)} · Trade credit: {money(chat.tradeCreditUsd)}</span>
          {chat.requestedCashUsd != null && <span>Customer asks: {money(chat.requestedCashUsd)} cash</span>}
          {chat.storeCards && chat.storeCards.length > 0 && (
            <span>Shop cards: {chat.storeCards.map((c) => `${c.name} (${money(Number(c.priceUsd))})`).join(', ')} = {money(chat.storeTotalUsd)}
              {chat.usdCadRate != null ? ` at ${chat.usdCadRate} CAD/USD` : ''}</span>
          )}
          {chat.askRatio != null && <span className={chat.askRatio > 1.005 ? 'tc-chat-admin-error' : 'tc-chat-admin-notice'}>
            Request is {chat.askRatio}× your rates</span>}
        </div>
      )}

      {chat.quoteExpiresAt && (
        <p className="tc-chat-admin-muted">Quote reference valid until {new Date(chat.quoteExpiresAt).toLocaleString()}; re-price on arrival.</p>
      )}

      {chat.redFlags && chat.redFlags.length > 0 && (
        <div className="tc-chat-admin-flags">
          <strong>Red flags</strong>
          <ul>{chat.redFlags.map((f) => <li key={f}>{f}</li>)}</ul>
        </div>
      )}

      {error && <p className="tc-chat-admin-error">{error}</p>}
      {notice && <p className="tc-chat-admin-notice">{notice}</p>}

      {!demo && (
        <div className="tc-chat-admin-actions">
          <input type="number" min="0.01" step="0.01" placeholder="Counter (USD)" value={counter}
            onChange={(e) => setCounter(e.target.value)} />
          <button type="button" disabled={busy || !(Number(counter) > 0)} onClick={sendCounter}>Send counter</button>
          <button type="button" disabled={busy} onClick={resetLimit} title="Let this email submit again today">Reset daily limit</button>
        </div>
      )}

      <details open>
        <summary>Lines ({chat.lineCount ?? detail?.chatDetails?.lines?.length ?? '…'})</summary>
        {!detail ? <p className="tc-chat-admin-muted">Loading lines…</p> : (
          <div className="tc-chat-admin-table-wrap">
            <table className="tc-chat-admin-table">
              <thead>
                <tr><th>#</th><th>Item</th><th>Qty</th><th>Cond / grade</th><th>Unit (USD)</th><th>Asks (unit)</th><th>Line (USD)</th><th>Status</th><th>Photo</th></tr>
              </thead>
              <tbody>
                {(detail.chatDetails?.lines ?? []).map((l) => (
                  <tr key={l.lineNo}>
                    <td>{l.lineNo}</td>
                    <td>
                      {l.kind === 'BULK' ? `Bulk: ${l.name}` : (l.matchedName ?? l.name)}
                      <div className="tc-chat-admin-muted">{[l.matchedSet ?? l.setName, l.cardNumber, l.variant, l.cardId].filter(Boolean).join(' · ')}</div>
                    </td>
                    <td>{l.quantity}</td>
                    <td>{l.grading ?? l.condition ?? '—'}</td>
                    <td>{l.kind === 'BULK' ? '—' : money(l.unitMarketUsd)}{l.grading && l.unitMarketUsd != null ? (l.priceBasis === 'GRADED' ? ` (${l.grading})` : ' (ungraded)') : ''}</td>
                    <td>{l.requestedUnitUsd != null ? money(l.requestedUnitUsd) : '—'}</td>
                    <td>{money(l.lineMarketUsd)}</td>
                    <td title={l.statusReason ?? undefined}><span className={`tc-chat-admin-status s-${l.status.toLowerCase()}`}>{l.status}</span></td>
                    <td>{l.photoUrl ? <a href={l.photoUrl} target="_blank" rel="noreferrer">View</a> : '—'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </details>

      <details>
        <summary>Chat transcript ({detail?.chatDetails?.transcript?.length ?? '…'})</summary>
        <div className="tc-chat-admin-transcript">
          {(detail?.chatDetails?.transcript ?? []).map((m, i) => (
            <div key={i} className={`tc-chat-admin-msg ${m.role === 'CUSTOMER' ? 'customer' : 'assistant'}`}>
              <span>{m.role === 'CUSTOMER' ? 'Customer' : 'Assistant'} · {new Date(m.at).toLocaleString()}</span>
              <p>{m.content}</p>
            </div>
          ))}
        </div>
      </details>
    </section>
  )
}
