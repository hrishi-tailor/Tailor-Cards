import { useState } from 'react'
import { Routes, Route, NavLink, Link, useNavigate } from 'react-router-dom'
import { ProductList } from './components/ProductList'
import { Cart } from './components/Cart'
import { SellBuylist } from './components/SellBuylist'
import { TrackBuylist } from './components/TrackBuylist'
import { AdminBuylist } from './components/AdminBuylist'
import { AdminLoginPage } from './components/AdminLoginPage'
import { ProtectedRoute } from './components/ProtectedRoute'
import { CartProvider, useCart } from './context/CartContext'
import logoImg from './assets/logo.jpg'
import './App.css'

interface NavigationProps {
  onCategorySelect: (cat: string) => void
  selectedCategory: string
}

function MainNavigation({ onCategorySelect, selectedCategory }: NavigationProps) {
  const { totalItems } = useCart()
  const [searchQuery, setSearchQuery] = useState('')
  const navigate = useNavigate()

  const handleSearchSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    navigate('/')
  }

  const handleCategoryClick = (category: string) => {
    onCategorySelect(category)
    navigate('/')
  }

  return (
    <header className="tc-header">
      <div className="tc-nav-container">
        {/* Brand Logo */}
        <Link to="/" className="tc-logo" onClick={() => handleCategoryClick('All')}>
          <img src={logoImg} alt="Tailor Cards Logo" className="tc-logo-img" />
          <span className="tc-logo-tailor">TAILOR</span>
          <span className="tc-logo-cards">CARDS</span>
        </Link>

        {/* Primary Navigation Links */}
        <nav className="tc-nav-links" aria-label="Main Navigation">
          <button
            type="button"
            className={`tc-nav-link ${selectedCategory === 'All' ? 'active' : ''}`}
            onClick={() => handleCategoryClick('All')}
          >
            Catalog
          </button>
          <button
            type="button"
            className={`tc-nav-link ${selectedCategory === 'Singles' ? 'active' : ''}`}
            onClick={() => handleCategoryClick('Singles')}
          >
            Singles
          </button>
          <button
            type="button"
            className={`tc-nav-link ${selectedCategory === 'Sealed' ? 'active' : ''}`}
            onClick={() => handleCategoryClick('Sealed')}
          >
            Sealed
          </button>
          <button
            type="button"
            className={`tc-nav-link ${selectedCategory === 'Slabs' ? 'active' : ''}`}
            onClick={() => handleCategoryClick('Slabs')}
          >
            Slabs
          </button>
          <NavLink
            to="/sell"
            className={({ isActive }) => `tc-nav-link ${isActive ? 'active' : ''}`}
          >
            Sell to Us
          </NavLink>
        </nav>

        {/* Search Bar & Cart Actions */}
        <div className="tc-nav-actions">
          <form className="tc-search-form" onSubmit={handleSearchSubmit}>
            <svg
              className="tc-search-icon"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              strokeWidth="2"
              width="16"
              height="16"
              aria-hidden="true"
            >
              <circle cx="11" cy="11" r="7" />
              <line x1="21" y1="21" x2="16.65" y2="16.65" />
            </svg>
            <input
              type="text"
              className="tc-search-input"
              placeholder="Search singles, sets, slabs..."
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
            />
          </form>

          <NavLink to="/cart" className="tc-cart-btn" aria-label={`Shopping cart with ${totalItems} items`}>
            <svg
              className="tc-cart-svg"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              strokeWidth="2"
              width="16"
              height="16"
              aria-hidden="true"
            >
              <circle cx="9" cy="21" r="1" />
              <circle cx="20" cy="21" r="1" />
              <path d="M1 1h4l2.68 13.39a2 2 0 0 0 2 1.61h9.72a2 2 0 0 0 2-1.61L23 6H6" />
            </svg>
            <span className="tc-cart-label">Cart</span>
            {totalItems > 0 && <span className="tc-cart-badge">{totalItems}</span>}
          </NavLink>
        </div>
      </div>
    </header>
  )
}

function AppContent() {
  const [selectedCategory, setSelectedCategory] = useState('All')

  return (
    <div className="tc-app-layout">
      <MainNavigation onCategorySelect={setSelectedCategory} selectedCategory={selectedCategory} />
      <main className="tc-main-content">
        <Routes>
          <Route path="/" element={<ProductList selectedCategory={selectedCategory} />} />
          <Route path="/cart" element={<Cart />} />
          <Route path="/sell" element={<SellBuylist />} />
          <Route path="/sell/track/:token" element={<TrackBuylist />} />
          <Route path="/admin/login" element={<AdminLoginPage />} />
          <Route
            path="/admin/buylist"
            element={
              <ProtectedRoute>
                <AdminBuylist />
              </ProtectedRoute>
            }
          />
        </Routes>
      </main>

      <footer className="tc-footer">
        <div className="tc-footer-container">
          <div className="tc-footer-left">
            <span className="tc-footer-brand">TAILOR CARDS</span>
            <span className="tc-footer-copy">Guaranteed Authentic Pokémon Singles &amp; Sealed Products.</span>
          </div>

          <div className="tc-footer-links">
            <Link to="/sell" className="tc-footer-link">Sell to Us</Link>
            <span className="tc-footer-divider" aria-hidden="true">/</span>
            <Link to="/admin/buylist" className="tc-footer-link tc-footer-staff-link">Staff Portal</Link>
          </div>
        </div>
      </footer>
    </div>
  )
}

function App() {
  return (
    <CartProvider>
      <AppContent />
    </CartProvider>
  )
}

export default App
