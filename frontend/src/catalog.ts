import type { Product } from './types'

export type CategorySlug = 'singles' | 'slabs' | 'sealed'

export interface ShopCategory {
  slug: CategorySlug
  label: string
  blurb: string
}

/** Shop collections, in menu order. */
export const CATEGORIES: ShopCategory[] = [
  { slug: 'singles', label: 'Singles', blurb: 'Full arts, alt arts and rainbows' },
  { slug: 'slabs', label: 'Graded slabs', blurb: 'PSA, BGS, CGC and more' },
  { slug: 'sealed', label: 'Sealed', blurb: 'Booster boxes and trainer boxes' },
]

export function categoryBySlug(slug: string | undefined): ShopCategory | undefined {
  return CATEGORIES.find((c) => c.slug === slug)
}

export function isSealed(p: Product): boolean {
  const cat = p.category?.name?.toLowerCase() ?? ''
  return cat.includes('seal') || (p.condition?.toUpperCase().includes('SEALED') ?? false)
    || /trainer box|booster (box|bundle)|\betb\b/i.test(p.name)
}

export function isSlab(p: Product): boolean {
  const cat = p.category?.name?.toLowerCase() ?? ''
  return Boolean(p.grading?.trim()) || cat.includes('graded') || cat.includes('slab')
    || /\b(psa|bgs|cgc|sgc|tag|ace)\s*\d/i.test(p.name)
}

export function inCategory(p: Product, slug: CategorySlug): boolean {
  if (slug === 'sealed') return isSealed(p)
  if (slug === 'slabs') return isSlab(p)
  return !isSealed(p) && !isSlab(p)
}

export function matchesSearch(p: Product, query: string): boolean {
  const q = query.trim().toLowerCase()
  if (!q) return true
  return [p.name, p.set, p.cardNumber, p.grading, p.category?.name]
    .some((field) => field?.toLowerCase().includes(q))
}

/** Short customer-facing label: the grade for slabs, Sealed, or the raw condition. */
export function conditionLabel(p: Product): string {
  if (isSealed(p)) return 'Sealed'
  if (p.grading?.trim()) return p.grading.trim()
  const c = p.condition?.trim().toUpperCase() ?? ''
  if (!c || c === 'NM' || c.includes('NEAR MINT') || c === 'MINT' || c.includes('GEM')) return 'Near mint'
  if (c === 'LP' || c.includes('LIGHTLY')) return 'Lightly played'
  if (c === 'MP' || c.includes('MODERATELY')) return 'Moderately played'
  if (c === 'HP' || c.includes('HEAVILY')) return 'Heavily played'
  if (c === 'DMG' || c.includes('DAMAGED')) return 'Damaged'
  return p.condition!.trim()
}

/** Product names without the internal "[DEMO]" prefix used by the demo dataset. */
export function displayName(p: Product): string {
  return p.name.replace(/^\[DEMO\]\s*/i, '')
}

const cad = new Intl.NumberFormat('en-CA', { style: 'currency', currency: 'CAD' })

export function formatCad(value: number | null | undefined): string {
  return value == null ? '—' : `${cad.format(value)} CAD`
}
