import { canWriteInRole } from '@repo/drive-core/categories'
import type { Space } from '@/api/drive'

export function filterVisibleSpaces(spaces: readonly Space[]): Space[] {
  return spaces
    .filter(s => s.kind !== 'PRIVATE')
    .slice()
    .sort((a, b) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0) || a.name.localeCompare(b.name, 'zh-CN'))
}

export function filterWritableSpaces(spaces: readonly Space[]): Space[] {
  return filterVisibleSpaces(spaces).filter(s => canWriteInRole(s.role))
}

export function isFolderPickBlocked(
  folderId: string,
  targetSpaceId: string,
  sourceSpaceId: string,
  excludedIds: ReadonlySet<string>,
): boolean {
  if (!folderId || targetSpaceId !== sourceSpaceId) return false
  return excludedIds.has(folderId)
}

export function resolveActiveSpace(
  visibleSpaces: readonly Space[],
  requestedSpaceId?: string,
  preferredSpaceId?: string,
): Space | null {
  if (visibleSpaces.length === 0) return null
  if (requestedSpaceId) {
    const direct = visibleSpaces.find(s => s.id === requestedSpaceId)
    if (direct) return direct
  }
  if (preferredSpaceId) {
    const pref = visibleSpaces.find(s => s.id === preferredSpaceId)
    if (pref) return pref
  }
  return visibleSpaces[0] ?? null
}

export function canEditSpace(space: Space | null | undefined): boolean {
  if (!space) return false
  return space.role === 'OWNER' || space.role === 'MANAGER'
}

export function canDeleteSpace(space: Space | null | undefined): boolean {
  if (!space) return false
  return space.role === 'OWNER'
}
