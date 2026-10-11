import { Link } from 'react-router-dom'
import { CATEGORIES } from '../catalog'
import { siteConfig } from '../siteConfig'
import { BrandMark } from './SiteHeader'

export function SiteFooter() {
  const { contactEmail, instagramUrl, tiktokUrl } = siteConfig
  return (
    <footer className="tc-site-footer">
      <div className="tc-wrap">
        <div className="tc-footer-cols">
          <div className="tc-footer-about">
            <Link to="/" aria-label="Tailor Cards home"><BrandMark /></Link>
            <p>Authentic Pokémon singles, graded slabs and sealed product, photographed in hand.</p>
          </div>
          <div>
            <h4>Shop</h4>
            <ul>
              <li><Link to="/shop">All cards</Link></li>
              {CATEGORIES.map((c) => <li key={c.slug}><Link to={`/shop/${c.slug}`}>{c.label}</Link></li>)}
            </ul>
          </div>
          <div>
            <h4>Sell</h4>
            <ul>
              <li><Link to="/sell">Get an estimate</Link></li>
              <li><Link to="/track">Track a submission</Link></li>
            </ul>
          </div>
          {(contactEmail || instagramUrl || tiktokUrl) && (
            <div>
              <h4>Contact</h4>
              <ul>
                {contactEmail && <li><a href={`mailto:${contactEmail}`}>{contactEmail}</a></li>}
                {instagramUrl && <li><a href={instagramUrl} target="_blank" rel="noopener noreferrer">Instagram</a></li>}
                {tiktokUrl && <li><a href={tiktokUrl} target="_blank" rel="noopener noreferrer">TikTok</a></li>}
              </ul>
            </div>
          )}
        </div>
        <div className="tc-footer-legal">
          <span>© {new Date().getFullYear()} Tailor Cards. Pokémon is a trademark of Nintendo, Creatures and Game Freak.</span>
          <span>Secure checkout with Stripe</span>
        </div>
      </div>
    </footer>
  )
}
