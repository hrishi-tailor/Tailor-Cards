import { useState } from 'react'
import { useNavigate } from 'react-router-dom'

/** /track: enter the tracking code from a submission receipt to open its status page. */
export function TrackLookup() {
  const navigate = useNavigate()
  const [code, setCode] = useState('')
  const [error, setError] = useState('')

  const submit = (e: React.FormEvent) => {
    e.preventDefault()
    // Accept the bare code or a pasted tracking link
    const token = code.trim().split('/').filter(Boolean).pop() ?? ''
    if (!token) {
      setError('Enter the tracking code from your receipt email.')
      return
    }
    navigate(`/sell/track/${encodeURIComponent(token)}`)
  }

  return (
    <section className="tc-track-lookup tc-wrap">
      <span className="tc-eyebrow">Track a submission</span>
      <h1>Check on your cards</h1>
      <p>Enter the tracking code from the receipt we emailed when you submitted your list.</p>
      <form onSubmit={submit} noValidate>
        <label htmlFor="tc-track-code" className="tc-sr-only">Tracking code</label>
        <input id="tc-track-code" value={code} placeholder="Tracking code or link" autoComplete="off"
          onChange={(e) => { setCode(e.target.value); setError('') }} aria-invalid={Boolean(error)}
          aria-describedby={error ? 'tc-track-error' : undefined} />
        <button type="submit" className="tc-button tc-button-solid">Track</button>
      </form>
      {error && <p id="tc-track-error" className="tc-track-error" role="alert">{error}</p>}
    </section>
  )
}
