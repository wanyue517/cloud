export type ProgressType = 'VIDEO' | 'PDF' | 'AUDIO'

export const VIDEO_RESUME_MIN_DURATION_SECONDS = 20

export interface FileProgressRecord {
  nodeId: string
  progressType: ProgressType
  positionValue: number
  updatedAt: string
}

export function formatProgressTime(seconds: number): string {
  if (!Number.isFinite(seconds) || seconds < 0) return '00:00'
  const total = Math.floor(seconds)
  const h = Math.floor(total / 3600)
  const m = Math.floor((total % 3600) / 60)
  const s = total % 60
  if (h > 0) {
    return `${String(h).padStart(2, '0')}:${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`
  }
  return `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`
}

export function formatResumePrompt(type: ProgressType, positionValue: number): string {
  if (type === 'PDF') {
    return `继续上次位置（第 ${Math.max(1, Math.round(positionValue))} 页）`
  }
  return `继续上次位置（${formatProgressTime(positionValue)}）`
}

export function shouldPromptResume(
  type: ProgressType,
  positionValue: number,
  totalOrDuration?: number,
): boolean {
  if (type === 'VIDEO' && (
    typeof totalOrDuration !== 'number' ||
    !Number.isFinite(totalOrDuration) ||
    totalOrDuration <= VIDEO_RESUME_MIN_DURATION_SECONDS
  )) return false
  if (!Number.isFinite(positionValue) || positionValue <= 0) return false
  if (type === 'PDF') {
    const page = Math.round(positionValue)
    if (page < 2) return false
    if (typeof totalOrDuration === 'number' && totalOrDuration > 0 && page > totalOrDuration) {
      return false
    }
    return true
  }
  if (positionValue < 5) return false
  if (
    typeof totalOrDuration === 'number' &&
    Number.isFinite(totalOrDuration) &&
    totalOrDuration > 10 &&
    positionValue >= totalOrDuration - 5
  ) {
    return false
  }
  return true
}

export interface ProgressReporter {
  report: (positionValue: number) => void
  flush: () => void
  reset: () => void
}

/**
 * 节流上报器：默认每 10 秒最多触发一次真实上报，避免播放/翻页高频刷接口；
 * 在暂停或组件卸载时可调用 flush() 将最新位置立即落库。
 */
export function createProgressReporter(
  saveFn: (positionValue: number) => void | Promise<unknown>,
  options?: { intervalMs?: number; now?: () => number },
): ProgressReporter {
  const intervalMs = options?.intervalMs ?? 10_000
  const now = options?.now ?? (() => Date.now())

  let lastSentAt = 0
  let lastSentValue: number | null = null
  let pendingValue: number | null = null

  function dispatch(val: number) {
    lastSentAt = now()
    lastSentValue = val
    pendingValue = null
    try {
      const res = saveFn(val)
      if (res && typeof (res as Promise<unknown>).catch === 'function') {
        void (res as Promise<unknown>).catch(() => undefined)
      }
    } catch {
      // ignore reporter errors
    }
  }

  return {
    report(positionValue: number) {
      if (!Number.isFinite(positionValue) || positionValue < 0) return
      const normalized = Math.round(positionValue * 10) / 10
      if (lastSentValue !== null && Math.abs(normalized - lastSentValue) < 0.5) {
        return
      }
      pendingValue = normalized
      const elapsed = now() - lastSentAt
      if (lastSentAt === 0 || elapsed >= intervalMs) {
        dispatch(normalized)
      }
    },
    flush() {
      if (pendingValue === null) return
      if (lastSentValue !== null && Math.abs(pendingValue - lastSentValue) < 0.5) {
        pendingValue = null
        return
      }
      dispatch(pendingValue)
    },
    reset() {
      lastSentAt = 0
      lastSentValue = null
      pendingValue = null
    },
  }
}
