import { useEffect, useRef, useState } from 'react'
import QRCode from 'qrcode'
import { buylistChatApi } from '../api/buylistChatApi'
import type { PhoneLinkStatus } from '../api/buylistChatApi'
import { phoneScanUrl } from './phoneLink'

const POLL_MS = 2500

/**
 * "Use your phone" panel for computers: a QR code that opens the scanner on the customer's phone. Cards scanned
 * there are added to this list; onAdded fires so the list on this screen refreshes.
 */
export function PhoneHandoff({ draftId, onAdded }: { draftId: string; onAdded: () => void }) {
  const [token, setToken] = useState<string | null>(null)
  const [svg, setSvg] = useState<string | null>(null)
  const [status, setStatus] = useState<PhoneLinkStatus | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [attempt, setAttempt] = useState(0)
  const addedCount = useRef(0)
  const onAddedRef = useRef(onAdded)
  useEffect(() => { onAddedRef.current = onAdded }, [onAdded])

  useEffect(() => {
    let cancelled = false
    buylistChatApi.createPhoneLink(draftId)
      .then(async (link) => {
        const code = await QRCode.toString(phoneScanUrl(link.token), { type: 'svg', margin: 1, errorCorrectionLevel: 'M' })
        if (cancelled) return
        addedCount.current = 0
        setToken(link.token)
        setSvg(code)
        setStatus(null)
        setError(null)
      })
      .catch((err) => { if (!cancelled) setError(err instanceof Error ? err.message : 'Could not make a phone code.') })
    return () => { cancelled = true }
  }, [draftId, attempt])

  // Poll: has the phone opened the link, and has it added cards?
  useEffect(() => {
    if (!token) return
    const timer = setInterval(() => {
      buylistChatApi.phoneLinkStatus(draftId, token)
        .then((s) => {
          setStatus(s)
          if (s.added.length !== addedCount.current) {
            addedCount.current = s.added.length
            onAddedRef.current()
          }
        })
        .catch(() => undefined)
    }, POLL_MS)
    return () => clearInterval(timer)
  }, [draftId, token])

  const local = /^(localhost|127\.0\.0\.1|\[::1\])$/.test(window.location.hostname)
  const expired = status?.expired ?? false

  return (
    <div className="tc-handoff">
      <div className="tc-handoff-qr" aria-label="QR code for scanning with your phone">
        {svg && !expired
          // QR code markup generated locally from our own link
          ? <span style={{ width: '100%', height: '100%' }} dangerouslySetInnerHTML={{ __html: svg }} />
          : <span className="tc-bc-muted">{error ? '—' : expired ? 'Expired' : '…'}</span>}
      </div>
      <div>
        <h3>Scan with your phone <small>Recommended</small></h3>
        <p>Point your phone's camera at this code. Phone cameras focus better on cards, and every card you add there shows up here.</p>
        {error && <p className="tc-bc-reason">{error}</p>}
        {expired ? (
          <button type="button" className="tc-bc-link" onClick={() => setAttempt((a) => a + 1)}>Make a new code</button>
        ) : (
          <div className="tc-handoff-state" aria-live="polite">
            <span className={`tc-handoff-dot ${status?.connected ? 'connected' : 'waiting'}`} />
            {status?.connected ? 'Phone connected' : 'Waiting for your phone…'}
          </div>
        )}
        {status && status.added.length > 0 && (
          <ul className="tc-handoff-added">
            {status.added.slice(-3).map((name, i) => <li key={`${i}-${name}`}>Added {name}</li>)}
          </ul>
        )}
        {local && token && (
          <p>Testing locally? Your phone can't open localhost. Run the dev server with <code>npm run dev -- --host</code> and
            open this page on your computer's network address.</p>
        )}
      </div>
    </div>
  )
}
