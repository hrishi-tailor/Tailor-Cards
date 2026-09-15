import { useState, useEffect, useMemo, useRef, useCallback } from 'react'
import { Link } from 'react-router-dom'
import {
  getAdminBuylistSubmissions,
  updateBuylistStatus,
  postAdminBuylistMessage,
  getBuylistSubmission,
  resolveImageUrl,
  setAdminAuth,
  clearAdminAuth,
} from '../api/buylistApi'
import type { BuylistSubmission, BuylistStatus } from '../types'
import './AdminBuylist.css'

type SortOption = 'newest' | 'oldest' | 'price_desc' | 'price_asc'
type ViewMode = 'table' | 'cards'

const QUICK_TEMPLATES = [
  {
    label: 'Request Corner Photos',
    text: 'Hello! Thank you for your submission. Could you please upload a couple more high-resolution close-up photos of the front and back corners under direct lighting?',
  },
  {
    label: 'Make Cash Offer',
    text: 'Hello! TailorCards has reviewed your card. We are pleased to make an official cash offer of $___ CAD for this item. Please let us know if you would like to proceed!',
  },
  {
    label: 'Accept Offer & Ship',
    text: 'Great news! Your card offer has been approved. Please prepare your card safely in a penny sleeve and semi-rigid holder/top loader, and we will email you insured shipping instructions.',
  },
  {
    label: 'Pass on Item',
    text: 'Thank you for your submission to TailorCards. After reviewing current market conditions and our inventory capacity, we will have to pass on this item at this time. We appreciate your interest!',
  },
]

export function AdminBuylist() {
  // Data state
  const [submissions, setSubmissions] = useState<BuylistSubmission[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [statusFilter, setStatusFilter] = useState<string>('ALL')
  const [searchQuery, setSearchQuery] = useState('')
  const [sortBy, setSortBy] = useState<SortOption>('newest')
  const [viewMode, setViewMode] = useState<ViewMode>('table')

  // Auth modal state (in case 401 is received)
  const [showAuthModal, setShowAuthModal] = useState(false)
  const [authUsername, setAuthUsername] = useState('admin')
  const [authPassword, setAuthPassword] = useState('admin')

  // Inspection Drawer state
  const [selectedSubmission, setSelectedSubmission] = useState<BuylistSubmission | null>(null)
  const [isUpdatingStatus, setIsUpdatingStatus] = useState(false)
  const [statusFeedback, setStatusFeedback] = useState<string | null>(null)

  // Drawer Chat state
  const [adminReplyText, setAdminReplyText] = useState('')
  const [isSendingMessage, setIsSendingMessage] = useState(false)
  const [chatError, setChatError] = useState<string | null>(null)
  const chatEndRef = useRef<HTMLDivElement>(null)

  // High-Resolution Lightbox & Corner Zoom state
  const [activePhotoIndex, setActivePhotoIndex] = useState<number | null>(null)
  const [zoomLevel, setZoomLevel] = useState<number>(1)
  const [zoomOrigin, setZoomOrigin] = useState<string>('50% 50%')

  // Copy feedback
  const [copiedToken, setCopiedToken] = useState(false)

  // Fetch submissions from API
  const fetchSubmissions = useCallback(async (showSpinner = true) => {
    if (showSpinner) setLoading(true)
    try {
      const data = await getAdminBuylistSubmissions()
      setSubmissions(data.content || [])
      setError(null)
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Failed to fetch buylist submissions'
      setError(msg)
      if (msg.includes('Unauthorized')) {
        setShowAuthModal(true)
      }
    } finally {
      if (showSpinner) setLoading(false)
    }
  }, [])

  useEffect(() => {
    fetchSubmissions(true)
  }, [fetchSubmissions])

  // Poll open submission for real-time customer replies every 7 seconds
  const selectedTrackingToken = selectedSubmission?.trackingToken

  useEffect(() => {
    if (!selectedTrackingToken) return

    const interval = setInterval(async () => {
      if (document.visibilityState !== 'visible') return
      try {
        const fresh = await getBuylistSubmission(selectedTrackingToken)
        setSelectedSubmission(fresh)
        // Also update submission in list
        setSubmissions((prev) =>
          prev.map((item) => (item.id === fresh.id ? fresh : item))
        )
      } catch {
        // silent poll failure
      }
    }, 7000)

    return () => clearInterval(interval)
  }, [selectedTrackingToken])

  // Scroll chat to bottom when messages change
  useEffect(() => {
    if (selectedSubmission?.messages && selectedSubmission.messages.length > 0) {
      chatEndRef.current?.scrollIntoView({ behavior: 'smooth' })
    }
  }, [selectedSubmission?.messages])

  // Keyboard navigation for lightbox
  useEffect(() => {
    if (activePhotoIndex === null || !selectedSubmission?.imageUrls) return

    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        setActivePhotoIndex(null)
      } else if (e.key === 'ArrowRight') {
        setZoomLevel(1)
        setZoomOrigin('50% 50%')
        setActivePhotoIndex((prev) =>
          prev !== null && prev < selectedSubmission.imageUrls.length - 1 ? prev + 1 : 0
        )
      } else if (e.key === 'ArrowLeft') {
        setZoomLevel(1)
        setZoomOrigin('50% 50%')
        setActivePhotoIndex((prev) =>
          prev !== null && prev > 0 ? prev - 1 : selectedSubmission.imageUrls.length - 1
        )
      } else if (e.key === '+' || e.key === '=') {
        setZoomLevel((z) => Math.min(3, z + 0.5))
      } else if (e.key === '-') {
        setZoomLevel((z) => Math.max(1, z - 0.5))
      }
    }

    window.addEventListener('keydown', handleKeyDown)
    return () => window.removeEventListener('keydown', handleKeyDown)
  }, [activePhotoIndex, selectedSubmission?.imageUrls])

  // Calculate statistics counts
  const stats = useMemo(() => {
    const total = submissions.length
    const pending = submissions.filter((s) => s.status === 'PENDING').length
    const underReview = submissions.filter((s) => s.status === 'UNDER_REVIEW').length
    const offered = submissions.filter((s) => s.status === 'OFFERED').length
    const accepted = submissions.filter((s) => s.status === 'ACCEPTED').length
    const rejected = submissions.filter((s) => s.status === 'REJECTED').length
    return { total, pending, underReview, offered, accepted, rejected }
  }, [submissions])

  // Filter and sort submissions
  const filteredSubmissions = useMemo(() => {
    let list = [...submissions]

    // Status filter
    if (statusFilter !== 'ALL') {
      list = list.filter((s) => s.status === statusFilter)
    }

    // Search query filter
    if (searchQuery.trim()) {
      const q = searchQuery.toLowerCase().trim()
      list = list.filter(
        (s) =>
          s.cardName.toLowerCase().includes(q) ||
          (s.cardSet && s.cardSet.toLowerCase().includes(q)) ||
          s.customerEmail.toLowerCase().includes(q) ||
          (s.customerName && s.customerName.toLowerCase().includes(q)) ||
          s.trackingToken.toLowerCase().includes(q)
      )
    }

    // Sort
    list.sort((a, b) => {
      if (sortBy === 'newest') {
        return new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime()
      }
      if (sortBy === 'oldest') {
        return new Date(a.createdAt).getTime() - new Date(b.createdAt).getTime()
      }
      if (sortBy === 'price_desc') {
        return (b.askingPrice || 0) - (a.askingPrice || 0)
      }
      if (sortBy === 'price_asc') {
        return (a.askingPrice || 0) - (b.askingPrice || 0)
      }
      return 0
    })

    return list
  }, [submissions, statusFilter, searchQuery, sortBy])

  // Handle status update
  const handleStatusChange = async (newStatus: BuylistStatus) => {
    if (!selectedSubmission || isUpdatingStatus) return
    setIsUpdatingStatus(true)
    setStatusFeedback(null)

    try {
      const updated = await updateBuylistStatus(selectedSubmission.id, newStatus)
      setSelectedSubmission(updated)
      setSubmissions((prev) =>
        prev.map((item) => (item.id === updated.id ? updated : item))
      )
      setStatusFeedback(`Status updated to ${newStatus.replace('_', ' ')}`)
      setTimeout(() => setStatusFeedback(null), 3500)
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Failed to update status'
      setStatusFeedback(`Error: ${msg}`)
    } finally {
      setIsUpdatingStatus(false)
    }
  }

  // Handle sending admin message
  const handleSendAdminMessage = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!selectedSubmission || !adminReplyText.trim() || isSendingMessage) return

    const text = adminReplyText.trim()
    setIsSendingMessage(true)
    setChatError(null)

    try {
      const newMsg = await postAdminBuylistMessage(selectedSubmission.id, text)
      setAdminReplyText('')

      setSelectedSubmission((prev) => {
        if (!prev) return prev
        const updatedMessages = [...(prev.messages || []), newMsg]
        return { ...prev, messages: updatedMessages }
      })

      setSubmissions((prev) =>
        prev.map((item) =>
          item.id === selectedSubmission.id
            ? { ...item, messages: [...(item.messages || []), newMsg] }
            : item
        )
      )
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Failed to send message'
      setChatError(msg)
    } finally {
      setIsSendingMessage(false)
    }
  }

  // Insert template into chat input
  const handleApplyTemplate = (text: string) => {
    setAdminReplyText(text)
  }

  // Copy tracking token
  const handleCopyToken = (token: string) => {
    navigator.clipboard.writeText(token).then(() => {
      setCopiedToken(true)
      setTimeout(() => setCopiedToken(false), 2000)
    })
  }

  // Save admin auth
  const handleSaveAuth = (e: React.FormEvent) => {
    e.preventDefault()
    setAdminAuth(authUsername, authPassword)
    setShowAuthModal(false)
    fetchSubmissions(true)
  }

  const handleResetAuth = () => {
    clearAdminAuth()
    setAuthUsername('admin')
    setAuthPassword('admin')
    setAdminAuth('admin', 'admin')
    setShowAuthModal(false)
    fetchSubmissions(true)
  }

  // Corner Zoom Preset helper
  const applyCornerZoom = (corner: 'TL' | 'TR' | 'BL' | 'BR' | 'CENTER' | 'RESET') => {
    if (corner === 'RESET') {
      setZoomLevel(1)
      setZoomOrigin('50% 50%')
      return
    }
    setZoomLevel(2.5)
    switch (corner) {
      case 'TL':
        setZoomOrigin('0% 0%')
        break
      case 'TR':
        setZoomOrigin('100% 0%')
        break
      case 'BL':
        setZoomOrigin('0% 100%')
        break
      case 'BR':
        setZoomOrigin('100% 100%')
        break
      case 'CENTER':
        setZoomOrigin('50% 50%')
        break
    }
  }

  // Status Badge UI helper
  const renderStatusBadge = (status: BuylistStatus) => {
    switch (status) {
      case 'PENDING':
        return <span className="tc-admin-badge badge-pending">Pending Review</span>
      case 'UNDER_REVIEW':
        return <span className="tc-admin-badge badge-review">Under Review</span>
      case 'OFFERED':
        return <span className="tc-admin-badge badge-offered">Offer Made</span>
      case 'ACCEPTED':
        return <span className="tc-admin-badge badge-accepted">Offer Accepted</span>
      case 'REJECTED':
        return <span className="tc-admin-badge badge-rejected">Declined</span>
      default:
        return <span className="tc-admin-badge">{status}</span>
    }
  }

  // Date formatter
  const formatDate = (isoString?: string) => {
    if (!isoString) return '—'
    try {
      const d = new Date(isoString)
      return d.toLocaleDateString(undefined, {
        month: 'short',
        day: 'numeric',
        year: 'numeric',
        hour: '2-digit',
        minute: '2-digit',
      })
    } catch {
      return isoString
    }
  }

  return (
    <div className="tc-admin-container">
      {/* Top Breadcrumb & Actions */}
      <div className="tc-admin-top-bar">
        <div className="tc-admin-breadcrumbs">
          <Link to="/" className="tc-admin-home-link">Storefront</Link>
          <span className="tc-admin-sep">/</span>
          <span className="tc-admin-current">Buylist Admin Portal</span>
        </div>
        <div className="tc-admin-top-actions">
          <button
            type="button"
            className="tc-admin-refresh-btn"
            onClick={() => fetchSubmissions(false)}
            title="Refresh submissions"
          >
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" className="tc-admin-icon">
              <path d="M23 4v6h-6M1 20v-6h6" />
              <path d="M3.51 9a9 9 0 0 1 14.85-3.36L23 10M1 14l4.64 4.36A9 9 0 0 0 20.49 15" />
            </svg>
            <span>Sync</span>
          </button>
          <button
            type="button"
            className="tc-admin-auth-toggle-btn"
            onClick={() => setShowAuthModal(true)}
            title="Configure Admin Credentials"
          >
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" className="tc-admin-icon">
              <rect x="3" y="11" width="18" height="11" rx="2" ry="2" />
              <path d="M7 11V7a5 5 0 0 1 10 0v4" />
            </svg>
            <span>Admin Key</span>
          </button>
        </div>
      </div>

      {/* Main Admin Header */}
      <header className="tc-admin-header-card">
        <div className="tc-admin-header-titles">
          <div className="tc-admin-badge-pill">🔒 Appraisal Operations</div>
          <h1 className="tc-admin-main-title">Buylist Submissions Dashboard</h1>
          <p className="tc-admin-sub-title">
            Inspect card condition photos in high resolution, evaluate market comps, make cash offers, and manage seller conversations.
          </p>
        </div>

        {/* Quick Summary Counts Chips */}
        <div className="tc-admin-chips-grid">
          <button
            type="button"
            className={`tc-admin-chip ${statusFilter === 'ALL' ? 'active' : ''}`}
            onClick={() => setStatusFilter('ALL')}
          >
            <span className="tc-chip-label">Total</span>
            <span className="tc-chip-count">{stats.total}</span>
          </button>

          <button
            type="button"
            className={`tc-admin-chip chip-pending ${statusFilter === 'PENDING' ? 'active' : ''}`}
            onClick={() => setStatusFilter('PENDING')}
          >
            <span className="tc-chip-label">Pending</span>
            <span className="tc-chip-count">{stats.pending}</span>
          </button>

          <button
            type="button"
            className={`tc-admin-chip chip-review ${statusFilter === 'UNDER_REVIEW' ? 'active' : ''}`}
            onClick={() => setStatusFilter('UNDER_REVIEW')}
          >
            <span className="tc-chip-label">Under Review</span>
            <span className="tc-chip-count">{stats.underReview}</span>
          </button>

          <button
            type="button"
            className={`tc-admin-chip chip-offered ${statusFilter === 'OFFERED' ? 'active' : ''}`}
            onClick={() => setStatusFilter('OFFERED')}
          >
            <span className="tc-chip-label">Offered</span>
            <span className="tc-chip-count">{stats.offered}</span>
          </button>

          <button
            type="button"
            className={`tc-admin-chip chip-accepted ${statusFilter === 'ACCEPTED' ? 'active' : ''}`}
            onClick={() => setStatusFilter('ACCEPTED')}
          >
            <span className="tc-chip-label">Accepted</span>
            <span className="tc-chip-count">{stats.accepted}</span>
          </button>

          <button
            type="button"
            className={`tc-admin-chip chip-rejected ${statusFilter === 'REJECTED' ? 'active' : ''}`}
            onClick={() => setStatusFilter('REJECTED')}
          >
            <span className="tc-chip-label">Declined</span>
            <span className="tc-chip-count">{stats.rejected}</span>
          </button>
        </div>
      </header>

      {/* Controls Bar: Search, Status Tabs, Sort, View Toggle */}
      <div className="tc-admin-controls-card">
        <div className="tc-admin-search-wrap">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" className="tc-admin-search-icon">
            <circle cx="11" cy="11" r="8" />
            <line x1="21" y1="21" x2="16.65" y2="16.65" />
          </svg>
          <input
            type="text"
            className="tc-admin-search-input"
            placeholder="Search by card name, expansion set, seller email, or tracking token..."
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
          />
          {searchQuery && (
            <button
              type="button"
              className="tc-admin-search-clear"
              onClick={() => setSearchQuery('')}
              title="Clear search"
            >
              ✕
            </button>
          )}
        </div>

        <div className="tc-admin-controls-right">
          {/* Status Tabs */}
          <div className="tc-admin-status-tabs" role="tablist">
            {(['ALL', 'PENDING', 'UNDER_REVIEW', 'OFFERED', 'ACCEPTED', 'REJECTED'] as const).map((st) => (
              <button
                key={st}
                type="button"
                role="tab"
                aria-selected={statusFilter === st}
                className={`tc-status-tab-btn ${statusFilter === st ? 'active' : ''}`}
                onClick={() => setStatusFilter(st)}
              >
                {st === 'ALL'
                  ? 'All'
                  : st === 'UNDER_REVIEW'
                  ? 'Review'
                  : st.charAt(0) + st.slice(1).toLowerCase()}
              </button>
            ))}
          </div>

          {/* Sort Dropdown */}
          <div className="tc-admin-sort-wrap">
            <label htmlFor="tc-admin-sort" className="tc-admin-sort-label">Sort:</label>
            <select
              id="tc-admin-sort"
              className="tc-admin-select"
              value={sortBy}
              onChange={(e) => setSortBy(e.target.value as SortOption)}
            >
              <option value="newest">Newest First</option>
              <option value="oldest">Oldest First</option>
              <option value="price_desc">Price: High to Low</option>
              <option value="price_asc">Price: Low to High</option>
            </select>
          </div>

          {/* View Mode Toggle */}
          <div className="tc-admin-view-toggle">
            <button
              type="button"
              className={`tc-view-toggle-btn ${viewMode === 'table' ? 'active' : ''}`}
              onClick={() => setViewMode('table')}
              title="Table View"
            >
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                <line x1="3" y1="6" x2="21" y2="6" />
                <line x1="3" y1="12" x2="21" y2="12" />
                <line x1="3" y1="18" x2="21" y2="18" />
              </svg>
            </button>
            <button
              type="button"
              className={`tc-view-toggle-btn ${viewMode === 'cards' ? 'active' : ''}`}
              onClick={() => setViewMode('cards')}
              title="Cards Grid View"
            >
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                <rect x="3" y="3" width="7" height="7" />
                <rect x="14" y="3" width="7" height="7" />
                <rect x="14" y="14" width="7" height="7" />
                <rect x="3" y="14" width="7" height="7" />
              </svg>
            </button>
          </div>
        </div>
      </div>

      {/* Error state */}
      {error && (
        <div className="tc-admin-alert-error">
          <div className="tc-alert-content">
            <strong>Connection Error:</strong> {error}
          </div>
          <button type="button" className="tc-admin-retry-btn" onClick={() => fetchSubmissions(true)}>
            Retry
          </button>
        </div>
      )}

      {/* Loading state */}
      {loading && !submissions.length ? (
        <div className="tc-admin-loading-card">
          <div className="tc-admin-spinner" />
          <p>Loading buylist submissions...</p>
        </div>
      ) : filteredSubmissions.length === 0 ? (
        <div className="tc-admin-empty-card">
          <div className="tc-admin-empty-icon">📭</div>
          <h3>No submissions found</h3>
          <p>
            {searchQuery || statusFilter !== 'ALL'
              ? 'Try changing your status filter or clearing your search term.'
              : 'No cards have been submitted to the buylist yet.'}
          </p>
          {(searchQuery || statusFilter !== 'ALL') && (
            <button
              type="button"
              className="tc-admin-clear-filters-btn"
              onClick={() => {
                setStatusFilter('ALL')
                setSearchQuery('')
              }}
            >
              Reset Filters
            </button>
          )}
        </div>
      ) : viewMode === 'table' ? (
        /* ==================== TABLE VIEW ==================== */
        <div className="tc-admin-table-card">
          <div className="tc-table-responsive">
            <table className="tc-admin-table">
              <thead>
                <tr>
                  <th style={{ width: '80px' }}>Photo</th>
                  <th>Card Name</th>
                  <th>Set / Expansion</th>
                  <th>Seller</th>
                  <th>Asking Price</th>
                  <th>Submitted</th>
                  <th>Status</th>
                  <th style={{ width: '150px', textAlign: 'right' }}>Action</th>
                </tr>
              </thead>
              <tbody>
                {filteredSubmissions.map((sub) => {
                  const thumb = sub.imageUrls?.[0]
                  const photoCount = sub.imageUrls?.length || 0
                  const messageCount = sub.messages?.length || 0

                  return (
                    <tr
                      key={sub.id}
                      className={`tc-admin-row ${selectedSubmission?.id === sub.id ? 'selected-row' : ''}`}
                      onClick={() => setSelectedSubmission(sub)}
                    >
                      <td className="tc-table-thumb-cell">
                        {thumb ? (
                          <div className="tc-thumb-wrapper">
                            <img
                              src={resolveImageUrl(thumb)}
                              alt={sub.cardName}
                              className="tc-table-thumb"
                              loading="lazy"
                            />
                            {photoCount > 1 && (
                              <span className="tc-thumb-count">+{photoCount - 1}</span>
                            )}
                          </div>
                        ) : (
                          <div className="tc-thumb-placeholder">No Photo</div>
                        )}
                      </td>
                      <td className="tc-table-card-name-cell">
                        <span className="tc-card-name-main">{sub.cardName}</span>
                        <div className="tc-card-token-sub">
                          <span>Token: <code>{sub.trackingToken.slice(0, 8)}...</code></span>
                          {messageCount > 0 && (
                            <span className="tc-message-pill">
                              💬 {messageCount} msg{messageCount > 1 ? 's' : ''}
                            </span>
                          )}
                        </div>
                      </td>
                      <td className="tc-table-set-cell">
                        {sub.cardSet ? (
                          <span className="tc-set-tag">{sub.cardSet}</span>
                        ) : (
                          <span className="tc-muted">—</span>
                        )}
                      </td>
                      <td className="tc-table-seller-cell">
                        {sub.customerName && (
                          <span className="tc-seller-name">{sub.customerName}</span>
                        )}
                        <span className="tc-seller-email">{sub.customerEmail}</span>
                      </td>
                      <td className="tc-table-price-cell">
                        {typeof sub.askingPrice === 'number' && sub.askingPrice > 0 ? (
                          <strong className="tc-price-val">${sub.askingPrice.toFixed(2)} CAD</strong>
                        ) : (
                          <span className="tc-appraisal-val">Appraisal</span>
                        )}
                      </td>
                      <td className="tc-table-date-cell">
                        <span className="tc-date-text">{formatDate(sub.createdAt)}</span>
                      </td>
                      <td className="tc-table-status-cell">
                        {renderStatusBadge(sub.status)}
                      </td>
                      <td className="tc-table-action-cell">
                        <button
                          type="button"
                          className="tc-admin-inspect-btn"
                          onClick={(e) => {
                            e.stopPropagation()
                            setSelectedSubmission(sub)
                          }}
                        >
                          Inspect & Manage
                        </button>
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>
        </div>
      ) : (
        /* ==================== CARDS GRID VIEW ==================== */
        <div className="tc-admin-cards-grid">
          {filteredSubmissions.map((sub) => {
            const thumb = sub.imageUrls?.[0]
            const photoCount = sub.imageUrls?.length || 0
            const messageCount = sub.messages?.length || 0

            return (
              <div
                key={sub.id}
                className={`tc-admin-grid-card ${selectedSubmission?.id === sub.id ? 'selected-card' : ''}`}
                onClick={() => setSelectedSubmission(sub)}
              >
                <div className="tc-grid-card-thumb-wrap">
                  {thumb ? (
                    <img
                      src={resolveImageUrl(thumb)}
                      alt={sub.cardName}
                      className="tc-grid-card-thumb"
                      loading="lazy"
                    />
                  ) : (
                    <div className="tc-grid-thumb-placeholder">No Card Photos</div>
                  )}
                  <div className="tc-grid-card-badges">
                    {renderStatusBadge(sub.status)}
                    {photoCount > 0 && (
                      <span className="tc-grid-photo-badge">📸 {photoCount}</span>
                    )}
                  </div>
                </div>

                <div className="tc-grid-card-body">
                  <h3 className="tc-grid-card-title">{sub.cardName}</h3>
                  {sub.cardSet && <p className="tc-grid-card-set">{sub.cardSet}</p>}

                  <div className="tc-grid-card-meta">
                    <div className="tc-meta-row">
                      <span className="tc-meta-label">Seller:</span>
                      <span className="tc-meta-val">{sub.customerName || sub.customerEmail}</span>
                    </div>
                    <div className="tc-meta-row">
                      <span className="tc-meta-label">Asking:</span>
                      <span className="tc-meta-val highlight">
                        {typeof sub.askingPrice === 'number' && sub.askingPrice > 0
                          ? `$${sub.askingPrice.toFixed(2)} CAD`
                          : 'Appraisal Request'}
                      </span>
                    </div>
                    <div className="tc-meta-row">
                      <span className="tc-meta-label">Date:</span>
                      <span className="tc-meta-val">{formatDate(sub.createdAt)}</span>
                    </div>
                  </div>

                  <div className="tc-grid-card-footer">
                    {messageCount > 0 && (
                      <span className="tc-grid-msg-pill">
                        💬 {messageCount} message{messageCount > 1 ? 's' : ''}
                      </span>
                    )}
                    <button
                      type="button"
                      className="tc-grid-inspect-btn"
                      onClick={(e) => {
                        e.stopPropagation()
                        setSelectedSubmission(sub)
                      }}
                    >
                      Inspect & Manage →
                    </button>
                  </div>
                </div>
              </div>
            )
          })}
        </div>
      )}

      {/* =========================================================================
          DETAILED INSPECTION & MANAGEMENT DRAWER / MODAL
          ========================================================================= */}
      {selectedSubmission && (
        <div
          className="tc-drawer-backdrop"
          onClick={() => setSelectedSubmission(null)}
          role="dialog"
          aria-modal="true"
        >
          <div
            className="tc-drawer-panel"
            onClick={(e) => e.stopPropagation()}
          >
            {/* Drawer Top Header */}
            <div className="tc-drawer-header">
              <div className="tc-drawer-title-area">
                <div className="tc-drawer-badge-row">
                  {renderStatusBadge(selectedSubmission.status)}
                  <span className="tc-drawer-id">ID #{selectedSubmission.id}</span>
                  <div className="tc-drawer-token-chip">
                    <span>Token: <code>{selectedSubmission.trackingToken.slice(0, 10)}...</code></span>
                    <button
                      type="button"
                      className="tc-drawer-copy-btn"
                      onClick={() => handleCopyToken(selectedSubmission.trackingToken)}
                      title="Copy full tracking token"
                    >
                      {copiedToken ? '✓ Copied' : 'Copy'}
                    </button>
                  </div>
                </div>
                <h2 className="tc-drawer-card-title">{selectedSubmission.cardName}</h2>
                {selectedSubmission.cardSet && (
                  <p className="tc-drawer-set-title">Expansion Set: <strong>{selectedSubmission.cardSet}</strong></p>
                )}
              </div>

              <button
                type="button"
                className="tc-drawer-close-btn"
                onClick={() => setSelectedSubmission(null)}
                aria-label="Close Drawer"
              >
                ✕
              </button>
            </div>

            {/* Status Update Feedback Alert */}
            {statusFeedback && (
              <div className={`tc-status-feedback-banner ${statusFeedback.startsWith('Error') ? 'feedback-error' : 'feedback-success'}`}>
                {statusFeedback}
              </div>
            )}

            {/* Drawer Content Tabs / Scrollable Body */}
            <div className="tc-drawer-scroll-body">
              {/* Section 1: Detailed Metadata & Valuation */}
              <div className="tc-drawer-section">
                <h3 className="tc-drawer-section-heading">Seller & Valuation Overview</h3>
                <div className="tc-drawer-meta-grid">
                  <div className="tc-meta-box">
                    <span className="tc-box-label">Customer Contact</span>
                    <strong className="tc-box-val">
                      {selectedSubmission.customerName ? `${selectedSubmission.customerName} ` : ''}
                      <a href={`mailto:${selectedSubmission.customerEmail}`} className="tc-email-link">
                        ({selectedSubmission.customerEmail})
                      </a>
                    </strong>
                  </div>

                  <div className="tc-meta-box">
                    <span className="tc-box-label">Asking Price</span>
                    <strong className="tc-box-val price-accent">
                      {typeof selectedSubmission.askingPrice === 'number' && selectedSubmission.askingPrice > 0
                        ? `$${selectedSubmission.askingPrice.toFixed(2)} CAD`
                        : 'Appraisal Request'}
                    </strong>
                  </div>

                  <div className="tc-meta-box">
                    <span className="tc-box-label">Submission Date</span>
                    <strong className="tc-box-val">{formatDate(selectedSubmission.createdAt)}</strong>
                  </div>

                  <div className="tc-meta-box">
                    <span className="tc-box-label">Customer Tracking Link</span>
                    <Link
                      to={`/sell/track/${selectedSubmission.trackingToken}`}
                      target="_blank"
                      rel="noopener noreferrer"
                      className="tc-public-view-link"
                    >
                      Open Public Tracking Page ↗
                    </Link>
                  </div>
                </div>

                {selectedSubmission.additionalComments && (
                  <div className="tc-drawer-comments-box">
                    <span className="tc-box-label">Seller Condition Notes & Comments:</span>
                    <p className="tc-comments-content">{selectedSubmission.additionalComments}</p>
                  </div>
                )}
              </div>

              {/* Section 2: Status Management */}
              <div className="tc-drawer-section status-section">
                <div className="tc-section-header-flex">
                  <h3 className="tc-drawer-section-heading">Update Submission Status</h3>
                  {isUpdatingStatus && <span className="tc-saving-indicator">Updating status...</span>}
                </div>
                <div className="tc-status-segmented-control">
                  {(['PENDING', 'UNDER_REVIEW', 'OFFERED', 'ACCEPTED', 'REJECTED'] as const).map((statusVal) => {
                    const isActive = selectedSubmission.status === statusVal

                    return (
                      <button
                        key={statusVal}
                        type="button"
                        className={`tc-status-segment-btn segment-${statusVal.toLowerCase()} ${isActive ? 'active' : ''}`}
                        onClick={() => handleStatusChange(statusVal)}
                        disabled={isUpdatingStatus}
                      >
                        {statusVal === 'PENDING' && '⏳ Pending'}
                        {statusVal === 'UNDER_REVIEW' && '🔍 Under Review'}
                        {statusVal === 'OFFERED' && '💰 Offer Made'}
                        {statusVal === 'ACCEPTED' && '✅ Accepted'}
                        {statusVal === 'REJECTED' && '✕ Declined'}
                      </button>
                    )
                  })}
                </div>
              </div>

              {/* Section 3: Full Photo Gallery with High-Resolution Lightbox */}
              <div className="tc-drawer-section gallery-section">
                <div className="tc-section-header-flex">
                  <h3 className="tc-drawer-section-heading">
                    Submitted Photos ({selectedSubmission.imageUrls?.length || 0})
                  </h3>
                  <span className="tc-gallery-sub">Click any photo to inspect corners & surface</span>
                </div>

                {(!selectedSubmission.imageUrls || selectedSubmission.imageUrls.length === 0) ? (
                  <div className="tc-no-photos-msg">No uploaded photos available for this submission.</div>
                ) : (
                  <div className="tc-drawer-photos-grid">
                    {selectedSubmission.imageUrls.map((url, idx) => (
                      <div
                        key={idx}
                        className="tc-drawer-photo-card"
                        onClick={() => {
                          setZoomLevel(1)
                          setZoomOrigin('50% 50%')
                          setActivePhotoIndex(idx)
                        }}
                      >
                        <img
                          src={resolveImageUrl(url)}
                          alt={`${selectedSubmission.cardName} preview ${idx + 1}`}
                          className="tc-drawer-thumb"
                          loading="lazy"
                        />
                        <div className="tc-photo-card-overlay">
                          <span className="tc-photo-idx-badge">Photo #{idx + 1}</span>
                          <span className="tc-inspect-tag">🔍 Inspect</span>
                        </div>
                      </div>
                    ))}
                  </div>
                )}
              </div>

              {/* Section 4: Embedded Admin Chat Thread */}
              <div className="tc-drawer-section chat-section">
                <div className="tc-section-header-flex">
                  <h3 className="tc-drawer-section-heading">
                    Seller Communications ({selectedSubmission.messages?.length || 0})
                  </h3>
                  <span className="tc-chat-live-pulse">
                    <span className="tc-live-dot" /> Live • Auto-refreshes every 7s
                  </span>
                </div>

                {/* Message Stream */}
                <div className="tc-admin-chat-stream">
                  {(!selectedSubmission.messages || selectedSubmission.messages.length === 0) ? (
                    <div className="tc-admin-chat-empty">
                      <p>No messages sent yet between TailorCards and this seller.</p>
                      <small>Use quick response templates below to initiate an inquiry or offer.</small>
                    </div>
                  ) : (
                    selectedSubmission.messages.map((msg) => {
                      const isAdmin = msg.senderRole === 'ADMIN'

                      return (
                        <div
                          key={msg.id}
                          className={`tc-admin-msg-row ${isAdmin ? 'admin-side' : 'customer-side'}`}
                        >
                          <div className="tc-admin-msg-bubble">
                            <div className="tc-msg-header">
                              <span className={`tc-sender-tag ${isAdmin ? 'tag-admin' : 'tag-customer'}`}>
                                {isAdmin ? 'TailorCards Staff' : selectedSubmission.customerName || 'Seller'}
                              </span>
                              <span className="tc-msg-date">{formatDate(msg.createdAt)}</span>
                            </div>
                            <p className="tc-msg-body">{msg.message}</p>
                          </div>
                        </div>
                      )
                    })
                  )}
                  <div ref={chatEndRef} />
                </div>

                {/* Quick Response Templates */}
                <div className="tc-quick-templates-wrap">
                  <span className="tc-templates-label">Quick Templates:</span>
                  <div className="tc-templates-chips">
                    {QUICK_TEMPLATES.map((tmpl, idx) => (
                      <button
                        key={idx}
                        type="button"
                        className="tc-template-chip"
                        onClick={() => handleApplyTemplate(tmpl.text)}
                      >
                        + {tmpl.label}
                      </button>
                    ))}
                  </div>
                </div>

                {/* Chat Reply Form */}
                <form className="tc-admin-reply-form" onSubmit={handleSendAdminMessage}>
                  {chatError && <div className="tc-chat-error-banner">{chatError}</div>}
                  <div className="tc-admin-input-group">
                    <textarea
                      rows={3}
                      className="tc-admin-textarea"
                      placeholder="Type a response to the seller or apply a quick template above..."
                      value={adminReplyText}
                      onChange={(e) => setAdminReplyText(e.target.value)}
                      onKeyDown={(e) => {
                        if (e.key === 'Enter' && !e.shiftKey) {
                          e.preventDefault()
                          handleSendAdminMessage(e)
                        }
                      }}
                      disabled={isSendingMessage}
                    />
                    <button
                      type="submit"
                      className="tc-admin-send-btn"
                      disabled={isSendingMessage || !adminReplyText.trim()}
                    >
                      {isSendingMessage ? 'Sending...' : 'Send Message'}
                    </button>
                  </div>
                  <span className="tc-reply-hint">Press Enter to send, Shift + Enter for new line.</span>
                </form>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* =========================================================================
          HIGH-RESOLUTION LIGHTBOX & CORNER INSPECTION MODAL
          ========================================================================= */}
      {activePhotoIndex !== null && selectedSubmission?.imageUrls && (
        <div
          className="tc-lightbox-backdrop"
          onClick={() => setActivePhotoIndex(null)}
          role="dialog"
          aria-modal="true"
        >
          <div
            className="tc-admin-lightbox-modal"
            onClick={(e) => e.stopPropagation()}
          >
            {/* Top Toolbar */}
            <div className="tc-lightbox-toolbar">
              <div className="tc-toolbar-left">
                <span className="tc-photo-counter">
                  Photo {activePhotoIndex + 1} of {selectedSubmission.imageUrls.length}
                </span>
                <span className="tc-zoom-indicator">{(zoomLevel * 100).toFixed(0)}% Zoom</span>
              </div>

              {/* Corner Inspection Preset Buttons */}
              <div className="tc-corner-presets">
                <span className="tc-corner-label">Corner Inspect:</span>
                <button type="button" className="tc-corner-btn" onClick={() => applyCornerZoom('TL')}>Top Left</button>
                <button type="button" className="tc-corner-btn" onClick={() => applyCornerZoom('TR')}>Top Right</button>
                <button type="button" className="tc-corner-btn" onClick={() => applyCornerZoom('BL')}>Bot Left</button>
                <button type="button" className="tc-corner-btn" onClick={() => applyCornerZoom('BR')}>Bot Right</button>
                <button type="button" className="tc-corner-btn" onClick={() => applyCornerZoom('CENTER')}>Center</button>
                <button type="button" className="tc-corner-btn reset" onClick={() => applyCornerZoom('RESET')}>Reset 1x</button>
              </div>

              {/* Zoom Buttons & Close */}
              <div className="tc-toolbar-right">
                <button
                  type="button"
                  className="tc-zoom-action-btn"
                  onClick={() => setZoomLevel((z) => Math.min(3.5, z + 0.5))}
                  title="Zoom In (+)"
                >
                  +
                </button>
                <button
                  type="button"
                  className="tc-zoom-action-btn"
                  onClick={() => setZoomLevel((z) => Math.max(1, z - 0.5))}
                  title="Zoom Out (-)"
                >
                  −
                </button>
                <button
                  type="button"
                  className="tc-lightbox-close"
                  onClick={() => setActivePhotoIndex(null)}
                  aria-label="Close Lightbox"
                >
                  ✕
                </button>
              </div>
            </div>

            {/* Image Canvas Container */}
            <div className="tc-lightbox-canvas">
              <img
                src={resolveImageUrl(selectedSubmission.imageUrls[activePhotoIndex])}
                alt={`${selectedSubmission.cardName} zoomable ${activePhotoIndex + 1}`}
                className="tc-inspect-img"
                style={{
                  transform: `scale(${zoomLevel})`,
                  transformOrigin: zoomOrigin,
                  cursor: zoomLevel > 1 ? 'zoom-out' : 'zoom-in',
                }}
                onClick={() => {
                  if (zoomLevel > 1) {
                    setZoomLevel(1)
                    setZoomOrigin('50% 50%')
                  } else {
                    setZoomLevel(2)
                  }
                }}
              />
            </div>

            {/* Bottom Nav / Filmstrip */}
            <div className="tc-lightbox-bottom-bar">
              <button
                type="button"
                className="tc-lightbox-prev-btn"
                onClick={() => {
                  setZoomLevel(1)
                  setZoomOrigin('50% 50%')
                  setActivePhotoIndex((prev) =>
                    prev !== null && prev > 0 ? prev - 1 : selectedSubmission.imageUrls.length - 1
                  )
                }}
              >
                ‹ Previous
              </button>

              <div className="tc-lightbox-filmstrip">
                {selectedSubmission.imageUrls.map((thumbUrl, tIdx) => (
                  <button
                    key={tIdx}
                    type="button"
                    className={`tc-filmstrip-thumb-btn ${tIdx === activePhotoIndex ? 'active' : ''}`}
                    onClick={() => {
                      setZoomLevel(1)
                      setZoomOrigin('50% 50%')
                      setActivePhotoIndex(tIdx)
                    }}
                  >
                    <img
                      src={resolveImageUrl(thumbUrl)}
                      alt={`Thumb ${tIdx + 1}`}
                      className="tc-filmstrip-img"
                    />
                  </button>
                ))}
              </div>

              <button
                type="button"
                className="tc-lightbox-next-btn"
                onClick={() => {
                  setZoomLevel(1)
                  setZoomOrigin('50% 50%')
                  setActivePhotoIndex((prev) =>
                    prev !== null && prev < selectedSubmission.imageUrls.length - 1 ? prev + 1 : 0
                  )
                }}
              >
                Next ›
              </button>
            </div>
          </div>
        </div>
      )}

      {/* =========================================================================
          ADMIN AUTH MODAL (HTTP BASIC / KEY SETUP)
          ========================================================================= */}
      {showAuthModal && (
        <div
          className="tc-modal-backdrop"
          onClick={() => setShowAuthModal(false)}
          role="dialog"
          aria-modal="true"
        >
          <div className="tc-auth-modal" onClick={(e) => e.stopPropagation()}>
            <div className="tc-auth-modal-header">
              <h3>Admin Credentials</h3>
              <button
                type="button"
                className="tc-modal-close-btn"
                onClick={() => setShowAuthModal(false)}
              >
                ✕
              </button>
            </div>
            <p className="tc-auth-modal-desc">
              Enter the administrator credentials configured on the Spring Boot backend to authenticate API requests to <code>/api/buylist/admin/**</code>.
            </p>

            <form onSubmit={handleSaveAuth} className="tc-auth-form">
              <div className="tc-auth-field">
                <label htmlFor="admin-user">Username</label>
                <input
                  id="admin-user"
                  type="text"
                  value={authUsername}
                  onChange={(e) => setAuthUsername(e.target.value)}
                  className="tc-auth-input"
                  required
                />
              </div>

              <div className="tc-auth-field">
                <label htmlFor="admin-pass">Password</label>
                <input
                  id="admin-pass"
                  type="password"
                  value={authPassword}
                  onChange={(e) => setAuthPassword(e.target.value)}
                  className="tc-auth-input"
                  required
                />
              </div>

              <div className="tc-auth-modal-actions">
                <button
                  type="button"
                  className="tc-auth-reset-btn"
                  onClick={handleResetAuth}
                >
                  Reset to Default (admin/admin)
                </button>
                <button type="submit" className="tc-auth-submit-btn">
                  Save & Connect
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  )
}
