import { useEffect } from 'react'
import { Routes, Route, Navigate, useLocation } from 'react-router-dom'
import { HomePage } from './components/HomePage'
import { ShopPage } from './components/ShopPage'
import { Cart } from './components/Cart'
import { TrackBuylist } from './components/TrackBuylist'
import { TrackLookup } from './components/TrackLookup'
import { AdminBuylist } from './components/AdminBuylist'
import { AdminLoginPage } from './components/AdminLoginPage'
import { ListingGenerator } from './components/ListingGenerator'
import { SellRoute } from './components/SellRoute'
import { ProtectedRoute } from './components/ProtectedRoute'
import { CheckoutSuccess } from './components/CheckoutSuccess'
import { SiteHeader } from './components/SiteHeader'
import { SiteFooter } from './components/SiteFooter'
import { CartProvider } from './context/CartContext'

/** Scrolls to the top on page changes (but not on in-page #anchors). */
function ScrollToTop() {
  const { pathname } = useLocation()
  useEffect(() => {
    window.scrollTo(0, 0)
  }, [pathname])
  return null
}

function AppContent() {
  return (
    <div className="tc-app-layout">
      <ScrollToTop />
      <SiteHeader />
      <main className="tc-main-content">
        <Routes>
          <Route path="/" element={<HomePage />} />
          <Route path="/shop" element={<ShopPage />} />
          <Route path="/shop/:category" element={<ShopPage />} />
          <Route path="/cart" element={<Cart />} />
          <Route path="/checkout/success" element={<CheckoutSuccess />} />
          <Route path="/sell" element={<SellRoute />} />
          <Route path="/track" element={<TrackLookup />} />
          <Route path="/sell/track/:token" element={<TrackBuylist />} />
          <Route path="/trade-assistant" element={<Navigate to="/sell" replace />} />
          <Route path="/admin/login" element={<AdminLoginPage />} />
          <Route
            path="/admin/buylist"
            element={
              <ProtectedRoute>
                <AdminBuylist />
              </ProtectedRoute>
            }
          />
          <Route
            path="/admin/listing-generator"
            element={
              <ProtectedRoute>
                <ListingGenerator />
              </ProtectedRoute>
            }
          />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </main>
      <SiteFooter />
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
