export const SEARCH_HISTORY_STORAGE_KEY = 'cloudlane.mobile.searchHistory'

export interface HighlightSegment {
  text: string
  hit: boolean
}

export function loadSearchHistory(
  storage: Pick<Storage, 'getItem'> | null | undefined = typeof globalThis.localStorage !== 'undefined'
    ? globalThis.localStorage
    : null,
): string[] {
  try {
    const raw = storage?.getItem(SEARCH_HISTORY_STORAGE_KEY)
    if (!raw) return []
    const parsed = JSON.parse(raw)
    if (!Array.isArray(parsed)) return []
    return parsed.filter((v): v is string => typeof v === 'string' && v.trim().length > 0)
  } catch {
    return []
  }
}

export function pushSearchHistory(
  query: string,
  storage: Pick<Storage, 'getItem' | 'setItem'> | null | undefined = typeof globalThis.localStorage !== 'undefined'
    ? globalThis.localStorage
    : null,
  maxItems = 12,
): string[] {
  const clean = query.trim()
  const existing = loadSearchHistory(storage)
  if (!clean) return existing
  const next = [clean, ...existing.filter(item => item !== clean)].slice(0, maxItems)
  try {
    storage?.setItem(SEARCH_HISTORY_STORAGE_KEY, JSON.stringify(next))
  } catch {
    // ignore storage errors
  }
  return next
}

export function removeSearchHistoryItem(
  query: string,
  storage: Pick<Storage, 'getItem' | 'setItem'> | null | undefined = typeof globalThis.localStorage !== 'undefined'
    ? globalThis.localStorage
    : null,
): string[] {
  const next = loadSearchHistory(storage).filter(item => item !== query)
  try {
    storage?.setItem(SEARCH_HISTORY_STORAGE_KEY, JSON.stringify(next))
  } catch {
    // ignore storage errors
  }
  return next
}

export function clearSearchHistory(
  storage: Pick<Storage, 'removeItem'> | null | undefined = typeof globalThis.localStorage !== 'undefined'
    ? globalThis.localStorage
    : null,
): string[] {
  try {
    storage?.removeItem(SEARCH_HISTORY_STORAGE_KEY)
  } catch {
    // ignore storage errors
  }
  return []
}

export function tokenizeHighlight(text: string, query: string): HighlightSegment[] {
  if (!text) return []
  const q = query.trim()
  if (!q) return [{ text, hit: false }]
  const escaped = q.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
  const regex = new RegExp(`(${escaped})`, 'gi')
  const parts = text.split(regex).filter(p => p.length > 0)
  return parts.map(part => ({
    text: part,
    hit: part.toLowerCase() === q.toLowerCase(),
  }))
}
