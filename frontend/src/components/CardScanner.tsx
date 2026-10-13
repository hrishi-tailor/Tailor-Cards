import { useEffect, useRef, useState } from 'react'
import { buylistChatApi } from '../api/buylistChatApi'
import type { ChatDraft, ScanCard, ScanResult } from '../api/buylistChatApi'
import type { Money } from './buylistMoney'
import { analyze, grayscale } from './cardQuality'
import type { QualityReport, Rect } from './cardQuality'
import './CardScanner.css'

type Side = 'front' | 'back'
type Phase = 'capture' | 'analyzing' | 'result'

const CARD_RATIO = 63 / 88
const PAD = 0.15
const CHECK_MS = 120
const MAX_EDGE_PX = 1600
const CONDITIONS: [string, string][] = [
  ['NM', 'Near mint'], ['LP+', 'LP+ (one white dot)'], ['LP', 'Lightly played'], ['MP', 'Moderately played'],
  ['HP', 'Heavily played'], ['DMG', 'Damaged'], ['UNKNOWN', 'Not sure'],
]
const NOTE_LABELS: Record<string, string> = { corners: 'Corners', edges: 'Edges', surface: 'Surface', centering: 'Centering' }

/** The card guide inside a box of this size: 63:88, as large as fits with some room around it. */
function guideRect(width: number, height: number) {
  let h = height * 0.8
  let w = h * CARD_RATIO
  if (w > width * 0.8) {
    w = width * 0.8
    h = w / CARD_RATIO
  }
  return { x: (width - w) / 2, y: (height - h) / 2, w, h }
}

/** Crops a region (plus padding) of the source into a JPEG no larger than MAX_EDGE_PX. */
function crop(source: CanvasImageSource, sx: number, sy: number, sw: number, sh: number): Promise<Blob | null> {
  const scale = Math.min(1, MAX_EDGE_PX / Math.max(sw, sh))
  const canvas = document.createElement('canvas')
  canvas.width = Math.round(sw * scale)
  canvas.height = Math.round(sh * scale)
  canvas.getContext('2d')?.drawImage(source, sx, sy, sw, sh, 0, 0, canvas.width, canvas.height)
  return new Promise((resolve) => canvas.toBlob(resolve, 'image/jpeg', 0.9))
}

function loadImage(file: Blob): Promise<HTMLImageElement> {
  return new Promise((resolve, reject) => {
    const img = new Image()
    img.onload = () => resolve(img)
    img.onerror = () => reject(new Error('That file is not an image we can read.'))
    img.src = URL.createObjectURL(file)
  })
}

/**
 * Scan a card with the camera (or an uploaded photo): live tips for light, glare, focus and framing, then the AI
 * identifies the card and estimates its condition. Nothing goes on the list until the customer confirms.
 */
export function CardScanner({ draftId, money, initialFile, onDraft, onClose }: {
  draftId: string
  money: Money
  /** A photo the customer already picked ("Upload a card"): skips the camera step. */
  initialFile?: File | null
  onDraft: (draft: ChatDraft) => void
  onClose: () => void
}) {
  const [side, setSide] = useState<Side>('front')
  const [phase, setPhase] = useState<Phase>('capture')
  const [stream, setStream] = useState<MediaStream | null>(null)
  const [cameraError, setCameraError] = useState<string | null>(null)
  const [quality, setQuality] = useState<QualityReport | null>(null)
  const [photos, setPhotos] = useState<{ front: Blob | null; back: Blob | null }>({ front: null, back: null })
  const [previews, setPreviews] = useState<{ front: string | null; back: string | null }>({ front: null, back: null })
  const [uploadWarning, setUploadWarning] = useState<string | null>(null)
  const [scan, setScan] = useState<ScanResult | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [cardId, setCardId] = useState<string | null>(null)
  const [condition, setCondition] = useState('UNKNOWN')
  const [quantity, setQuantity] = useState(1)
  const [adding, setAdding] = useState(false)

  const videoRef = useRef<HTMLVideoElement>(null)
  const stageRef = useRef<HTMLDivElement>(null)
  const sampleRef = useRef<HTMLCanvasElement | null>(null)
  const capturingRef = useRef(false)
  const streamRef = useRef<MediaStream | null>(null)

  const stopCamera = () => {
    streamRef.current?.getTracks().forEach((t) => t.stop())
    streamRef.current = null
    setStream(null)
  }

  useEffect(() => () => streamRef.current?.getTracks().forEach((t) => t.stop()), [])

  const initialHandled = useRef(false)
  useEffect(() => {
    if (!initialFile || initialHandled.current) return
    initialHandled.current = true
    upload(initialFile)
    // upload only reads refs and setters
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [initialFile])

  useEffect(() => {
    if (videoRef.current && stream) {
      videoRef.current.srcObject = stream
      videoRef.current.play().catch(() => undefined)
    }
  }, [stream])

  const startCamera = async () => {
    setCameraError(null)
    if (!navigator.mediaDevices?.getUserMedia) {
      setCameraError('Your browser can\'t open the camera here. Upload a photo instead.')
      return
    }
    try {
      const media = await navigator.mediaDevices.getUserMedia({
        video: { facingMode: { ideal: 'environment' }, width: { ideal: 1920 }, height: { ideal: 1080 } },
        audio: false,
      })
      streamRef.current = media
      setStream(media)
    } catch {
      setCameraError('We couldn\'t open the camera. Allow camera access, or upload a photo instead.')
    }
  }

  /** The guide in video pixels, given the video is drawn with object-fit: cover. */
  const guideInVideo = () => {
    const video = videoRef.current
    const stage = stageRef.current
    if (!video || !stage || !video.videoWidth) return null
    const sw = stage.clientWidth
    const sh = stage.clientHeight
    const scale = Math.max(sw / video.videoWidth, sh / video.videoHeight)
    const ox = (sw - video.videoWidth * scale) / 2
    const oy = (sh - video.videoHeight * scale) / 2
    const g = guideRect(sw, sh)
    return { x: (g.x - ox) / scale, y: (g.y - oy) / scale, w: g.w / scale, h: g.h / scale }
  }

  const accept = (blob: Blob) => {
    const url = URL.createObjectURL(blob)
    setPhotos((p) => ({ ...p, [side]: blob }))
    setPreviews((p) => {
      if (p[side]) URL.revokeObjectURL(p[side]!)
      return { ...p, [side]: url }
    })
    setQuality(null)
  }

  /** The region we analyse: the guide plus some room around it, kept inside the video. */
  const sampleRegion = () => {
    const video = videoRef.current
    const g = guideInVideo()
    if (!video || !g) return null
    const sx = Math.max(0, g.x - g.w * PAD)
    const sy = Math.max(0, g.y - g.h * PAD)
    const sw = Math.min(video.videoWidth, g.x + g.w * (1 + PAD)) - sx
    const sh = Math.min(video.videoHeight, g.y + g.h * (1 + PAD)) - sy
    return { video, g, sx, sy, sw, sh }
  }

  /** Takes the photo: the detected card plus a small margin, or the whole guide when no card was found. */
  const capture = async (card?: Rect | null, scale = 1) => {
    const region = sampleRegion()
    if (!region || capturingRef.current) return
    const { video, g, sx, sy } = region
    capturingRef.current = true
    let x = g.x
    let y = g.y
    let w = g.w
    let h = g.h
    if (card) {
      x = sx + card.x0 * scale
      y = sy + card.y0 * scale
      w = (card.x1 - card.x0) * scale
      h = (card.y1 - card.y0) * scale
    }
    const mx = w * 0.06
    const my = h * 0.06
    const cx = Math.max(0, x - mx)
    const cy = Math.max(0, y - my)
    const blob = await crop(video, cx, cy, Math.min(video.videoWidth - cx, w + 2 * mx), Math.min(video.videoHeight - cy, h + 2 * my))
    capturingRef.current = false
    if (blob) accept(blob)
  }

  // Live checks while the camera is on and this side has no photo yet; the photo is taken on the first good frame
  const waiting = phase === 'capture' && !!stream && !photos[side]
  useEffect(() => {
    if (!waiting) return
    const timer = setInterval(() => {
      const region = sampleRegion()
      if (!region || region.video.readyState < 2 || region.sw <= 0 || region.sh <= 0 || capturingRef.current) return
      const { g, sx, sy, sw, sh } = region
      sampleRef.current ??= document.createElement('canvas')
      const frame = grayscale(region.video, sx, sy, sw, sh, sampleRef.current)
      if (!frame) return
      const scale = sw / frame.width
      const guide = { x0: (g.x - sx) / scale, y0: (g.y - sy) / scale, x1: (g.x + g.w - sx) / scale, y1: (g.y + g.h - sy) / scale }
      const report = analyze(frame, guide, true)
      setQuality(report)
      if (report.ok) capture(report.card, scale)
    }, CHECK_MS)
    return () => clearInterval(timer)
    // capture and sampleRegion only read refs
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [waiting])

  const upload = async (file: File | undefined) => {
    if (!file) return
    setUploadWarning(null)
    try {
      const img = await loadImage(file)
      URL.revokeObjectURL(img.src)
      sampleRef.current ??= document.createElement('canvas')
      const frame = grayscale(img, 0, 0, img.naturalWidth, img.naturalHeight, sampleRef.current)
      const report = frame ? analyze(frame, { x0: 0, y0: 0, x1: frame.width, y1: frame.height }, false) : null
      const blob = await crop(img, 0, 0, img.naturalWidth, img.naturalHeight)
      if (!blob) throw new Error('That file is not an image we can read.')
      accept(blob)
      if (report?.hint) setUploadWarning(`${report.hint}. You can use it anyway or take another.`)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'That photo could not be used.')
    }
  }

  const retakeSide = (s: Side) => {
    setPhotos((p) => ({ ...p, [s]: null }))
    setUploadWarning(null)
    setSide(s)
    setPhase('capture')
  }

  const analyzePhotos = async (back: Blob | null) => {
    if (!photos.front) return
    stopCamera()
    setPhase('analyzing')
    setError(null)
    try {
      const result = await buylistChatApi.scan(draftId, photos.front, back)
      setScan(result)
      setCardId(result.card?.cardId ?? null)
      setCondition(result.condition)
      setQuantity(1)
      setPhase('result')
    } catch (err) {
      setError(err instanceof Error ? err.message : 'The scan failed. Try again.')
      setPhase('capture')
    }
  }

  const add = async () => {
    if (!scan?.scanId) return
    setAdding(true)
    setError(null)
    try {
      onDraft(await buylistChatApi.addScan(draftId, scan.scanId, cardId, condition, quantity))
      reset()
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not add the card.')
    } finally {
      setAdding(false)
    }
  }

  const reset = () => {
    Object.values(previews).forEach((u) => u && URL.revokeObjectURL(u))
    setPreviews({ front: null, back: null })
    setPhotos({ front: null, back: null })
    setScan(null)
    setSide('front')
    setPhase('capture')
    setUploadWarning(null)
  }

  const close = () => {
    stopCamera()
    Object.values(previews).forEach((u) => u && URL.revokeObjectURL(u))
    onClose()
  }

  const hint = quality?.hint ?? 'Place the card inside the frame'
  const guideState = quality?.ok ? 'good' : quality?.card ? 'found' : ''
  const photo = photos[side]

  return (
    <div className="tc-scan" role="region" aria-label="Card scanner">
      <div className="tc-scan-head">
        <strong>Scan a card</strong>
        <button type="button" className="tc-bc-link" onClick={close}>Close</button>
      </div>
      {error && <div className="tc-bc-alert" role="alert">{error}</div>}

      {phase === 'capture' && (
        <>
          <ol className="tc-scan-steps">
            <li className={side === 'front' ? 'active' : 'done'}>Front</li>
            <li className={side === 'back' ? 'active' : ''}>Back <small>(optional)</small></li>
          </ol>

          {!photo && stream && (
            <div className="tc-scan-stage" ref={stageRef}>
              <video ref={videoRef} playsInline muted autoPlay />
              <div className={`tc-scan-guide ${guideState}`}>
                <span /><span /><span /><span />
              </div>
              <div className="tc-scan-hint" aria-live="polite">{hint}</div>
              <button type="button" className="tc-scan-shutter" aria-label="Take photo" onClick={() => capture()} />
            </div>
          )}

          {!photo && !stream && (
            <div className="tc-scan-start">
              <p>
                {side === 'front'
                  ? 'Lay the card flat on a plain, dark surface in good light. Take it out of any top loader to avoid glare.'
                  : 'Now the back: this is where whitening shows, so it helps us judge the condition.'}
              </p>
              <div className="tc-bc-actions">
                <button type="button" className="tc-bc-btn tc-bc-btn-gold" onClick={startCamera}>Use camera</button>
                <label className="tc-bc-btn tc-bc-btn-ghost tc-bc-file">
                  Upload a photo
                  <input type="file" accept="image/jpeg,image/png,image/webp" hidden
                    onChange={(e) => { upload(e.target.files?.[0]); e.target.value = '' }} />
                </label>
                {side === 'back' && (
                  <button type="button" className="tc-bc-link" onClick={() => analyzePhotos(null)}>Skip the back</button>
                )}
              </div>
              {cameraError && <p className="tc-bc-reason">{cameraError}</p>}
            </div>
          )}

          {photo && previews[side] && (
            <div className="tc-scan-review">
              <img src={previews[side]!} alt={`${side} of the card`} />
              {uploadWarning && <p className="tc-bc-reason">{uploadWarning}</p>}
              <div className="tc-bc-actions">
                {side === 'front' ? (
                  <>
                    <button type="button" className="tc-bc-btn tc-bc-btn-gold" onClick={() => { setSide('back'); setUploadWarning(null) }}>
                      Next: the back
                    </button>
                    <button type="button" className="tc-bc-btn tc-bc-btn-ghost" onClick={() => analyzePhotos(null)}>
                      Scan front only
                    </button>
                  </>
                ) : (
                  <button type="button" className="tc-bc-btn tc-bc-btn-gold" onClick={() => analyzePhotos(photos.back)}>
                    Scan the card
                  </button>
                )}
                <button type="button" className="tc-bc-link" onClick={() => retakeSide(side)}>Retake</button>
              </div>
            </div>
          )}
        </>
      )}

      {phase === 'analyzing' && (
        <div className="tc-scan-busy" aria-live="polite">
          {previews.front && <img src={previews.front} alt="" />}
          <p>Reading the card and checking its corners, edges and surface…</p>
        </div>
      )}

      {phase === 'result' && scan && (
        <ScanResultView scan={scan} money={money} cardId={cardId} condition={condition} quantity={quantity}
          adding={adding} onCard={setCardId} onCondition={setCondition} onQuantity={setQuantity} onAdd={add}
          onRetake={() => { setScan(null); retakeSide('front') }} onAddBack={() => retakeSide('back')} />
      )}
    </div>
  )
}

function ScanResultView({ scan, money, cardId, condition, quantity, adding, onCard, onCondition, onQuantity, onAdd,
  onRetake, onAddBack }: {
  scan: ScanResult
  money: Money
  cardId: string | null
  condition: string
  quantity: number
  adding: boolean
  onCard: (id: string) => void
  onCondition: (c: string) => void
  onQuantity: (q: number) => void
  onAdd: () => void
  onRetake: () => void
  onAddBack: () => void
}) {
  if (!scan.readable) {
    return (
      <div className="tc-scan-result">
        <h3>We couldn't read that photo</h3>
        <ul className="tc-scan-tips">
          {(scan.retakeTips.length ? scan.retakeTips : ['Move closer and make sure the whole card is in good light'])
            .map((t) => <li key={t}>{t}</li>)}
        </ul>
        <button type="button" className="tc-bc-btn tc-bc-btn-gold" onClick={onRetake}>Retake</button>
      </div>
    )
  }

  const options: ScanCard[] = [...(scan.card ? [scan.card] : []),
    ...scan.candidates.filter((c) => c.cardId !== scan.card?.cardId)]
  const chosen = options.find((c) => c.cardId === cardId) ?? null
  const raw = !scan.sealed && !scan.grading
  const offerApplies = chosen?.cardId === scan.card?.cardId && condition === scan.condition
  const title = scan.sealed ? scan.productName ?? 'Sealed product' : chosen?.name ?? 'Card not matched'
  const notes = Object.entries(scan.conditionNotes)

  return (
    <div className="tc-scan-result">
      <div className="tc-scan-match">
        {chosen?.imageUrl ? <img src={chosen.imageUrl} alt={chosen.name} /> : <div className="tc-bc-line-thumb" />}
        <div>
          <h3>{title}</h3>
          {chosen && <p className="tc-bc-muted">{[chosen.setName, chosen.cardNumber].filter(Boolean).join(' · ')}</p>}
          {!chosen && !scan.sealed && (
            <p className="tc-bc-reason">We read the card but couldn't match it exactly; we'll look it up once it's on your list.</p>
          )}
          {scan.grading && <span className="tc-bc-grade">{scan.grading}</span>}
          {raw && (
            <span className={`tc-scan-cond cond-${scan.condition.replace('+', 'plus').toLowerCase()}`}>
              {scan.conditionLabel}
              {scan.whiteDots != null && scan.whiteDots > 0 && ` · ${scan.whiteDots} white dot${scan.whiteDots > 1 ? 's' : ''}`}
            </span>
          )}
          {scan.confidence === 'LOW' && <p className="tc-bc-reason">Low confidence: double-check the card below.</p>}
          {chosen?.marketUsd != null && <p className="tc-bc-muted">Market {money.fmt(chosen.marketUsd)}</p>}
          {offerApplies && scan.offerUnitUsd != null && (
            <p className="tc-bc-offer">Our offer <strong>{money.fmt(scan.offerUnitUsd)}</strong>{quantity > 1 ? ' each' : ''}</p>
          )}
        </div>
      </div>

      {raw && notes.length > 0 && (
        <dl className="tc-scan-notes">
          {notes.map(([k, v]) => (
            <div key={k}><dt>{NOTE_LABELS[k] ?? k}</dt><dd>{v}</dd></div>
          ))}
        </dl>
      )}
      {raw && scan.needsBackPhoto && !scan.backPhotoUrl && (
        <p className="tc-bc-reason">
          Whitening shows mostly on the back, so this estimate is from the front only.{' '}
          <button type="button" className="tc-bc-link" onClick={onAddBack}>Add the back</button>
        </p>
      )}

      {options.length > 1 && (
        <fieldset className="tc-scan-options">
          <legend>Which card is it?</legend>
          {options.map((c) => (
            <label key={c.cardId} className={c.cardId === cardId ? 'active' : ''}>
              <input type="radio" name="scan-card" checked={c.cardId === cardId} onChange={() => onCard(c.cardId)} />
              {c.imageUrl && <img src={c.imageUrl} alt="" loading="lazy" />}
              <span>{c.name}<small>{[c.setName, c.cardNumber].filter(Boolean).join(' · ')}</small></span>
            </label>
          ))}
        </fieldset>
      )}

      <div className="tc-scan-controls">
        {raw && (
          <label className="tc-bc-field">
            <span>Condition</span>
            <select value={condition} onChange={(e) => onCondition(e.target.value)}>
              {CONDITIONS.map(([v, l]) => <option key={v} value={v}>{l}</option>)}
            </select>
          </label>
        )}
        <label className="tc-bc-field">
          <span>Quantity</span>
          <input type="number" min={1} max={100} value={quantity}
            onChange={(e) => { const q = Number(e.target.value); if (q >= 1 && q <= 100) onQuantity(q) }} />
        </label>
      </div>
      <p className="tc-bc-disclaimer">
        The condition is an estimate from your photos. We confirm it when we inspect the card, and the offer can change.
      </p>
      <div className="tc-bc-actions">
        <button type="button" className="tc-bc-btn tc-bc-btn-gold" onClick={onAdd} disabled={adding}>
          {adding ? 'Adding…' : 'Add to my list'}
        </button>
        <button type="button" className="tc-bc-btn tc-bc-btn-ghost" onClick={onRetake}>Retake</button>
      </div>
    </div>
  )
}
