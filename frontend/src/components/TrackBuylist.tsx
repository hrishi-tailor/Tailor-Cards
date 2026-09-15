import { useState, useEffect, useRef, useCallback } from 'react'
import { useParams, Link } from 'react-router-dom'
import { getBuylistSubmission, postBuylistMessage, resolveImageUrl } from '../api/buylistApi'
import type { BuylistSubmission, BuylistStatus } from '../types'
import './TrackBuylist.css'

export function TrackBuylist() {
  const { token } = useParams<{ token: string }>()

  const [submission, setSubmission] = useState<BuylistSubmission | null>(null)
  const [loading, setLoading] = useState(Boolean(token))
  const [error, setError] = useState<string | null>(null)

  // Chat message state
  const [replyText, setReplyText] = useState('')
  const [isSending, setIsSending] = useState(false)
  const [sendError, setSendError] = useState<string | null>(null)
  const messagesEndRef = useRef<HTMLDivElement>(null)

  // Lightbox state
  const [activePhotoIndex, setActivePhotoIndex] = useState<number | null>(null)

  // Copy Link feedback
  const [copiedLink, setCopiedLink] = useState(false)

  // Fetch submission data silently or on demand
  const fetchSubmission = useCallback(async (isSilent = false) => {
    if (!token) return

    if (!isSilent) {
      setLoading(true)
    }

    try {
      const data = await getBuylistSubmission(token)
      setSubmission(data)
      setError(null)
    } catch (err) {
      if (!isSilent) {
        setError(err instanceof Error ? err.message : 'Unable to load submission details.')
      }
    } finally {
      if (!isSilent) {
        setLoading(false)
      }
    }
  }, [token])

  // Initial fetch
  useEffect(() => {
    let ignore = false

    if (!token) {
      return
    }

    getBuylistSubmission(token)
      .then((data) => {
        if (!ignore) {
          setSubmission(data)
          setError(null)
        }
      })
      .catch((err) => {
        if (!ignore) {
          setError(err instanceof Error ? err.message : 'Unable to load submission details.')
        }
      })
      .finally(() => {
        if (!ignore) {
          setLoading(false)
        }
      })

    return () => {
      ignore = true
    }
  }, [token])

  // Polling interval (every 8 seconds when active tab)
  useEffect(() => {
    if (!token) return

    const pollInterval = setInterval(() => {
      // Only poll if tab is visible
      if (document.visibilityState === 'visible') {
        fetchSubmission(true)
      }
    }, 8000)

    const handleVisibilityChange = () => {
      if (document.visibilityState === 'visible') {
        fetchSubmission(true)
      }
    }

    document.addEventListener('visibilitychange', handleVisibilityChange)

    return () => {
      clearInterval(pollInterval)
      document.removeEventListener('visibilitychange', handleVisibilityChange)
    }
  }, [token, fetchSubmission])

  // Auto-scroll chat to bottom on new messages
  useEffect(() => {
    if (submission?.messages && submission.messages.length > 0) {
      messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' })
    }
  }, [submission?.messages])

  // Keyboard navigation for lightbox
  useEffect(() => {
    if (activePhotoIndex === null || !submission?.imageUrls) return

    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        setActivePhotoIndex(null)
      } else if (e.key === 'ArrowRight') {
        setActivePhotoIndex((prev) =>
          prev !== null && prev < submission.imageUrls.length - 1 ? prev + 1 : 0
        )
      } else if (e.key === 'ArrowLeft') {
        setActivePhotoIndex((prev) =>
          prev !== null && prev > 0 ? prev - 1 : submission.imageUrls.length - 1
        )
      }
    }

    window.addEventListener('keydown', handleKeyDown)
    return () => window.removeEventListener('keydown', handleKeyDown)
  }, [activePhotoIndex, submission?.imageUrls])

  // Handle posting a reply message
  const handleSendMessage = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!token || !replyText.trim() || isSending) return

    const textToSend = replyText.trim()
    setIsSending(true)
    setSendError(null)

    try {
      const newMsg = await postBuylistMessage(token, textToSend, submission?.customerEmail)
      setReplyText('')

      // Optimistically append the new message to state
      setSubmission((prev) => {
        if (!prev) return prev
        return {
          ...prev,
          messages: [...(prev.messages || []), newMsg],
        }
      })
    } catch (err) {
      setSendError(err instanceof Error ? err.message : 'Failed to send message.')
    } finally {
      setIsSending(false)
    }
  }

  // Copy tracking link helper
  const handleCopyLink = () => {
    navigator.clipboard.writeText(window.location.href).then(() => {
      setCopiedLink(true)
      setTimeout(() => setCopiedLink(false), 2500)
    })
  }

  // Helper for status badge styling and labels
  const getStatusBadge = (status: BuylistStatus) => {
    switch (status) {
      case 'PENDING':
        return <span className="tc-status-badge badge-pending">[PENDING REVIEW]</span>
      case 'UNDER_REVIEW':
        return <span className="tc-status-badge badge-review">[UNDER REVIEW]</span>
      case 'OFFERED':
        return <span className="tc-status-badge badge-offered">[OFFER MADE]</span>
      case 'ACCEPTED':
        return <span className="tc-status-badge badge-accepted">[OFFER ACCEPTED]</span>
      case 'REJECTED':
        return <span className="tc-status-badge badge-rejected">[OFFER DECLINED]</span>
      default:
        return <span className="tc-status-badge">{status}</span>
    }
  }

  // Helper for status description banner
  const getStatusDescription = (status: BuylistStatus) => {
    switch (status) {
      case 'PENDING':
        return 'Your card submission has been logged and queued for our appraisal team. You will receive an update shortly.'
      case 'UNDER_REVIEW':
        return 'Our card grading and appraisal specialists are currently inspecting your photos and evaluating recent market comps.'
      case 'OFFERED':
        return 'TailorCards has prepared an official cash valuation for your card! Check the message thread below to review the offer.'
      case 'ACCEPTED':
        return 'Offer accepted! Please check the message thread for shipping instructions and payout confirmation details.'
      case 'REJECTED':
        return 'This submission has been closed. Please see comments in the chat below for details.'
      default:
        return ''
    }
  }

  // Date formatting helper
  const formatDate = (isoString?: string) => {
    if (!isoString) return '—'
    try {
      const date = new Date(isoString)
      return date.toLocaleDateString(undefined, {
        year: 'numeric',
        month: 'short',
        day: 'numeric',
        hour: '2-digit',
        minute: '2-digit',
      })
    } catch {
      return isoString
    }
  }

  // ==================== LOADING STATE ====================
  if (loading && !submission) {
    return (
      <div className="tc-track-wrapper">
        <div className="tc-track-loading-card">
          <div className="tc-spinner large" />
          <h2>Retrieving Submission Details...</h2>
          <p>Connecting to TailorCards Appraisal System</p>
        </div>
      </div>
    )
  }

  // ==================== ERROR STATE ====================
  if (error && !submission) {
    return (
      <div className="tc-track-wrapper">
        <div className="tc-track-error-card">
          <div className="tc-track-error-icon">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" style={{ width: 24, height: 24 }}>
              <line x1="18" y1="6" x2="6" y2="18" />
              <line x1="6" y1="6" x2="18" y2="18" />
            </svg>
          </div>
          <h2>Submission Not Found</h2>
          <p>{error}</p>
          <div className="tc-track-error-actions">
            <button type="button" className="tc-primary-cta-btn" onClick={() => fetchSubmission(false)}>
              Retry
            </button>
            <Link to="/sell" className="tc-secondary-cta-btn">
              Submit a New Card
            </Link>
          </div>
        </div>
      </div>
    )
  }

  if (!submission) return null

  const photos = submission.imageUrls || []
  const messages = submission.messages || []

  return (
    <div className="tc-track-wrapper">
      {/* Top Breadcrumb & Link Header */}
      <div className="tc-track-top-bar">
        <Link to="/sell" className="tc-track-breadcrumb">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" className="tc-back-icon">
            <path strokeLinecap="round" strokeLinejoin="round" d="M19 12H5M12 19l-7-7 7-7" />
          </svg>
          <span>Sell to Us</span>
        </Link>
        <div className="tc-token-pill-wrap">
          <span className="tc-token-pill-label">Tracking ID:</span>
          <code className="tc-token-pill-code">{submission.trackingToken}</code>
          <button
            type="button"
            className={`tc-track-copy-btn ${copiedLink ? 'copied' : ''}`}
            onClick={handleCopyLink}
            title="Copy tracking link"
          >
            {copiedLink ? 'Link Copied' : 'Copy Tracking Link'}
          </button>
        </div>
      </div>

      {/* Main Submission Header Card */}
      <header className="tc-track-header-card">
        <div className="tc-track-header-main">
          <div className="tc-track-title-group">
            <div className="tc-track-status-line">
              {getStatusBadge(submission.status)}
              <span className="tc-track-date">Submitted {formatDate(submission.createdAt)}</span>
            </div>
            <h1 className="tc-track-card-title">{submission.cardName}</h1>
            {submission.cardSet && (
              <p className="tc-track-card-set">Set / Expansion: <strong>{submission.cardSet}</strong></p>
            )}
          </div>

          <div className="tc-track-price-box">
            <span className="tc-track-price-label">Asking Price</span>
            <span className="tc-track-price-val">
              {typeof submission.askingPrice === 'number' && submission.askingPrice > 0
                ? `$${submission.askingPrice.toFixed(2)} CAD`
                : 'Appraisal Request (No set price)'}
            </span>
          </div>
        </div>

        {/* Dynamic status helper */}
        <div className="tc-track-status-info">
          <div className="tc-pulse-dot" />
          <p>{getStatusDescription(submission.status)}</p>
        </div>
      </header>

      {/* Two-Column Grid: Left Details & Gallery, Right Chat Thread */}
      <div className="tc-track-layout-grid">
        {/* Left Section: Details & Photo Gallery */}
        <section className="tc-track-details-col">
          {/* Card & Customer Details Card */}
          <div className="tc-track-section-card">
            <h2 className="tc-track-card-heading">Card & Submitter Details</h2>

            <dl className="tc-details-list">
              <div className="tc-details-item">
                <dt>Card Name</dt>
                <dd>{submission.cardName}</dd>
              </div>

              {submission.cardSet && (
                <div className="tc-details-item">
                  <dt>Set / Expansion</dt>
                  <dd>{submission.cardSet}</dd>
                </div>
              )}

              <div className="tc-details-item">
                <dt>Customer Email</dt>
                <dd>{submission.customerEmail}</dd>
              </div>

              {submission.customerName && (
                <div className="tc-details-item">
                  <dt>Customer Name</dt>
                  <dd>{submission.customerName}</dd>
                </div>
              )}

              {submission.additionalComments && (
                <div className="tc-details-item full-width">
                  <dt>Condition Notes & Seller Comments</dt>
                  <dd className="tc-notes-block">{submission.additionalComments}</dd>
                </div>
              )}
            </dl>
          </div>

          {/* Photo Gallery Card */}
          <div className="tc-track-section-card">
            <div className="tc-gallery-header">
              <h2 className="tc-track-card-heading">Submitted Photos ({photos.length})</h2>
              <span className="tc-gallery-hint">Click any photo to open lightbox</span>
            </div>

            {photos.length === 0 ? (
              <div className="tc-no-photos">No photos attached to this submission.</div>
            ) : (
              <div className="tc-track-gallery-grid">
                {photos.map((url, idx) => (
                  <button
                    key={idx}
                    type="button"
                    className="tc-gallery-item-btn"
                    onClick={() => setActivePhotoIndex(idx)}
                    aria-label={`View photo ${idx + 1}`}
                  >
                    <img
                      src={resolveImageUrl(url)}
                      alt={`${submission.cardName} photo ${idx + 1}`}
                      className="tc-gallery-thumb"
                      loading="lazy"
                    />
                    <span className="tc-gallery-thumb-badge">Photo #{idx + 1}</span>
                    <div className="tc-gallery-hover-overlay">
                      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" className="tc-zoom-icon">
                        <circle cx="11" cy="11" r="8" />
                        <line x1="21" y1="21" x2="16.65" y2="16.65" />
                        <line x1="11" y1="8" x2="11" y2="14" />
                        <line x1="8" y1="11" x2="14" y2="11" />
                      </svg>
                      <span>Expand</span>
                    </div>
                  </button>
                ))}
              </div>
            )}
          </div>
        </section>

        {/* Right Section: Customer & Admin Chat Thread */}
        <section className="tc-track-chat-col">
          <div className="tc-chat-card">
            <div className="tc-chat-header">
              <div className="tc-chat-title-group">
                <div className="tc-team-avatar">TC</div>
                <div>
                  <h2 className="tc-chat-title">Conversation with TailorCards Team</h2>
                  <span className="tc-chat-status-sub">
                    <span className="tc-live-dot" />
                    Auto-refreshes every 8s
                  </span>
                </div>
              </div>
            </div>

            {/* Message Stream */}
            <div className="tc-chat-messages-container" aria-live="polite">
              {messages.length === 0 ? (
                <div className="tc-chat-empty-state">
                  <div className="tc-chat-empty-icon">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" style={{ width: 28, height: 28, color: '#64748b' }}>
                      <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z" />
                    </svg>
                  </div>
                  <h3>No messages yet</h3>
                  <p>
                    Our appraisal specialists are reviewing your submission.
                    Questions, valuations, or cash offers will appear here.
                    You can also send questions directly to our team below.
                  </p>
                </div>
              ) : (
                <div className="tc-chat-bubbles">
                  {messages.map((msg) => {
                    const isAdmin = msg.senderRole === 'ADMIN'

                    return (
                      <div
                        key={msg.id}
                        className={`tc-chat-bubble-wrap ${isAdmin ? 'admin-wrap' : 'customer-wrap'}`}
                      >
                        <div className="tc-chat-bubble-meta">
                          <span className={`tc-role-badge ${isAdmin ? 'role-admin' : 'role-customer'}`}>
                            {isAdmin ? 'TailorCards Team' : 'You'}
                          </span>
                          <time className="tc-chat-time">{formatDate(msg.createdAt)}</time>
                        </div>
                        <div className={`tc-chat-bubble ${isAdmin ? 'bubble-admin' : 'bubble-customer'}`}>
                          <p className="tc-bubble-text">{msg.message}</p>
                        </div>
                      </div>
                    )
                  })}
                  <div ref={messagesEndRef} />
                </div>
              )}
            </div>

            {/* Reply Input Box */}
            <form className="tc-chat-reply-form" onSubmit={handleSendMessage}>
              {sendError && <div className="tc-chat-send-error">{sendError}</div>}
              <div className="tc-chat-input-row">
                <textarea
                  rows={2}
                  className="tc-chat-input"
                  placeholder="Ask a question or reply to the team..."
                  value={replyText}
                  onChange={(e) => setReplyText(e.target.value)}
                  onKeyDown={(e) => {
                    if (e.key === 'Enter' && !e.shiftKey) {
                      e.preventDefault()
                      handleSendMessage(e)
                    }
                  }}
                  disabled={isSending}
                />
                <button
                  type="submit"
                  className="tc-chat-send-btn"
                  disabled={isSending || !replyText.trim()}
                >
                  {isSending ? (
                    <span className="tc-spinner small" />
                  ) : (
                    <>
                      <span>Send</span>
                      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" className="tc-send-icon">
                        <line x1="22" y1="2" x2="11" y2="13" />
                        <polygon points="22 2 15 22 11 13 2 9 22 2" />
                      </svg>
                    </>
                  )}
                </button>
              </div>
              <span className="tc-chat-hint">Press Enter to send, Shift + Enter for new line</span>
            </form>
          </div>
        </section>
      </div>

      {/* Expandable Lightbox / Photo Gallery Modal */}
      {activePhotoIndex !== null && photos.length > 0 && (
        <div
          className="tc-lightbox-backdrop"
          onClick={() => setActivePhotoIndex(null)}
          role="dialog"
          aria-modal="true"
          aria-label="Photo Lightbox"
        >
          <div className="tc-lightbox-container" onClick={(e) => e.stopPropagation()}>
            <button
              type="button"
              className="tc-lightbox-close-btn"
              onClick={() => setActivePhotoIndex(null)}
              aria-label="Close Lightbox"
            >
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" style={{ width: 16, height: 16 }}>
                <line x1="18" y1="6" x2="6" y2="18" />
                <line x1="6" y1="6" x2="18" y2="18" />
              </svg>
            </button>

            <div className="tc-lightbox-image-wrap">
              <img
                src={resolveImageUrl(photos[activePhotoIndex])}
                alt={`${submission.cardName} full view ${activePhotoIndex + 1}`}
                className="tc-lightbox-img"
              />
            </div>

            {/* Lightbox Footer Controls */}
            <div className="tc-lightbox-footer">
              <button
                type="button"
                className="tc-lightbox-nav-btn prev"
                onClick={() =>
                  setActivePhotoIndex((prev) =>
                    prev !== null && prev > 0 ? prev - 1 : photos.length - 1
                  )
                }
                aria-label="Previous Photo"
              >
                Previous
              </button>

              <span className="tc-lightbox-counter">
                Photo {activePhotoIndex + 1} of {photos.length}
              </span>

              <button
                type="button"
                className="tc-lightbox-nav-btn next"
                onClick={() =>
                  setActivePhotoIndex((prev) =>
                    prev !== null && prev < photos.length - 1 ? prev + 1 : 0
                  )
                }
                aria-label="Next Photo"
              >
                Next
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
