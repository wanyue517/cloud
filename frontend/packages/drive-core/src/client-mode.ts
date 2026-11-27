export const CLIENT_MODE_STORAGE_KEY = 'cloudlane.clientMode'

export type ClientModePreference = 'auto' | 'mobile' | 'desktop'

export interface SimpleStorage {
  getItem(key: string): string | null
  setItem(key: string, value: string): void
}

const MOBILE_UA_REGEX = /Android|webOS|iPhone|iPad|iPod|BlackBerry|IEMobile|Opera Mini|Mobile|HarmonyOS/i

export function isMobileUserAgent(userAgent?: string): boolean {
  const ua = userAgent ?? (typeof navigator !== 'undefined' ? navigator.userAgent : '')
  return MOBILE_UA_REGEX.test(ua)
}

export function loadClientModePreference(
  storage: Pick<SimpleStorage, 'getItem'> | null | undefined = typeof globalThis.localStorage !== 'undefined'
    ? globalThis.localStorage
    : null,
): ClientModePreference {
  try {
    const raw = storage?.getItem(CLIENT_MODE_STORAGE_KEY)
    if (raw === 'mobile' || raw === 'desktop' || raw === 'auto') return raw
  } catch {
    // Ignore storage access restrictions
  }
  return 'auto'
}

export function saveClientModePreference(
  mode: ClientModePreference,
  storage: Pick<SimpleStorage, 'setItem'> | null | undefined = typeof globalThis.localStorage !== 'undefined'
    ? globalThis.localStorage
    : null,
): ClientModePreference {
  try {
    storage?.setItem(CLIENT_MODE_STORAGE_KEY, mode)
  } catch {
    // Ignore storage quota/security errors
  }
  return mode
}

export function mapWebPathToMobile(webPath: string): string {
  const [pathname = '', rest = ''] = webPath.split(/(?=[?#])/, 2)
  if (pathname === '/m' || pathname.startsWith('/m/')) return webPath

  const folderMatch = /^\/app\/spaces\/(\d+)\/folders\/(\d+)\/?$/.exec(pathname)
  if (folderMatch) return `/m/files/${folderMatch[1]}/folders/${folderMatch[2]}${rest}`

  const spaceMatch = /^\/app\/spaces\/(\d+)\/?$/.exec(pathname)
  if (spaceMatch) return `/m/files/${spaceMatch[1]}${rest}`

  const shareMatch = /^\/s\/([A-Za-z0-9_-]+)\/?$/.exec(pathname)
  if (shareMatch) return `/m/s/${shareMatch[1]}${rest}`

  if (pathname === '/auth/login') return `/m/auth/login${rest}`
  if (pathname === '/auth/register') return `/m/auth/register${rest}`
  if (pathname === '/auth/forgot-password') return `/m/auth/forgot-password${rest}`
  if (pathname === '/app/shared') return `/m/shared${rest}`
  if (pathname === '/app/search') return `/m/search${rest}`
  if (pathname === '/app/tasks') return `/m/tasks${rest}`
  if (pathname === '/app/trash' || pathname === '/app/settings/trash') return `/m/trash${rest}`
  if (pathname === '/app/settings/private') return `/m/private${rest}`

  const settingMatch = /^\/app\/settings\/(profile|security|appearance|files)\/?$/.exec(pathname)
  if (settingMatch) return `/m/settings/${settingMatch[1]}${rest}`

  if (pathname === '/app/favorites' || pathname === '/app/recent' || pathname === '/app' || pathname === '/') {
    return `/m/home${rest}`
  }
  return `/m/home`
}

export function mapMobilePathToWeb(mobilePath: string): string {
  const [pathname = '', rest = ''] = mobilePath.split(/(?=[?#])/, 2)
  const stripped = pathname.startsWith('/m/') ? pathname.slice(2) : pathname === '/m' ? '/' : pathname

  const folderMatch = /^\/files\/(\d+)\/folders\/(\d+)\/?$/.exec(stripped)
  if (folderMatch) return `/app/spaces/${folderMatch[1]}/folders/${folderMatch[2]}${rest}`

  const spaceMatch = /^\/files\/(\d+)\/?$/.exec(stripped)
  if (spaceMatch) return `/app/spaces/${spaceMatch[1]}${rest}`

  const shareMatch = /^\/s\/([A-Za-z0-9_-]+)\/?$/.exec(stripped)
  if (shareMatch) return `/s/${shareMatch[1]}${rest}`

  if (stripped === '/auth/login') return `/auth/login${rest}`
  if (stripped === '/auth/register') return `/auth/register${rest}`
  if (stripped === '/auth/forgot-password') return `/auth/forgot-password${rest}`
  if (stripped === '/shared' || stripped.startsWith('/shared/')) return `/app/shared${rest}`
  if (stripped === '/search') return `/app/search${rest}`
  if (stripped === '/tasks') return `/app/tasks${rest}`
  if (stripped === '/trash') return `/app/settings/trash${rest}`
  if (stripped === '/private') return `/app/settings/private${rest}`
  if (stripped === '/admin-overview') return `/admin/`

  const settingMatch = /^\/settings\/(profile|security|appearance|files)\/?$/.exec(stripped)
  if (settingMatch) return `/app/settings/${settingMatch[1]}${rest}`

  return `/app`
}
