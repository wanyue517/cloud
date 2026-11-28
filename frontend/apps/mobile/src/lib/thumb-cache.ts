export interface ThumbCacheEntry {
  url: string
  bytes: number
}

export interface ThumbCacheStats {
  count: number
  bytes: number
  maxEntries: number
}

export const THUMB_SUPPORTED_EXTS: ReadonlySet<string> = new Set([
  // 图片
  'png', 'jpg', 'jpeg', 'gif', 'webp', 'bmp', 'avif',
  // 视频抽帧封面
  'mp4', 'webm', 'mov', 'mkv', 'avi', 'm4v', 'flv', 'ts', 'wmv', 'ogv',
  // 音频内嵌专辑封面
  'mp3', 'flac', 'm4a', 'ogg', 'opus', 'aac', 'wav',
  // 文档与表格
  'pdf', 'docx', 'pptx', 'xlsx', 'xls', 'csv', 'zip',
  // 文本与代码
  'md', 'markdown', 'mdx', 'txt', 'log', 'env',
  'js', 'mjs', 'cjs', 'jsx', 'ts', 'mts', 'cts', 'tsx', 'vue',
  'html', 'htm', 'css', 'scss', 'less', 'json', 'xml', 'yaml', 'yml',
  'java', 'kt', 'py', 'go', 'rs', 'c', 'cpp', 'h', 'hpp', 'cs',
  'php', 'rb', 'sql', 'sh', 'bash', 'ps1', 'toml', 'ini', 'conf', 'properties',
])

export function supportsFileThumbnail(node: {
  nodeType: string
  name: string
  mimeType?: string
}): boolean {
  if (node.nodeType !== 'FILE') return false
  const mime = (node.mimeType || '').toLowerCase()
  if (mime.startsWith('image/') && !mime.includes('svg')) return true
  const ext = node.name.includes('.') ? node.name.split('.').pop()?.toLowerCase() || '' : ''
  return THUMB_SUPPORTED_EXTS.has(ext)
}

const PERSISTENT_CACHE_NAME = 'cloudlane-mobile-thumbs-v1'
const PERSISTENT_MAX_ENTRIES = 200
const PERSISTENT_MAX_BYTES = 5 * 1024 * 1024

function persistentRequestUrl(key: string): string {
  return `https://cloudlane.local/mobile-thumbs/${encodeURIComponent(key)}`
}

async function readPersistentBlob(key: string): Promise<Blob | null> {
  if (typeof window === 'undefined' || !('caches' in window)) return null
  try {
    const cache = await window.caches.open(PERSISTENT_CACHE_NAME)
    const hit = await cache.match(persistentRequestUrl(key))
    if (!hit) return null
    const blob = await hit.blob()
    return blob.size > 0 ? blob : null
  } catch {
    return null
  }
}

async function writePersistentBlob(key: string, blob: Blob): Promise<void> {
  if (typeof window === 'undefined' || !('caches' in window)) return
  if (!blob || blob.size === 0 || blob.size > PERSISTENT_MAX_BYTES) return
  try {
    const cache = await window.caches.open(PERSISTENT_CACHE_NAME)
    const keys = await cache.keys()
    if (keys.length >= PERSISTENT_MAX_ENTRIES) {
      const removeCount = Math.max(1, keys.length - PERSISTENT_MAX_ENTRIES + 20)
      await Promise.all(keys.slice(0, removeCount).map(req => cache.delete(req)))
    }
    await cache.put(
      persistentRequestUrl(key),
      new Response(blob, {
        headers: { 'Content-Type': blob.type || 'image/webp' },
      }),
    )
  } catch {
    // ignore storage quota errors
  }
}

async function clearPersistentCache(): Promise<void> {
  if (typeof window === 'undefined' || !('caches' in window)) return
  try {
    await window.caches.delete(PERSISTENT_CACHE_NAME)
  } catch {
    // ignore
  }
}

export function createThumbLruCache(
  maxEntries = 120,
  revokeFn: (url: string) => void = url => {
    if (typeof URL !== 'undefined' && typeof URL.revokeObjectURL === 'function') {
      URL.revokeObjectURL(url)
    }
  },
) {
  const entries = new Map<string, ThumbCacheEntry>()
  const inflight = new Map<string, Promise<string | null>>()
  const missing = new Set<string>()
  let totalBytes = 0

  function evictIfNeeded() {
    while (entries.size > maxEntries) {
      const oldestKey = entries.keys().next().value
      if (oldestKey === undefined) break
      const oldest = entries.get(oldestKey)
      entries.delete(oldestKey)
      if (oldest) {
        totalBytes = Math.max(0, totalBytes - oldest.bytes)
        revokeFn(oldest.url)
      }
    }
  }

  return {
    get(key: string): string | null {
      const hit = entries.get(key)
      if (!hit) return null
      // Move to most-recently-used position
      entries.delete(key)
      entries.set(key, hit)
      return hit.url
    },
    set(key: string, url: string, bytes = 0) {
      missing.delete(key)
      const prev = entries.get(key)
      if (prev) {
        totalBytes = Math.max(0, totalBytes - prev.bytes)
        if (prev.url !== url) revokeFn(prev.url)
        entries.delete(key)
      }
      const safeBytes = Math.max(0, bytes)
      entries.set(key, { url, bytes: safeBytes })
      totalBytes += safeBytes
      evictIfNeeded()
    },
    async getOrFetch(key: string, fetcher: () => Promise<Blob | null>): Promise<string | null> {
      const existing = this.get(key)
      if (existing) return existing
      if (missing.has(key)) return null
      const pending = inflight.get(key)
      if (pending) return pending

      const task = (async () => {
        try {
          const persisted = await readPersistentBlob(key)
          if (persisted) {
            const url = URL.createObjectURL(persisted)
            this.set(key, url, persisted.size)
            return url
          }
          const blob = await fetcher()
          if (!blob || blob.size === 0) {
            if (!blob) missing.add(key) // An empty 202 response is still being prepared, not permanently missing.
            return null
          }
          void writePersistentBlob(key, blob)
          const url = URL.createObjectURL(blob)
          this.set(key, url, blob.size)
          return url
        } catch {
          missing.add(key)
          return null
        } finally {
          inflight.delete(key)
        }
      })()

      inflight.set(key, task)
      return task
    },
    clear() {
      for (const entry of entries.values()) {
        revokeFn(entry.url)
      }
      entries.clear()
      inflight.clear()
      missing.clear()
      totalBytes = 0
      void clearPersistentCache()
    },
    stats(): ThumbCacheStats {
      return { count: entries.size, bytes: totalBytes, maxEntries }
    },
  }
}

export const mobileThumbCache = createThumbLruCache(120)
