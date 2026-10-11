import { useCallback, useEffect, useState } from 'react'

export type Theme = 'light' | 'dark'

const STORAGE_KEY = 'tc-theme'
const darkQuery = () => window.matchMedia?.('(prefers-color-scheme: dark)')

function savedTheme(): Theme | null {
  try {
    const value = localStorage.getItem(STORAGE_KEY)
    return value === 'light' || value === 'dark' ? value : null
  } catch {
    return null
  }
}

function systemTheme(): Theme {
  return darkQuery()?.matches ? 'dark' : 'light'
}

/**
 * The active theme: the visitor's choice when they made one (stored and set as data-theme on
 * <html>), otherwise the system setting. index.html applies a saved choice before first paint.
 */
export function useTheme() {
  const [theme, setTheme] = useState<Theme>(() => savedTheme() ?? systemTheme())

  useEffect(() => {
    const query = darkQuery()
    if (!query) return
    const onChange = () => {
      if (!savedTheme()) setTheme(systemTheme())
    }
    query.addEventListener('change', onChange)
    return () => query.removeEventListener('change', onChange)
  }, [])

  const toggle = useCallback(() => {
    setTheme((current) => {
      const next: Theme = current === 'dark' ? 'light' : 'dark'
      document.documentElement.setAttribute('data-theme', next)
      try {
        localStorage.setItem(STORAGE_KEY, next)
      } catch {
        // Private mode: the choice lasts for this page view only
      }
      return next
    })
  }, [])

  return { theme, toggle }
}
