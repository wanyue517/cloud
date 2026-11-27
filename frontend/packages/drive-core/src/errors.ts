export interface ApiLikeError {
  message?: string
  code?: number
  httpStatus?: number
}

export function isLockedError(error: unknown): boolean {
  if (!error || typeof error !== 'object') return false
  const e = error as ApiLikeError
  return e.httpStatus === 423 || e.code === 423
}

export function isUnauthorizedError(error: unknown): boolean {
  if (!error || typeof error !== 'object') return false
  const e = error as ApiLikeError
  return e.httpStatus === 401 || e.code === 401
}

export function isConflictError(error: unknown): boolean {
  if (!error || typeof error !== 'object') return false
  const e = error as ApiLikeError
  return e.httpStatus === 409 || e.code === 409
}

export function normalizeErrorMessage(error: unknown, fallback = '操作失败，请稍后重试'): string {
  if (error instanceof Error && error.message.trim()) return error.message
  if (error && typeof error === 'object') {
    const msg = (error as ApiLikeError).message
    if (typeof msg === 'string' && msg.trim()) return msg
  }
  return fallback
}
