import { useMemo, useState } from 'react'
import { Link, Navigate, NavLink, useParams, useSearchParams } from 'react-router-dom'
import type { Product } from '../types'
import { CATEGORIES, categoryBySlug, inCategory, matchesSearch } from '../catalog'
import type { CategorySlug } from '../catalog'
import { useProducts } from '../hooks/useProducts'
import { ProductCard } from './ProductCard'
import { ProductQuickView } from './ProductQuickView'

type Sort = 'featured' | 'price-desc' | 'price-asc' | 'name'

const SORTS: { id: Sort; label: string }[] = [
  { id: 'featured', label: 'Featured' },
  { id: 'price-desc', label: 'Price, high to low' },
  { id: 'price-asc', label: 'Price, low to high' },
  { id: 'name', label: 'Name, A to Z' },
]

/** /shop and /shop/:category: product grid with category, availability, search and sort filters. */
export function ShopPage() {
  const { category: slug } = useParams()
  const [params, setParams] = useSearchParams()
  const { products, loading, error } = useProducts()
  const [quickView, setQuickView] = useState<Product | null>(null)
  const [filtersOpen, setFiltersOpen] = useState(false)
  const category = categoryBySlug(slug)
  const query = params.get('q') ?? ''
  const sort = (SORTS.find((s) => s.id === params.get('sort'))?.id ?? 'featured') as Sort
  const inStockOnly = params.get('stock') === '1'

  const setParam = (key: string, value: string | null) => {
    const next = new URLSearchParams(params)
    if (value) next.set(key, value)
    else next.delete(key)
    setParams(next, { replace: true })
  }

  const visible = useMemo(() => {
    const list = products.filter((p) =>
      (!category || inCategory(p, category.slug))
      && (!inStockOnly || (p.status !== 'SOLD' && p.stock > 0))
      && matchesSearch(p, query))
    const available = (p: Product) => (p.status === 'SOLD' || p.stock <= 0 ? 1 : 0)
    return list.sort((a, b) => {
      if (sort === 'price-desc') return b.price - a.price
      if (sort === 'price-asc') return a.price - b.price
      if (sort === 'name') return a.name.localeCompare(b.name)
      return available(a) - available(b) || b.price - a.price
    })
  }, [products, category, inStockOnly, query, sort])

  if (slug && !category) {
    return <Navigate to="/shop" replace />
  }

  const title = query ? `Results for “${query}”` : category ? category.label : 'All cards'
  const counts = (s: CategorySlug) => products.filter((p) => inCategory(p, s)).length

  return (
    <div className="tc-shop tc-wrap">
      <header className="tc-shop-head">
        <nav className="tc-crumbs" aria-label="Breadcrumb">
          <Link to="/">Home</Link><span aria-hidden="true">/</span>
          {category || query ? <Link to="/shop">Shop</Link> : <span>Shop</span>}
          {category && <><span aria-hidden="true">/</span><span>{category.label}</span></>}
        </nav>
        <h1>{title}</h1>
        {category && !query && <p>{category.blurb}</p>}
      </header>

      <div className="tc-shop-toolbar">
        <button type="button" className="tc-button tc-button-ghost tc-filter-btn" aria-expanded={filtersOpen}
          onClick={() => setFiltersOpen((o) => !o)}>
          <svg viewBox="0 0 24 24" aria-hidden="true" fill="none" stroke="currentColor" strokeWidth="1.8"><path d="M4 6h16M7 12h10M10 18h4" /></svg>
          Filters
        </button>
        <span className="tc-shop-count">{loading ? 'Loading…' : `${visible.length} ${visible.length === 1 ? 'item' : 'items'}`}</span>
        <label className="tc-sort">
          <span>Sort</span>
          <select value={sort} onChange={(e) => setParam('sort', e.target.value === 'featured' ? null : e.target.value)}>
            {SORTS.map((s) => <option key={s.id} value={s.id}>{s.label}</option>)}
          </select>
        </label>
      </div>

      <div className="tc-shop-body">
        <aside className={`tc-shop-filters ${filtersOpen ? 'is-open' : ''}`} aria-label="Filters">
          <div className="tc-filter-group">
            <h2>Category</h2>
            <NavLink to={{ pathname: '/shop', search: query ? `?q=${encodeURIComponent(query)}` : '' }} end
              className={() => (!category ? 'active' : '')}>
              All cards <span>{products.length}</span>
            </NavLink>
            {CATEGORIES.map((c) => (
              <NavLink key={c.slug} to={{ pathname: `/shop/${c.slug}`, search: query ? `?q=${encodeURIComponent(query)}` : '' }}
                className={() => (category?.slug === c.slug ? 'active' : '')}>
                {c.label} <span>{counts(c.slug)}</span>
              </NavLink>
            ))}
          </div>
          <div className="tc-filter-group">
            <h2>Availability</h2>
            <label className="tc-check">
              <input type="checkbox" checked={inStockOnly} onChange={(e) => setParam('stock', e.target.checked ? '1' : null)} />
              In stock only
            </label>
          </div>
          <div className="tc-filter-group">
            <h2>Search</h2>
            <label htmlFor="tc-shop-search" className="tc-sr-only">Search this collection</label>
            <input id="tc-shop-search" type="search" value={query} placeholder="Card, set or grade"
              onChange={(e) => setParam('q', e.target.value || null)} />
          </div>
        </aside>

        <section className="tc-shop-results" aria-live="polite">
          {error ? (
            <div className="tc-shop-empty"><h2>Shop unavailable</h2><p>{error}</p></div>
          ) : loading ? (
            <div className="tc-product-grid" aria-hidden="true">
              {Array.from({ length: 8 }, (_, i) => <div key={i} className="tc-product-skeleton" />)}
            </div>
          ) : visible.length === 0 ? (
            <div className="tc-shop-empty">
              <h2>Nothing here yet</h2>
              <p>{query ? 'No cards match that search. Try a card name, set or grade.' : 'New stock is added often. Check back soon, or browse all cards.'}</p>
              <Link to="/shop" className="tc-button tc-button-ghost">Browse all cards</Link>
            </div>
          ) : (
            <div className="tc-product-grid">
              {visible.map((p) => <ProductCard key={p.id} product={p} onQuickView={setQuickView} />)}
            </div>
          )}
        </section>
      </div>

      {quickView && (
        <ProductQuickView product={quickView} siblings={visible} onNavigate={setQuickView} onClose={() => setQuickView(null)} />
      )}
    </div>
  )
}
