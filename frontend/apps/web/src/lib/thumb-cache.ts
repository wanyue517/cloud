const CACHE_NAME = 'cloudlane-thumbs-v1'
const MAX_ENTRIES = 300

function cacheRequestKey(key: string): string {
  return `https://cloudlane.local/thumbs/${encodeURIComponent(key)}`
}

/**
 * 浏览器本地持久化缩略图缓存（基于 Cache Storage API）：
 * 命中时直接返回本地 Blob（刷新页面零网络流量），未命中则调用 fetcher 拉取并写入缓存；
 * 若当前环境不支持 Cache Storage（如非安全上下文或隐私模式），自动静默降级为直连拉取。
 */
export async function getOrFetchThumbnail(
  cacheKey: string,
  fetcher: () => Promise<Blob>,
): Promise<Blob> {
  const hasCacheApi = typeof window !== 'undefined' && 'caches' in window
  if (hasCacheApi) {
    try {
      const cache = await window.caches.open(CACHE_NAME)
      const hit = await cache.match(cacheRequestKey(cacheKey))
      if (hit) {
        const blob = await hit.blob()
        if (blob.size > 0) return blob
      }
      const fresh = await fetcher()
      if (fresh && fresh.size > 0) {
        void writeWithLimit(cache, cacheKey, fresh)
      }
      return fresh
    } catch {
      // Cache API unavailable or quota exceeded; fallback to direct fetch
    }
  }
  return fetcher()
}

async function writeWithLimit(cache: Cache, cacheKey: string, blob: Blob) {
  try {
    const keys = await cache.keys()
    if (keys.length >= MAX_ENTRIES) {
      const removeCount = Math.max(1, keys.length - MAX_ENTRIES + 20)
      await Promise.all(keys.slice(0, removeCount).map(req => cache.delete(req)))
    }
    await cache.put(
      cacheRequestKey(cacheKey),
      new Response(blob, {
        headers: { 'Content-Type': blob.type || 'image/jpeg' },
      }),
    )
  } catch {
    // ignore storage quota errors
  }
}
