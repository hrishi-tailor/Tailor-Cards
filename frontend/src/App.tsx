import { useState } from 'react'
import { Routes, Route, NavLink, Link, useNavigate } from 'react-router-dom'
import { ProductList } from './components/ProductList'
import { Cart } from './components/Cart'
import { CartProvider, useCart } from './context/CartContext'
import logoImg from './assets/logo.jpg'
import './App.css'

function BoutiqueTopNav({ onCategorySelect, selectedCategory }: { onCategorySelect: (cat: string) => void; selectedCategory: string }) {
  const { totalItems } = useCart()
  const [searchQuery, setSearchQuery] = useState('')
  const navigate = useNavigate()

  const handleSearchSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    navigate('/')
  }

  const handleNavCategoryClick = (category: string) => {
    onCategorySelect(category)
    navigate('/')
  }

  return (
    <header className="tc-header">
      {/* Primary Midnight Navy Header */}
      <div className="tc-nav-primary">
        <div className="tc-nav-container">
          {/* Brand Logo */}
          <Link to="/" className="tc-logo" onClick={() => handleNavCategoryClick('All')}>
            <img src={logoImg} alt="Tailor Cards Logo" className="tc-logo-img" />
            <span className="tc-logo-tailor">TAILOR</span>
            <span className="tc-logo-cards">CARDS</span>
          </Link>

          {/* Sleek Minimal Search Bar */}
          <form className="tc-search-form" onSubmit={handleSearchSubmit}>
            <svg className="tc-search-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
              <circle cx="11" cy="11" r="7" />
              <line x1="21" y1="21" x2="16.65" y2="16.65" />
            </svg>
            <input
              type="text"
              className="tc-search-input"
              placeholder="Search singles, booster packs, graded slabs..."
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
            />
          </form>

          {/* Cart on Right */}
          <div className="tc-nav-actions">
            <NavLink to="/cart" className="tc-cart-btn">
              <div className="tc-cart-icon-wrap">
                <svg className="tc-cart-svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                  <circle cx="9" cy="21" r="1" />
                  <circle cx="20" cy="21" r="1" />
                  <path d="M1 1h4l2.68 13.39a2 2 0 0 0 2 1.61h9.72a2 2 0 0 0 2-1.61L23 6H6" />
                </svg>
                {totalItems > 0 && <span className="tc-cart-badge">{totalItems}</span>}
              </div>
              <span className="tc-cart-text">Cart</span>
            </NavLink>
          </div>
        </div>
      </div>

      {/* Secondary Royal Purple Nav */}
      <nav className="tc-nav-secondary">
        <div className="tc-nav-container secondary-container">
          <button
            type="button"
            className={`tc-menu-link ${selectedCategory === 'All' ? 'active' : ''}`}
            onClick={() => handleNavCategoryClick('All')}
          >
            All Products
          </button>
          <button
            type="button"
            className={`tc-menu-link ${selectedCategory === 'Singles' ? 'active' : ''}`}
            onClick={() => handleNavCategoryClick('Singles')}
          >
            Singles
          </button>
          <button
            type="button"
            className={`tc-menu-link ${selectedCategory === 'Sealed' ? 'active' : ''}`}
            onClick={() => handleNavCategoryClick('Sealed')}
          >
            Sealed
          </button>
          <button
            type="button"
            className={`tc-menu-link ${selectedCategory === 'Slabs' ? 'active' : ''}`}
            onClick={() => handleNavCategoryClick('Slabs')}
          >
            Slabs
          </button>
          <button
            type="button"
            className={`tc-menu-link ${selectedCategory === 'Buying Requests' ? 'active' : ''}`}
            onClick={() => handleNavCategoryClick('Buying Requests')}
          >
            Buying Requests
          </button>
          <span className="tc-nav-guarantee">✨ Verified Authenticity & Insured Shipping</span>
        </div>
      </nav>
    </header>
  )
}

function AppContent() {
  const [selectedCategory, setSelectedCategory] = useState('All')

  return (
    <div className="tc-app-layout">
      <BoutiqueTopNav onCategorySelect={setSelectedCategory} selectedCategory={selectedCategory} />
      <main className="tc-main-content">
        <Routes>
          <Route path="/" element={<ProductList selectedCategory={selectedCategory} />} />
          <Route path="/cart" element={<Cart />} />
        </Routes>
      </main>
      <footer className="tc-footer">
        <div className="tc-footer-container">
          <div className="tc-footer-brand">
            <span className="tc-logo-tailor">TAILOR</span>
            <span className="tc-logo-cards">CARDS</span>
            <p>Premium Trading Cards, Graded Slabs & Collector Supplies.</p>
          </div>
          <div className="tc-footer-copy">
            © {new Date().getFullYear()} Tailor Cards. All rights reserved.
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
