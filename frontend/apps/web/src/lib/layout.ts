export interface LayoutPreferences {
  sidebarCollapsed: boolean
  treeCollapsed: boolean
  treeWidth: number
}

export const LAYOUT_STORAGE_KEY = 'cloudlane.layout'
export const DEFAULT_TREE_WIDTH = 260
export const MIN_TREE_WIDTH = 200
export const MAX_TREE_WIDTH = 400
export const TABLET_BREAKPOINT = 1024
export const MOBILE_BREAKPOINT = 760

export const DEFAULT_LAYOUT: Readonly<LayoutPreferences> = Object.freeze({
  sidebarCollapsed: false,
  treeCollapsed: false,
  treeWidth: DEFAULT_TREE_WIDTH,
})

export function clampTreeWidth(width: unknown): number {
  if (typeof width !== 'number' || !Number.isFinite(width)) return DEFAULT_TREE_WIDTH
  return Math.min(MAX_TREE_WIDTH, Math.max(MIN_TREE_WIDTH, Math.round(width)))
}

export function normalizeLayoutPreferences(raw: unknown): LayoutPreferences {
  if (!raw || typeof raw !== 'object' || Array.isArray(raw)) {
    return { ...DEFAULT_LAYOUT }
  }
  const value = raw as Record<string, unknown>
  return {
    sidebarCollapsed: typeof value.sidebarCollapsed === 'boolean' ? value.sidebarCollapsed : DEFAULT_LAYOUT.sidebarCollapsed,
    treeCollapsed: typeof value.treeCollapsed === 'boolean' ? value.treeCollapsed : DEFAULT_LAYOUT.treeCollapsed,
    treeWidth: clampTreeWidth(value.treeWidth),
  }
}

export function loadLayoutPreferences(storage: Pick<Storage, 'getItem'> | null | undefined = globalThis.localStorage): LayoutPreferences {
  try {
    const saved = storage?.getItem(LAYOUT_STORAGE_KEY)
    if (!saved) return { ...DEFAULT_LAYOUT }
    return normalizeLayoutPreferences(JSON.parse(saved))
  } catch {
    return { ...DEFAULT_LAYOUT }
  }
}

export function saveLayoutPreferences(
  prefs: LayoutPreferences,
  storage: Pick<Storage, 'setItem'> | null | undefined = globalThis.localStorage,
): LayoutPreferences {
  const normalized = normalizeLayoutPreferences(prefs)
  try {
    storage?.setItem(LAYOUT_STORAGE_KEY, JSON.stringify(normalized))
  } catch {
    // Keep in-memory layout usable when storage is disabled or full.
  }
  return normalized
}

export type ViewportTier = 'desktop' | 'tablet' | 'mobile'

export function resolveViewportTier(width: number): ViewportTier {
  if (!Number.isFinite(width) || width > TABLET_BREAKPOINT) return 'desktop'
  if (width > MOBILE_BREAKPOINT) return 'tablet'
  return 'mobile'
}
