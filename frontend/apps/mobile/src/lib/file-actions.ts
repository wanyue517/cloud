import { canWriteInRole, categoryLabel } from '@repo/drive-core/categories'
import type { MobileActionItem } from '../components/common/ActionSheet.vue'

export type FileViewMode = 'list' | 'grid'
export type SortField = 'name' | 'updatedAt' | 'size'
export type SortOrder = 'asc' | 'desc'

export interface SortConfig {
  field: SortField
  order: SortOrder
}

export interface DirectoryStatsLike {
  folders: number
  files: number
  categories: Record<string, number>
}

export interface CategoryPill {
  key: string
  label: string
  count: number
}

export interface NodeActionTarget {
  id: string
  name: string
  nodeType: 'FILE' | 'FOLDER'
  favorite: boolean
  hidden: boolean
  pinned?: boolean
}

export const FILE_VIEW_STORAGE_KEY = 'cloudlane.mobile.fileView'
export const FILE_SORT_STORAGE_KEY = 'cloudlane.mobile.fileSort'

const CATEGORY_ORDER = [
  'IMAGE',
  'PDF',
  'VIDEO',
  'AUDIO',
  'WORD',
  'EXCEL',
  'PPT',
  'CODE',
  'ARCHIVE',
  'OTHER',
] as const

export const SORT_FIELD_LABELS: Readonly<Record<SortField, string>> = Object.freeze({
  name: '名称',
  updatedAt: '修改时间',
  size: '大小',
})

export function loadFileViewMode(
  storage: Pick<Storage, 'getItem'> | null | undefined = typeof globalThis.localStorage !== 'undefined'
    ? globalThis.localStorage
    : null,
): FileViewMode {
  try {
    const raw = storage?.getItem(FILE_VIEW_STORAGE_KEY)
    return raw === 'grid' ? 'grid' : 'list'
  } catch {
    return 'list'
  }
}

export function saveFileViewMode(
  mode: FileViewMode,
  storage: Pick<Storage, 'setItem'> | null | undefined = typeof globalThis.localStorage !== 'undefined'
    ? globalThis.localStorage
    : null,
) {
  try {
    storage?.setItem(FILE_VIEW_STORAGE_KEY, mode)
  } catch {
    // ignore storage errors
  }
}

export function loadSortConfig(
  storage: Pick<Storage, 'getItem'> | null | undefined = typeof globalThis.localStorage !== 'undefined'
    ? globalThis.localStorage
    : null,
): SortConfig {
  try {
    const raw = storage?.getItem(FILE_SORT_STORAGE_KEY)
    if (!raw) return { field: 'updatedAt', order: 'desc' }
    const parsed = JSON.parse(raw) as Partial<SortConfig>
    const field: SortField =
      parsed.field === 'name' || parsed.field === 'size' || parsed.field === 'updatedAt'
        ? parsed.field
        : 'updatedAt'
    const order: SortOrder = parsed.order === 'asc' || parsed.order === 'desc' ? parsed.order : 'desc'
    return { field, order }
  } catch {
    return { field: 'updatedAt', order: 'desc' }
  }
}

export function saveSortConfig(
  config: SortConfig,
  storage: Pick<Storage, 'setItem'> | null | undefined = typeof globalThis.localStorage !== 'undefined'
    ? globalThis.localStorage
    : null,
) {
  try {
    storage?.setItem(FILE_SORT_STORAGE_KEY, JSON.stringify(config))
  } catch {
    // ignore storage errors
  }
}

export function formatSortLabel(sort: SortConfig): string {
  const fieldLabel = SORT_FIELD_LABELS[sort.field] ?? '修改时间'
  const dirLabel = sort.order === 'asc' ? '升序' : '降序'
  return `${fieldLabel}·${dirLabel}`
}

export function buildCategoryPills(stats: DirectoryStatsLike | null): CategoryPill[] {
  if (!stats) return []
  const pills: CategoryPill[] = [{ key: 'ALL', label: '全部', count: stats.files + stats.folders }]
  const seen = new Set<string>()
  for (const key of CATEGORY_ORDER) {
    const count = stats.categories[key] ?? 0
    if (count > 0) {
      pills.push({ key, label: categoryLabel(key), count })
      seen.add(key)
    }
  }
  for (const [key, count] of Object.entries(stats.categories)) {
    if (!seen.has(key) && count > 0) {
      pills.push({ key, label: categoryLabel(key), count })
    }
  }
  return pills
}

export function buildNodeActions(
  node: NodeActionTarget,
  role: string | null | undefined,
): MobileActionItem[] {
  const isFile = node.nodeType === 'FILE'
  const writable = canWriteInRole(role)
  const actions: MobileActionItem[] = []

  actions.push({
    key: 'open',
    name: isFile ? '预览' : '打开文件夹',
  })

  if (isFile) {
    actions.push({ key: 'download', name: '下载文件' })
  }

  actions.push({
    key: 'share',
    name: '分享',
    subname: node.hidden ? '隐藏项不可分享' : '公开链接 / 共享给成员',
    disabled: node.hidden,
  })

  actions.push({
    key: 'favorite',
    name: node.favorite ? '取消收藏' : '收藏',
  })

  actions.push({
    key: 'pin',
    name: node.pinned ? '取消快捷访问' : '固定到快捷访问',
    subname: node.hidden ? '隐藏项不可固定' : undefined,
    disabled: node.hidden,
  })

  if (writable) {
    actions.push({ key: 'rename', name: '重命名' })
    actions.push({ key: 'move', name: '移动到…' })
    actions.push({ key: 'copy', name: '复制到…' })
  }

  if (isFile) {
    actions.push({ key: 'versions', name: '历史版本' })
    if (writable) {
      actions.push({ key: 'replace', name: '替换文件', subname: '上传新版本覆盖当前内容' })
    }
  }

  if (writable) {
    actions.push({
      key: 'hidden',
      name: node.hidden ? '取消隐藏' : '设为隐藏',
      subname: node.hidden ? '恢复在普通目录中可见' : '隐藏后需密码解锁才可见',
    })
  }

  actions.push({ key: 'detail', name: '文件详情' })

  if (writable) {
    actions.push({ key: 'delete', name: '移入回收站', danger: true })
  }

  return actions
}

export function triggerBlobDownload(blob: Blob, filename: string) {
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  a.rel = 'noopener'
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  setTimeout(() => URL.revokeObjectURL(url), 10_000)
}
