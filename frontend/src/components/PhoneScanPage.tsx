import { useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import { buylistChatApi } from '../api/buylistChatApi'
import { CardScanner } from './CardScanner'
import { makeMoney } from './buylistMoney'
import './BuylistChat.css'

/**
 * The page a phone opens from the QR code on the computer: the card scanner, adding to the list open on the
 * computer. Uses only the link token from the URL; no sign-in on the phone.
 */
export function PhoneScanPage() {
  const { token = '' } = useParams()
  const [rate, setRate] = useState<number | null>(null)
  const [state, setState] = useState<'loading' | 'ready' | 'error'>('loading')
  const [error, setError] = useState<string | null>(null)
  const [count, setCount] = useState(0)

  useEffect(() => {
    buylistChatApi.phoneOpen(token)
      .then((r) => { setRate(r.usdCadRate); setState('ready') })
      .catch((err) => { setError(err instanceof Error ? err.message : 'This link is not valid.'); setState('error') })
  }, [token])

  return (
    <div className="tc-bc-container tc-phone-scan">
      <header className="tc-bc-header">
        <span className="tc-eyebrow">Sell or trade</span>
        <h1 className="tc-bc-title">Scan your cards</h1>
        <p className="tc-bc-muted">
          {count > 0
            ? `${count === 1 ? '1 card added. It\'s' : `${count} cards added. They're`} on the list on your computer, where you can review and submit.`
            : 'Cards you add here go straight onto the list open on your computer.'}
        </p>
      </header>
      {state === 'loading' && <div className="tc-bc-panel tc-bc-muted">Connecting to your computer…</div>}
      {state === 'error' && <div className="tc-bc-alert" role="alert">{error}</div>}
      {state === 'ready' && (
        <CardScanner money={makeMoney(rate)}
          onScan={(front, back) => buylistChatApi.phoneScan(token, front, back)}
          onAdd={async (scanId, cardId, condition, quantity) => {
            const r = await buylistChatApi.phoneAddScan(token, scanId, cardId, condition, quantity)
            setCount((c) => c + 1)
            return r.added
          }} />
      )}
    </div>
  )
}
