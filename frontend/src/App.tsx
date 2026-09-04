import { Routes, Route, NavLink, Link } from 'react-router-dom'
import { ProductList } from './components/ProductList'
import { Cart } from './components/Cart'
import { CartProvider, useCart } from './context/CartContext'
import './App.css'

function NavigationBar() {
  const { totalItems } = useCart()

  return (
    <header className="navbar">
      <div className="navbar-container">
        <Link to="/" className="navbar-brand">
          🎴 Tailor Cards
        </Link>
        <nav className="navbar-nav">
          <NavLink
            to="/"
            end
            className={({ isActive }) => (isActive ? 'nav-link active' : 'nav-link')}
          >
            Home
          </NavLink>
          <NavLink
            to="/cart"
            className={({ isActive }) => (isActive ? 'nav-link active' : 'nav-link')}
          >
            Cart
            {totalItems > 0 && <span className="cart-counter-badge">{totalItems}</span>}
          </NavLink>
        </nav>
      </div>
    </header>
  )
}

function AppContent() {
  return (
    <div className="app-layout">
      <NavigationBar />
      <main className="app-main">
        <Routes>
          <Route path="/" element={<ProductList />} />
          <Route path="/cart" element={<Cart />} />
        </Routes>
      </main>
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
