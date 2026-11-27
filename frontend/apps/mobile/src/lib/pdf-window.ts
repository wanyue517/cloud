export function computeVisiblePageWindow(
  currentPage: number,
  totalPages: number,
  radius = 2,
): number[] {
  if (totalPages <= 0) return []
  const clamped = Math.min(totalPages, Math.max(1, Math.floor(currentPage)))
  const start = Math.max(1, clamped - radius)
  const end = Math.min(totalPages, clamped + radius)
  const pages: number[] = []
  for (let p = start; p <= end; p++) {
    pages.push(p)
  }
  return pages
}

export function clampZoomScale(scale: number, min = 0.5, max = 2.5): number {
  if (!Number.isFinite(scale)) return 1
  return Math.min(max, Math.max(min, Math.round(scale * 100) / 100))
}

export function excelColumnLabel(index: number): string {
  let n = Math.max(0, Math.floor(index))
  let label = ''
  while (true) {
    label = String.fromCharCode(65 + (n % 26)) + label
    n = Math.floor(n / 26) - 1
    if (n < 0) break
  }
  return label
}
