import { reactive } from 'vue'

export const NETWORK_PREFS_STORAGE_KEY = 'cloudlane.mobile.network'

export type NetworkKind = 'wifi' | 'cellular' | 'offline' | 'unknown'

export interface NetworkPreferences {
  wifiOnlyUpload: boolean
  warnCellularVideo: boolean
  lowResThumbOnCellular: boolean
}

export interface NetworkStatus {
  online: boolean
  kind: NetworkKind
  effectiveType: string
  saveData: boolean
}

export const DEFAULT_NETWORK_PREFS: Readonly<NetworkPreferences> = Object.freeze({
  wifiOnlyUpload: false,
  warnCellularVideo: true,
  lowResThumbOnCellular: true,
})

interface BrowserConnectionLike {
  type?: string
  effectiveType?: string
  saveData?: boolean
  addEventListener?: (event: string, listener: () => void) => void
  removeEventListener?: (event: string, listener: () => void) => void
}

export function normalizeNetworkPreferences(raw: unknown): NetworkPreferences {
  if (!raw || typeof raw !== 'object' || Array.isArray(raw)) {
    return { ...DEFAULT_NETWORK_PREFS }
  }
  const value = raw as Record<string, unknown>
  return {
    wifiOnlyUpload:
      typeof value.wifiOnlyUpload === 'boolean' ? value.wifiOnlyUpload : DEFAULT_NETWORK_PREFS.wifiOnlyUpload,
    warnCellularVideo:
      typeof value.warnCellularVideo === 'boolean' ? value.warnCellularVideo : DEFAULT_NETWORK_PREFS.warnCellularVideo,
    lowResThumbOnCellular:
      typeof value.lowResThumbOnCellular === 'boolean'
        ? value.lowResThumbOnCellular
        : DEFAULT_NETWORK_PREFS.lowResThumbOnCellular,
  }
}

export function loadNetworkPreferences(
  storage: Pick<Storage, 'getItem'> | null | undefined = typeof globalThis.localStorage !== 'undefined'
    ? globalThis.localStorage
    : null,
): NetworkPreferences {
  try {
    const raw = storage?.getItem(NETWORK_PREFS_STORAGE_KEY)
    if (!raw) return { ...DEFAULT_NETWORK_PREFS }
    return normalizeNetworkPreferences(JSON.parse(raw))
  } catch {
    return { ...DEFAULT_NETWORK_PREFS }
  }
}

export function saveNetworkPreferences(
  prefs: Partial<NetworkPreferences>,
  storage: Pick<Storage, 'setItem'> | null | undefined = typeof globalThis.localStorage !== 'undefined'
    ? globalThis.localStorage
    : null,
): NetworkPreferences {
  const merged = normalizeNetworkPreferences({ ...networkPrefs, ...prefs })
  networkPrefs.wifiOnlyUpload = merged.wifiOnlyUpload
  networkPrefs.warnCellularVideo = merged.warnCellularVideo
  networkPrefs.lowResThumbOnCellular = merged.lowResThumbOnCellular
  try {
    storage?.setItem(NETWORK_PREFS_STORAGE_KEY, JSON.stringify(merged))
  } catch {
    // Ignore storage quota/security errors
  }
  return merged
}

export function detectNetworkStatus(
  online: boolean,
  connection?: BrowserConnectionLike | null,
): NetworkStatus {
  if (!online) {
    return { online: false, kind: 'offline', effectiveType: 'none', saveData: Boolean(connection?.saveData) }
  }
  const rawType = (connection?.type || '').toLowerCase()
  const effectiveType = (connection?.effectiveType || '').toLowerCase()
  let kind: NetworkKind = 'unknown'
  if (rawType === 'wifi' || rawType === 'ethernet') {
    kind = 'wifi'
  } else if (rawType === 'cellular' || ['2g', '3g', '4g', 'slow-2g'].includes(effectiveType)) {
    kind = 'cellular'
  }
  return {
    online: true,
    kind,
    effectiveType: effectiveType || rawType || 'unknown',
    saveData: Boolean(connection?.saveData),
  }
}

export function shouldConfirmUploadOnNetwork(prefs: NetworkPreferences, status: NetworkStatus): boolean {
  if (!status.online) return false
  return prefs.wifiOnlyUpload && (status.kind === 'cellular' || status.saveData)
}

export function shouldWarnVideoOnNetwork(prefs: NetworkPreferences, status: NetworkStatus): boolean {
  if (!status.online) return false
  return prefs.warnCellularVideo && (status.kind === 'cellular' || status.saveData)
}

export function getMobileThumbWidth(
  prefs: NetworkPreferences,
  status: NetworkStatus,
  dpr = typeof window !== 'undefined' ? window.devicePixelRatio || 1 : 1,
): number {
  if (prefs.lowResThumbOnCellular && (status.kind === 'cellular' || status.saveData)) {
    return 240
  }
  return dpr >= 2.5 ? 480 : 360
}

export const networkPrefs = reactive<NetworkPreferences>(loadNetworkPreferences())
export const networkStatus = reactive<NetworkStatus>({
  online: typeof navigator !== 'undefined' ? navigator.onLine : true,
  kind: 'unknown',
  effectiveType: 'unknown',
  saveData: false,
})

let initialized = false

function readBrowserConnection(): BrowserConnectionLike | null {
  if (typeof navigator === 'undefined') return null
  const nav = navigator as Navigator & { connection?: BrowserConnectionLike }
  return nav.connection ?? null
}

function refreshBrowserNetworkStatus() {
  const online = typeof navigator !== 'undefined' ? navigator.onLine : true
  const next = detectNetworkStatus(online, readBrowserConnection())
  networkStatus.online = next.online
  networkStatus.kind = next.kind
  networkStatus.effectiveType = next.effectiveType
  networkStatus.saveData = next.saveData
}

export function initNetworkGuard() {
  if (initialized || typeof window === 'undefined') return
  initialized = true
  refreshBrowserNetworkStatus()
  window.addEventListener('online', refreshBrowserNetworkStatus)
  window.addEventListener('offline', refreshBrowserNetworkStatus)
  readBrowserConnection()?.addEventListener?.('change', refreshBrowserNetworkStatus)
}
