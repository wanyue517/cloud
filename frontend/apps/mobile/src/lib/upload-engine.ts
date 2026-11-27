import { withExponentialBackoff } from '@repo/drive-core/upload-queue'

export interface UploadSessionLike {
  id: string
  chunkSize: number
  chunkCount: number
  received?: number[]
  status: string
  fileId?: string | null
  instantHit?: boolean
}

export interface ChunkedUploadStorage {
  getItem(key: string): string | null
  setItem(key: string, value: string): void
  removeItem(key: string): void
}

export interface ChunkedUploadDeps {
  getSession: (uploadId: string, signal?: AbortSignal) => Promise<UploadSessionLike>
  createSession: (
    payload: { spaceId: string; parentId: string; name: string; size: number; mimeType: string; expectedSha256?: string },
    signal?: AbortSignal,
  ) => Promise<UploadSessionLike>
  uploadChunk: (
    uploadId: string,
    chunkIndex: number,
    chunkBlob: Blob,
    filename: string,
    onChunkProgress: (loaded: number, total: number) => void,
    signal?: AbortSignal,
  ) => Promise<void>
  completeSession: (uploadId: string, signal?: AbortSignal) => Promise<string>
  storage?: ChunkedUploadStorage | null
  sleep?: (ms: number, signal?: AbortSignal) => Promise<void>
  maxRetries?: number
  baseDelayMs?: number
}

export interface ChunkedUploadParams {
  spaceId: string
  parentId: string
  file: Pick<File, 'name' | 'size' | 'type' | 'lastModified' | 'slice'>
  name?: string
  expectedSha256?: string
  onProgress?: (percent: number) => void
  signal?: AbortSignal
}

/**
 * 计算文件 SHA-256 哈希（用于 M039 用户级秒传去重；默认仅对 <= 64MB 文件在前端计算避免阻塞主线程）。
 */
export async function computeFileSha256(file: Blob, maxBytes = 64 * 1024 * 1024): Promise<string | undefined> {
  if (file.size <= 0 || file.size > maxBytes) return undefined
  const subtle = globalThis.crypto?.subtle
  if (!subtle) return undefined
  try {
    const buf = await file.arrayBuffer()
    const hash = await subtle.digest('SHA-256', buf)
    return Array.from(new Uint8Array(hash))
      .map(b => b.toString(16).padStart(2, '0'))
      .join('')
  } catch {
    return undefined
  }
}

export function uploadResumeKey(
  spaceId: string,
  parentId: string,
  file: Pick<File, 'name' | 'size' | 'lastModified'>,
  overrideName?: string,
): string {
  const effectiveName = overrideName || file.name
  return `cloudlane.mobile.upload.${spaceId}.${parentId}.${effectiveName}.${file.size}.${file.lastModified}`
}

export function formatBatchOutcomeMessage(
  actionLabel: string,
  result: {
    status: string
    totalCount: number
    successCount: number
    failedCount: number
    outcomes?: { nodeId: string; ok: boolean; message: string }[]
    errorMessage?: string | null
  },
): { ok: boolean; title: string; detail: string } {
  if (result.failedCount <= 0 && result.status !== 'PARTIAL' && result.status !== 'FAILED') {
    return {
      ok: true,
      title: `${actionLabel}完成（共 ${result.successCount} 项）`,
      detail: '',
    }
  }
  const failedReasons = (result.outcomes ?? [])
    .filter(o => !o.ok)
    .slice(0, 5)
    .map(o => o.message || `节点 ${o.nodeId} 失败`)
  const extra =
    (result.outcomes ?? []).filter(o => !o.ok).length > 5 ? ' 等' : ''
  const detail =
    failedReasons.length > 0
      ? failedReasons.join('；') + extra
      : result.errorMessage || '部分项目操作失败'

  return {
    ok: false,
    title: `${actionLabel}部分完成：成功 ${result.successCount} 项，失败 ${result.failedCount} 项`,
    detail,
  }
}

export async function executeChunkedUpload(
  deps: ChunkedUploadDeps,
  params: ChunkedUploadParams,
): Promise<string> {
  const { spaceId, parentId, file, onProgress, signal } = params
  const effectiveName = params.name || file.name
  const storage = deps.storage
  const key = uploadResumeKey(spaceId, parentId, file, effectiveName)

  let session: UploadSessionLike | undefined
  const previousId = storage?.getItem(key)
  if (previousId) {
    try {
      session = await deps.getSession(previousId, signal)
    } catch (err) {
      if (signal?.aborted) throw err
      storage?.removeItem(key)
    }
  }

  if (!session || session.status === 'CANCELLED') {
    const sha =
      params.expectedSha256 ??
      (file instanceof Blob ? await computeFileSha256(file) : undefined)
    session = await deps.createSession(
      {
        spaceId,
        parentId,
        name: effectiveName,
        size: file.size,
        mimeType: file.type || 'application/octet-stream',
        ...(sha ? { expectedSha256: sha } : {}),
      },
      signal,
    )
    try {
      storage?.setItem(key, session.id)
    } catch {
      // ignore storage quota errors
    }
  }

  let fileId = session.fileId || ''
  if (session.status === 'COMPLETED') {
    onProgress?.(100)
  } else {
    const received = new Set(session.received ?? [])
    const totalChunks = Math.max(1, session.chunkCount)
    for (let index = 0; index < totalChunks; index++) {
      if (signal?.aborted) throw new Error('上传已暂停')
      if (!received.has(index)) {
        const start = index * session.chunkSize
        const end = Math.min(file.size, (index + 1) * session.chunkSize)
        const chunkBlob = file.slice(start, end)
        await withExponentialBackoff(
          () =>
            deps.uploadChunk(
              session!.id,
              index,
              chunkBlob,
              effectiveName,
              (loaded, total) => {
                const denom = total || chunkBlob.size || 1
                const fraction = Math.min(1, loaded / denom)
                onProgress?.(Math.min(99, Math.round(((index + fraction) / totalChunks) * 100)))
              },
              signal,
            ),
          {
            maxRetries: deps.maxRetries ?? 3,
            baseDelayMs: deps.baseDelayMs ?? 300,
            signal,
            sleep: deps.sleep,
          },
        )
      }
      onProgress?.(Math.round(((index + 1) / totalChunks) * 100))
    }

    fileId = await deps.completeSession(session.id, signal)
  }

  try {
    storage?.removeItem(key)
  } catch {
    // ignore storage errors
  }
  return fileId
}
