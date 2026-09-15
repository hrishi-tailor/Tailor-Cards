import { useState, useEffect } from 'react'
import { useNavigate, useLocation, Link } from 'react-router-dom'
import { verifyAdminAuth, setAdminAuth, isAdminAuthenticated } from '../api/buylistApi'
import logoImg from '../assets/logo.jpg'
import './AdminLoginPage.css'

export function AdminLoginPage() {
  const navigate = useNavigate()
  const location = useLocation()

  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [rememberMe, setRememberMe] = useState(true)
  const [showPassword, setShowPassword] = useState(false)
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)

  // Where to redirect after successful login
  const fromLocation = (location.state as { from?: { pathname?: string } })?.from?.pathname || '/admin/buylist'

  // If already authenticated, redirect to /admin/buylist
  useEffect(() => {
    if (isAdminAuthenticated()) {
      navigate(fromLocation, { replace: true })
    }
  }, [navigate, fromLocation])

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setErrorMessage(null)

    const trimmedUser = username.trim()
    if (!trimmedUser || !password) {
      setErrorMessage('Please enter both username and password.')
      return
    }

    setIsSubmitting(true)

    try {
      // Submits credentials to GET /api/auth/verify using Basic Auth
      await verifyAdminAuth(trimmedUser, password)

      // On success: Stores the base64 auth header securely and redirects to /admin/buylist
      setAdminAuth(trimmedUser, password, rememberMe)
      navigate(fromLocation, { replace: true })
    } catch {
      // On failure: Displays a clear "Invalid credentials" error banner without leaking details
      setErrorMessage('Invalid credentials. Please verify your admin username and password.')
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <div className="tc-login-wrapper">
      <div className="tc-login-card">
        {/* Brand Header */}
        <div className="tc-login-header">
          <Link to="/" className="tc-login-logo">
            <img src={logoImg} alt="Tailor Cards Logo" className="tc-login-logo-img" />
            <span className="tc-logo-tailor">TAILOR</span>
            <span className="tc-logo-cards">CARDS</span>
          </Link>
          <div className="tc-login-badge">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" className="tc-lock-icon">
              <rect x="3" y="11" width="18" height="11" rx="2" ry="2" />
              <path d="M7 11V7a5 5 0 0 1 10 0v4" />
            </svg>
            <span>Admin Management Portal</span>
          </div>
          <h1 className="tc-login-title">Administrator Sign In</h1>
          <p className="tc-login-subtitle">
            Secure appraisal dashboard access for TailorCards team members.
          </p>
        </div>

        {/* Error Banner */}
        {errorMessage && (
          <div className="tc-login-error" role="alert">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" className="tc-alert-svg">
              <circle cx="12" cy="12" r="10" />
              <line x1="12" y1="8" x2="12" y2="12" />
              <line x1="12" y1="16" x2="12.01" y2="16" />
            </svg>
            <span>{errorMessage}</span>
          </div>
        )}

        {/* Login Form */}
        <form onSubmit={handleSubmit} className="tc-login-form" noValidate>
          <div className="tc-login-field">
            <label htmlFor="username" className="tc-login-label">
              Admin Username
            </label>
            <div className="tc-login-input-wrap">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" className="tc-field-icon">
                <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2" />
                <circle cx="12" cy="7" r="4" />
              </svg>
              <input
                id="username"
                type="text"
                className="tc-login-input"
                placeholder="e.g. admin"
                value={username}
                onChange={(e) => setUsername(e.target.value)}
                autoComplete="username"
                autoFocus
                disabled={isSubmitting}
                required
              />
            </div>
          </div>

          <div className="tc-login-field">
            <label htmlFor="password" className="tc-login-label">
              Password
            </label>
            <div className="tc-login-input-wrap">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" className="tc-field-icon">
                <rect x="3" y="11" width="18" height="11" rx="2" ry="2" />
                <path d="M7 11V7a5 5 0 0 1 10 0v4" />
              </svg>
              <input
                id="password"
                type={showPassword ? 'text' : 'password'}
                className="tc-login-input password-input"
                placeholder="••••••••••••"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                autoComplete="current-password"
                disabled={isSubmitting}
                required
              />
              <button
                type="button"
                className="tc-password-toggle-btn"
                onClick={() => setShowPassword(!showPassword)}
                aria-label={showPassword ? 'Hide password' : 'Show password'}
                tabIndex={-1}
              >
                {showPassword ? (
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" className="tc-eye-svg">
                    <path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19m-6.72-1.07a3 3 0 1 1-4.24-4.24" />
                    <line x1="1" y1="1" x2="23" y2="23" />
                  </svg>
                ) : (
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" className="tc-eye-svg">
                    <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z" />
                    <circle cx="12" cy="12" r="3" />
                  </svg>
                )}
              </button>
            </div>
          </div>

          <div className="tc-login-options">
            <label className="tc-remember-label">
              <input
                type="checkbox"
                checked={rememberMe}
                onChange={(e) => setRememberMe(e.target.checked)}
                disabled={isSubmitting}
              />
              <span>Remember session on this device</span>
            </label>
          </div>

          <button
            type="submit"
            className="tc-login-submit-btn"
            disabled={isSubmitting}
          >
            {isSubmitting ? (
              <>
                <span className="tc-spinner small" />
                <span>Verifying Credentials...</span>
              </>
            ) : (
              <>
                <span>Sign In to Admin Portal</span>
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" className="tc-btn-icon">
                  <path strokeLinecap="round" strokeLinejoin="round" d="M5 12h14M12 5l7 7-7 7" />
                </svg>
              </>
            )}
          </button>
        </form>

        {/* Footer back link */}
        <div className="tc-login-footer">
          <Link to="/" className="tc-login-back-link">
            ← Return to Public Storefront
          </Link>
        </div>
      </div>
    </div>
  )
}
