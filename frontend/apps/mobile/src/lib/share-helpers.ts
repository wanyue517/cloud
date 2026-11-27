const CODE_ALPHABET = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789'

export function generateExtractCode(length = 4): string {
  let out = ''
  for (let i = 0; i < length; i++) {
    const idx = Math.floor(Math.random() * CODE_ALPHABET.length)
    out += CODE_ALPHABET[idx] ?? 'A'
  }
  return out
}

export function buildMobileShareUrl(origin: string, token: string): string {
  const cleanOrigin = origin.replace(/\/+$/, '')
  return `${cleanOrigin}/m/s/${encodeURIComponent(token)}`
}

export function buildWechatShareText(params: {
  name: string
  url: string
  password?: string
  expiresAt?: string
}): string {
  const lines = [`我通过 Cloudlane 分享了「${params.name}」`, `链接：${params.url}`]
  if (params.password) {
    lines.push(`提取码：${params.password}`)
  }
  if (params.expiresAt) {
    lines.push(`有效期至：${params.expiresAt}`)
  }
  lines.push('复制此条消息在浏览器打开即可查看')
  return lines.join('\n')
}

export type ShareLinkStatus = 'ACTIVE' | 'DISABLED' | 'EXPIRED' | 'LIMIT_REACHED'

export function resolveShareLinkStatus(
  item: {
    disabledAt?: string | null
    expiresAt?: string | null
    maxDownloads?: number | null
    downloadCount?: number
  },
  nowMs = Date.now(),
): ShareLinkStatus {
  if (item.disabledAt) return 'DISABLED'
  if (item.expiresAt) {
    const exp = Date.parse(item.expiresAt)
    if (Number.isFinite(exp) && exp <= nowMs) return 'EXPIRED'
  }
  if (
    typeof item.maxDownloads === 'number' &&
    item.maxDownloads > 0 &&
    typeof item.downloadCount === 'number' &&
    item.downloadCount >= item.maxDownloads
  ) {
    return 'LIMIT_REACHED'
  }
  return 'ACTIVE'
}

/**
 * 严格截断共享文件夹祖先路径：仅保留授权根及其以下层级，严禁泄露父级路径。
 */
export function truncateSharedBreadcrumbs<T extends { id: string }>(
  ancestors: readonly T[],
  authorizedRootId: string,
): T[] {
  const rootIdx = ancestors.findIndex(a => a.id === authorizedRootId)
  if (rootIdx < 0) return []
  return ancestors.slice(rootIdx)
}
