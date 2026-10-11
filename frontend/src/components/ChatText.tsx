import { Fragment } from 'react'
import type { ReactNode } from 'react'

/** "**bold**" (or "__bold__") becomes <strong>; everything else stays plain text. */
function inline(text: string): ReactNode[] {
  return text.split(/(\*\*[^*\n]+\*\*|__[^_\n]+__)/g).map((part, i) =>
    /^(\*\*|__).+(\*\*|__)$/.test(part)
      ? <strong key={i}>{part.slice(2, -2)}</strong>
      : <Fragment key={i}>{part.replace(/\*\*/g, '')}</Fragment>)
}

/**
 * Assistant messages as light formatting: paragraphs, "- " / "* " / "• " bullet lists and bold.
 * Built from React elements only (no HTML injection), so the model's text can't add markup.
 */
export function ChatText({ text }: { text: string }) {
  const blocks: ReactNode[] = []
  let bullets: string[] = []
  let paragraph: string[] = []

  const flushParagraph = () => {
    if (paragraph.length) {
      blocks.push(<p key={blocks.length}>{paragraph.flatMap((line, i) => (i ? [<br key={`b${i}`} />, ...inline(line)] : inline(line)))}</p>)
      paragraph = []
    }
  }
  const flushBullets = () => {
    if (bullets.length) {
      blocks.push(<ul key={blocks.length}>{bullets.map((b, i) => <li key={i}>{inline(b)}</li>)}</ul>)
      bullets = []
    }
  }

  for (const raw of text.split('\n')) {
    const line = raw.trim().replace(/^#{1,6}\s+/, '')
    const bullet = line.match(/^(?:[-*•]|\d+[.)])\s+(.*)$/)
    if (bullet) {
      flushParagraph()
      bullets.push(bullet[1])
    } else if (!line) {
      flushParagraph()
      flushBullets()
    } else {
      flushBullets()
      paragraph.push(line)
    }
  }
  flushParagraph()
  flushBullets()
  return <>{blocks}</>
}
