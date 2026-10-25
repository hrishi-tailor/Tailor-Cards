import { Link } from 'react-router-dom'
import './Home.css'

const FEATURES: { title: string; body: string }[] = [
  {
    title: 'Shop with confidence',
    body: "Every card is photographed in hand, so you see the exact one you'll receive. Payment goes through Stripe, and stock is checked twice during checkout, so the same card can never be sold to two people.",
  },
  {
    title: 'An offer in seconds',
    body: 'Sellers type what they have, paste a list or scan cards with a phone camera. An AI assistant recognises each card and looks up today\'s market price; a 60-card collection is priced in about 17 seconds.',
  },
  {
    title: 'Fair by design',
    body: 'The AI explains, but it never sets a price. Every offer is worked out from rates published on the site, and a person checks every deal before any money moves.',
  },
  {
    title: 'Run like a real business',
    body: 'Market prices are collected automatically every day, sales feed a business dashboard, and a summary of the week lands in my inbox every Monday morning.',
  },
]

/** /about: the story behind the shop and what the website does, for customers and curious visitors. */
export function AboutPage() {
  return (
    <div className="tc-home tc-about">
      <section className="tc-home-block">
        <div className="tc-wrap tc-about-intro">
          <span className="tc-eyebrow">Our story</span>
          <h1>A card business that grew its own website</h1>
          <p className="tc-hero-lede">
            Tailor Cards started in September 2024 as a one-person business, selling Pokémon cards at card shows, on
            Facebook Marketplace, Instagram and eBay. Two jobs didn't scale by hand: keeping one-of-a-kind cards from
            being sold twice, and pricing the collections people wanted to sell, one card at a time.
          </p>
          <p className="tc-hero-lede">
            So I built tailorcards.com myself: a shop with secure checkout, and a pricing assistant that turns a pile
            of cards into a fair offer in seconds.
          </p>
        </div>
      </section>

      <section className="tc-home-block tc-proof" aria-labelledby="what-heading">
        <div className="tc-wrap">
          <div className="tc-block-head">
            <div>
              <span className="tc-eyebrow">What the website does</span>
              <h2 id="what-heading">Built for buyers, sellers and the business behind them</h2>
            </div>
          </div>
          <ul className="tc-about-grid">
            {FEATURES.map((f, i) => (
              <li key={f.title}>
                <span className="tc-about-num" aria-hidden="true">{String(i + 1).padStart(2, '0')}</span>
                <h3>{f.title}</h3>
                <p>{f.body}</p>
              </li>
            ))}
          </ul>
        </div>
      </section>

      <section className="tc-home-block">
        <div className="tc-wrap tc-about-maker">
          <div>
            <span className="tc-eyebrow">Who's behind it</span>
            <h2>Hrishi Tailor</h2>
            <p>
              I'm a student at the University of Waterloo. I run the shop, and I designed and built every part of this
              website, from the storefront to the pricing assistant and the systems that keep it running.
            </p>
          </div>
          <div className="tc-about-links">
            <Link to="/shop" className="tc-button tc-button-solid">Browse the shop</Link>
            <Link to="/sell" className="tc-button tc-button-ghost">Try the pricing assistant</Link>
            <a href="https://hrishitailor.com" className="tc-link-underline" target="_blank" rel="noopener noreferrer">hrishitailor.com</a>
            <a href="https://github.com/hrishi-tailor/Tailor-Cards" className="tc-link-underline" target="_blank" rel="noopener noreferrer">How it's built (GitHub)</a>
          </div>
        </div>
      </section>
    </div>
  )
}
