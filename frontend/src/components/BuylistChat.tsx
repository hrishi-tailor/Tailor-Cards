import { useCallback, useEffect, useRef, useState } from 'react'
import type { FormEvent } from 'react'
import { Link } from 'react-router-dom'
import {
  ChatApiError,
  buylistChatApi,
  getSessionToken,
  setSessionToken,
} from '../api/buylistChatApi'
import type { ChatDraft, ChatLine, ChatStatus, ConfirmResult } from '../api/buylistChatApi'
import { BuylistDealPanel } from './BuylistDealPanel'
import { CardScanner } from './CardScanner'
import { makeMoney } from './buylistMoney'
import { ChatText } from './ChatText'
import type { Money } from './buylistMoney'
import './BuylistChat.css'

declare global {
  interface Window {
    turnstile?: {
      render: (el: HTMLElement, opts: { sitekey: string; callback: (token: string) => void }) => string
      reset: (id?: string) => void
    }
  }
}

type Step = 'start' | 'email' | 'code' | 'workspace' | 'done'

const STATUS_LABELS: Record<string, string> = {
  PENDING: 'Checking',
  ELIGIBLE: 'Eligible',
  BELOW_MINIMUM: 'Below minimum',
  NEEDS_PHOTO: 'Needs photo',
  NEEDS_REVIEW: 'Needs review',
  UNIDENTIFIED: 'Unidentified',
}

const CONDITIONS = ['NM', 'LP+', 'LP', 'MP', 'HP', 'DMG', 'UNKNOWN']
const GRADERS = ['PSA', 'BGS', 'CGC', 'SGC', 'TAG', 'ACE']
const GRADES = ['10', '9.5', '9', '8.5', '8', '7.5', '7', '6.5', '6', '5', '4', '3', '2', '1']
const SPECIAL_TENS: Record<string, string> = { BGS: '10 Black Label', CGC: '10 Pristine' }
const MIN_GRADED_SALES = 3
const DEFAULT_DISCLAIMER = 'This percentage is an estimated guess at how likely we are to accept your request. '
  + 'It is not a guaranteed price or a guaranteed acceptance: final offers are confirmed after we inspect your cards.'

/** "PSA 10" -> ["PSA", "10"]; "BGS 10 Black Label" -> ["BGS", "10 Black Label"]; raw -> ["", ""]. */
function splitGrading(grading: string | null): [string, string] {
  if (!grading) return ['', '']
  const i = grading.indexOf(' ')
  return i < 0 ? [grading, ''] : [grading.slice(0, i), grading.slice(i + 1)]
}

export function BuylistChat({ status }: { status: ChatStatus }) {
  const verificationOn = status.emailVerificationRequired
  const firstStep: Step = verificationOn ? 'email' : 'start'
  const [step, setStep] = useState<Step>(getSessionToken() ? 'workspace' : firstStep)
  const [email, setEmail] = useState('')
  const [code, setCode] = useState('')
  const [turnstileToken, setTurnstileToken] = useState<string | undefined>()
  const [cooldown, setCooldown] = useState(0)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const [draft, setDraft] = useState<ChatDraft | null>(null)
  const [message, setMessage] = useState('')
  const [chatBusy, setChatBusy] = useState(false)
  const [menuOpen, setMenuOpen] = useState(false)
  const [scanner, setScanner] = useState<{ file: File | null } | null>(null)
  const [customerName, setCustomerName] = useState('')
  const [notes, setNotes] = useState('')
  const [result, setResult] = useState<ConfirmResult | null>(null)

  const money = makeMoney(draft?.summary.deal?.usdCadRate)
  const turnstileRef = useRef<HTMLDivElement>(null)
  const messagesEndRef = useRef<HTMLDivElement>(null)
  const menuRef = useRef<HTMLDivElement>(null)
  const photoInputRef = useRef<HTMLInputElement>(null)
  const csvInputRef = useRef<HTMLInputElement>(null)
  const composerRef = useRef<HTMLTextAreaElement>(null)

  const fail = useCallback((err: unknown) => {
    if (err instanceof ChatApiError && err.status === 401) {
      setStep(firstStep)
      setDraft(null)
    }
    setError(err instanceof Error ? err.message : 'Something went wrong.')
  }, [firstStep])

  // Cloudflare Turnstile widget, only when the server reports a site key
  useEffect(() => {
    if ((step !== 'email' && step !== 'start') || !status.turnstileSiteKey || !turnstileRef.current) return
    const el = turnstileRef.current
    const render = () => window.turnstile?.render(el, { sitekey: status.turnstileSiteKey!, callback: setTurnstileToken })
    if (window.turnstile) {
      render()
      return
    }
    const script = document.createElement('script')
    script.src = 'https://challenges.cloudflare.com/turnstile/v0/api.js'
    script.async = true
    script.onload = render
    document.head.appendChild(script)
  }, [step, status.turnstileSiteKey])

  // Verification off: start an anonymous session (after the Turnstile check when configured)
  const startingGuest = useRef(false)
  useEffect(() => {
    if (step !== 'start' || startingGuest.current) return
    if (status.turnstileSiteKey && !turnstileToken) return
    startingGuest.current = true
    buylistChatApi.guestSession(turnstileToken)
      .then(() => setStep('workspace'))
      .catch(fail)
      .finally(() => { startingGuest.current = false })
  }, [step, turnstileToken, status.turnstileSiteKey, fail])

  useEffect(() => {
    if (cooldown <= 0) return
    const timer = setTimeout(() => setCooldown((c) => c - 1), 1000)
    return () => clearTimeout(timer)
  }, [cooldown])

  // Open (or resume) the draft once signed in
  useEffect(() => {
    if (step !== 'workspace' || draft) return
    buylistChatApi.openDraft().then(setDraft).catch(fail)
  }, [step, draft, fail])

  // Poll while cards are being checked
  const pending = draft?.progress.pending ?? 0
  const draftId = draft?.draftId
  useEffect(() => {
    if (!draftId || pending === 0) return
    const timer = setTimeout(() => {
      buylistChatApi.getDraft(draftId).then(setDraft).catch(fail)
    }, 1500)
    return () => clearTimeout(timer)
  }, [draftId, pending, draft, fail])

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ block: 'end' })
  }, [draft?.messages.length])

  // Close the + menu on an outside click or Escape
  useEffect(() => {
    if (!menuOpen) return
    const onClick = (e: MouseEvent) => {
      if (!menuRef.current?.contains(e.target as Node)) setMenuOpen(false)
    }
    const onKey = (e: KeyboardEvent) => { if (e.key === 'Escape') setMenuOpen(false) }
    document.addEventListener('mousedown', onClick)
    document.addEventListener('keydown', onKey)
    return () => {
      document.removeEventListener('mousedown', onClick)
      document.removeEventListener('keydown', onKey)
    }
  }, [menuOpen])

  // Grow the composer with its text, up to a few lines
  useEffect(() => {
    const el = composerRef.current
    if (!el) return
    el.style.height = 'auto'
    el.style.height = `${Math.min(el.scrollHeight, 200)}px`
  }, [message])

  const sendCode = async (e?: FormEvent) => {
    e?.preventDefault()
    setBusy(true)
    setError(null)
    try {
      await buylistChatApi.requestCode(email, turnstileToken)
      setStep('code')
      setCooldown(60)
    } catch (err) {
      fail(err)
    } finally {
      setBusy(false)
    }
  }

  const verify = async (e: FormEvent) => {
    e.preventDefault()
    setBusy(true)
    setError(null)
    try {
      await buylistChatApi.verifyCode(email, code.trim())
      setStep('workspace')
    } catch (err) {
      fail(err)
    } finally {
      setBusy(false)
    }
  }

  const run = async (action: () => Promise<ChatDraft>) => {
    setError(null)
    try {
      setDraft(await action())
    } catch (err) {
      fail(err)
    }
  }

  const sendChat = async (e?: FormEvent) => {
    e?.preventDefault()
    if (!draft || !message.trim() || chatBusy) return
    const text = message.trim()
    setMessage('')
    setChatBusy(true)
    setError(null)
    if (text.length > status.maxMessageLength) {
      // A long pasted list: add it line by line instead of sending it to the assistant
      try {
        if (text.includes('\n')) setDraft(await buylistChatApi.paste(draft.draftId, text))
        else setError(`That message is too long (${status.maxMessageLength} characters max).`)
      } catch (err) {
        fail(err)
      } finally {
        setChatBusy(false)
      }
      return
    }
    // Show the customer's message right away
    setDraft({ ...draft, messages: [...draft.messages, { role: 'CUSTOMER', content: text, createdAt: new Date().toISOString() }] })
    try {
      const turn = await buylistChatApi.sendMessage(draft.draftId, text)
      setDraft(turn.draft)
    } catch (err) {
      fail(err)
      buylistChatApi.getDraft(draft.draftId).then(setDraft).catch(() => undefined)
    } finally {
      setChatBusy(false)
    }
  }

  const uploadCsv = async (file: File | undefined) => {
    if (!draft || !file) return
    setBusy(true)
    await run(() => buylistChatApi.uploadCsv(draft.draftId, file))
    setBusy(false)
  }

  const confirm = async () => {
    if (!draft) return
    setBusy(true)
    setError(null)
    try {
      setResult(await buylistChatApi.confirm(draft.draftId, draft.summary.contentHash, customerName, notes,
        verificationOn ? undefined : email.trim()))
      setStep('done')
    } catch (err) {
      if (err instanceof ChatApiError && err.status === 409) {
        buylistChatApi.getDraft(draft.draftId).then(setDraft).catch(() => undefined)
      }
      fail(err)
    } finally {
      setBusy(false)
    }
  }

  const signOut = () => {
    setSessionToken(null)
    setDraft(null)
    setStep(firstStep)
  }

  if (step === 'done' && result) {
    return (
      <div className="tc-bc-container">
        <section className="tc-bc-panel tc-bc-done">
          <h1 className="tc-bc-title">List submitted</h1>
          <p>We'll review it and email you. Every submission is checked by a person.</p>
          <p className="tc-bc-muted">{result.quoteNotice}</p>
          <div className="tc-bc-actions">
            <Link to={`/sell/track/${result.trackingToken}`} className="tc-bc-btn tc-bc-btn-gold">Track your submission</Link>
            <Link to="/" className="tc-bc-btn tc-bc-btn-ghost">Back to the store</Link>
          </div>
        </section>
      </div>
    )
  }

  return (
    <div className="tc-bc-container">
      <header className="tc-bc-header">
        <span className="tc-eyebrow">Sell or trade</span>
        <h1 className="tc-bc-title">Sell your cards</h1>
        <ol className="tc-bc-how">
          <li><b>List your cards</b> in the chat, or tap + to scan a card or upload a CSV</li>
          <li><b>See our offer</b> at our published rates</li>
          <li><b>Submit</b> and we'll review it and email you</li>
        </ol>
        <p className="tc-bc-muted">Prices are market references in CAD, not offers. Every submission is checked by a person.</p>
      </header>

      {error && <div className="tc-bc-alert" role="alert">{error}</div>}

      {step === 'start' && (
        <div className="tc-bc-panel tc-bc-narrow">
          <h2>Starting your list…</h2>
          {status.turnstileSiteKey && <div ref={turnstileRef} className="tc-bc-turnstile" />}
          {!status.turnstileSiteKey && <p className="tc-bc-muted">One moment.</p>}
        </div>
      )}

      {step === 'email' && (
        <form className="tc-bc-panel tc-bc-narrow" onSubmit={sendCode}>
          <h2>Verify your email</h2>
          <p className="tc-bc-muted">We'll send a 6-digit code. No account needed.</p>
          <label className="tc-bc-field">
            <span>Email</span>
            <input type="email" autoComplete="email" required value={email} onChange={(e) => setEmail(e.target.value)} />
          </label>
          {status.turnstileSiteKey && <div ref={turnstileRef} className="tc-bc-turnstile" />}
          <button type="submit" className="tc-bc-btn tc-bc-btn-gold" disabled={busy || !email}>
            {busy ? 'Sending…' : 'Send code'}
          </button>
        </form>
      )}

      {step === 'code' && (
        <form className="tc-bc-panel tc-bc-narrow" onSubmit={verify}>
          <h2>Enter your code</h2>
          <p className="tc-bc-muted">Sent to {email}. It expires in 10 minutes.</p>
          <label className="tc-bc-field">
            <span>6-digit code</span>
            <input inputMode="numeric" autoComplete="one-time-code" maxLength={6} pattern="\d{6}" required
              value={code} onChange={(e) => setCode(e.target.value.replace(/\D/g, ''))} />
          </label>
          <div className="tc-bc-actions">
            <button type="submit" className="tc-bc-btn tc-bc-btn-gold" disabled={busy || code.length !== 6}>
              {busy ? 'Checking…' : 'Verify'}
            </button>
            <button type="button" className="tc-bc-btn tc-bc-btn-ghost" disabled={busy || cooldown > 0} onClick={() => sendCode()}>
              {cooldown > 0 ? `Resend in ${cooldown}s` : 'Resend code'}
            </button>
            <button type="button" className="tc-bc-link" onClick={() => setStep('email')}>Use another email</button>
          </div>
        </form>
      )}

      {step === 'workspace' && !draft && <div className="tc-bc-panel tc-bc-muted">Loading your list…</div>}

      {step === 'workspace' && draft && (
        <div className="tc-bc-workspace">
          <section className="tc-bc-panel tc-bc-chat">
            {scanner ? (
              <CardScanner draftId={draft.draftId} money={money} initialFile={scanner.file} onDraft={setDraft}
                onClose={() => setScanner(null)} />
            ) : (
              <div className="tc-bc-messages" aria-live="polite">
                {draft.messages.length === 0 && (
                  <div className="tc-bc-empty">
                    <p>Tell me what you have, or paste your whole list.</p>
                    <p className="tc-bc-muted">Try: "Base Set Charizard 4/102 and 3 Pikachu from 151".
                      {status.scannerEnabled ? ' Tap + to scan a card with your camera.' : ''}</p>
                  </div>
                )}
                {draft.messages.map((m, i) => (
                  <div key={i} className={`tc-bc-msg ${m.role === 'CUSTOMER' ? 'mine' : 'theirs'}`}>
                    {m.role === 'CUSTOMER' ? m.content : <ChatText text={m.content} />}
                  </div>
                ))}
                {chatBusy && <div className="tc-bc-msg theirs tc-bc-typing">Thinking…</div>}
                <div ref={messagesEndRef} />
              </div>
            )}

            <form className="tc-bc-composer" onSubmit={sendChat}>
              <div className="tc-bc-plus" ref={menuRef}>
                <button type="button" className="tc-bc-plus-btn" aria-label="Add photos and files" aria-haspopup="menu"
                  aria-expanded={menuOpen} onClick={() => setMenuOpen((o) => !o)} disabled={busy}>
                  <svg viewBox="0 0 24 24" width="20" height="20" aria-hidden="true"><path d="M12 5v14M5 12h14" stroke="currentColor" strokeWidth="2" strokeLinecap="round" /></svg>
                </button>
                {menuOpen && (
                  <div className="tc-bc-menu" role="menu">
                    {status.scannerEnabled && (
                      <>
                        <button type="button" role="menuitem" onClick={() => { setMenuOpen(false); setScanner({ file: null }) }}>
                          <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M4 8a2 2 0 0 1 2-2h2l1.5-2h5L16 6h2a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2z" /><circle cx="12" cy="12.5" r="3.5" /></svg>
                          <span>Scan a card<small>Use your camera</small></span>
                        </button>
                        <button type="button" role="menuitem" onClick={() => { setMenuOpen(false); photoInputRef.current?.click() }}>
                          <svg viewBox="0 0 24 24" aria-hidden="true"><rect x="4" y="4" width="16" height="16" rx="2" /><circle cx="9" cy="9.5" r="1.5" /><path d="m20 15-4.5-4.5L6 20" /></svg>
                          <span>Upload a card<small>A photo from your device</small></span>
                        </button>
                      </>
                    )}
                    <button type="button" role="menuitem" onClick={() => { setMenuOpen(false); csvInputRef.current?.click() }}>
                      <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M14 3H6a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V9z" /><path d="M14 3v6h6M8 13h8M8 17h8" /></svg>
                      <span>Upload CSV<small>name, set, number, quantity, condition, grading</small></span>
                    </button>
                  </div>
                )}
                <input ref={photoInputRef} type="file" accept="image/jpeg,image/png,image/webp" hidden
                  onChange={(e) => { const f = e.target.files?.[0]; if (f) setScanner({ file: f }); e.target.value = '' }} />
                <input ref={csvInputRef} type="file" accept=".csv,text/csv" hidden
                  onChange={(e) => { uploadCsv(e.target.files?.[0]); e.target.value = '' }} />
              </div>
              <textarea ref={composerRef} rows={1} value={message} placeholder="Ask about a card, or paste your list"
                aria-label="Message" disabled={chatBusy}
                onChange={(e) => setMessage(e.target.value)}
                onKeyDown={(e) => {
                  if (e.key === 'Enter' && !e.shiftKey && !e.nativeEvent.isComposing) { e.preventDefault(); sendChat() }
                }} />
              <button type="submit" className="tc-bc-send" aria-label="Send" disabled={chatBusy || !message.trim()}>
                <svg viewBox="0 0 24 24" width="18" height="18" aria-hidden="true"><path d="M12 19V5M5 12l7-7 7 7" stroke="currentColor" strokeWidth="2.2" fill="none" strokeLinecap="round" strokeLinejoin="round" /></svg>
              </button>
            </form>
            {busy && <p className="tc-bc-muted tc-bc-uploading">Uploading…</p>}
          </section>

          <section className="tc-bc-panel tc-bc-list">
            <div className="tc-bc-list-head">
              <h2>Your list <span className="tc-bc-muted">({draft.progress.total}/{draft.maxLines})</span></h2>
              <button type="button" className="tc-bc-link" onClick={signOut}>{verificationOn ? 'Sign out' : 'Start over'}</button>
            </div>
            {pending > 0 && (
              <div className="tc-bc-progress" aria-label="Checking cards">
                <div style={{ width: `${Math.round(((draft.progress.total - pending) / draft.progress.total) * 100)}%` }} />
                <span>Checking cards… {draft.progress.total - pending}/{draft.progress.total}</span>
              </div>
            )}
            {draft.lines.length === 0 ? (
              <p className="tc-bc-muted">No cards yet. Tell the assistant what you have, or tap + to scan a card.</p>
            ) : (
              <ul className="tc-bc-lines">
                {draft.lines.map((line) => (
                  <LineRow key={line.id} line={line} money={money} askable={(draft.summary.deal?.dealType ?? 'SELL') === 'SELL'}
                    onChange={(changes) => run(() => buylistChatApi.updateLine(draft.draftId, line.id, changes))}
                    onRemove={() => run(() => buylistChatApi.removeLine(draft.draftId, line.id))}
                    onPhoto={(file) => run(() => buylistChatApi.uploadPhoto(draft.draftId, line.id, file))} />
                ))}
              </ul>
            )}

            {draft.lines.length > 0 && draft.summary.deal && (
              <BuylistDealPanel draft={draft} deal={draft.summary.deal} money={money} onDraft={setDraft} onError={fail} />
            )}

            {draft.lines.length > 0 && (
              <SummaryCard draft={draft} money={money} busy={busy} customerName={customerName} notes={notes}
                onName={setCustomerName} onNotes={setNotes} onConfirm={confirm}
                askEmail={!verificationOn} email={email} onEmail={setEmail} />
            )}
          </section>

          {draft.lines.length > 0 && (
            <div className="tc-bc-sticky" aria-hidden="true">
              <div>
                <strong>{money.fmt(draft.summary.deal?.dealType === 'TRADE' ? draft.summary.deal?.tradeCreditUsd
                  : draft.summary.deal?.cashOfferUsd, '—')}</strong>
                <span>{Math.max(0, Math.min(100, draft.summary.likelihoodPct))}% estimated approval</span>
              </div>
              <button type="button" className="tc-button tc-button-solid" tabIndex={-1}
                onClick={() => document.getElementById('tc-bc-summary')?.scrollIntoView({ behavior: 'smooth' })}>
                Review and submit
              </button>
            </div>
          )}
        </div>
      )}
    </div>
  )
}

function LineRow({ line, money, askable, onChange, onRemove, onPhoto }: {
  line: ChatLine
  money: Money
  askable: boolean
  onChange: (changes: { quantity?: number; condition?: string; requestedUnitUsd?: number; grading?: string }) => void
  onRemove: () => void
  onPhoto: (file: File) => void
}) {
  const [ask, setAsk] = useState(line.requestedUnitUsd != null ? String(money.toDisplay(line.requestedUnitUsd)) : '')
  const title = line.matchedName ?? line.name
  const detail = [line.matchedSet ?? line.setName, line.matchedNumber ?? line.cardNumber, line.variant].filter(Boolean).join(' · ')
  const [grader, grade] = splitGrading(line.grading)
  const photoSuggested = (line.kind === 'CARD' || line.kind === 'SEALED') && line.photoRequired && !line.photoUrl
  const thinSales = line.priceBasis === 'GRADED' && (line.priceSampleSize ?? 0) < MIN_GRADED_SALES
  const priceTitle = line.kind === 'SEALED'
    ? (line.eurTrend ? `Cardmarket 30-day average: €${line.eurTrend}` : 'Cardmarket reference')
    : line.priceBasis === 'GRADED'
      ? `Median of ${line.priceSampleSize ?? 'a few'} recent eBay sales for ${line.grading}`
      : line.priceSource === 'CARDMARKET'
        ? `No TCGplayer price: Cardmarket 30-day average${line.eurTrend ? ` (€${line.eurTrend})` : ''}, converted to USD`
        : line.priceUpdatedAt ? `TCGplayer market via TCGdex, updated ${line.priceUpdatedAt.slice(0, 10)}` : undefined
  return (
    <li className={`tc-bc-line status-${line.status.toLowerCase()}`}>
      {line.imageUrl ? <img src={line.imageUrl} alt="" loading="lazy" /> : <div className="tc-bc-line-thumb" />}
      <div className="tc-bc-line-main">
        <strong>{line.kind === 'BULK' ? `Bulk: ${line.name}` : title}</strong>
        {detail && <span className="tc-bc-muted">{detail}</span>}
        {line.grading && <span className="tc-bc-grade">{line.grading}</span>}
        {line.kind === 'SEALED' && <span className="tc-bc-grade">Sealed</span>}
        <span className={`tc-bc-chip chip-${line.status.toLowerCase()}`} title={line.statusReason}>
          {STATUS_LABELS[line.status] ?? line.status}
        </span>
        {line.status !== 'ELIGIBLE' && line.status !== 'PENDING' && <span className="tc-bc-reason">{line.statusReason}</span>}
        {photoSuggested && <span className="tc-bc-reason">Photo optional: adding one can raise your estimate</span>}
        {thinSales && <span className="tc-bc-reason">Few recent sales for this grade, so we'll double-check the price</span>}
      </div>
      <div className="tc-bc-line-side">
        <span className="tc-bc-price" title={priceTitle}>
          {line.kind === 'BULK' ? '—' : money.fmt(line.unitMarketUsd)}
          {line.kind === 'SEALED' && line.unitMarketUsd != null && (
            <small className="tc-bc-muted"> sealed ref</small>
          )}
          {line.grading && line.unitMarketUsd != null && (
            <small className="tc-bc-muted"> {line.priceBasis === 'GRADED' ? `${line.grading} market` : 'ungraded ref'}</small>
          )}
        </span>
        <div className="tc-bc-line-controls">
          <input type="number" min={1} aria-label="Quantity" value={line.quantity}
            onChange={(e) => { const q = Number(e.target.value); if (q >= 1) onChange({ quantity: q }) }} />
          {line.kind === 'CARD' && (
            <select aria-label="Raw or graded" value={grader}
              onChange={(e) => onChange({ grading: e.target.value === '' ? 'RAW' : `${e.target.value} ${GRADES.includes(grade) ? grade : '10'}` })}>
              <option value="">Raw</option>
              {GRADERS.map((g) => <option key={g} value={g}>{g}</option>)}
            </select>
          )}
          {line.kind === 'CARD' && grader && (
            <select aria-label="Grade" value={grade} onChange={(e) => onChange({ grading: `${grader} ${e.target.value}` })}>
              {SPECIAL_TENS[grader] && <option value={SPECIAL_TENS[grader]}>{SPECIAL_TENS[grader]}</option>}
              {GRADES.map((g) => <option key={g} value={g}>{g}</option>)}
            </select>
          )}
          {line.kind === 'CARD' && !line.grading && (
            <select aria-label="Condition" value={line.condition ?? 'UNKNOWN'} onChange={(e) => onChange({ condition: e.target.value })}>
              {CONDITIONS.map((c) => <option key={c} value={c}>{c === 'UNKNOWN' ? '?' : c}</option>)}
            </select>
          )}
        </div>
        {(line.kind === 'CARD' || line.kind === 'SEALED') && line.offerUnitUsd != null && (
          <span className="tc-bc-offer">Our offer <strong>{money.fmt(line.offerUnitUsd)}</strong>{line.quantity > 1 ? ' each' : ''}</span>
        )}
        {askable && (line.kind === 'CARD' || line.kind === 'SEALED') && (
          <label className="tc-bc-ask">
            <span>Your price ({money.code})</span>
            <input type="number" min={0} step="0.01" inputMode="decimal" value={ask} placeholder="Our rate"
              aria-label={`Your price for ${title}`}
              onChange={(e) => setAsk(e.target.value)}
              onBlur={() => {
                const value = ask === '' ? 0 : money.fromDisplay(Number(ask))
                if (value !== (line.requestedUnitUsd ?? 0) && value >= 0) onChange({ requestedUnitUsd: value })
              }} />
          </label>
        )}
        <div className="tc-bc-line-controls">
          {(line.photoRequired || line.photoUrl) && (line.kind === 'CARD' || line.kind === 'SEALED') && (
            <label className={`tc-bc-link ${line.photoUrl ? '' : 'tc-bc-photo-needed'}`}>
              {line.photoUrl ? 'Replace photo' : 'Add photo (optional)'}
              <input type="file" accept="image/jpeg,image/png,image/webp" hidden
                onChange={(e) => { const f = e.target.files?.[0]; if (f) onPhoto(f); e.target.value = '' }} />
            </label>
          )}
          <button type="button" className="tc-bc-link" onClick={onRemove} aria-label={`Remove ${title}`}>Remove</button>
        </div>
      </div>
    </li>
  )
}

function SummaryCard({ draft, money, busy, customerName, notes, onName, onNotes, onConfirm, askEmail, email, onEmail }: {
  draft: ChatDraft
  money: Money
  busy: boolean
  customerName: string
  notes: string
  onName: (v: string) => void
  onNotes: (v: string) => void
  onConfirm: () => void
  askEmail: boolean
  email: string
  onEmail: (v: string) => void
}) {
  const s = draft.summary
  const emailMissing = askEmail && !/^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/.test(email.trim())
  const pct = Math.max(0, Math.min(100, s.likelihoodPct))
  return (
    <div className="tc-bc-summary" id="tc-bc-summary">
      <div className="tc-bc-summary-row">
        <span>Market reference total</span>
        <strong>{money.fmt(s.totalMarketUsd)} <small>{money.code}</small></strong>
      </div>
      <div className="tc-bc-summary-row">
        <span>Eligible items</span>
        <strong>{money.fmt(s.eligibleMarketUsd ?? (s.totalMarketUsd != null ? 0 : null))} <small>{money.code}</small></strong>
      </div>
      <div className="tc-bc-counts">
        {Object.entries(s.statusCounts).map(([k, n]) => (
          <span key={k} className={`tc-bc-chip chip-${k.toLowerCase()}`}>{STATUS_LABELS[k] ?? k}: {n}</span>
        ))}
      </div>

      <div className="tc-bc-meter" aria-label={`${pct}% ${s.likelihoodLabel}`}>
        <div className="tc-bc-meter-bar"><div className="tc-bc-meter-mark" style={{ left: `${pct}%` }} /></div>
        <div className="tc-bc-meter-label"><strong>{pct}%</strong> <span>{s.likelihoodLabel}</span></div>
        <ul>{s.likelihoodReasons.map((r) => <li key={r}>{r}</li>)}</ul>
        <p className="tc-bc-disclaimer">{s.likelihoodDisclaimer ?? DEFAULT_DISCLAIMER}</p>
      </div>

      <p className="tc-bc-muted">{s.quoteNotice}</p>

      {askEmail && (
        <label className="tc-bc-field">
          <span>Email (so we can contact you)</span>
          <input type="email" autoComplete="email" required value={email} maxLength={254} onChange={(e) => onEmail(e.target.value)} />
        </label>
      )}
      <label className="tc-bc-field">
        <span>Name (optional)</span>
        <input value={customerName} maxLength={150} onChange={(e) => onName(e.target.value)} />
      </label>
      <label className="tc-bc-field">
        <span>Notes for our team (optional)</span>
        <textarea rows={2} value={notes} maxLength={2000} onChange={(e) => onNotes(e.target.value)} />
      </label>

      <p className="tc-bc-daily">{s.dailyLimitNotice}</p>
      <button type="button" className="tc-bc-btn tc-bc-btn-gold tc-bc-confirm" onClick={onConfirm}
        disabled={busy || !s.ready || !s.canSubmitToday || emailMissing || !!s.deal?.needsStoreCards}>
        {!s.ready ? 'Checking cards…' : busy ? 'Submitting…' : s.deal?.needsStoreCards ? 'Pick shop cards for your trade'
          : emailMissing ? 'Enter your email to submit' : 'Confirm and submit'}
      </button>
    </div>
  )
}
