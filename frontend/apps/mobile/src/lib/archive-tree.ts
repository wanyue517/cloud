export interface ArchiveEntryLike {
  path: string
  name: string
  directory: boolean
  size: number
  compressedSize: number
  modifiedAt: string
}

export interface ArchiveBrowserItem {
  name: string
  fullPath: string
  directory: boolean
  size: number
  modifiedAt: string
}

export function normalizeArchiveDir(dir: string): string {
  return dir.replace(/^\/+|\/+$/g, '')
}

export function archiveBreadcrumbs(currentDir: string): { label: string; path: string }[] {
  const norm = normalizeArchiveDir(currentDir)
  if (!norm) return []
  const parts = norm.split('/').filter(Boolean)
  const result: { label: string; path: string }[] = []
  let acc = ''
  for (const part of parts) {
    acc = acc ? `${acc}/${part}` : part
    result.push({ label: part, path: acc })
  }
  return result
}

export function listArchiveDirectory(
  entries: readonly ArchiveEntryLike[],
  currentDir = '',
): ArchiveBrowserItem[] {
  const prefix = normalizeArchiveDir(currentDir)
  const prefixWithSlash = prefix ? `${prefix}/` : ''
  const dirs = new Map<string, ArchiveBrowserItem>()
  const files: ArchiveBrowserItem[] = []

  for (const entry of entries) {
    const clean = entry.path.replace(/^\/+|\/+$/g, '')
    if (!clean) continue
    if (prefixWithSlash && !clean.startsWith(prefixWithSlash)) continue
    const rest = prefixWithSlash ? clean.slice(prefixWithSlash.length) : clean
    if (!rest) continue

    const slashIdx = rest.indexOf('/')
    if (slashIdx >= 0) {
      const folderName = rest.slice(0, slashIdx)
      const folderPath = prefixWithSlash ? `${prefixWithSlash}${folderName}` : folderName
      if (!dirs.has(folderPath)) {
        dirs.set(folderPath, {
          name: folderName,
          fullPath: folderPath,
          directory: true,
          size: 0,
          modifiedAt: entry.modifiedAt || '',
        })
      }
    } else if (entry.directory) {
      const folderPath = prefixWithSlash ? `${prefixWithSlash}${rest}` : rest
      if (!dirs.has(folderPath)) {
        dirs.set(folderPath, {
          name: rest,
          fullPath: folderPath,
          directory: true,
          size: 0,
          modifiedAt: entry.modifiedAt || '',
        })
      }
    } else {
      files.push({
        name: rest,
        fullPath: clean,
        directory: false,
        size: entry.size,
        modifiedAt: entry.modifiedAt || '',
      })
    }
  }

  const sortedDirs = Array.from(dirs.values()).sort((a, b) => a.name.localeCompare(b.name, 'zh-CN'))
  const sortedFiles = files.sort((a, b) => a.name.localeCompare(b.name, 'zh-CN'))
  return [...sortedDirs, ...sortedFiles]
}

export function formatMediaTime(seconds: number): string {
  if (!Number.isFinite(seconds) || seconds < 0) return '00:00'
  const total = Math.floor(seconds)
  const h = Math.floor(total / 3600)
  const m = Math.floor((total % 3600) / 60)
  const s = total % 60
  const mm = String(m).padStart(2, '0')
  const ss = String(s).padStart(2, '0')
  return h > 0 ? `${h}:${mm}:${ss}` : `${mm}:${ss}`
}
