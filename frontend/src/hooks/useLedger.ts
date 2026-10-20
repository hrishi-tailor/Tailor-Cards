import { useCallback, useEffect, useState } from 'react'
import type { LedgerLot, LedgerSummary } from '../types'
import { fetchLots, fetchSummary } from '../api/ledgerApi'

/** Every ledger lot plus the summary for a date range (empty strings mean unbounded); reload() fetches again. */
export function useLedger(from: string, to: string) {
  const [lots, setLots] = useState<LedgerLot[]>([])
  const [summary, setSummary] = useState<LedgerSummary | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [version, setVersion] = useState(0)

  useEffect(() => {
    let cancelled = false
    const load = async () => {
      try {
        const [l, s] = await Promise.all([fetchLots(), fetchSummary(from || undefined, to || undefined)])
        if (!cancelled) {
          setLots(l)
          setSummary(s)
          setError(null)
        }
      } catch (err) {
        if (!cancelled) setError(err instanceof Error ? err.message : 'Could not load the ledger.')
      } finally {
        if (!cancelled) setLoading(false)
      }
    }
    load()
    return () => { cancelled = true }
  }, [from, to, version])

  const reload = useCallback(() => setVersion((v) => v + 1), [])

  return { lots, summary, loading, error, reload }
}
