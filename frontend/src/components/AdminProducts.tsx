import { useState } from 'react'
import { Link } from 'react-router-dom'
import type { Product } from '../types'
import { displayName, isOfficialImage, productImages } from '../catalog'
import { useProducts } from '../hooks/useProducts'
import {
  addProductPhotos,
  applyOfficialImagesForAll,
  applyOfficialProductImage,
  removeProductPhoto,
} from '../api/listingGeneratorApi'
import './AdminProducts.css'

type RowState = { busy?: boolean; error?: string; note?: string }

/**
 * /admin/products: each listing's pictures. The default picture is the official card image; your own
 * photos follow it and are what shoppers see with the arrows.
 */
export function AdminProducts() {
  const { products: loaded, loading, error, reload } = useProducts()
  // Products changed on this page, shown in place of the loaded copies
  const [changed, setChanged] = useState<Record<number, Product>>({})
  const [rows, setRows] = useState<Record<number, RowState>>({})
  const [bulk, setBulk] = useState<{ busy?: boolean; message?: string; error?: string }>({})
  const [filter, setFilter] = useState('')

  const setRow = (id: number, state: RowState) => setRows((r) => ({ ...r, [id]: state }))
  const replace = (updated: Product) => setChanged((c) => ({ ...c, [updated.id]: updated }))
  const products = loaded.map((p) => changed[p.id] ?? p)

  const run = async (id: number, action: () => Promise<Product>, note: string) => {
    setRow(id, { busy: true })
    try {
      replace(await action())
      setRow(id, { note })
    } catch (err) {
      setRow(id, { error: err instanceof Error ? err.message : 'Something went wrong.' })
    }
  }

  const officialForAll = async () => {
    setBulk({ busy: true })
    try {
      const result = await applyOfficialImagesForAll()
      const missing = result.unmatched.length
        ? ` Couldn't match ${result.unmatched.length}: ${result.unmatched.slice(0, 5).join(', ')}${result.unmatched.length > 5 ? '…' : ''}`
        : ''
      setBulk({ message: `Updated ${result.updated} ${result.updated === 1 ? 'listing' : 'listings'}.${missing}` })
      setChanged({})
      reload()
    } catch (err) {
      setBulk({ error: err instanceof Error ? err.message : 'Something went wrong.' })
    }
  }

  const visible = products.filter((p) => !filter.trim() || p.name.toLowerCase().includes(filter.trim().toLowerCase()))

  return (
    <div className="tc-ap tc-wrap">
      <nav className="tc-crumbs" aria-label="Breadcrumb">
        <Link to="/admin/buylist">Admin</Link><span aria-hidden="true">/</span><span>Product photos</span>
      </nav>
      <header className="tc-ap-head">
        <div>
          <h1>Product photos</h1>
          <p>Shoppers see the official card image first, then your photos with the arrows. Photos you add here appear right away.</p>
        </div>
        <button type="button" className="tc-button tc-button-solid" onClick={officialForAll} disabled={bulk.busy}>
          {bulk.busy ? 'Updating…' : 'Use official images for all'}
        </button>
      </header>
      {bulk.message && <p className="tc-ap-note" role="status">{bulk.message}</p>}
      {bulk.error && <p className="tc-ap-error" role="alert">{bulk.error}</p>}

      <label htmlFor="tc-ap-filter" className="tc-sr-only">Filter listings</label>
      <input id="tc-ap-filter" className="tc-ap-filter" type="search" placeholder="Filter by name" value={filter}
        onChange={(e) => setFilter(e.target.value)} />

      {error && <p className="tc-ap-error">{error}</p>}
      {loading && <p>Loading listings…</p>}
      {!loading && visible.length === 0 && <p>No listings found.</p>}

      <ul className="tc-ap-list">
        {visible.map((p) => {
          const row = rows[p.id] ?? {}
          const images = productImages(p)
          const photos = p.photoUrls ?? []
          return (
            <li key={p.id} className="tc-ap-row">
              <div className="tc-ap-info">
                <strong>{displayName(p)}</strong>
                <span>{[p.set, p.cardNumber && `#${p.cardNumber}`, p.pokemontcgId && `card ${p.pokemontcgId}`].filter(Boolean).join(' · ') || 'No set or number'}</span>
              </div>
              <div className="tc-ap-images">
                {images.length === 0 && <span className="tc-ap-empty">No pictures yet</span>}
                {p.imageUrl && (
                  <figure>
                    <img src={p.imageUrl} alt="" className={isOfficialImage(p.imageUrl) ? 'is-official' : ''} />
                    <figcaption>{isOfficialImage(p.imageUrl) ? 'Official' : 'Default'}</figcaption>
                  </figure>
                )}
                {photos.map((url, i) => (
                  <figure key={url}>
                    <img src={url} alt="" />
                    <figcaption>Photo {i + 1}</figcaption>
                    <button type="button" className="tc-ap-remove" aria-label={`Remove photo ${i + 1}`} disabled={row.busy}
                      onClick={() => run(p.id, () => removeProductPhoto(p.id, i), 'Photo removed.')}>×</button>
                  </figure>
                ))}
              </div>
              <div className="tc-ap-actions">
                {!isOfficialImage(p.imageUrl) && (
                  <button type="button" className="tc-button tc-button-ghost" disabled={row.busy}
                    onClick={() => run(p.id, () => applyOfficialProductImage(p.id), 'Official image set.')}>
                    Use official image
                  </button>
                )}
                <label className={`tc-button tc-button-ghost ${row.busy ? 'is-disabled' : ''}`}>
                  {row.busy ? 'Working…' : 'Add photos'}
                  <input type="file" accept="image/jpeg,image/png,image/webp" multiple hidden disabled={row.busy}
                    onChange={(e) => {
                      const files = Array.from(e.target.files ?? [])
                      e.target.value = ''
                      if (files.length) run(p.id, () => addProductPhotos(p.id, files), `${files.length} photo${files.length > 1 ? 's' : ''} added.`)
                    }} />
                </label>
                {row.note && <span className="tc-ap-note" role="status">{row.note}</span>}
                {row.error && <span className="tc-ap-error" role="alert">{row.error}</span>}
              </div>
            </li>
          )
        })}
      </ul>
    </div>
  )
}
