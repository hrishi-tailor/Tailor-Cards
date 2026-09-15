import { useState, useRef, useEffect } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { uploadBuylistImage, submitBuylist } from '../api/buylistApi'
import type { BuylistSubmission } from '../types'
import './SellBuylist.css'

interface SelectedFile {
  id: string
  file: File
  previewUrl: string
}

const MAX_FILE_SIZE = 10 * 1024 * 1024 // 10MB
const ALLOWED_TYPES = ['image/jpeg', 'image/png', 'image/webp']

export function SellBuylist() {
  const navigate = useNavigate()
  const fileInputRef = useRef<HTMLInputElement>(null)

  // Form Fields
  const [cardName, setCardName] = useState('')
  const [cardSet, setCardSet] = useState('')
  const [askingPrice, setAskingPrice] = useState('')
  const [customerName, setCustomerName] = useState('')
  const [customerEmail, setCustomerEmail] = useState('')
  const [additionalComments, setAdditionalComments] = useState('')

  // Files & Previews
  const [selectedFiles, setSelectedFiles] = useState<SelectedFile[]>([])
  const [isDragging, setIsDragging] = useState(false)

  // Status & Feedback
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [generalError, setGeneralError] = useState<string | null>(null)
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [uploadStatusText, setUploadStatusText] = useState('')
  const [submissionSuccess, setSubmissionSuccess] = useState<BuylistSubmission | null>(null)
  const [copiedLink, setCopiedLink] = useState(false)

  // Clean up object URLs on unmount
  useEffect(() => {
    return () => {
      selectedFiles.forEach((f) => URL.revokeObjectURL(f.previewUrl))
    }
  }, [selectedFiles])

  // Handle file addition & validation
  const handleFiles = (files: FileList | null) => {
    if (!files || files.length === 0) return

    setGeneralError(null)
    const newFiles: SelectedFile[] = []
    const rejectedReasons: string[] = []

    Array.from(files).forEach((file) => {
      if (!ALLOWED_TYPES.includes(file.type)) {
        rejectedReasons.push(`"${file.name}" is not a JPEG, PNG, or WEBP image.`)
        return
      }

      if (file.size > MAX_FILE_SIZE) {
        const sizeMb = (file.size / (1024 * 1024)).toFixed(1)
        rejectedReasons.push(`"${file.name}" is ${sizeMb}MB (max 10MB allowed).`)
        return
      }

      newFiles.push({
        id: `${file.name}-${file.size}-${Date.now()}-${Math.random()}`,
        file,
        previewUrl: URL.createObjectURL(file),
      })
    })

    if (rejectedReasons.length > 0) {
      setGeneralError(rejectedReasons.join(' '))
    }

    if (newFiles.length > 0) {
      setSelectedFiles((prev) => [...prev, ...newFiles])
    }
  }

  const handleRemoveFile = (idToRemove: string) => {
    setSelectedFiles((prev) => {
      const target = prev.find((f) => f.id === idToRemove)
      if (target) {
        URL.revokeObjectURL(target.previewUrl)
      }
      return prev.filter((f) => f.id !== idToRemove)
    })
  }

  // Drag & Drop Handlers
  const handleDragOver = (e: React.DragEvent) => {
    e.preventDefault()
    e.stopPropagation()
    setIsDragging(true)
  }

  const handleDragLeave = (e: React.DragEvent) => {
    e.preventDefault()
    e.stopPropagation()
    setIsDragging(false)
  }

  const handleDrop = (e: React.DragEvent) => {
    e.preventDefault()
    e.stopPropagation()
    setIsDragging(false)
    if (e.dataTransfer.files) {
      handleFiles(e.dataTransfer.files)
    }
  }

  // Form Validation
  const validateForm = (): boolean => {
    const errors: Record<string, string> = {}

    if (!cardName.trim()) {
      errors.cardName = 'Card name is required.'
    }

    const emailTrimmed = customerEmail.trim()
    if (!emailTrimmed) {
      errors.customerEmail = 'Email address is required.'
    } else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(emailTrimmed)) {
      errors.customerEmail = 'Please enter a valid email address.'
    }

    if (askingPrice.trim()) {
      const num = parseFloat(askingPrice)
      if (isNaN(num) || num < 0) {
        errors.askingPrice = 'Asking price must be a valid positive number.'
      }
    }

    setFieldErrors(errors)
    return Object.keys(errors).length === 0
  }

  // Handle Form Submission
  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setGeneralError(null)

    if (!validateForm()) {
      return
    }

    setIsSubmitting(true)
    setUploadStatusText('Preparing images...')

    try {
      // 1. Upload photos sequentially (or in parallel) to POST /api/buylist/upload
      const uploadedImageUrls: string[] = []
      const totalFiles = selectedFiles.length

      for (let i = 0; i < totalFiles; i++) {
        setUploadStatusText(`Uploading photo ${i + 1} of ${totalFiles}...`)
        const uploadedUrl = await uploadBuylistImage(selectedFiles[i].file)
        uploadedImageUrls.push(uploadedUrl)
      }

      // 2. Submit form to POST /api/buylist/submit
      setUploadStatusText('Submitting your card for valuation...')
      const numericPrice = askingPrice.trim() ? parseFloat(askingPrice) : undefined

      const submissionResponse = await submitBuylist({
        cardName: cardName.trim(),
        cardSet: cardSet.trim() || undefined,
        askingPrice: numericPrice,
        customerName: customerName.trim() || undefined,
        customerEmail: customerEmail.trim(),
        additionalComments: additionalComments.trim() || undefined,
        imageUrls: uploadedImageUrls,
      })

      setSubmissionSuccess(submissionResponse)
    } catch (err) {
      setGeneralError(err instanceof Error ? err.message : 'An unexpected error occurred while submitting.')
    } finally {
      setIsSubmitting(false)
      setUploadStatusText('')
    }
  }

  // Reset form to submit another card
  const handleResetForm = () => {
    selectedFiles.forEach((f) => URL.revokeObjectURL(f.previewUrl))
    setSelectedFiles([])
    setCardName('')
    setCardSet('')
    setAskingPrice('')
    setCustomerName('')
    setCustomerEmail('')
    setAdditionalComments('')
    setFieldErrors({})
    setGeneralError(null)
    setSubmissionSuccess(null)
    setCopiedLink(false)
  }

  const getTrackingUrl = (token: string) => {
    return `${window.location.origin}/sell/track/${token}`
  }

  const handleCopyLink = (token: string) => {
    const url = getTrackingUrl(token)
    navigator.clipboard.writeText(url).then(() => {
      setCopiedLink(true)
      setTimeout(() => setCopiedLink(false), 3000)
    })
  }

  // ==================== SUCCESS SCREEN ====================
  if (submissionSuccess) {
    const trackingUrl = getTrackingUrl(submissionSuccess.trackingToken)

    return (
      <div className="tc-sell-wrapper">
        <div className="tc-sell-success-card">
          <div className="tc-success-icon-wrap">
            <svg className="tc-success-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5">
              <path strokeLinecap="round" strokeLinejoin="round" d="M5 13l4 4L19 7" />
            </svg>
          </div>

          <span className="tc-success-pill">Submission Confirmed</span>
          <h1 className="tc-success-title">We Received Your Card Submission!</h1>
          <p className="tc-success-subtitle">
            Thank you! Our card appraisal team is reviewing <strong>{submissionSuccess.cardName}</strong>.
            We will provide a formal cash offer or valuation update directly in your customer portal.
          </p>

          {/* Bookmark Notice Callout */}
          <div className="tc-bookmark-notice">
            <div className="tc-bookmark-icon">📌</div>
            <div className="tc-bookmark-content">
              <strong>Important: Bookmark this page or save your tracking link!</strong>
              <p>
                Use this link anytime to check your valuation status, accept or decline offers, and chat directly
                with the TailorCards appraisal team.
              </p>
            </div>
          </div>

          {/* Tracking Token & Link Box */}
          <div className="tc-tracking-box">
            <div className="tc-tracking-meta-row">
              <span className="tc-tracking-label">Your Tracking Token:</span>
              <code className="tc-tracking-token-code">{submissionSuccess.trackingToken}</code>
            </div>

            <div className="tc-tracking-url-row">
              <input
                type="text"
                readOnly
                value={trackingUrl}
                className="tc-tracking-url-input"
                aria-label="Tracking URL"
              />
              <button
                type="button"
                className={`tc-copy-btn ${copiedLink ? 'copied' : ''}`}
                onClick={() => handleCopyLink(submissionSuccess.trackingToken)}
              >
                {copiedLink ? (
                  <>
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" className="tc-btn-icon">
                      <path strokeLinecap="round" strokeLinejoin="round" d="M5 13l4 4L19 7" />
                    </svg>
                    Link Copied!
                  </>
                ) : (
                  <>
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" className="tc-btn-icon">
                      <rect x="9" y="9" width="13" height="13" rx="2" ry="2" />
                      <path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1" />
                    </svg>
                    Copy Link
                  </>
                )}
              </button>
            </div>
          </div>

          {/* Action Buttons */}
          <div className="tc-success-actions">
            <button
              type="button"
              className="tc-primary-cta-btn"
              onClick={() => navigate(`/sell/track/${submissionSuccess.trackingToken}`)}
            >
              View Submission Status & Chat
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" className="tc-btn-icon">
                <path strokeLinecap="round" strokeLinejoin="round" d="M5 12h14M12 5l7 7-7 7" />
              </svg>
            </button>

            <button
              type="button"
              className="tc-secondary-cta-btn"
              onClick={handleResetForm}
            >
              Submit Another Card
            </button>
          </div>
        </div>
      </div>
    )
  }

  // ==================== SUBMISSION FORM ====================
  return (
    <div className="tc-sell-wrapper">
      {/* Hero Header */}
      <section className="tc-sell-hero">
        <div className="tc-sell-hero-content">
          <div className="tc-sell-badge">💎 Sell Directly to Tailor Cards</div>
          <h1 className="tc-sell-hero-title">Turn Your Cards into Instant Cash (CAD)</h1>
          <p className="tc-sell-hero-subtitle">
            We purchase high-end Pokémon, Magic: The Gathering, Lorcana singles, vintage holos, and graded slabs.
            Submit your photos for a fast, competitive valuation with insured shipping and zero hassle.
          </p>
        </div>
        <div className="tc-sell-hero-stats">
          <div className="tc-stat-card">
            <span className="tc-stat-number">24-48h</span>
            <span className="tc-stat-label">Fast Offer Turnaround</span>
          </div>
          <div className="tc-stat-card">
            <span className="tc-stat-number">100%</span>
            <span className="tc-stat-label">CAD Cash or Credit</span>
          </div>
        </div>
      </section>

      {/* Visual Guidelines & Tips Banner */}
      <section className="tc-guidelines-banner">
        <div className="tc-guidelines-header">
          <div className="tc-guidelines-icon-badge">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" className="tc-camera-svg">
              <path d="M23 19a2 2 0 0 1-2 2H3a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h4l2-3h6l2 3h4a2 2 0 0 1 2 2z" />
              <circle cx="12" cy="13" r="4" />
            </svg>
          </div>
          <div>
            <h2 className="tc-guidelines-title">Photo Requirements & Grading Tips</h2>
            <p className="tc-guidelines-subtitle">
              High-clarity photos enable our appraisal team to offer top dollar for your cards.
            </p>
          </div>
        </div>

        <div className="tc-tips-grid">
          <div className="tc-tip-item">
            <span className="tc-tip-check">✓</span>
            <p><strong>Take clear, well-lit, close-up photos outside of binders/sleeves.</strong></p>
          </div>
          <div className="tc-tip-item">
            <span className="tc-tip-check">✓</span>
            <p><strong>Include front, back, all 4 corners, and any surface scratches, creases, or imperfections.</strong></p>
          </div>
          <div className="tc-tip-item">
            <span className="tc-tip-check">✓</span>
            <p>Use a clean, non-reflective dark background to highlight centering and edge whitening.</p>
          </div>
        </div>
      </section>

      {/* Main Form Container */}
      <form className="tc-sell-form" onSubmit={handleSubmit} noValidate>
        {generalError && (
          <div className="tc-sell-error-banner" role="alert">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" className="tc-alert-svg">
              <circle cx="12" cy="12" r="10" />
              <line x1="12" y1="8" x2="12" y2="12" />
              <line x1="12" y1="16" x2="12.01" y2="16" />
            </svg>
            <span>{generalError}</span>
          </div>
        )}

        <div className="tc-sell-form-grid">
          {/* Left Column: Card & Contact Details */}
          <div className="tc-form-column">
            <div className="tc-form-section-card">
              <h2 className="tc-section-title">1. Card Information</h2>

              {/* Card Name */}
              <div className="tc-input-group">
                <label htmlFor="cardName" className="tc-input-label">
                  Card Name <span className="tc-required">*</span>
                </label>
                <input
                  id="cardName"
                  type="text"
                  className={`tc-text-input ${fieldErrors.cardName ? 'tc-input-error' : ''}`}
                  placeholder="e.g. Charizard Base Set Holo #4/102"
                  value={cardName}
                  onChange={(e) => {
                    setCardName(e.target.value)
                    if (fieldErrors.cardName) {
                      setFieldErrors((prev) => ({ ...prev, cardName: '' }))
                    }
                  }}
                  disabled={isSubmitting}
                />
                {fieldErrors.cardName && <span className="tc-field-error">{fieldErrors.cardName}</span>}
              </div>

              {/* Set / Expansion */}
              <div className="tc-input-group">
                <label htmlFor="cardSet" className="tc-input-label">
                  Set / Expansion <span className="tc-optional">(Optional)</span>
                </label>
                <input
                  id="cardSet"
                  type="text"
                  className="tc-text-input"
                  placeholder="e.g. 1999 Base Set / Scarlet & Violet 151"
                  value={cardSet}
                  onChange={(e) => setCardSet(e.target.value)}
                  disabled={isSubmitting}
                />
              </div>

              {/* Asking Price */}
              <div className="tc-input-group">
                <label htmlFor="askingPrice" className="tc-input-label">
                  Asking Price (CAD) <span className="tc-optional">(Optional)</span>
                </label>
                <div className="tc-price-input-wrap">
                  <span className="tc-currency-symbol">$</span>
                  <input
                    id="askingPrice"
                    type="number"
                    step="0.01"
                    min="0"
                    className={`tc-text-input tc-price-input ${fieldErrors.askingPrice ? 'tc-input-error' : ''}`}
                    placeholder="0.00"
                    value={askingPrice}
                    onChange={(e) => {
                      setAskingPrice(e.target.value)
                      if (fieldErrors.askingPrice) {
                        setFieldErrors((prev) => ({ ...prev, askingPrice: '' }))
                      }
                    }}
                    disabled={isSubmitting}
                  />
                  <span className="tc-currency-code">CAD</span>
                </div>
                {fieldErrors.askingPrice && <span className="tc-field-error">{fieldErrors.askingPrice}</span>}
                <span className="tc-helper-text">Leave blank if you'd prefer our appraisal team to evaluate and make an offer first.</span>
              </div>

              {/* Additional Comments / Condition Notes */}
              <div className="tc-input-group">
                <label htmlFor="additionalComments" className="tc-input-label">
                  Condition Notes & Comments <span className="tc-optional">(Optional)</span>
                </label>
                <textarea
                  id="additionalComments"
                  rows={4}
                  className="tc-textarea"
                  placeholder="Describe condition details (e.g., Near Mint, light silvering on top edge, slight binder indent on back, or if you prefer store credit bonus)..."
                  value={additionalComments}
                  onChange={(e) => setAdditionalComments(e.target.value)}
                  disabled={isSubmitting}
                />
              </div>
            </div>

            {/* Customer Contact Section */}
            <div className="tc-form-section-card">
              <h2 className="tc-section-title">2. Your Contact Information</h2>

              {/* Customer Email */}
              <div className="tc-input-group">
                <label htmlFor="customerEmail" className="tc-input-label">
                  Email Address <span className="tc-required">*</span>
                </label>
                <input
                  id="customerEmail"
                  type="email"
                  className={`tc-text-input ${fieldErrors.customerEmail ? 'tc-input-error' : ''}`}
                  placeholder="you@example.com"
                  value={customerEmail}
                  onChange={(e) => {
                    setCustomerEmail(e.target.value)
                    if (fieldErrors.customerEmail) {
                      setFieldErrors((prev) => ({ ...prev, customerEmail: '' }))
                    }
                  }}
                  disabled={isSubmitting}
                />
                {fieldErrors.customerEmail && <span className="tc-field-error">{fieldErrors.customerEmail}</span>}
                <span className="tc-helper-text">We'll use this to notify you when an appraisal or offer is posted.</span>
              </div>

              {/* Customer Name */}
              <div className="tc-input-group">
                <label htmlFor="customerName" className="tc-input-label">
                  Full Name <span className="tc-optional">(Optional)</span>
                </label>
                <input
                  id="customerName"
                  type="text"
                  className="tc-text-input"
                  placeholder="e.g. John Doe"
                  value={customerName}
                  onChange={(e) => setCustomerName(e.target.value)}
                  disabled={isSubmitting}
                />
              </div>
            </div>
          </div>

          {/* Right Column: Photo Upload Section */}
          <div className="tc-form-column">
            <div className="tc-form-section-card tc-photo-section">
              <div className="tc-section-title-wrap">
                <h2 className="tc-section-title">3. Upload Card Photos</h2>
                <span className="tc-photo-counter-pill">
                  {selectedFiles.length} {selectedFiles.length === 1 ? 'photo' : 'photos'} selected
                </span>
              </div>

              {/* Drag and Drop Zone */}
              <div
                className={`tc-dropzone ${isDragging ? 'dragging' : ''} ${isSubmitting ? 'disabled' : ''}`}
                onDragOver={handleDragOver}
                onDragLeave={handleDragLeave}
                onDrop={handleDrop}
                onClick={() => !isSubmitting && fileInputRef.current?.click()}
                role="button"
                tabIndex={0}
                onKeyDown={(e) => {
                  if (e.key === 'Enter' || e.key === ' ') {
                    e.preventDefault()
                    fileInputRef.current?.click()
                  }
                }}
              >
                <input
                  ref={fileInputRef}
                  type="file"
                  multiple
                  accept="image/jpeg,image/png,image/webp"
                  className="tc-file-hidden-input"
                  onChange={(e) => handleFiles(e.target.files)}
                  disabled={isSubmitting}
                />

                <div className="tc-dropzone-icon-wrap">
                  <svg className="tc-dropzone-svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8">
                    <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4" />
                    <polyline points="17 8 12 3 7 8" />
                    <line x1="12" y1="3" x2="12" y2="15" />
                  </svg>
                </div>

                <p className="tc-dropzone-primary-text">
                  <strong>Click to browse files</strong> or drag and drop images here
                </p>
                <p className="tc-dropzone-secondary-text">
                  Supports JPEG, PNG, WEBP — up to 10MB each
                </p>
              </div>

              {/* Selected Thumbnails Grid */}
              {selectedFiles.length > 0 && (
                <div className="tc-thumbnails-container">
                  <span className="tc-thumbnails-header">Selected Photos Preview:</span>
                  <div className="tc-thumbnails-grid">
                    {selectedFiles.map((item, index) => (
                      <div key={item.id} className="tc-thumbnail-card">
                        <div className="tc-thumbnail-img-wrap">
                          <img src={item.previewUrl} alt={`Card preview ${index + 1}`} className="tc-thumbnail-img" />
                          <span className="tc-thumbnail-badge">#{index + 1}</span>
                          <button
                            type="button"
                            className="tc-thumbnail-remove-btn"
                            onClick={(e) => {
                              e.stopPropagation()
                              handleRemoveFile(item.id)
                            }}
                            disabled={isSubmitting}
                            title="Remove photo"
                            aria-label={`Remove photo ${item.file.name}`}
                          >
                            ✕
                          </button>
                        </div>
                        <div className="tc-thumbnail-info">
                          <span className="tc-thumbnail-filename" title={item.file.name}>
                            {item.file.name}
                          </span>
                          <span className="tc-thumbnail-size">
                            {(item.file.size / (1024 * 1024)).toFixed(2)} MB
                          </span>
                        </div>
                      </div>
                    ))}
                  </div>
                </div>
              )}

              {/* Submission Submit Section */}
              <div className="tc-submit-box">
                {isSubmitting ? (
                  <div className="tc-upload-progress-box">
                    <div className="tc-spinner" />
                    <div className="tc-progress-text-group">
                      <span className="tc-progress-title">Uploading & Submitting</span>
                      <span className="tc-progress-detail">{uploadStatusText}</span>
                    </div>
                  </div>
                ) : (
                  <button type="submit" className="tc-submit-buylist-btn">
                    Submit Card for Valuation
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" className="tc-btn-icon">
                      <path strokeLinecap="round" strokeLinejoin="round" d="M5 12h14M12 5l7 7-7 7" />
                    </svg>
                  </button>
                )}

                <p className="tc-submit-note">
                  🔒 No obligation to sell. All offers are valid for 7 days upon delivery of our appraisal.
                </p>
              </div>
            </div>
          </div>
        </div>
      </form>

      {/* Return to store footer link */}
      <div className="tc-sell-footer-nav">
        <Link to="/" className="tc-back-store-link">
          ← Return to Cards Catalog
        </Link>
      </div>
    </div>
  )
}
