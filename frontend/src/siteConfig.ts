/**
 * Store details shown in the header and footer. Each one is optional and set at build time
 * (Render: the frontend service's environment). Anything left empty is simply not shown.
 */
const env = import.meta.env

export const siteConfig = {
  /** Thin bar above the header, e.g. "Free tracked shipping across Canada over $150". */
  announcement: (env.VITE_ANNOUNCEMENT as string | undefined)?.trim() || '',
  contactEmail: (env.VITE_CONTACT_EMAIL as string | undefined)?.trim() || '',
  instagramUrl: (env.VITE_INSTAGRAM_URL as string | undefined)?.trim() || '',
  tiktokUrl: (env.VITE_TIKTOK_URL as string | undefined)?.trim() || '',
}
