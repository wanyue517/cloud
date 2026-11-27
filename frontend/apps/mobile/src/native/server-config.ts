const SERVER_ORIGIN_KEY = 'cloudlane.mobile.serverOrigin'

/**
 * 规范化用户输入的自托管服务器地址：
 * - 允许 http:// 或 https://（兼容内网自托管 IP/端口与公网域名）
 * - 自动去除末尾斜杠以及误粘贴的 /api、/m 路径后缀
 * - 非法 URL 抛出友好错误
 */
export function normalizeServerOrigin(raw: string): string {
  const trimmed = raw.trim()
  if (!trimmed) return ''
  const hasScheme = /^[a-zA-Z][a-zA-Z0-9+.-]*:\/\//.test(trimmed)
  const withProto = hasScheme ? trimmed : `https://${trimmed}`
  let parsed: URL
  try {
    parsed = new URL(withProto)
  } catch {
    throw new Error('服务器地址格式无效，请输入如 https://cloud.example.com 或 http://192.168.1.10:8000')
  }
  if (parsed.protocol !== 'http:' && parsed.protocol !== 'https:') {
    throw new Error('仅支持 http:// 或 https:// 协议')
  }
  if (!parsed.hostname) {
    throw new Error('缺少有效的主机名或 IP 地址')
  }
  return parsed.origin
}

export function buildApiBaseUrl(serverOrigin: string): string {
  const clean = serverOrigin.trim().replace(/\/+$/, '')
  return clean ? `${clean}/api` : '/api'
}

export function getSavedServerOrigin(): string {
  try {
    const raw = globalThis.localStorage?.getItem(SERVER_ORIGIN_KEY) ?? ''
    return raw ? normalizeServerOrigin(raw) : ''
  } catch {
    return ''
  }
}

export function persistServerOriginLocal(origin: string): string {
  const normalized = normalizeServerOrigin(origin)
  try {
    if (normalized) {
      globalThis.localStorage?.setItem(SERVER_ORIGIN_KEY, normalized)
    } else {
      globalThis.localStorage?.removeItem(SERVER_ORIGIN_KEY)
    }
  } catch {
    // ignore storage errors
  }
  return normalized
}

export async function syncServerOriginToNative(origin: string): Promise<void> {
  try {
    const { Preferences } = await import('@capacitor/preferences')
    if (origin) {
      await Preferences.set({ key: SERVER_ORIGIN_KEY, value: origin })
    } else {
      await Preferences.remove({ key: SERVER_ORIGIN_KEY })
    }
  } catch {
    // Web H5 fallback already saved to localStorage
  }
}

export async function hydrateServerOriginFromNative(): Promise<string> {
  const local = getSavedServerOrigin()
  if (local) return local
  try {
    const { Preferences } = await import('@capacitor/preferences')
    const { value } = await Preferences.get({ key: SERVER_ORIGIN_KEY })
    if (value) {
      return persistServerOriginLocal(value)
    }
  } catch {
    // ignore on web
  }
  return ''
}
