import { useState } from 'react'
import type { LedgerImportReport } from '../types'
import { importLedgerCsv } from '../api/ledgerApi'
import { count, formatCad } from './ledgerFormat'

/** The columns the importer reads; the first five are required. Format and examples in docs/ledger.md. */
export const LEDGER_CSV_HEADER = 'lot_ref,title,kind,quantity,total_cost_cad,acquired_on,acquired_channel,listed_on,'
  + 'listed_price_cad,card_ref,notes,sold_on,sold_quantity,sale_unit_price_cad,sale_channel,payment_method,sale_notes'

const TEMPLATE = `${LEDGER_CSV_HEADER}
PRE-1,Prismatic Evolutions ETB (2 cases),case,20,1832,2026-08-14,card show,2026-08-16,230,,,2026-08-16,17,230,facebook,e-transfer,
PRE-1,,,,,,,,,,,2026-08-27,1,0,show,,given away
`

function downloadTemplate() {
  const url = URL.createObjectURL(new Blob([TEMPLATE], { type: 'text/csv' }))
  const link = document.createElement('a')
  link.href = url
  link.download = 'ledger-template.csv'
  link.click()
  URL.revokeObjectURL(url)
}

/**
 * Imports a ledger CSV in two steps: the file is checked first (nothing saved) and the report shows what it holds
 * or every error with its line; then Import saves it all at once.
 */
export function LedgerImport({ onImported }: { onImported: () => void }) {
  const [file, setFile] = useState<File | null>(null)
  const [report, setReport] = useState<LedgerImportReport | null>(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [done, setDone] = useState<string | null>(null)

  const run = async (chosen: File, dryRun: boolean) => {
    setBusy(true)
    setError(null)
    setDone(null)
    try {
      const result = await importLedgerCsv(chosen, dryRun)
      setReport(result)
      if (result.saved) {
        setDone(`Imported ${count(result.lots, 'purchase')} and ${count(result.sales, 'sale')}.`)
        setFile(null)
        onImported()
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not read the file.')
    } finally {
      setBusy(false)
    }
  }

  return (
    <section className="tc-ledger-import" aria-labelledby="tc-ledger-import-title">
      <h2 id="tc-ledger-import-title">Import from CSV</h2>
      <p className="tc-ledger-muted">
        One row per purchase; rows with the same <code>lot_ref</code> are one purchase, and each row can add one sale.
        The file is checked before anything is saved, and importing it again adds nothing new.
      </p>
      <div className="tc-ledger-tools">
        <label className={`tc-button tc-button-ghost ${busy ? 'is-disabled' : ''}`}>
          {file ? file.name : 'Choose CSV file'}
          <input id="tc-ledger-import-file" type="file" accept=".csv,text/csv" hidden disabled={busy} onChange={(e) => {
            const chosen = e.target.files?.[0] ?? null
            e.target.value = ''
            setFile(chosen)
            setReport(null)
            if (chosen) run(chosen, true)
          }} />
        </label>
        <button type="button" className="tc-ledger-link-button tc-ledger-plain" onClick={downloadTemplate}>Download template</button>
      </div>

      {busy && <p role="status">Checking…</p>}
      {error && <p className="tc-ledger-error" role="alert">{error}</p>}
      {done && <p className="tc-ledger-note" role="status">{done}</p>}

      {report && !report.saved && (
        <div className="tc-ledger-import-report">
          <p>
            {count(report.rows, 'row')}: {count(report.lots, 'new purchase')} and {count(report.sales, 'sale')}
            {' '}({formatCad(report.revenueCad)} revenue, {formatCad(report.costCad)} cost of goods)
            {report.lotsAlreadyImported > 0 && `; ${count(report.lotsAlreadyImported, 'purchase')} imported before will be skipped`}.
          </p>
          {report.errors.length > 0 ? (
            <>
              <p className="tc-ledger-error">Fix these rows and choose the file again. Nothing has been saved.</p>
              <ul className="tc-ledger-import-errors">
                {report.errors.map((e, i) => <li key={i}><span className="num">Line {e.line}</span> {e.message}</li>)}
              </ul>
            </>
          ) : (
            file && report.lots > 0 && (
              <button type="button" className="tc-button tc-button-solid" disabled={busy} onClick={() => run(file, false)}>
                Import {count(report.lots, 'purchase')} and {count(report.sales, 'sale')}
              </button>
            )
          )}
        </div>
      )}
    </section>
  )
}
