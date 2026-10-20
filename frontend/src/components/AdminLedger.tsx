import { useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import type { LedgerSummaryLine } from '../types'
import { createLot, deleteLot, deleteSale, recordSale } from '../api/ledgerApi'
import { useLedger } from '../hooks/useLedger'
import { LotForm, SaleForm } from './LedgerForms'
import { LedgerImport } from './LedgerImport'
import { CHANNEL_LABELS, KIND_LABELS, count, formatCad, summaryLabel } from './ledgerFormat'
import './AdminLedger.css'

function pct(value: number | null): string {
  return value === null ? '—' : `${value.toFixed(1)}%`
}

function days(value: number | null): string {
  return value === null ? '—' : `${value} d`
}

function Breakdown({ title, lines }: { title: string; lines: LedgerSummaryLine[] }) {
  return (
    <section className="tc-ledger-breakdown">
      <h3>{title}</h3>
      {lines.length === 0 ? <p className="tc-ledger-muted">No sales yet.</p> : (
        <div className="tc-ledger-table-wrap">
          <table className="tc-ledger-table">
            <thead>
              <tr><th></th><th className="num">Units</th><th className="num">Revenue</th><th className="num">Profit</th>
                <th className="num">Margin</th><th className="num">Median days</th></tr>
            </thead>
            <tbody>
              {lines.map((l) => (
                <tr key={l.key}>
                  <td>{summaryLabel(l.key)}</td>
                  <td className="num">{l.units}</td>
                  <td className="num">{formatCad(l.revenueCad)}</td>
                  <td className="num">{formatCad(l.grossProfitCad)}</td>
                  <td className="num">{pct(l.marginPct)}</td>
                  <td className="num">{days(l.medianDaysListed)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  )
}

/**
 * /admin/ledger: what the store bought and sold. The summary counts realised sales only; stock still on hand is
 * shown at cost. Each lot lists its sales, and new purchases and sales can be added here or from show mode.
 */
export function AdminLedger() {
  const [range, setRange] = useState({ from: '', to: '' })
  const { lots, summary, loading, error: loadError, reload } = useLedger(range.from, range.to)
  const [actionError, setActionError] = useState<string | null>(null)
  const [adding, setAdding] = useState(false)
  const [sellingLot, setSellingLot] = useState<number | null>(null)
  const [openLot, setOpenLot] = useState<number | null>(null)
  const [filter, setFilter] = useState('')
  const [onlyInStock, setOnlyInStock] = useState(false)
  const error = actionError ?? loadError

  const visible = useMemo(() => lots.filter((l) =>
    (!filter.trim() || l.title.toLowerCase().includes(filter.trim().toLowerCase())) && (!onlyInStock || l.unitsLeft > 0)),
  [lots, filter, onlyInStock])

  const act = async (action: () => Promise<unknown>) => {
    setActionError(null)
    try {
      await action()
      reload()
    } catch (err) {
      setActionError(err instanceof Error ? err.message : 'That change could not be saved.')
    }
  }

  const total = summary?.total

  return (
    <div className="tc-ledger tc-wrap">
      <nav className="tc-crumbs" aria-label="Breadcrumb">
        <Link to="/admin/buylist">Admin</Link><span aria-hidden="true">/</span><span>Ledger</span>
      </nav>
      <header className="tc-ledger-head">
        <div>
          <h1>Ledger</h1>
          <p>What you bought and what you sold from it. Profit counts sales only; unsold stock is shown at what it cost.</p>
        </div>
        <div className="tc-ledger-head-actions">
          <Link to="/admin/show" className="tc-button tc-button-ghost">Show mode</Link>
          <button type="button" className="tc-button tc-button-solid" onClick={() => setAdding((a) => !a)}>
            {adding ? 'Close' : 'Add purchase'}
          </button>
        </div>
      </header>

      {error && <p className="tc-ledger-error" role="alert">{error}</p>}

      {adding && (
        <LotForm onCancel={() => setAdding(false)} onSubmit={async (lot) => {
          await createLot(lot)
          setAdding(false)
          reload()
        }} />
      )}

      <section className="tc-ledger-summary" aria-labelledby="tc-ledger-summary-title">
        <div className="tc-ledger-section-head">
          <h2 id="tc-ledger-summary-title">Results</h2>
          <div className="tc-ledger-range">
            <label>From <input id="tc-ledger-from" type="date" value={range.from}
              onChange={(e) => setRange((r) => ({ ...r, from: e.target.value }))} /></label>
            <label>To <input id="tc-ledger-to" type="date" value={range.to}
              onChange={(e) => setRange((r) => ({ ...r, to: e.target.value }))} /></label>
            {(range.from || range.to) && (
              <button type="button" className="tc-button tc-button-ghost" onClick={() => setRange({ from: '', to: '' })}>All time</button>
            )}
          </div>
        </div>
        {loading && <p>Loading…</p>}
        {total && summary && (
          <>
            <dl className="tc-ledger-kpis">
              <div><dt>Revenue</dt><dd>{formatCad(total.revenueCad)}</dd></div>
              <div><dt>Gross profit</dt><dd>{formatCad(total.grossProfitCad)}</dd></div>
              <div><dt>Margin</dt><dd>{pct(total.marginPct)}</dd></div>
              <div><dt>Units sold</dt><dd>{total.units}</dd></div>
              <div><dt>Median days listed</dt><dd>{days(total.medianDaysListed)}</dd></div>
              <div><dt>On hand at cost</dt><dd>{formatCad(summary.onHand.costCad)}</dd>
                <span>{count(summary.onHand.units, 'unit')} in {count(summary.onHand.lots, 'lot')}</span></div>
            </dl>
            <div className="tc-ledger-breakdowns">
              <Breakdown title="By kind" lines={summary.byKind} />
              <Breakdown title="By channel" lines={summary.byChannel} />
              <Breakdown title="By month" lines={summary.byMonth} />
            </div>
          </>
        )}
      </section>

      <LedgerImport onImported={reload} />

      <section className="tc-ledger-lots" aria-labelledby="tc-ledger-lots-title">
        <div className="tc-ledger-section-head">
          <h2 id="tc-ledger-lots-title">Purchases</h2>
          <div className="tc-ledger-tools">
            <label htmlFor="tc-ledger-filter" className="tc-sr-only">Filter purchases</label>
            <input id="tc-ledger-filter" type="search" placeholder="Filter by title" value={filter}
              onChange={(e) => setFilter(e.target.value)} />
            <label className="tc-check">
              <input id="tc-ledger-in-stock" type="checkbox" checked={onlyInStock} onChange={(e) => setOnlyInStock(e.target.checked)} />
              Only with units left
            </label>
          </div>
        </div>
        {!loading && visible.length === 0 && <p className="tc-ledger-muted">No purchases yet. Add one, import a CSV, or log deals in show mode.</p>}
        <ul className="tc-ledger-lot-list">
          {visible.map((lot) => (
            <li key={lot.id} className="tc-ledger-lot">
              <button type="button" className="tc-ledger-lot-row" aria-expanded={openLot === lot.id}
                onClick={() => setOpenLot((o) => (o === lot.id ? null : lot.id))}>
                <span className="tc-ledger-lot-title">
                  <strong>{lot.title}</strong>
                  <span>{KIND_LABELS[lot.kind]} · bought {lot.acquiredOn ?? 'date unknown'}
                    {lot.acquiredChannel ? ` at ${CHANNEL_LABELS[lot.acquiredChannel]}` : ''}</span>
                </span>
                <span className="num">{lot.unitsSold}/{lot.quantity} sold</span>
                <span className="num">cost {formatCad(lot.totalCostCad)}</span>
                <span className={`num ${lot.grossProfitCad < 0 ? 'is-loss' : ''}`}>profit {formatCad(lot.grossProfitCad)}</span>
              </button>
              {openLot === lot.id && (
                <div className="tc-ledger-lot-detail">
                  {lot.sales.length > 0 && (
                    <div className="tc-ledger-table-wrap">
                      <table className="tc-ledger-table">
                        <thead>
                          <tr><th>Sold on</th><th className="num">Units</th><th className="num">Price</th>
                            <th className="num">Profit</th><th>Where</th><th></th></tr>
                        </thead>
                        <tbody>
                          {lot.sales.map((s) => (
                            <tr key={s.id}>
                              <td>{s.soldOn}</td>
                              <td className="num">{s.quantity}</td>
                              <td className="num">{s.unitPriceCad === 0 ? 'Given away' : formatCad(s.unitPriceCad)}</td>
                              <td className="num">{formatCad(s.grossProfitCad)}</td>
                              <td>{CHANNEL_LABELS[s.channel]}</td>
                              <td><button type="button" className="tc-ledger-link-button"
                                onClick={() => window.confirm(`Delete this sale of ${s.quantity} from "${lot.title}"?`)
                                  && act(() => deleteSale(s.id))}>Delete</button></td>
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    </div>
                  )}
                  {sellingLot === lot.id ? (
                    <SaleForm unitsLeft={lot.unitsLeft} onCancel={() => setSellingLot(null)} onSubmit={async (sale) => {
                      await recordSale(lot.id, sale)
                      setSellingLot(null)
                      reload()
                    }} />
                  ) : (
                    <div className="tc-ledger-lot-actions">
                      {lot.unitsLeft > 0 && (
                        <button type="button" className="tc-button tc-button-solid" onClick={() => setSellingLot(lot.id)}>Record sale</button>
                      )}
                      {lot.sales.length === 0 && (
                        <button type="button" className="tc-button tc-button-ghost" onClick={() => window.confirm(`Delete the purchase "${lot.title}"?`) && act(() => deleteLot(lot.id))}>
                          Delete purchase
                        </button>
                      )}
                      <span className="tc-ledger-muted">
                        {formatCad(lot.unitCostCad)} per unit{lot.notes ? ` · ${lot.notes}` : ''}
                      </span>
                    </div>
                  )}
                </div>
              )}
            </li>
          ))}
        </ul>
      </section>
    </div>
  )
}
