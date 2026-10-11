import { useEffect, useRef, useState } from 'react'
import type { ChangeEvent, DragEvent, FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { isDemoRole } from '../api/buylistApi'
import {
  ListingApiError,
  createProduct,
  uploadProductPhoto,
  generateListingDraft,
  getCategories,
  getListingMarketReference,
} from '../api/listingGeneratorApi'
import type {
  Category,
  ListingCardCandidate,
  ListingCondition,
  ListingDraft,
  ListingMarketReference,
  Product,
} from '../types'
import './ListingGenerator.css'

const MAX_FILES = 2
const MAX_BYTES = 5 * 1024 * 1024
const ACCEPTED_TYPES = ['image/jpeg', 'image/png', 'image/webp']
const NOTE_MAX = 500
const TITLE_MAX = 80
const DESCRIPTION_MAX = 600
const PRODUCT_DESCRIPTION_MAX = 1000

const CONDITION_LABELS: Record<ListingCondition, string> = {
  NEAR_MINT: 'Near Mint',
  LIGHTLY_PLAYED: 'Lightly Played',
  MODERATELY_PLAYED: 'Moderately Played',
  HEAVILY_PLAYED: 'Heavily Played',
  DAMAGED: 'Damaged',
  UNKNOWN: 'Unknown (choose before creating)',
}

const EMPTY_DRAFT: ListingDraft = {
  cardName: '',
  setName: null,
  cardNumber: null,
  rarity: null,
  language: null,
  condition: 'UNKNOWN',
  gradingCompany: null,
  grade: null,
  isSealed: false,
  title: '',
  description: '',
  conditionNotes: null,
  confidence: 'LOW',
  uncertainties: [],
}

interface PhotoItem {
  file: File
  previewUrl: string
}

interface ProductFields {
  categoryId: string
  price: string
  stock: string
  imageUrl: string
  pokemontcgId: string
}

type Phase = 'upload' | 'loading' | 'review' | 'created'

const cad = new Intl.NumberFormat('en-CA', { style: 'currency', currency: 'CAD' })

export function ListingGenerator() {
  const [phase, setPhase] = useState<Phase>('upload')
  const [photos, setPhotos] = useState<PhotoItem[]>([])
  const [note, setNote] = useState('')
  const [dragActive, setDragActive] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [upstreamFailed, setUpstreamFailed] = useState(false)

  const [draft, setDraft] = useState<ListingDraft>(EMPTY_DRAFT)
  const [fromAi, setFromAi] = useState(false)
  const [candidates, setCandidates] = useState<ListingCardCandidate[]>([])
  const [reference, setReference] = useState<ListingMarketReference | null>(null)
  const [referenceLoading, setReferenceLoading] = useState(false)

  const [categories, setCategories] = useState<Category[]>([])
  const [product, setProduct] = useState<ProductFields>({ categoryId: '', price: '', stock: '', imageUrl: '', pokemontcgId: '' })
  const [conditionChecked, setConditionChecked] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const [attachPhotos, setAttachPhotos] = useState(true)
  const [created, setCreated] = useState<Product | null>(null)

  const fileInputRef = useRef<HTMLInputElement>(null)
  const photosRef = useRef<PhotoItem[]>([])

  useEffect(() => {
    photosRef.current = photos
  }, [photos])

  useEffect(() => {
    getCategories().then(setCategories).catch(() => setCategories([]))
    // Release preview object URLs when leaving the page
    return () => photosRef.current.forEach((p) => URL.revokeObjectURL(p.previewUrl))
  }, [])

  const addFiles = (incoming: FileList | File[]) => {
    setError(null)
    const list = Array.from(incoming)
    const rejected = list.find((f) => !ACCEPTED_TYPES.includes(f.type) || f.size > MAX_BYTES)
    if (rejected) {
      setError(`${rejected.name}: use a JPEG, PNG or WebP photo up to 5 MB.`)
      return
    }
    const room = MAX_FILES - photos.length
    if (list.length > room) {
      setError(`Up to ${MAX_FILES} photos (front and back).`)
    }
    const added = list.slice(0, Math.max(room, 0)).map((file) => ({ file, previewUrl: URL.createObjectURL(file) }))
    setPhotos([...photos, ...added])
  }

  const removePhoto = (index: number) => {
    URL.revokeObjectURL(photos[index].previewUrl)
    setPhotos(photos.filter((_, i) => i !== index))
  }

  const onDrop = (e: DragEvent<HTMLDivElement>) => {
    e.preventDefault()
    setDragActive(false)
    if (e.dataTransfer.files.length) addFiles(e.dataTransfer.files)
  }

  const onPick = (e: ChangeEvent<HTMLInputElement>) => {
    if (e.target.files?.length) addFiles(e.target.files)
    e.target.value = ''
  }

  const defaultCategoryId = (sealed: boolean): string => {
    const wanted = sealed ? 'sealed' : 'singles'
    const match = categories.find((c) => c.name.toLowerCase() === wanted)
    return match ? String(match.id) : ''
  }

  const applyReference = (ref: ListingMarketReference | null) => {
    setReference(ref)
    setProduct((p) => ({
      ...p,
      imageUrl: ref?.stockImageUrl ?? p.imageUrl,
      pokemontcgId: ref?.cardId ?? p.pokemontcgId,
    }))
  }

  const startReview = (nextDraft: ListingDraft, ai: boolean) => {
    setDraft(nextDraft)
    setFromAi(ai)
    setConditionChecked(false)
    setProduct({ categoryId: defaultCategoryId(nextDraft.isSealed), price: '', stock: '', imageUrl: '', pokemontcgId: '' })
    setPhase('review')
  }

  const handleGenerate = async () => {
    if (!photos.length) {
      setError('Add at least one photo.')
      return
    }
    setPhase('loading')
    setError(null)
    setUpstreamFailed(false)
    try {
      const result = await generateListingDraft(photos.map((p) => p.file), note)
      startReview(result.draft, true)
      setCandidates(result.matchStatus === 'AMBIGUOUS' ? result.candidates : [])
      applyReference(result.marketReference)
    } catch (err) {
      const status = err instanceof ListingApiError ? err.status : 0
      setUpstreamFailed(status === 502 || status === 503 || status === 429 || status === 0)
      setError(err instanceof Error ? err.message : 'Draft failed.')
      setPhase('upload')
    }
  }

  const fillManually = () => {
    setCandidates([])
    applyReference(null)
    startReview(EMPTY_DRAFT, false)
  }

  const pickCandidate = async (candidate: ListingCardCandidate) => {
    setReferenceLoading(true)
    setError(null)
    try {
      const ref = await getListingMarketReference({
        cardId: candidate.cardId,
        condition: draft.condition,
        gradingCompany: draft.gradingCompany ?? '',
        grade: draft.grade ?? '',
        sealed: draft.isSealed,
      })
      applyReference(ref)
      setCandidates([])
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not load that card.')
    } finally {
      setReferenceLoading(false)
    }
  }

  const updateDraft = <K extends keyof ListingDraft>(key: K, value: ListingDraft[K]) => {
    setDraft((d) => ({ ...d, [key]: value }))
    if (key === 'condition') setConditionChecked(false)
  }

  const textOrNull = (value: string) => (value.trim() ? value : null)

  const graded = Boolean(draft.gradingCompany?.trim() && draft.grade?.trim())
  const needsCondition = !draft.isSealed && !graded
  const productDescription = draft.conditionNotes?.trim()
    ? `${draft.description.trim()}\n\nCondition notes: ${draft.conditionNotes.trim()}`
    : draft.description.trim()

  const validationError = (): string | null => {
    if (!draft.title.trim()) return 'Add a title.'
    if (!product.categoryId) return 'Choose a category.'
    const price = Number(product.price)
    if (!product.price || !(price > 0)) return 'Enter your price.'
    const stock = Number(product.stock)
    if (product.stock === '' || !Number.isInteger(stock) || stock < 0) return 'Enter stock (0 or more).'
    if (needsCondition && draft.condition === 'UNKNOWN') return 'Choose the condition after checking the card.'
    if (needsCondition && !conditionChecked) return 'Confirm you checked the condition yourself.'
    if (productDescription.length > PRODUCT_DESCRIPTION_MAX) return 'Description plus condition notes is over 1000 characters.'
    return null
  }

  const handleCreate = async (e: FormEvent) => {
    e.preventDefault()
    const problem = validationError()
    if (problem) {
      setError(problem)
      return
    }
    setSubmitting(true)
    setError(null)
    try {
      // Your photos go on the listing after the stock image
      const photoUrls = attachPhotos ? await Promise.all(photos.map((p) => uploadProductPhoto(p.file))) : []
      const result = await createProduct({
        name: draft.title.trim(),
        description: productDescription,
        price: Number(product.price),
        imageUrl: textOrNull(product.imageUrl),
        stock: Number(product.stock),
        categoryId: Number(product.categoryId),
        cardNumber: textOrNull(draft.cardNumber ?? ''),
        set: textOrNull(draft.setName ?? ''),
        condition: draft.isSealed ? 'Sealed' : graded ? null : CONDITION_LABELS[draft.condition],
        grading: graded ? `${draft.gradingCompany!.trim()} ${draft.grade!.trim()}` : null,
        pokemontcgId: textOrNull(product.pokemontcgId),
        photoUrls,
      })
      setCreated(result)
      setPhase('created')
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not create product.')
    } finally {
      setSubmitting(false)
    }
  }

  const resetAll = () => {
    photos.forEach((p) => URL.revokeObjectURL(p.previewUrl))
    setPhotos([])
    setNote('')
    setDraft(EMPTY_DRAFT)
    setCandidates([])
    setReference(null)
    setCreated(null)
    setError(null)
    setUpstreamFailed(false)
    setPhase('upload')
  }

  if (isDemoRole()) {
    return (
      <div className="tc-lg-container">
        <div className="tc-lg-panel">
          <h1 className="tc-lg-title">Listing Generator</h1>
          <p className="tc-lg-muted">Admins only. Demo accounts cannot generate drafts.</p>
          <Link to="/admin/buylist" className="tc-lg-btn tc-lg-btn-ghost">Back to admin</Link>
        </div>
      </div>
    )
  }

  return (
    <div className="tc-lg-container">
      <div className="tc-lg-breadcrumbs">
        <Link to="/admin/buylist">Admin</Link>
        <span>/</span>
        <span className="tc-lg-current">Listing Generator</span>
      </div>

      <header className="tc-lg-header">
        <h1 className="tc-lg-title">Listing Generator</h1>
        <p className="tc-lg-muted">Photos in, editable draft out. You set the price. Nothing is published until you create it.</p>
      </header>

      {error && (
        <div className="tc-lg-alert" role="alert">
          <span>{error}</span>
          {upstreamFailed && phase === 'upload' && (
            <button type="button" className="tc-lg-btn tc-lg-btn-ghost" onClick={fillManually}>Fill in manually</button>
          )}
        </div>
      )}

      {(phase === 'upload' || phase === 'loading') && (
        <section className="tc-lg-panel">
          <div
            className={`tc-lg-dropzone ${dragActive ? 'active' : ''}`}
            onDragOver={(e) => { e.preventDefault(); setDragActive(true) }}
            onDragLeave={() => setDragActive(false)}
            onDrop={onDrop}
            onClick={() => fileInputRef.current?.click()}
            role="button"
            tabIndex={0}
            onKeyDown={(e) => { if (e.key === 'Enter' || e.key === ' ') fileInputRef.current?.click() }}
          >
            <input
              ref={fileInputRef}
              type="file"
              accept={ACCEPTED_TYPES.join(',')}
              multiple
              hidden
              onChange={onPick}
            />
            <strong>Drop 1–2 photos</strong>
            <span className="tc-lg-muted">or tap to choose · JPEG, PNG, WebP · max 5 MB</span>
          </div>

          {photos.length > 0 && (
            <div className="tc-lg-previews">
              {photos.map((p, i) => (
                <figure key={p.previewUrl} className="tc-lg-preview">
                  <img src={p.previewUrl} alt={`Photo ${i + 1}`} />
                  <button type="button" className="tc-lg-remove" onClick={() => removePhoto(i)} aria-label={`Remove photo ${i + 1}`}>×</button>
                </figure>
              ))}
            </div>
          )}

          <label className="tc-lg-field">
            <span>Note (optional)</span>
            <textarea
              value={note}
              maxLength={NOTE_MAX}
              rows={2}
              placeholder="e.g. back has a small dent"
              onChange={(e) => setNote(e.target.value)}
            />
            <small className="tc-lg-muted">{note.length}/{NOTE_MAX}</small>
          </label>

          <div className="tc-lg-actions">
            <button type="button" className="tc-lg-btn tc-lg-btn-gold" onClick={handleGenerate} disabled={phase === 'loading' || !photos.length}>
              {phase === 'loading' ? 'Reading photos…' : 'Generate draft'}
            </button>
            <button type="button" className="tc-lg-btn tc-lg-btn-ghost" onClick={fillManually} disabled={phase === 'loading'}>
              Skip, fill in manually
            </button>
          </div>
          {phase === 'loading' && <p className="tc-lg-muted">This usually takes 5–15 seconds.</p>}
          {phase === 'upload' && !photos.length && !error && (
            <p className="tc-lg-muted">No photos yet. Front is enough; add the back for condition notes.</p>
          )}
        </section>
      )}

      {phase === 'review' && (
        <form className="tc-lg-review" onSubmit={handleCreate} noValidate>
          <section className="tc-lg-panel">
            <div className="tc-lg-panel-head">
              <h2>{fromAi ? 'Review draft' : 'New listing'}</h2>
              {fromAi && (
                <span className={`tc-lg-badge tc-lg-conf-${draft.confidence.toLowerCase()}`}>
                  {draft.confidence} confidence
                </span>
              )}
            </div>

            {fromAi && draft.uncertainties.length > 0 && (
              <div className="tc-lg-uncertain">
                <strong>Check these</strong>
                <ul>
                  {draft.uncertainties.map((u) => <li key={u}>{u}</li>)}
                </ul>
              </div>
            )}

            <div className="tc-lg-grid">
              <TextField label="Card name" value={draft.cardName} onChange={(v) => updateDraft('cardName', v)} />
              <TextField label="Set" value={draft.setName ?? ''} onChange={(v) => updateDraft('setName', v)} />
              <TextField label="Number" value={draft.cardNumber ?? ''} onChange={(v) => updateDraft('cardNumber', v)} />
              <TextField label="Rarity" value={draft.rarity ?? ''} onChange={(v) => updateDraft('rarity', v)} />
              <TextField label="Language" value={draft.language ?? ''} onChange={(v) => updateDraft('language', v)} />
              <label className="tc-lg-field tc-lg-check">
                <input type="checkbox" checked={draft.isSealed} onChange={(e) => updateDraft('isSealed', e.target.checked)} />
                <span>Sealed product</span>
              </label>
              <TextField label="Grading company" value={draft.gradingCompany ?? ''} placeholder="e.g. PSA" onChange={(v) => updateDraft('gradingCompany', v)} />
              <TextField label="Grade" value={draft.grade ?? ''} placeholder="e.g. 10" onChange={(v) => updateDraft('grade', v)} />
            </div>

            {needsCondition && (
              <div className="tc-lg-condition">
                <label className="tc-lg-field">
                  <span>
                    Condition {fromAi && <em className="tc-lg-estimate">AI estimate, please verify</em>}
                  </span>
                  <select value={draft.condition} onChange={(e) => updateDraft('condition', e.target.value as ListingCondition)}>
                    {(Object.keys(CONDITION_LABELS) as ListingCondition[]).map((c) => (
                      <option key={c} value={c}>{CONDITION_LABELS[c]}</option>
                    ))}
                  </select>
                </label>
                <label className="tc-lg-field tc-lg-check">
                  <input type="checkbox" checked={conditionChecked} onChange={(e) => setConditionChecked(e.target.checked)} />
                  <span>I checked the condition myself</span>
                </label>
              </div>
            )}

            <label className="tc-lg-field">
              <span>Condition notes</span>
              <textarea rows={2} value={draft.conditionNotes ?? ''} onChange={(e) => updateDraft('conditionNotes', e.target.value)} />
            </label>

            <label className="tc-lg-field">
              <span>Title</span>
              <input value={draft.title} maxLength={TITLE_MAX} onChange={(e) => updateDraft('title', e.target.value)} />
              <small className="tc-lg-muted">{draft.title.length}/{TITLE_MAX}</small>
            </label>

            <label className="tc-lg-field">
              <span>Description</span>
              <textarea rows={5} value={draft.description} maxLength={DESCRIPTION_MAX} onChange={(e) => updateDraft('description', e.target.value)} />
              <small className="tc-lg-muted">{draft.description.length}/{DESCRIPTION_MAX}</small>
            </label>
          </section>

          {candidates.length > 0 && (
            <section className="tc-lg-panel">
              <h2>Which card is it?</h2>
              <p className="tc-lg-muted">Several cards match. Pick one to load its market reference, or skip.</p>
              <div className="tc-lg-candidates">
                {candidates.map((c) => (
                  <button key={c.cardId} type="button" className="tc-lg-candidate" disabled={referenceLoading} onClick={() => pickCandidate(c)}>
                    {c.imageUrl && <img src={c.imageUrl} alt="" loading="lazy" />}
                    <span>{c.name}</span>
                    <small className="tc-lg-muted">{c.setName} · {c.cardNumber}</small>
                  </button>
                ))}
              </div>
            </section>
          )}

          <section className="tc-lg-panel">
            <h2>Product</h2>
            <div className="tc-lg-reference">
              {reference?.marketReferenceCad != null ? (
                <>
                  <span className="tc-lg-muted">Market reference</span>
                  <strong>{cad.format(reference.marketReferenceCad)}</strong>
                  <small className="tc-lg-muted">For comparison only. Not your price.</small>
                </>
              ) : (
                <span className="tc-lg-muted">No market reference available.</span>
              )}
            </div>

            <div className="tc-lg-grid">
              <label className="tc-lg-field">
                <span>Your price (CAD)</span>
                <input type="number" inputMode="decimal" min="0.01" step="0.01" value={product.price} placeholder="Required"
                  onChange={(e) => setProduct({ ...product, price: e.target.value })} />
              </label>
              <label className="tc-lg-field">
                <span>Stock</span>
                <input type="number" inputMode="numeric" min="0" step="1" value={product.stock} placeholder="Required"
                  onChange={(e) => setProduct({ ...product, stock: e.target.value })} />
              </label>
              <label className="tc-lg-field">
                <span>Category</span>
                <select value={product.categoryId} onChange={(e) => setProduct({ ...product, categoryId: e.target.value })}>
                  <option value="">Choose…</option>
                  {categories.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
                </select>
              </label>
              <TextField label="Catalog id" value={product.pokemontcgId} placeholder="e.g. base1-4"
                onChange={(v) => setProduct({ ...product, pokemontcgId: v })} />
            </div>

            <label className="tc-lg-field">
              <span>Image URL <em className="tc-lg-estimate">Stock image, not your photo</em></span>
              <input value={product.imageUrl} placeholder="Optional" onChange={(e) => setProduct({ ...product, imageUrl: e.target.value })} />
            </label>
            {product.imageUrl && (
              <figure className="tc-lg-stock">
                <img src={product.imageUrl} alt="Stock card image" />
                <figcaption className="tc-lg-muted">Stock image</figcaption>
              </figure>
            )}
            <label className="tc-lg-check">
              <input type="checkbox" checked={attachPhotos} onChange={(e) => setAttachPhotos(e.target.checked)} />
              <span>Show my {photos.length === 1 ? 'photo' : `${photos.length} photos`} on the listing, after the stock image</span>
            </label>
          </section>

          <div className="tc-lg-actions tc-lg-sticky">
            <button type="submit" className="tc-lg-btn tc-lg-btn-gold" disabled={submitting}>
              {submitting ? 'Creating…' : 'Create product'}
            </button>
            <button type="button" className="tc-lg-btn tc-lg-btn-ghost" onClick={resetAll} disabled={submitting}>
              Start over
            </button>
          </div>
        </form>
      )}

      {phase === 'created' && created && (
        <section className="tc-lg-panel">
          <h2>Product created</h2>
          <p className="tc-lg-muted">#{created.id} · {created.name}</p>
          <div className="tc-lg-actions">
            <button type="button" className="tc-lg-btn tc-lg-btn-gold" onClick={resetAll}>New listing</button>
            <Link to="/" className="tc-lg-btn tc-lg-btn-ghost">View storefront</Link>
          </div>
        </section>
      )}
    </div>
  )
}

function TextField({ label, value, placeholder, onChange }: {
  label: string
  value: string
  placeholder?: string
  onChange: (value: string) => void
}) {
  return (
    <label className="tc-lg-field">
      <span>{label}</span>
      <input value={value} placeholder={placeholder} onChange={(e) => onChange(e.target.value)} />
    </label>
  )
}
