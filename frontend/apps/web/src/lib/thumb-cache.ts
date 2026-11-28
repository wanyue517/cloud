const THUMB_CACHE_NAME = 'cloudlane-thumbs-v1'
const THUMB_MAX_ENTRIES = 300
const PREVIEW_CACHE_NAME = 'cloudlane-preview-v1'
const PREVIEW_MAX_ENTRIES = 40
const PREVIEW_MAX_BYTES = 20 * 1024 * 1024

function cacheRequestKey(namespace: string, key: string): string {
  return `https://cloudlane.local/${namespace}/${encodeURIComponent(key)}`
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
  return getOrFetchCachedBlob(THUMB_CACHE_NAME, 'thumbs', THUMB_MAX_ENTRIES, 5 * 1024 * 1024, cacheKey, fetcher)
}

/** 查询预览缓存是否已存在本地 Blob（用于命中时避免闪烁“正在加载”） */
export async function matchPreviewBlob(cacheKey: string): Promise<Blob | null> {
  if (typeof window === 'undefined' || !('caches' in window)) return null
  try {
    const cache = await window.caches.open(PREVIEW_CACHE_NAME)
    const hit = await cache.match(cacheRequestKey('preview', cacheKey))
    if (!hit) return null
    const blob = await hit.blob()
    return blob.size > 0 ? blob : null
  } catch {
    return null
  }
}

/**
 * 浏览器本地持久化预览原图 / PDF 缓存（单文件默认 ≤ 20 MB，最多保留 40 个）
 */
export async function getOrFetchPreviewBlob(
  cacheKey: string,
  fetcher: () => Promise<Blob>,
  maxBytes = PREVIEW_MAX_BYTES,
): Promise<Blob> {
  return getOrFetchCachedBlob(PREVIEW_CACHE_NAME, 'preview', PREVIEW_MAX_ENTRIES, maxBytes, cacheKey, fetcher)
}

async function getOrFetchCachedBlob(
  cacheName: string,
  namespace: string,
  maxEntries: number,
  maxBytes: number,
  cacheKey: string,
  fetcher: () => Promise<Blob>,
): Promise<Blob> {
  const hasCacheApi = typeof window !== 'undefined' && 'caches' in window
  let cache: Cache | undefined
  if (hasCacheApi) {
    try {
      cache = await window.caches.open(cacheName)
      const hit = await cache.match(cacheRequestKey(namespace, cacheKey))
      if (hit) {
        const blob = await hit.blob()
        if (blob.size > 0) return blob
      }
    } catch {
      // Cache failure may fall back to one network request; a fetch failure must not send it twice.
      cache = undefined
    }
  }
  const fresh = await fetcher()
  if (cache && fresh && fresh.size > 0 && fresh.size <= maxBytes) {
    void writeWithLimit(cache, namespace, maxEntries, cacheKey, fresh)
  }
  return fresh
}

async function writeWithLimit(cache: Cache, namespace: string, maxEntries: number, cacheKey: string, blob: Blob) {
  try {
    const keys = await cache.keys()
    if (keys.length >= maxEntries) {
      const removeCount = Math.max(1, keys.length - maxEntries + Math.min(10, Math.ceil(maxEntries / 5)))
      await Promise.all(keys.slice(0, removeCount).map(req => cache.delete(req)))
    }
    await cache.put(
      cacheRequestKey(namespace, cacheKey),
      new Response(blob, {
        headers: { 'Content-Type': blob.type || 'application/octet-stream' },
      }),
    )
  } catch {
    // ignore storage quota errors
  }
}
