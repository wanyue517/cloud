import { isLockedError } from '@repo/drive-core/errors'

export function handleVaultLockedError(
  error: unknown,
  clearTokens: () => void,
  onLocked: () => void,
): boolean {
  if (!isLockedError(error)) return false
  clearTokens()
  onLocked()
  return true
}

/**
 * 将站内消息中的 PC 端 actionUrl 智能映射至移动端路由。
 */
export function mapMessageActionUrlToMobile(actionUrl?: string | null): string {
  const raw = (actionUrl || '').trim()
  if (!raw) return ''
  if (raw.startsWith('/m/')) return raw.slice(2) || '/home'
  if (raw === '/app' || raw === '/app/' || raw === '/') return '/home'
  if (raw.startsWith('/app/shared/received/')) {
    return raw.replace(/^\/app/, '')
  }
  if (raw.startsWith('/app/shared')) return '/shared'
  if (raw.startsWith('/app/trash')) return '/trash'
  if (raw.startsWith('/app/messages')) return '/messages'
  if (raw.startsWith('/app/private')) return '/private'
  if (raw.startsWith('/app/search')) return '/search'
  const spaceMatch = /^\/app\/spaces\/(\d+)(?:\/folders\/(\d+))?/.exec(raw)
  if (spaceMatch) {
    return spaceMatch[2]
      ? `/files/${spaceMatch[1]}/folders/${spaceMatch[2]}`
      : `/files/${spaceMatch[1]}`
  }
  if (raw.startsWith('/s/')) return raw
  if (raw.startsWith('/files') || raw.startsWith('/shared') || raw.startsWith('/home')) return raw
  return '/home'
}
