import type { LedgerChannel, LedgerItemKind, LedgerPaymentMethod } from '../types'

export const KIND_LABELS: Record<LedgerItemKind, string> = {
  SINGLE: 'Single',
  GRADED: 'Graded',
  SEALED: 'Sealed',
  SEALED_CASE: 'Sealed case',
  LOT: 'Lot / binder',
}

export const CHANNEL_LABELS: Record<LedgerChannel, string> = {
  CARD_SHOW: 'Card show',
  FACEBOOK_MARKETPLACE: 'Facebook Marketplace',
  INSTAGRAM: 'Instagram',
  EBAY: 'eBay',
  WEBSITE: 'Tailor Cards website',
  BUYLIST: 'Buylist',
  OTHER: 'Other',
}

export const PAYMENT_LABELS: Record<LedgerPaymentMethod, string> = {
  CASH: 'Cash',
  E_TRANSFER: 'e-Transfer',
  CARD: 'Card',
  PAYPAL: 'PayPal',
  TRADE: 'Trade',
  OTHER: 'Other',
}

const cad = new Intl.NumberFormat('en-CA', { style: 'currency', currency: 'CAD' })

export function formatCad(amount: number): string {
  return cad.format(amount)
}

/** Today's date in the store's time zone (Toronto), as YYYY-MM-DD. */
export function storeToday(): string {
  return new Intl.DateTimeFormat('en-CA', { timeZone: 'America/Toronto' }).format(new Date())
}

/** Label for a summary breakdown key (an item kind, a channel or a month). */
export function summaryLabel(key: string): string {
  return KIND_LABELS[key as LedgerItemKind] ?? CHANNEL_LABELS[key as LedgerChannel] ?? key
}
