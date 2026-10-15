/** True on phones and tablets, where the scanner should open the camera directly instead of offering a QR code. */
export function isTouchDevice(): boolean {
  if (typeof window === 'undefined') return false
  const coarse = window.matchMedia?.('(pointer: coarse)').matches ?? false
  return coarse && Math.min(window.screen.width, window.screen.height) < 900
}

/** The phone page for a link token, on this site. */
export function phoneScanUrl(token: string): string {
  return `${window.location.origin}/sell/scan/${token}`
}
