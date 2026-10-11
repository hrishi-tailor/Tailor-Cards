/**
 * Buylist amounts are stored and priced in USD (TCGplayer market). Customers see CAD, converted at
 * the Bank of Canada rate the server sends with the deal; without a rate amounts stay in USD.
 */
export interface Money {
  code: 'CAD' | 'USD'
  /** Formats a USD amount in the display currency; null shows `empty`. */
  fmt: (usd: number | null | undefined, empty?: string) => string
  toDisplay: (usd: number) => number
  fromDisplay: (amount: number) => number
}

const cadFormat = new Intl.NumberFormat('en-CA', { style: 'currency', currency: 'CAD' })
const usdFormat = new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' })

export function makeMoney(cadPerUsd: number | null | undefined): Money {
  const rate = cadPerUsd != null && cadPerUsd > 0 ? cadPerUsd : null
  return {
    code: rate ? 'CAD' : 'USD',
    fmt: (usd, empty = 'No price') => (usd == null ? empty : rate ? cadFormat.format(usd * rate) : usdFormat.format(usd)),
    toDisplay: (usd) => (rate ? Math.round(usd * rate * 100) / 100 : usd),
    fromDisplay: (amount) => (rate ? Math.round((amount / rate) * 100) / 100 : amount),
  }
}
