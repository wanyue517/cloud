export interface QuotaState {
  percent: number
  level: 'normal' | 'warning' | 'danger'
}

export function computeQuotaState(
  usedBytes: number | null | undefined,
  quotaBytes: number | null | undefined,
): QuotaState {
  const used = typeof usedBytes === 'number' && Number.isFinite(usedBytes) && usedBytes > 0 ? usedBytes : 0
  const quota = typeof quotaBytes === 'number' && Number.isFinite(quotaBytes) && quotaBytes > 0 ? quotaBytes : 0
  if (quota === 0) return { percent: 0, level: 'normal' }
  const rawPct = Math.round((used / quota) * 100)
  const percent = Math.max(0, Math.min(100, rawPct))
  const level = rawPct >= 95 ? 'danger' : rawPct >= 85 ? 'warning' : 'normal'
  return { percent, level }
}
