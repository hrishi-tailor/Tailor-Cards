import { useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import type { Product } from '../types'
import { CATEGORIES } from '../catalog'
import { useProducts } from '../hooks/useProducts'
import { ProductCard } from './ProductCard'
import { ProductQuickView } from './ProductQuickView'
// Official card scans (pokemontcg.io) and product art (tcggo), not shop photos
import mCharizardX from '../assets/home/m-charizard-ex-flashfire-108.jpg'
import mCharizardY from '../assets/home/m-charizard-ex-flashfire-107.jpg'
import magmaGroudon from '../assets/home/team-magma-groudon-ex.jpg'
import mewtwoUpc from '../assets/home/mewtwo-151-upc-promo.jpg'
import mewUpc from '../assets/home/mew-ex-151-upc-promo.jpg'
import charizard151 from '../assets/home/charizard-ex-151-sir.jpg'
import moonbreon from '../assets/home/umbreon-vmax-alt-art.jpg'
import umbreonSir from '../assets/home/umbreon-ex-prismatic-sir.jpg'
import prismaticEtb from '../assets/home/prismatic-evolutions-etb-official.png'
import './Home.css'

const COLLECTION_IMAGES: Record<string, { src: string; product?: boolean }> = {
  slabs: { src: moonbreon },
  singles: { src: charizard151 },
  sealed: { src: prismaticEtb, product: true },
}

const SETS = ['Prismatic Evolutions', '151', 'Flashfire', 'Double Crisis', 'Evolving Skies', 'Crown Zenith',
  'Ascended Heroes', 'Base Set']

const GRADERS = ['PSA', 'BGS', 'CGC', 'SGC', 'TAG']

const Arrow = () => (
  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden="true"><path d="M5 12h14M13 6l6 6-6 6" /></svg>
)

/** Landing page: hero, collections, new arrivals from the shop, why buy here, and the sell band. */
export function HomePage() {
  const { products, loading } = useProducts()
  const [quickView, setQuickView] = useState<Product | null>(null)

  // Newest available stock first (highest id), up to eight
  const arrivals = useMemo(() => products
    .filter((p) => p.status !== 'SOLD' && p.stock > 0)
    .sort((a, b) => b.id - a.id)
    .slice(0, 8), [products])

  return (
    <div className="tc-home">
      <section className="tc-hero">
        <div className="tc-wrap tc-hero-grid">
          <div className="tc-hero-copy">
            <span className="tc-eyebrow">Pokémon TCG · Canada</span>
            <h1>Chase cards and graded slabs, <em>inspected up close.</em></h1>
            <p className="tc-hero-lede">
              Every listing shows the official card image and our own photos of the exact copy you'll receive, so you
              can check the corners before you buy. Selling a collection? See our offer in minutes and get paid by
              Interac e-Transfer.
            </p>
            <div className="tc-hero-ctas">
              <Link to="/shop" className="tc-button tc-button-solid">Shop cards <Arrow /></Link>
              <Link to="/sell" className="tc-button tc-button-ghost">Sell your cards</Link>
            </div>
            <ul className="tc-hero-proofs">
              <li>
                <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M12 3 5 6v5c0 5 3 8.5 7 10 4-1.5 7-5 7-10V6l-7-3Z" /><path d="m9 12 2 2 4-4" /></svg>
                Authenticity guaranteed
              </li>
              <li>
                <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M3 7h11v9H3zM14 10h4l3 3v3h-7z" /><circle cx="7" cy="17.5" r="1.5" /><circle cx="17" cy="17.5" r="1.5" /></svg>
                Tracked shipping
              </li>
              <li>
                <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M13 3 4 14h7l-1 7 9-11h-7l1-7Z" /></svg>
                Interac e-Transfer payouts
              </li>
            </ul>
          </div>

          <div className="tc-hero-stage" aria-hidden="true">
            <div className="tc-slab tc-slab-left"><img src={magmaGroudon} alt="" /></div>
            <div className="tc-slab tc-slab-right"><img src={mewtwoUpc} alt="" /></div>
            <div className="tc-slab tc-slab-center"><img src={mCharizardX} alt="" /></div>
            <div className="tc-hero-tag tc-hero-tag-a"><small>Flashfire · Secret rare</small><strong>M Charizard EX</strong></div>
            <div className="tc-hero-tag tc-hero-tag-b"><small>151 Ultra-Premium Collection</small><strong>Mewtwo promo</strong></div>
          </div>
        </div>
      </section>

      <div className="tc-graders">
        <div className="tc-wrap tc-graders-row">
          <p>We buy and sell slabs from every major grader</p>
          <ul>{GRADERS.map((g) => <li key={g}>{g}</li>)}</ul>
        </div>
      </div>

      <section className="tc-home-block" aria-labelledby="collections-heading">
        <div className="tc-wrap">
          <div className="tc-block-head">
            <div>
              <span className="tc-eyebrow">Shop by collection</span>
              <h2 id="collections-heading">Find your next grail</h2>
            </div>
            <Link to="/shop" className="tc-link-underline">Shop all</Link>
          </div>
          <div className="tc-collections">
            {CATEGORIES.map((c) => (
              <Link key={c.slug} to={`/shop/${c.slug}`}
                className={`tc-collection ${COLLECTION_IMAGES[c.slug].product ? 'is-product' : ''}`}>
                <img src={COLLECTION_IMAGES[c.slug].src} alt="" loading="lazy" />
                <span className="tc-collection-cap">
                  <span>
                    <span className="tc-collection-title">{c.label}</span>
                    <small>{c.blurb}</small>
                  </span>
                  <span className="tc-collection-arrow"><Arrow /></span>
                </span>
              </Link>
            ))}
          </div>
          <nav className="tc-set-chips" aria-label="Shop by set">
            {SETS.map((s) => <Link key={s} to={`/shop?q=${encodeURIComponent(s)}`}>{s}</Link>)}
          </nav>
        </div>
      </section>

      {(loading || arrivals.length > 0) && (
        <section className="tc-home-block tc-home-arrivals" aria-labelledby="arrivals-heading">
          <div className="tc-wrap">
            <div className="tc-block-head">
              <div>
                <span className="tc-eyebrow">New arrivals</span>
                <h2 id="arrivals-heading">Fresh in the case</h2>
              </div>
              <Link to="/shop" className="tc-link-underline">View all</Link>
            </div>
            {loading ? (
              <div className="tc-product-grid" aria-hidden="true">
                {Array.from({ length: 4 }, (_, i) => <div key={i} className="tc-product-skeleton" />)}
              </div>
            ) : (
              <div className="tc-product-grid tc-arrivals-grid">
                {arrivals.map((p) => <ProductCard key={p.id} product={p} onQuickView={setQuickView} />)}
              </div>
            )}
          </div>
        </section>
      )}

      <section className="tc-home-block tc-proof" aria-labelledby="proof-heading">
        <div className="tc-wrap tc-proof-grid">
          <div className="tc-proof-photo">
            <img src={umbreonSir} alt="Umbreon ex special illustration rare from Prismatic Evolutions" loading="lazy" />
            <span className="tc-proof-loupe" aria-hidden="true" style={{ backgroundImage: `url(${umbreonSir})` }} />
            <p className="tc-proof-note">Zoom into every corner from the quick view.</p>
          </div>
          <div className="tc-proof-copy">
            <span className="tc-eyebrow">Why buy from us</span>
            <h2 id="proof-heading">See the exact copy before you buy</h2>
            <p>Every listing starts with the official card image, followed by our own photos of the copy you'll receive, so you can judge corners, centering and surfaces for yourself.</p>
            <ul className="tc-checks">
              <li><svg viewBox="0 0 24 24" aria-hidden="true"><path d="m5 12 4 4 10-10" /></svg><span><b>Photos of the exact copy</b> after the official image</span></li>
              <li><svg viewBox="0 0 24 24" aria-hidden="true"><path d="m5 12 4 4 10-10" /></svg><span><b>Zoom into every corner</b> from the quick view before you buy</span></li>
              <li><svg viewBox="0 0 24 24" aria-hidden="true"><path d="m5 12 4 4 10-10" /></svg><span><b>Authenticity guaranteed</b> on everything we sell</span></li>
            </ul>
            <Link to="/shop" className="tc-button tc-button-ghost">Browse the shop</Link>
          </div>
        </div>
      </section>

      <section className="tc-sell-band" aria-labelledby="sell-heading">
        <div className="tc-wrap tc-sell-grid">
          <div>
            <span className="tc-eyebrow">Sell or trade</span>
            <h2 id="sell-heading">Turn your collection into cash or store credit</h2>
            <p>Tell our assistant what you have, or paste a list. You'll see live market prices, our offer at our published rates, and an estimate of how likely we are to accept.</p>
            <ol className="tc-steps">
              <li><strong>List your cards</strong>Chat, paste a list or upload a CSV</li>
              <li><strong>See our offer</strong>Cash, store credit or both</li>
              <li><strong>Ship and get paid</strong>Interac e-Transfer once we inspect</li>
            </ol>
            <Link to="/sell" className="tc-button tc-button-solid tc-sell-cta">Get an estimate <Arrow /></Link>
          </div>
          <div className="tc-estimate" aria-label="Example estimate">
            <span className="tc-estimate-label">Example estimate</span>
            <div className="tc-estimate-row">
              <img src={mewUpc} alt="" loading="lazy" />
              <div><b>Mew ex</b><small>151 UPC promo · PSA 10</small></div>
              <span className="tc-estimate-num">$300.00<small>offer $246.00</small></span>
            </div>
            <div className="tc-estimate-row">
              <img src={mCharizardY} alt="" loading="lazy" />
              <div><b>M Charizard EX</b><small>Flashfire 107/106 · near mint</small></div>
              <span className="tc-estimate-num">$260.00<small>offer $200.20</small></span>
            </div>
            <div className="tc-estimate-row">
              <img src={moonbreon} alt="" loading="lazy" />
              <div><b>Umbreon VMAX</b><small>Evolving Skies 215/203 · near mint</small></div>
              <span className="tc-estimate-num">$1,450.00<small>offer $1,116.50</small></span>
            </div>
            <div className="tc-estimate-total"><span>Our cash offer</span><strong>$1,562.70 CAD</strong></div>
            <div className="tc-estimate-meter">
              <div className="tc-estimate-track"><div className="tc-estimate-fill" /></div>
              <p><b>86% estimated approval</b> · an estimate, not a guarantee</p>
            </div>
          </div>
        </div>
      </section>

      {quickView && (
        <ProductQuickView key={quickView.id} product={quickView} onClose={() => setQuickView(null)} />
      )}
    </div>
  )
}
