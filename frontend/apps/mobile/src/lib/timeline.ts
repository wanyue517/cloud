export interface TimelineMediaItem {
  id: string
  spaceId: string
  spaceName: string
  parentId: string
  name: string
  size: number
  mimeType: string
  mediaType: 'IMAGE' | 'VIDEO'
  takenAt: string
  dateGroup: string
  width?: number | null
  height?: number | null
  durationSeconds?: number | null
  currentVersionId?: string | null
  revision: number
}

export interface TimelineDateGroup {
  date: string
  label: string
  items: TimelineMediaItem[]
}

export function formatTimelineDateLabel(dateStr: string, nowDate?: Date): string {
  const ref = nowDate ?? new Date()
  const today = ref.toISOString().slice(0, 10)
  const yesterdayDate = new Date(ref.getTime() - 86_400_000)
  const yesterday = yesterdayDate.toISOString().slice(0, 10)
  if (dateStr === today) return '今天'
  if (dateStr === yesterday) return '昨天'
  const parts = dateStr.split('-')
  if (parts.length === 3) {
    const [y, m, d] = parts
    if (y === String(ref.getUTCFullYear())) {
      return `${Number(m)}月${Number(d)}日`
    }
    return `${y}年${Number(m)}月${Number(d)}日`
  }
  return dateStr
}

export function groupTimelineByDate(items: TimelineMediaItem[], nowDate?: Date): TimelineDateGroup[] {
  const map = new Map<string, TimelineMediaItem[]>()
  for (const item of items) {
    const key = item.dateGroup || (item.takenAt ? item.takenAt.slice(0, 10) : '未知日期')
    const list = map.get(key)
    if (list) list.push(item)
    else map.set(key, [item])
  }
  return Array.from(map.entries()).map(([date, list]) => ({
    date,
    label: formatTimelineDateLabel(date, nowDate),
    items: list,
  }))
}
