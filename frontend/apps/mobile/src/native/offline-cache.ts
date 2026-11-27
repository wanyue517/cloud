export interface OfflineEntry {
  nodeId: string
  spaceId: string
  name: string
  size: number
  mimeType: string
  revision: number
  currentVersionId: string | null
  localPath: string
  cachedAt: string
  hasUpdate?: boolean
}

const OFFLINE_INDEX_KEY = 'cloudlane.mobile.offlineIndex'
const OFFLINE_LIMIT_MB_KEY = 'cloudlane.mobile.offlineLimitMb'
const DEFAULT_LIMIT_MB = 512

/**
 * 安全红线：严禁私密空间文件或隐藏文件进入离线缓存。
 */
export function assertOfflineCacheAllowed(spaceKind: string | undefined, hidden?: boolean): void {
  if (String(spaceKind ?? '').toUpperCase() === 'PRIVATE') {
    throw new Error('私密空间文件禁止设为离线可用')
  }
  if (hidden) {
    throw new Error('隐藏文件禁止设为离线可用')
  }
}

export function getOfflineLimitMb(): number {
  try {
    const raw = globalThis.localStorage?.getItem(OFFLINE_LIMIT_MB_KEY)
    const n = raw ? Number.parseInt(raw, 10) : DEFAULT_LIMIT_MB
    return Number.isFinite(n) && n >= 64 ? n : DEFAULT_LIMIT_MB
  } catch {
    return DEFAULT_LIMIT_MB
  }
}

export function setOfflineLimitMb(mb: number): number {
  const safe = Math.max(64, Math.min(8192, Math.round(mb)))
  try {
    globalThis.localStorage?.setItem(OFFLINE_LIMIT_MB_KEY, String(safe))
  } catch {
    // ignore
  }
  return safe
}

export function listOfflineEntries(): OfflineEntry[] {
  try {
    const raw = globalThis.localStorage?.getItem(OFFLINE_INDEX_KEY)
    if (!raw) return []
    const parsed = JSON.parse(raw)
    return Array.isArray(parsed) ? (parsed as OfflineEntry[]) : []
  } catch {
    return []
  }
}

function saveOfflineEntries(entries: OfflineEntry[]): void {
  try {
    globalThis.localStorage?.setItem(OFFLINE_INDEX_KEY, JSON.stringify(entries))
  } catch {
    // ignore
  }
}

export function getOfflineTotalBytes(entries: OfflineEntry[] = listOfflineEntries()): number {
  return entries.reduce((sum, e) => sum + Math.max(0, e.size || 0), 0)
}

async function blobToBase64(blob: Blob): Promise<string> {
  const buf = await blob.arrayBuffer()
  const bytes = new Uint8Array(buf)
  let binary = ''
  const chunk = 0x8000
  for (let i = 0; i < bytes.length; i += chunk) {
    binary += String.fromCharCode(...bytes.subarray(i, i + chunk))
  }
  return btoa(binary)
}

function base64ToBlob(base64: string, mimeType: string): Blob {
  const binary = atob(base64)
  const bytes = new Uint8Array(binary.length)
  for (let i = 0; i < binary.length; i++) {
    bytes[i] = binary.charCodeAt(i)
  }
  return new Blob([bytes], { type: mimeType || 'application/octet-stream' })
}

export async function saveOfflineFile(
  meta: {
    nodeId: string
    spaceId: string
    spaceKind?: string
    hidden?: boolean
    name: string
    size: number
    mimeType: string
    revision: number
    currentVersionId: string | null
  },
  blob: Blob,
): Promise<OfflineEntry> {
  assertOfflineCacheAllowed(meta.spaceKind, meta.hidden)

  const limitBytes = getOfflineLimitMb() * 1024 * 1024
  const existing = listOfflineEntries().filter(e => e.nodeId !== meta.nodeId)
  const projected = getOfflineTotalBytes(existing) + blob.size
  if (projected > limitBytes) {
    throw new Error(`离线缓存超出上限（${getOfflineLimitMb()} MB），请先清理部分离线文件或调大上限`)
  }

  const localPath = `cloudlane-offline/${meta.nodeId}`
  try {
    const { Filesystem, Directory } = await import('@capacitor/filesystem')
    const data = await blobToBase64(blob)
    await Filesystem.writeFile({
      path: localPath,
      data,
      directory: Directory.Data,
      recursive: true,
    })
  } catch {
    // 非原生环境兜底写入 Cache Storage（便于调试与测试）
    if (typeof caches !== 'undefined') {
      const cache = await caches.open('cloudlane-offline-v1')
      await cache.put(`/offline/${meta.nodeId}`, new Response(blob, { headers: { 'Content-Type': meta.mimeType } }))
    }
  }

  const entry: OfflineEntry = {
    nodeId: meta.nodeId,
    spaceId: meta.spaceId,
    name: meta.name,
    size: blob.size || meta.size,
    mimeType: meta.mimeType || 'application/octet-stream',
    revision: meta.revision,
    currentVersionId: meta.currentVersionId,
    localPath,
    cachedAt: new Date().toISOString(),
    hasUpdate: false,
  }
  saveOfflineEntries([entry, ...existing])
  return entry
}

export async function readOfflineFileBlob(entry: OfflineEntry): Promise<Blob | null> {
  try {
    const { Filesystem, Directory } = await import('@capacitor/filesystem')
    const res = await Filesystem.readFile({
      path: entry.localPath,
      directory: Directory.Data,
    })
    if (typeof res.data === 'string') {
      return base64ToBlob(res.data, entry.mimeType)
    }
    return res.data
  } catch {
    if (typeof caches !== 'undefined') {
      const cache = await caches.open('cloudlane-offline-v1')
      const hit = await cache.match(`/offline/${entry.nodeId}`)
      if (hit) return await hit.blob()
    }
    return null
  }
}

export async function removeOfflineFile(nodeId: string): Promise<void> {
  const entries = listOfflineEntries()
  const target = entries.find(e => e.nodeId === nodeId)
  if (target) {
    try {
      const { Filesystem, Directory } = await import('@capacitor/filesystem')
      await Filesystem.deleteFile({ path: target.localPath, directory: Directory.Data })
    } catch {
      // ignore
    }
    try {
      if (typeof caches !== 'undefined') {
        const cache = await caches.open('cloudlane-offline-v1')
        await cache.delete(`/offline/${nodeId}`)
      }
    } catch {
      // ignore
    }
  }
  saveOfflineEntries(entries.filter(e => e.nodeId !== nodeId))
}

export async function clearAllOfflineFiles(): Promise<void> {
  const entries = listOfflineEntries()
  for (const e of entries) {
    await removeOfflineFile(e.nodeId)
  }
  saveOfflineEntries([])
}

/**
 * 联网后比对云端 revision：
 * - 云端已删除或失权（remoteMap 中返回 null）-> 自动清理本地失效副本
 * - 云端 revision 更大 -> 标记 hasUpdate = true
 */
export function reconcileOfflineEntries(
  entries: OfflineEntry[],
  remoteState: Record<string, { revision: number } | null>,
): { kept: OfflineEntry[]; removedIds: string[]; updatedIds: string[] } {
  const kept: OfflineEntry[] = []
  const removedIds: string[] = []
  const updatedIds: string[] = []

  for (const entry of entries) {
    if (!(entry.nodeId in remoteState)) {
      kept.push(entry)
      continue
    }
    const remote = remoteState[entry.nodeId]
    if (remote === null) {
      removedIds.push(entry.nodeId)
      continue
    }
    const hasUpdate = remote.revision > entry.revision
    if (hasUpdate) updatedIds.push(entry.nodeId)
    kept.push({ ...entry, hasUpdate })
  }
  return { kept, removedIds, updatedIds }
}
