export interface PhotoBackupConfig {
  enabled: boolean
  targetSpaceId: string
  targetParentId: string
  targetLabel: string
  wifiOnly: boolean
  chargingOnly: boolean
  selectedAlbums: string[]
  lastRunAt: string | null
}

export interface BackupMediaFingerprint {
  key: string
  name: string
  size: number
  lastModified: number
}

const CONFIG_KEY = 'cloudlane.mobile.photoBackupConfig'
const LEDGER_KEY = 'cloudlane.mobile.photoBackupLedger'

const DEFAULT_CONFIG: PhotoBackupConfig = {
  enabled: false,
  targetSpaceId: '',
  targetParentId: '0',
  targetLabel: '未选择目录',
  wifiOnly: true,
  chargingOnly: false,
  selectedAlbums: ['Camera', 'Screenshots'],
  lastRunAt: null,
}

export function loadPhotoBackupConfig(): PhotoBackupConfig {
  try {
    const raw = globalThis.localStorage?.getItem(CONFIG_KEY)
    if (!raw) return { ...DEFAULT_CONFIG }
    const parsed = JSON.parse(raw) as Partial<PhotoBackupConfig>
    return {
      ...DEFAULT_CONFIG,
      ...parsed,
      selectedAlbums: Array.isArray(parsed.selectedAlbums) ? parsed.selectedAlbums : [...DEFAULT_CONFIG.selectedAlbums],
    }
  } catch {
    return { ...DEFAULT_CONFIG }
  }
}

export function savePhotoBackupConfig(next: Partial<PhotoBackupConfig>): PhotoBackupConfig {
  const merged = { ...loadPhotoBackupConfig(), ...next }
  try {
    globalThis.localStorage?.setItem(CONFIG_KEY, JSON.stringify(merged))
  } catch {
    // ignore storage quota errors
  }
  return merged
}

export function buildMediaFingerprint(item: Pick<BackupMediaFingerprint, 'name' | 'size' | 'lastModified'>): string {
  return `${item.name}::${item.size}::${item.lastModified}`
}

export function loadBackedUpLedger(): Set<string> {
  try {
    const raw = globalThis.localStorage?.getItem(LEDGER_KEY)
    if (!raw) return new Set()
    const arr = JSON.parse(raw)
    return Array.isArray(arr) ? new Set(arr.filter((v): v is string => typeof v === 'string')) : new Set()
  } catch {
    return new Set()
  }
}

export function markBackedUpFingerprints(keys: string[]): number {
  const set = loadBackedUpLedger()
  for (const k of keys) {
    if (k) set.add(k)
  }
  const capped = Array.from(set).slice(-20_000)
  try {
    globalThis.localStorage?.setItem(LEDGER_KEY, JSON.stringify(capped))
  } catch {
    // ignore
  }
  return capped.length
}

export function clearBackedUpLedger(): void {
  try {
    globalThis.localStorage?.removeItem(LEDGER_KEY)
  } catch {
    // ignore
  }
}

export function filterUnbackedMedia<T extends Pick<BackupMediaFingerprint, 'name' | 'size' | 'lastModified'>>(
  items: T[],
  ledger: Set<string>,
): T[] {
  return items.filter(it => !ledger.has(buildMediaFingerprint(it)))
}

export function shouldRunAutoBackup(
  config: PhotoBackupConfig,
  env: { online: boolean; isWifi: boolean; isCharging: boolean },
): { allowed: boolean; reason?: string } {
  if (!config.enabled) return { allowed: false, reason: '相册自动备份未开启' }
  if (!config.targetSpaceId) return { allowed: false, reason: '请先选择备份目标空间与目录' }
  if (!env.online) return { allowed: false, reason: '当前无网络连接' }
  if (config.wifiOnly && !env.isWifi) return { allowed: false, reason: '已开启「仅 Wi-Fi 备份」，等待 Wi-Fi 连接' }
  if (config.chargingOnly && !env.isCharging) return { allowed: false, reason: '已开启「仅充电时备份」，等待接入电源' }
  return { allowed: true }
}

/**
 * 原生后台传输桥接（Android WorkManager / iOS URLSession Background）：
 * 若原生插件已注入则交由系统后台服务保活上传，否则回退至前端分片断点续传队列。
 */
export function hasNativeBackgroundTransferPlugin(): boolean {
  const win = globalThis as unknown as {
    Capacitor?: { Plugins?: { BackgroundTransfer?: { enqueueUpload?: unknown } } }
  }
  return typeof win.Capacitor?.Plugins?.BackgroundTransfer?.enqueueUpload === 'function'
}
