/**
 * Photo checks for the card scanner, run on a small grayscale copy of the frame so they are cheap enough to run
 * many times a second: find the card inside the guide, then check light, glare and focus on the card itself.
 */

export interface Rect { x0: number; y0: number; x1: number; y1: number }
export interface Frame { gray: Float32Array; width: number; height: number }

export interface QualityReport {
  /** Good enough to take the photo. */
  ok: boolean
  hint: string | null
  /** Where the card is, in frame pixels; null when not found. */
  card: Rect | null
  /** How many of the card's four edges were found. */
  edges: number
  brightness: number
  glare: number
  sharpness: number
}

export const ANALYSIS_WIDTH = 240
const CARD_RATIO = 63 / 88
const SEGMENTS = 8
const MIN_INLIERS = 4
const MIN_EDGE = 12
const DARK = 80          // brightest 10% of the card darker than this: not enough light
const BRIGHT = 225       // darkest 10% brighter than this: washed out
const GLARE_LEVEL = 250
const MAX_GLARE = 0.08
const MIN_SHARPNESS = 12   // crisp frames score about 30; 12 is clearly blurry
const MIN_HEIGHT = 0.55  // card height as a share of the guide height
const MAX_SLOPE = 0.3     // tilt is fine; this only rejects lines that can't be a card edge

/** Grayscale copy of a region of the source, scaled to `width` wide. */
export function grayscale(
  source: CanvasImageSource, sx: number, sy: number, sw: number, sh: number, canvas: HTMLCanvasElement,
  width = ANALYSIS_WIDTH,
): Frame | null {
  const height = Math.max(1, Math.round((width * sh) / sw))
  canvas.width = width
  canvas.height = height
  const ctx = canvas.getContext('2d', { willReadFrequently: true })
  if (!ctx) return null
  ctx.drawImage(source, sx, sy, sw, sh, 0, 0, width, height)
  const { data } = ctx.getImageData(0, 0, width, height)
  const gray = new Float32Array(width * height)
  for (let i = 0, p = 0; i < gray.length; i++, p += 4) {
    gray[i] = 0.299 * data[p] + 0.587 * data[p + 1] + 0.114 * data[p + 2]
  }
  return { gray, width, height }
}

interface Line { at: (t: number) => number; slope: number }

/**
 * Finds one straight edge of the card. The side is split into segments; in each, walking from outside the guide
 * inwards, the first strong gradient is the card's edge (anything further in is the card's own artwork). A line
 * through the segments that agree is the edge; tilt is fine, scattered hits (a hand, a busy table) are not.
 */
function findEdge(f: Frame, guide: Rect, side: 'left' | 'right' | 'top' | 'bottom', threshold: number): Line | null {
  const { gray, width: w, height: h } = f
  const vertical = side === 'left' || side === 'right'
  const gw = guide.x1 - guide.x0
  const gh = guide.y1 - guide.y0
  // Along the edge: the middle of the guide. Across it: from the frame border to a third of the way in.
  const alongFrom = vertical ? guide.y0 + gh * 0.08 : guide.x0 + gw * 0.08
  const alongTo = vertical ? guide.y1 - gh * 0.08 : guide.x1 - gw * 0.08
  const across = vertical ? w : h
  const reach = (vertical ? gw : gh) * 0.36
  const outer = side === 'left' || side === 'top' ? 1 : across - 2
  const inner = side === 'left' ? guide.x0 + reach : side === 'right' ? guide.x1 - reach
    : side === 'top' ? guide.y0 + reach : guide.y1 - reach
  const step = outer < inner ? 1 : -1
  const segment = (alongTo - alongFrom) / SEGMENTS

  const strength = (pos: number, from: number, to: number) => {
    let s = 0
    let n = 0
    for (let t = from; t < to; t++) {
      s += vertical
        ? Math.abs(gray[t * w + pos + 1] - gray[t * w + pos - 1])
        : Math.abs(gray[(pos + 1) * w + t] - gray[(pos - 1) * w + t])
      n++
    }
    return n ? s / n : 0
  }

  const points: [number, number][] = []
  for (let i = 0; i < SEGMENTS; i++) {
    const from = Math.max(1, Math.round(alongFrom + i * segment))
    const to = Math.min((vertical ? h : w) - 1, Math.round(alongFrom + (i + 1) * segment))
    for (let pos = outer; step > 0 ? pos <= inner : pos >= inner; pos += step) {
      if (pos < 1 || pos > across - 2) continue
      const s = strength(pos, from, to)
      if (s >= threshold) {
        // Settle on the peak of this edge rather than its first pixel
        let best = pos
        let bestS = s
        for (let k = 1; k <= 3; k++) {
          const p = pos + k * step
          if (p < 1 || p > across - 2) break
          const sk = strength(p, from, to)
          if (sk > bestS) { best = p; bestS = sk }
        }
        points.push([(from + to) / 2, best])
        break
      }
    }
  }
  if (points.length < MIN_INLIERS) return null

  // Best line through pairs of points, by how many points agree with it
  const tolerance = Math.max(2.5, across * 0.015)
  let bestInliers: [number, number][] = []
  for (let a = 0; a < points.length; a++) {
    for (let b = a + 1; b < points.length; b++) {
      const slope = (points[b][1] - points[a][1]) / (points[b][0] - points[a][0])
      if (Math.abs(slope) > MAX_SLOPE) continue
      const inliers = points.filter(([t, p]) => Math.abs(p - (points[a][1] + slope * (t - points[a][0]))) <= tolerance)
      if (inliers.length > bestInliers.length) bestInliers = inliers
    }
  }
  if (bestInliers.length < MIN_INLIERS) return null
  // Least squares through the agreeing points
  const n = bestInliers.length
  const mt = bestInliers.reduce((s, [t]) => s + t, 0) / n
  const mp = bestInliers.reduce((s, [, p]) => s + p, 0) / n
  const den = bestInliers.reduce((s, [t]) => s + (t - mt) ** 2, 0)
  const slope = den ? bestInliers.reduce((s, [t, p]) => s + (t - mt) * (p - mp), 0) / den : 0
  return { at: (t) => mp + slope * (t - mt), slope }
}

function percentiles(f: Frame, r: Rect): { p10: number; p90: number; glare: number } {
  const hist = new Uint32Array(256)
  let n = 0
  for (let y = Math.round(r.y0); y < Math.round(r.y1); y++) {
    for (let x = Math.round(r.x0); x < Math.round(r.x1); x++) {
      hist[Math.min(255, Math.max(0, Math.round(f.gray[y * f.width + x])))]++
      n++
    }
  }
  let acc = 0
  let p10 = 0
  let p90 = 255
  let found10 = false
  for (let v = 0; v < 256; v++) {
    acc += hist[v]
    if (!found10 && acc >= n * 0.1) { p10 = v; found10 = true }
    if (acc >= n * 0.9) { p90 = v; break }
  }
  let blown = 0
  for (let v = GLARE_LEVEL; v < 256; v++) blown += hist[v]
  return { p10, p90, glare: n ? blown / n : 0 }
}

/**
 * Focus score: variance of the Laplacian after a 3x3 smooth (so sensor noise doesn't count as detail), divided by
 * the card's contrast so dim but sharp photos still pass. Roughly 0-100+; low means blurry.
 */
function sharpness(f: Frame, r: Rect, contrast: number): number {
  const { gray, width: w, height: h } = f
  const x0 = Math.max(2, Math.round(r.x0))
  const x1 = Math.min(w - 2, Math.round(r.x1))
  const y0 = Math.max(2, Math.round(r.y0))
  const y1 = Math.min(h - 2, Math.round(r.y1))
  if (x1 - x0 < 4 || y1 - y0 < 4) return 0
  const sm = (x: number, y: number) => {
    let s = 0
    for (let dy = -1; dy <= 1; dy++) {
      const row = (y + dy) * w
      s += gray[row + x - 1] + gray[row + x] + gray[row + x + 1]
    }
    return s / 9
  }
  let sum = 0
  let sq = 0
  let n = 0
  for (let y = y0 + 1; y < y1 - 1; y++) {
    for (let x = x0 + 1; x < x1 - 1; x++) {
      const lap = sm(x - 1, y) + sm(x + 1, y) + sm(x, y - 1) + sm(x, y + 1) - 4 * sm(x, y)
      sum += lap
      sq += lap * lap
      n++
    }
  }
  const mean = sum / n
  return ((sq / n - mean * mean) / Math.max(400, contrast * contrast)) * 1000
}

function inset(r: Rect, by: number): Rect {
  const dx = (r.x1 - r.x0) * by
  const dy = (r.y1 - r.y0) * by
  return { x0: r.x0 + dx, y0: r.y0 + dy, x1: r.x1 - dx, y1: r.y1 - dy }
}

/**
 * Scores one frame. `guide` is where the on-screen card guide sits in the frame; with `findCard` false (an
 * uploaded photo) the whole frame is treated as the card.
 */
export function analyze(f: Frame, guide: Rect, findCard: boolean): QualityReport {
  const report = (hint: string | null, card: Rect | null, edges: number, light = { p10: 0, p90: 0, glare: 0 }, sharp = 0): QualityReport =>
    ({ ok: hint === null, hint, card, edges, brightness: light.p90, glare: light.glare, sharpness: sharp })

  let card: Rect = guide
  let edges = 4
  if (findCard) {
    const scene = percentiles(f, guide)
    if (scene.p90 < DARK * 0.7) return report('Needs more light: move near a window or turn on a light', null, 0, scene)

    // Edge threshold scales with how busy the scene is
    let g = 0
    let m = 0
    for (let i = 1; i < f.gray.length - 1; i += 7) { g += Math.abs(f.gray[i + 1] - f.gray[i - 1]); m++ }
    const threshold = Math.max(MIN_EDGE, (g / Math.max(1, m)) * 2.2)

    const left = findEdge(f, guide, 'left', threshold)
    const right = findEdge(f, guide, 'right', threshold)
    const top = findEdge(f, guide, 'top', threshold)
    const bottom = findEdge(f, guide, 'bottom', threshold)
    const lines = [left, right, top, bottom]
    edges = lines.filter(Boolean).length
    if (!left || !right || !top || !bottom) {
      return report(edges <= 1 ? 'Place the card inside the frame' : 'Show the whole card: all four edges inside the frame',
        null, edges, scene)
    }
    const cy = (guide.y0 + guide.y1) / 2
    const cx = (guide.x0 + guide.x1) / 2
    card = { x0: left.at(cy), x1: right.at(cy), y0: top.at(cx), y1: bottom.at(cx) }
    const cw = card.x1 - card.x0
    const ch = card.y1 - card.y0
    if (cw <= 0 || ch <= 0) return report('Place the card inside the frame', null, edges, scene)
    if (ch < (guide.y1 - guide.y0) * MIN_HEIGHT) return report('Move closer so the card fills the frame', card, edges, scene)
    const ratio = cw / ch
    if (ratio < CARD_RATIO * 0.8 || ratio > CARD_RATIO * 1.22) {
      return report('Hold the phone flat above the card, not at an angle', card, edges, scene)
    }
  }

  const area = inset(card, 0.04)
  const light = percentiles(f, area)
  const sharp = sharpness(f, inset(card, 0.08), light.p90 - light.p10)
  let hint: string | null = null
  if (light.p90 < DARK) hint = 'Needs more light: move near a window or turn on a light'
  else if (light.p10 > BRIGHT) hint = 'Too bright: move away from the direct light'
  else if (light.glare > MAX_GLARE) hint = 'Glare on the card: tilt it slightly away from the light'
  else if (sharp < MIN_SHARPNESS) hint = findCard ? 'Hold steady: the picture is blurry' : 'This photo looks blurry'
  return report(hint, findCard ? card : null, edges, light, sharp)
}
