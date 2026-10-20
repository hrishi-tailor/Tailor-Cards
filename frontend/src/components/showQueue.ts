import type { LedgerLot, LedgerLotRequest, LedgerSaleRequest } from '../types'
import { createLot, LedgerApiError, recordSale } from '../api/ledgerApi'

/**
 * Show mode's offline queue. Every deal is saved on the phone first and sent when there is a connection; each
 * entry carries an id made on the phone (externalRef / clientRef), so sending it twice records it once.
 * A sale can point at a purchase that is still in the queue (lotRef) and is sent after it.
 */
export type QueuedEntry =
  | { id: string; type: 'lot'; createdAt: string; lot: LedgerLotRequest; error?: string }
  | { id: string; type: 'sale'; createdAt: string; title: string; lotId?: number; lotRef?: string; sale: LedgerSaleRequest; error?: string }

interface StoredQueue {
  entries: QueuedEntry[]
  /** Server ids of purchases sent from this phone, by their externalRef. */
  lotIds: Record<string, number>
  /** Purchases with units left, kept so sales can be logged offline. */
  stock: LedgerLot[]
}

const KEY = 'tc-show-mode-v1'

function read(): StoredQueue {
  try {
    const raw = localStorage.getItem(KEY)
    if (raw) return { entries: [], lotIds: {}, stock: [], ...JSON.parse(raw) }
  } catch {
    // storage unavailable or corrupt: start empty
  }
  return { entries: [], lotIds: {}, stock: [] }
}

function write(queue: StoredQueue): void {
  try {
    localStorage.setItem(KEY, JSON.stringify(queue))
  } catch {
    // storage unavailable: entries stay in memory for this visit only
  }
}

let memory = read()

export function newRef(): string {
  return `show:${crypto.randomUUID()}`
}

export function queued(): QueuedEntry[] {
  return memory.entries
}

export function cachedStock(): LedgerLot[] {
  return memory.stock
}

export function saveStock(lots: LedgerLot[]): void {
  memory = { ...memory, stock: lots.filter((l) => l.unitsLeft > 0) }
  write(memory)
}

/** Takes sold units off the cached stock so the phone doesn't offer what's already gone. */
export function takeFromStock(lotId: number | undefined, units: number): void {
  if (lotId === undefined) return
  memory = {
    ...memory,
    stock: memory.stock
      .map((l) => (l.id === lotId ? { ...l, unitsLeft: l.unitsLeft - units, unitsSold: l.unitsSold + units } : l))
      .filter((l) => l.unitsLeft > 0),
  }
  write(memory)
}

export function enqueue(entry: QueuedEntry): void {
  memory = { ...memory, entries: [...memory.entries, entry] }
  write(memory)
}

export function discard(id: string): void {
  memory = { ...memory, entries: memory.entries.filter((e) => e.id !== id) }
  write(memory)
}

export interface SyncResult {
  sent: number
  /** No connection: everything not sent is still queued. */
  offline: boolean
  /** The admin session expired: sign in again, then sync. */
  signedOut: boolean
}

/**
 * Sends queued entries in order. Stops at a connection failure or an expired session (everything not sent stays
 * queued); an entry the server refuses keeps its error message and is skipped until it is discarded.
 */
export async function sync(): Promise<SyncResult> {
  let sent = 0
  for (const entry of [...memory.entries]) {
    if (entry.error) continue
    try {
      if (entry.type === 'lot') {
        const lot = await createLot(entry.lot, 'SHOW_MODE')
        memory = { ...memory, lotIds: { ...memory.lotIds, [entry.lot.externalRef as string]: lot.id } }
      } else {
        const lotId = entry.lotId ?? (entry.lotRef ? memory.lotIds[entry.lotRef] : undefined)
        if (lotId === undefined) continue // its purchase hasn't been sent yet
        await recordSale(lotId, entry.sale, 'SHOW_MODE')
      }
      memory = { ...memory, entries: memory.entries.filter((e) => e.id !== entry.id) }
      sent++
    } catch (err) {
      if (err instanceof LedgerApiError && (err.status === 0 || err.status === 401)) {
        write(memory)
        return { sent, offline: err.status === 0, signedOut: err.status === 401 }
      }
      const message = err instanceof Error ? err.message : 'The server refused this entry.'
      memory = { ...memory, entries: memory.entries.map((e) => (e.id === entry.id ? { ...e, error: message } : e)) }
    }
    write(memory)
  }
  return { sent, offline: false, signedOut: false }
}
