/**
 * Photo quality checks for the card scanner, run on a small grayscale copy of the frame so they are cheap enough
 * to run several times a second. Thresholds are on a 0-255 brightness scale.
 */

export interface QualityReport {
  brightness: number
  glare: number
  sharpness: number
  /** How many of the four card edges line up with the guide (camera only). */
  edges: number
  motion: number
  hint: string | null
}

export const ANALYSIS_WIDTH = 200
const DARK = 70
const BRIGHT = 225
const GLARE_LEVEL = 248
const MAX_GLARE = 0.03
const MIN_SHARPNESS = 45
const MIN_EDGE = 14
const MAX_MOTION = 9

/** Grayscale copy of a region of the source, scaled to ANALYSIS_WIDTH wide. */
export function grayscale(
  source: CanvasImageSource, sx: number, sy: number, sw: number, sh: number, canvas: HTMLCanvasElement,
): { gray: Float32Array; width: number; height: number } | null {
  const width = ANALYSIS_WIDTH
  const height = Math.max(1, Math.round((ANALYSIS_WIDTH * sh) / sw))
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

/**
 * Scores one frame. `pad` is the margin (as a fraction of each side) around the card guide that was included in the
 * sample, so the guide edges sit at pad and 1 - pad. `previous` is the last frame, for motion.
 */
export function analyze(
  frame: { gray: Float32Array; width: number; height: number },
  pad: number,
  previous: Float32Array | null,
  checkEdges: boolean,
): QualityReport {
  const { gray, width: w, height: h } = frame
  const x0 = Math.round(w * pad)
  const x1 = Math.round(w * (1 - pad))
  const y0 = Math.round(h * pad)
  const y1 = Math.round(h * (1 - pad))

  // Brightness and glare inside the card area
  let sum = 0
  let blown = 0
  let n = 0
  for (let y = y0; y < y1; y++) {
    for (let x = x0; x < x1; x++) {
      const v = gray[y * w + x]
      sum += v
      if (v >= GLARE_LEVEL) blown++
      n++
    }
  }
  const brightness = n ? sum / n : 0
  const glare = n ? blown / n : 0

  // Sharpness: variance of the Laplacian inside the card area
  let lapSum = 0
  let lapSq = 0
  let m = 0
  for (let y = Math.max(1, y0); y < Math.min(h - 1, y1); y++) {
    for (let x = Math.max(1, x0); x < Math.min(w - 1, x1); x++) {
      const i = y * w + x
      const lap = gray[i - 1] + gray[i + 1] + gray[i - w] + gray[i + w] - 4 * gray[i]
      lapSum += lap
      lapSq += lap * lap
      m++
    }
  }
  const mean = m ? lapSum / m : 0
  const sharpness = m ? lapSq / m - mean * mean : 0

  // Framing: a card lined up with the guide gives a straight, strong edge along each side of it. For each side,
  // take the strongest line of gradients near the guide edge; a tilted or missing card smears it out.
  let edges = 4
  if (checkEdges) {
    const bandX = Math.max(2, Math.round(w * 0.05))
    const bandY = Math.max(2, Math.round(h * 0.05))
    const vertical = (x: number) => {
      let s = 0
      let c = 0
      for (let y = y0 + Math.round((y1 - y0) * 0.1); y < y1 - Math.round((y1 - y0) * 0.1); y++) {
        s += Math.abs(gray[y * w + x + 1] - gray[y * w + x - 1])
        c++
      }
      return c ? s / c : 0
    }
    const horizontal = (y: number) => {
      let s = 0
      let c = 0
      for (let x = x0 + Math.round((x1 - x0) * 0.1); x < x1 - Math.round((x1 - x0) * 0.1); x++) {
        s += Math.abs(gray[(y + 1) * w + x] - gray[(y - 1) * w + x])
        c++
      }
      return c ? s / c : 0
    }
    const strongest = (from: number, to: number, max: number, line: (i: number) => number) => {
      let best = 0
      for (let i = Math.max(1, from); i <= Math.min(max - 2, to); i++) best = Math.max(best, line(i))
      return best
    }
    const sides = [
      strongest(x0 - bandX, x0 + bandX, w, vertical),
      strongest(x1 - bandX, x1 + bandX, w, vertical),
      strongest(y0 - bandY, y0 + bandY, h, horizontal),
      strongest(y1 - bandY, y1 + bandY, h, horizontal),
    ]
    edges = sides.filter((s) => s >= MIN_EDGE).length
  }

  let motion = 0
  if (previous && previous.length === gray.length) {
    let d = 0
    for (let i = 0; i < gray.length; i += 3) d += Math.abs(gray[i] - previous[i])
    motion = d / Math.ceil(gray.length / 3)
  }

  let hint: string | null = null
  if (brightness < DARK) hint = 'Needs more light: move near a window or turn on a light'
  else if (brightness > BRIGHT) hint = 'Too bright: move away from the direct light'
  else if (motion > MAX_MOTION) hint = 'Hold steady'
  else if (checkEdges && edges <= 1) hint = 'Fit the card inside the frame'
  else if (checkEdges && edges < 3) hint = 'Straighten the card so its edges line up with the frame'
  else if (glare > MAX_GLARE) hint = 'Glare on the card: tilt it slightly away from the light'
  else if (sharpness < MIN_SHARPNESS) hint = checkEdges ? 'Blurry: hold steady or move a little further away' : 'This photo looks blurry'
  return { brightness, glare, sharpness, edges, motion, hint }
}
