import { useState } from 'react'
import type { LedgerChannel, LedgerItemKind, LedgerLotRequest, LedgerPaymentMethod, LedgerSaleRequest } from '../types'
import { CHANNEL_LABELS, KIND_LABELS, PAYMENT_LABELS, storeToday } from './ledgerFormat'

const kinds = Object.keys(KIND_LABELS) as LedgerItemKind[]
const channels = Object.keys(CHANNEL_LABELS) as LedgerChannel[]
const payments = Object.keys(PAYMENT_LABELS) as LedgerPaymentMethod[]

type Submit<T> = (value: T) => Promise<void>

/** A purchase: title, kind, units, what was paid for all of them, and optional dates and listing price. */
export function LotForm({ onSubmit, onCancel }: { onSubmit: Submit<LedgerLotRequest>; onCancel: () => void }) {
  const [form, setForm] = useState({
    title: '', kind: 'SINGLE' as LedgerItemKind, quantity: '1', totalCost: '', acquiredOn: storeToday(),
    acquiredChannel: 'CARD_SHOW' as LedgerChannel, listedOn: '', listedPrice: '', cardRef: '', notes: '',
  })
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const set = (field: keyof typeof form) => (e: { target: { value: string } }) => setForm((f) => ({ ...f, [field]: e.target.value }))

  return (
    <form className="tc-ledger-form" onSubmit={async (e) => {
      e.preventDefault()
      setBusy(true)
      setError(null)
      try {
        await onSubmit({
          title: form.title.trim(), kind: form.kind, quantity: Number(form.quantity), totalCostCad: Number(form.totalCost),
          acquiredOn: form.acquiredOn || null, acquiredChannel: form.acquiredChannel, listedOn: form.listedOn || null,
          listedPriceCad: form.listedPrice === '' ? null : Number(form.listedPrice),
          cardRef: form.cardRef.trim() || null, notes: form.notes.trim() || null,
        })
      } catch (err) {
        setError(err instanceof Error ? err.message : 'Could not save the purchase.')
      } finally {
        setBusy(false)
      }
    }}>
      <label className="tc-ledger-wide">Title
        <input id="tc-ledger-lot-title" required maxLength={200} value={form.title} onChange={set('title')}
          placeholder="e.g. Prismatic Evolutions ETB case" />
      </label>
      <label>Kind
        <select id="tc-ledger-lot-kind" value={form.kind} onChange={set('kind')}>
          {kinds.map((k) => <option key={k} value={k}>{KIND_LABELS[k]}</option>)}
        </select>
      </label>
      <label>Units
        <input id="tc-ledger-lot-quantity" type="number" min={1} required value={form.quantity} onChange={set('quantity')} />
      </label>
      <label>Total paid (CAD)
        <input id="tc-ledger-lot-cost" type="number" min={0} step="0.01" required value={form.totalCost} onChange={set('totalCost')} />
      </label>
      <label>Bought on
        <input id="tc-ledger-lot-acquired" type="date" max={storeToday()} value={form.acquiredOn} onChange={set('acquiredOn')} />
      </label>
      <label>Bought at
        <select id="tc-ledger-lot-channel" value={form.acquiredChannel} onChange={set('acquiredChannel')}>
          {channels.map((c) => <option key={c} value={c}>{CHANNEL_LABELS[c]}</option>)}
        </select>
      </label>
      <label>Listed on
        <input id="tc-ledger-lot-listed" type="date" max={storeToday()} value={form.listedOn} onChange={set('listedOn')} />
      </label>
      <label>Asking price per unit
        <input id="tc-ledger-lot-price" type="number" min={0} step="0.01" value={form.listedPrice} onChange={set('listedPrice')} />
      </label>
      <label>TCGdex card ID
        <input id="tc-ledger-lot-card" maxLength={100} value={form.cardRef} onChange={set('cardRef')} placeholder="optional, e.g. sv03.5-166" />
      </label>
      <label className="tc-ledger-wide">Notes
        <input id="tc-ledger-lot-notes" maxLength={500} value={form.notes} onChange={set('notes')} />
      </label>
      {error && <p className="tc-ledger-error tc-ledger-wide" role="alert">{error}</p>}
      <div className="tc-ledger-form-actions tc-ledger-wide">
        <button type="submit" className="tc-button tc-button-solid" disabled={busy}>{busy ? 'Saving…' : 'Save purchase'}</button>
        <button type="button" className="tc-button tc-button-ghost" onClick={onCancel} disabled={busy}>Cancel</button>
      </div>
    </form>
  )
}

/** Units sold from one lot at one price. A price of 0 records a giveaway. */
export function SaleForm({ unitsLeft, onSubmit, onCancel }: {
  unitsLeft: number
  onSubmit: Submit<LedgerSaleRequest>
  onCancel: () => void
}) {
  const [form, setForm] = useState({
    soldOn: storeToday(), quantity: '1', price: '', channel: 'CARD_SHOW' as LedgerChannel,
    payment: 'CASH' as LedgerPaymentMethod, notes: '',
  })
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const set = (field: keyof typeof form) => (e: { target: { value: string } }) => setForm((f) => ({ ...f, [field]: e.target.value }))

  return (
    <form className="tc-ledger-form tc-ledger-sale-form" onSubmit={async (e) => {
      e.preventDefault()
      setBusy(true)
      setError(null)
      try {
        await onSubmit({
          soldOn: form.soldOn, quantity: Number(form.quantity), unitPriceCad: Number(form.price),
          channel: form.channel, paymentMethod: form.payment, notes: form.notes.trim() || null,
        })
      } catch (err) {
        setError(err instanceof Error ? err.message : 'Could not save the sale.')
      } finally {
        setBusy(false)
      }
    }}>
      <label>Sold on
        <input type="date" required max={storeToday()} value={form.soldOn} onChange={set('soldOn')} />
      </label>
      <label>Units (of {unitsLeft})
        <input type="number" min={1} max={unitsLeft} required value={form.quantity} onChange={set('quantity')} />
      </label>
      <label>Price per unit (CAD)
        <input type="number" min={0} step="0.01" required value={form.price} onChange={set('price')} placeholder="0 for a giveaway" />
      </label>
      <label>Sold at
        <select value={form.channel} onChange={set('channel')}>
          {channels.map((c) => <option key={c} value={c}>{CHANNEL_LABELS[c]}</option>)}
        </select>
      </label>
      <label>Paid by
        <select value={form.payment} onChange={set('payment')}>
          {payments.map((p) => <option key={p} value={p}>{PAYMENT_LABELS[p]}</option>)}
        </select>
      </label>
      <label>Notes
        <input maxLength={500} value={form.notes} onChange={set('notes')} />
      </label>
      {error && <p className="tc-ledger-error tc-ledger-wide" role="alert">{error}</p>}
      <div className="tc-ledger-form-actions tc-ledger-wide">
        <button type="submit" className="tc-button tc-button-solid" disabled={busy}>{busy ? 'Saving…' : 'Save sale'}</button>
        <button type="button" className="tc-button tc-button-ghost" onClick={onCancel} disabled={busy}>Cancel</button>
      </div>
    </form>
  )
}
