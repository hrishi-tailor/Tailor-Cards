import { useEffect, useRef, useState } from 'react'
import { Link, NavLink, useLocation, useNavigate } from 'react-router-dom'
import { useCart } from '../context/CartContext'
import { CATEGORIES } from '../catalog'
import { siteConfig } from '../siteConfig'
import { ThemeToggle } from './ThemeToggle'
import logoImg from '../assets/logo.jpg'
import './SiteLayout.css'

export function BrandMark() {
  return (
    <span className="tc-brand">
      <span className="tc-brand-mark"><img src={logoImg} alt="" /></span>
      <span className="tc-brand-word">TAILOR CARDS</span>
    </span>
  )
}

/** Announcement bar, sticky header with the shop menu, search, theme toggle and cart. */
export function SiteHeader() {
  const { totalItems } = useCart()
  const navigate = useNavigate()
  const location = useLocation()
  const [menuOpen, setMenuOpen] = useState(false)
  const [searchOpen, setSearchOpen] = useState(false)
  const [query, setQuery] = useState('')
  const searchRef = useRef<HTMLInputElement>(null)

  // Close the mobile menu and search whenever the page changes
  useEffect(() => {
    setMenuOpen(false)
    setSearchOpen(false)
  }, [location.pathname, location.search])

  useEffect(() => {
    if (searchOpen) searchRef.current?.focus()
  }, [searchOpen])

  useEffect(() => {
    document.body.style.overflow = menuOpen ? 'hidden' : ''
    return () => { document.body.style.overflow = '' }
  }, [menuOpen])

  const submitSearch = (e: React.FormEvent) => {
    e.preventDefault()
    const q = query.trim()
    navigate(q ? `/shop?q=${encodeURIComponent(q)}` : '/shop')
  }

  const shopActive = location.pathname.startsWith('/shop')
  const sellActive = location.pathname === '/sell' || location.pathname.startsWith('/sell/')
    && !location.pathname.startsWith('/sell/track')
  const trackActive = location.pathname.startsWith('/track') || location.pathname.startsWith('/sell/track')

  return (
    <>
      {siteConfig.announcement && <div className="tc-announce">{siteConfig.announcement}</div>}
      <header className="tc-site-header">
        <div className="tc-wrap tc-header-bar">
          <button type="button" className="tc-icon-btn tc-menu-btn" aria-label="Open menu" aria-expanded={menuOpen}
            onClick={() => setMenuOpen(true)}>
            <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M3 7h18M3 12h18M3 17h18" /></svg>
          </button>

          <Link to="/" className="tc-brand-link" aria-label="Tailor Cards home"><BrandMark /></Link>

          <nav className="tc-main-nav" aria-label="Main">
            <div className="tc-nav-item tc-has-menu">
              <NavLink to="/shop" className={() => `tc-nav-a ${shopActive ? 'active' : ''}`}>
                Shop
                <svg viewBox="0 0 24 24" aria-hidden="true" className="tc-caret"><path d="m6 9 6 6 6-6" /></svg>
              </NavLink>
              <div className="tc-dropdown" role="menu">
                <Link to="/shop" role="menuitem">All cards</Link>
                {CATEGORIES.map((c) => (
                  <Link key={c.slug} to={`/shop/${c.slug}`} role="menuitem">{c.label}</Link>
                ))}
              </div>
            </div>
            <NavLink to="/sell" className={() => `tc-nav-a ${sellActive ? 'active' : ''}`}>Sell to us</NavLink>
            <NavLink to="/track" className={() => `tc-nav-a ${trackActive ? 'active' : ''}`}>Track</NavLink>
          </nav>

          <div className="tc-header-tools">
            <button type="button" className="tc-icon-btn" aria-label="Search" aria-expanded={searchOpen}
              onClick={() => setSearchOpen((o) => !o)}>
              <svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="11" cy="11" r="7" /><path d="m20 20-3.5-3.5" /></svg>
            </button>
            <ThemeToggle className="tc-hide-sm" />
            <NavLink to="/cart" className="tc-icon-btn tc-cart-link" aria-label={`Cart, ${totalItems} items`}>
              <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M5 7h14l-1.2 12.2a1 1 0 0 1-1 .8H7.2a1 1 0 0 1-1-.8L5 7Z" /><path d="M9 7a3 3 0 0 1 6 0" /></svg>
              {totalItems > 0 && <span className="tc-cart-count">{totalItems}</span>}
            </NavLink>
          </div>
        </div>

        {searchOpen && (
          <form className="tc-search-panel" onSubmit={submitSearch} role="search">
            <div className="tc-wrap tc-search-row">
              <svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="11" cy="11" r="7" /><path d="m20 20-3.5-3.5" /></svg>
              <label htmlFor="tc-site-search" className="tc-sr-only">Search cards</label>
              <input id="tc-site-search" ref={searchRef} type="search" value={query} placeholder="Search by card, set or grade"
                onChange={(e) => setQuery(e.target.value)} onKeyDown={(e) => { if (e.key === 'Escape') setSearchOpen(false) }} />
              <button type="submit" className="tc-button tc-button-solid">Search</button>
            </div>
          </form>
        )}
      </header>

      {menuOpen && (
        <div className="tc-drawer-scrim" onClick={() => setMenuOpen(false)}>
          <aside className="tc-drawer" aria-label="Menu" onClick={(e) => e.stopPropagation()}>
            <div className="tc-drawer-head">
              <BrandMark />
              <button type="button" className="tc-icon-btn" aria-label="Close menu" onClick={() => setMenuOpen(false)}>
                <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M6 6l12 12M18 6 6 18" /></svg>
              </button>
            </div>
            <form onSubmit={submitSearch} className="tc-drawer-search" role="search">
              <label htmlFor="tc-drawer-search" className="tc-sr-only">Search cards</label>
              <input id="tc-drawer-search" type="search" value={query} placeholder="Search cards"
                onChange={(e) => setQuery(e.target.value)} />
            </form>
            <nav className="tc-drawer-nav" aria-label="Mobile">
              <span className="tc-eyebrow">Shop</span>
              <Link to="/shop">All cards</Link>
              {CATEGORIES.map((c) => <Link key={c.slug} to={`/shop/${c.slug}`}>{c.label}</Link>)}
              <span className="tc-eyebrow">Sell</span>
              <Link to="/sell">Sell to us</Link>
              <Link to="/track">Track a submission</Link>
            </nav>
            <div className="tc-drawer-foot">
              <span>Theme</span>
              <ThemeToggle />
            </div>
          </aside>
        </div>
      )}
    </>
  )
}
