import { useCallback, useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import type { LedgerItemKind, LedgerLot, LedgerPaymentMethod } from '../types'
import { fetchLots } from '../api/ledgerApi'
import { cachedStock, discard, enqueue, newRef, queued, saveStock, sync, takeFromStock } from './showQueue'
import type { QueuedEntry, SyncResult } from './showQueue'
import { KIND_LABELS, PAYMENT_LABELS, count, formatCad, storeToday } from './ledgerFormat'
import './ShowMode.css'

const KINDS: LedgerItemKind[] = ['SINGLE', 'GRADED', 'SEALED', 'SEALED_CASE', 'LOT']
const PAYMENTS: LedgerPaymentMethod[] = ['CASH', 'E_TRANSFER', 'TRADE']

/** Something that can be sold from the phone: stock from the server, or a purchase still waiting in the queue. */
interface Sellable {
  key: string
  title: string
  unitsLeft: number
  unitCost: number
  listedPrice: number | null
  lotId?: number
  lotRef?: string
}

/** Sends what's queued, then fetches fresh stock if there's a connection (null when there isn't). */
async function runSync(): Promise<{ result: SyncResult; stock: LedgerLot[] | null }> {
  const result = await sync()
  if (result.offline || result.signedOut) return { result, stock: null }
  try {
    saveStock(await fetchLots())
    return { result, stock: cachedStock() }
  } catch {
    return { result, stock: null }
  }
}

function Chips<T extends string>({ label, options, labels, value, onChange }: {
  label: string
  options: T[]
  labels: Record<T, string>
  value: T
  onChange: (value: T) => void
}) {
  return (
    <fieldset className="tc-show-chips">
      <legend>{label}</legend>
      {options.map((o) => (
        <button key={o} type="button" aria-pressed={value === o} onClick={() => onChange(o)}>{labels[o]}</button>
      ))}
    </fieldset>
  )
}

/**
 * /admin/show: log card-show deals on a phone in a few taps. Every deal is saved on the phone first and sent to
 * the ledger when there's a connection, so a hall with no signal loses nothing. Deals are dated today and
 * recorded as bought or sold at a card show.
 */
export function ShowMode() {
  const [mode, setMode] = useState<'BUY' | 'SELL'>('BUY')
  const [stock, setStock] = useState(cachedStock)
  const [entries, setEntries] = useState<QueuedEntry[]>(queued)
  const [status, setStatus] = useState<SyncResult | null>(null)
  const [syncing, setSyncing] = useState(false)
  const [message, setMessage] = useState<string | null>(null)

  const [buy, setBuy] = useState({ title: '', kind: 'SINGLE' as LedgerItemKind, units: '1', paid: '', payment: 'CASH' as LedgerPaymentMethod })
  const [search, setSearch] = useState('')
  const [chosen, setChosen] = useState<Sellable | null>(null)
  const [sell, setSell] = useState({ units: '1', price: '', payment: 'CASH' as LedgerPaymentMethod })

  const applySync = useCallback(({ result, stock: fresh }: Awaited<ReturnType<typeof runSync>>) => {
    setStatus(result)
    setEntries(queued())
    if (fresh) setStock(fresh)
    setSyncing(false)
  }, [])

  const syncNow = useCallback(async () => applySync(await runSync()), [applySync])

  // Send anything left from an earlier visit, and again whenever the connection comes back
  useEffect(() => {
    let cancelled = false
    const run = async () => {
      const outcome = await runSync()
      if (!cancelled) applySync(outcome)
    }
    run()
    window.addEventListener('online', run)
    return () => {
      cancelled = true
      window.removeEventListener('online', run)
    }
  }, [applySync])

  const sellable = useMemo<Sellable[]>(() => {
    const fromServer = stock.map((l) => ({
      key: `lot-${l.id}`, title: l.title, unitsLeft: l.unitsLeft, unitCost: l.unitCostCad, listedPrice: l.listedPriceCad, lotId: l.id,
    }))
    const soldFromQueued = (ref: string) => entries
      .filter((e) => e.type === 'sale' && e.lotRef === ref)
      .reduce((n, e) => n + (e.type === 'sale' ? e.sale.quantity : 0), 0)
    const fromQueue = entries.flatMap((e) => (e.type === 'lot' && e.lot.externalRef ? [{
      key: e.lot.externalRef, title: e.lot.title, unitsLeft: e.lot.quantity - soldFromQueued(e.lot.externalRef),
      unitCost: e.lot.totalCostCad / e.lot.quantity, listedPrice: e.lot.listedPriceCad ?? null, lotRef: e.lot.externalRef,
    }] : [])).filter((s) => s.unitsLeft > 0)
    const q = search.trim().toLowerCase()
    return [...fromQueue, ...fromServer].filter((s) => !q || s.title.toLowerCase().includes(q))
  }, [stock, entries, search])

  const logged = (text: string) => {
    setEntries(queued())
    setMessage(text)
    syncNow()
  }

  const logPurchase = () => {
    const units = Number(buy.units)
    const paid = Number(buy.paid)
    enqueue({
      id: crypto.randomUUID(), type: 'lot', createdAt: new Date().toISOString(),
      lot: {
        title: buy.title.trim(), kind: buy.kind, quantity: units, totalCostCad: paid, acquiredOn: storeToday(),
        acquiredChannel: 'CARD_SHOW', notes: `Paid by ${PAYMENT_LABELS[buy.payment]}`, externalRef: newRef(),
      },
    })
    setBuy((b) => ({ ...b, title: '', units: '1', paid: '' }))
    logged(`Bought ${buy.title.trim()} for ${formatCad(paid)}.`)
  }

  const logSale = () => {
    if (!chosen) return
    const units = Number(sell.units)
    const price = Number(sell.price)
    enqueue({
      id: crypto.randomUUID(), type: 'sale', createdAt: new Date().toISOString(), title: chosen.title,
      lotId: chosen.lotId, lotRef: chosen.lotRef,
      sale: { soldOn: storeToday(), quantity: units, unitPriceCad: price, channel: 'CARD_SHOW', paymentMethod: sell.payment, clientRef: newRef() },
    })
    takeFromStock(chosen.lotId, units)
    setStock(cachedStock())
    setChosen(null)
    setSearch('')
    setSell((s) => ({ ...s, units: '1', price: '' }))
    logged(`Sold ${count(units, 'unit')} of ${chosen.title} for ${formatCad(units * price)}.`)
  }

  const waiting = entries.filter((e) => !e.error).length
  const failed = entries.filter((e) => e.error)

  return (
    <div className="tc-show">
      <header className="tc-show-head">
        <div>
          <h1>Show mode</h1>
          <p className={`tc-show-status ${status?.offline ? 'is-offline' : ''}`} role="status">
            {syncing ? 'Syncing…'
              : status?.signedOut ? 'Signed out. Sign in again to send your deals.'
                : status?.offline ? `Offline. ${count(waiting, 'deal')} saved on this phone.`
                  : waiting > 0 ? `${count(waiting, 'deal')} waiting to send.` : 'All deals sent.'}
          </p>
        </div>
        <button type="button" className="tc-button tc-button-ghost" disabled={syncing}
          onClick={() => { setSyncing(true); syncNow() }}>Sync</button>
      </header>

      <div className="tc-show-tabs" role="tablist" aria-label="Deal type">
        <button type="button" role="tab" aria-selected={mode === 'BUY'} onClick={() => setMode('BUY')}>Bought</button>
        <button type="button" role="tab" aria-selected={mode === 'SELL'} onClick={() => setMode('SELL')}>Sold</button>
      </div>

      {message && <p className="tc-show-message" role="status">{message}</p>}

      {mode === 'BUY' ? (
        <form className="tc-show-form" onSubmit={(e) => { e.preventDefault(); logPurchase() }}>
          <label>What
            <input id="tc-show-buy-title" required maxLength={200} value={buy.title} autoComplete="off"
              onChange={(e) => setBuy((b) => ({ ...b, title: e.target.value }))} placeholder="e.g. Umbreon VMAX 215/203" />
          </label>
          <Chips label="Kind" options={KINDS} labels={KIND_LABELS} value={buy.kind} onChange={(kind) => setBuy((b) => ({ ...b, kind }))} />
          <div className="tc-show-row">
            <label>Units
              <input id="tc-show-buy-units" type="number" inputMode="numeric" min={1} required value={buy.units}
                onChange={(e) => setBuy((b) => ({ ...b, units: e.target.value }))} />
            </label>
            <label>Paid in total (CAD)
              <input id="tc-show-buy-paid" type="number" inputMode="decimal" min={0} step="0.01" required value={buy.paid}
                onChange={(e) => setBuy((b) => ({ ...b, paid: e.target.value }))} />
            </label>
          </div>
          <Chips label="Paid by" options={PAYMENTS} labels={PAYMENT_LABELS} value={buy.payment} onChange={(payment) => setBuy((b) => ({ ...b, payment }))} />
          <button type="submit" className="tc-button tc-button-solid tc-show-submit">Log purchase</button>
        </form>
      ) : (
        <form className="tc-show-form" onSubmit={(e) => { e.preventDefault(); logSale() }}>
          {chosen ? (
            <div className="tc-show-chosen">
              <strong>{chosen.title}</strong>
              <span>{count(chosen.unitsLeft, 'unit')} left · cost {formatCad(chosen.unitCost)} each</span>
              <button type="button" className="tc-show-link tc-show-change" onClick={() => setChosen(null)}>Change</button>
            </div>
          ) : (
            <>
              <label>What sold
                <input id="tc-show-sell-search" type="search" value={search} autoComplete="off" placeholder="Search your stock"
                  onChange={(e) => setSearch(e.target.value)} />
              </label>
              <ul className="tc-show-stock">
                {sellable.slice(0, 8).map((s) => (
                  <li key={s.key}>
                    <button type="button" onClick={() => {
                      setChosen(s)
                      setSell((v) => ({ ...v, price: s.listedPrice === null ? '' : String(s.listedPrice) }))
                    }}>
                      <span>{s.title}</span>
                      <span>{s.unitsLeft} left</span>
                    </button>
                  </li>
                ))}
                {sellable.length === 0 && <li className="tc-show-empty">Nothing in stock matches. Log it as bought first.</li>}
              </ul>
            </>
          )}
          {chosen && (
            <>
              <div className="tc-show-row">
                <label>Units
                  <input id="tc-show-sell-units" type="number" inputMode="numeric" min={1} max={chosen.unitsLeft} required value={sell.units}
                    onChange={(e) => setSell((s) => ({ ...s, units: e.target.value }))} />
                </label>
                <label>Price each (CAD)
                  <input id="tc-show-sell-price" type="number" inputMode="decimal" min={0} step="0.01" required value={sell.price}
                    onChange={(e) => setSell((s) => ({ ...s, price: e.target.value }))} />
                </label>
              </div>
              <Chips label="Paid by" options={PAYMENTS} labels={PAYMENT_LABELS} value={sell.payment} onChange={(payment) => setSell((s) => ({ ...s, payment }))} />
              <button type="submit" className="tc-button tc-button-solid tc-show-submit">Log sale</button>
            </>
          )}
        </form>
      )}

      {failed.length > 0 && (
        <section className="tc-show-failed" aria-label="Deals the server refused">
          <h2>Not saved</h2>
          <ul>
            {failed.map((e) => (
              <li key={e.id}>
                <span>{e.type === 'lot' ? `Bought ${e.lot.title}` : `Sold ${e.title}`}: {e.error}</span>
                <button type="button" className="tc-show-link" onClick={() => { discard(e.id); setEntries(queued()) }}>Discard</button>
              </li>
            ))}
          </ul>
        </section>
      )}

      <p className="tc-show-foot"><Link to="/admin/ledger">Open the full ledger</Link></p>
    </div>
  )
}
