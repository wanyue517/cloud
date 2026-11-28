import { withExponentialBackoff } from '@repo/drive-core/upload-queue'

export interface DirectSession {
  id: string
  uploadMode?: string
  status: string
  chunkSize: number
  chunkCount: number
  received?: number[]
}

export interface DirectUploadClient {
  info: (id: string, signal?: AbortSignal) => Promise<{ received: number[]; concurrency: number; fallbackAllowed: boolean }>
  presign: (id: string, index: number, signal?: AbortSignal) => Promise<{ uploadUrl: string }>
  fallback: (id: string, signal?: AbortSignal) => Promise<DirectSession>
}

/** No application Authorization/private-token headers or cookies are sent to the storage host. */
export function putDirectPart(url: string, part: Blob, progress: (loaded: number) => void, signal?: AbortSignal): Promise<void> {
  return new Promise((resolve, reject) => {
    if (signal?.aborted) { reject(new Error('上传已暂停')); return }
    const xhr = new XMLHttpRequest()
    const cleanup = () => signal?.removeEventListener('abort', abort)
    const abort = () => { xhr.abort(); cleanup(); reject(new Error('上传已暂停')) }
    xhr.open('PUT', url)
    xhr.withCredentials = false
    xhr.timeout = 300_000
    xhr.upload.onprogress = event => progress(event.loaded)
    xhr.onload = () => {
      cleanup()
      if (xhr.status >= 200 && xhr.status < 300) resolve()
      else reject(new Error(`对象存储上传失败（HTTP ${xhr.status}）`))
    }
    xhr.onerror = () => { cleanup(); reject(new Error('无法直连对象存储，请检查网络或联系管理员检查跨域配置')) }
    xhr.ontimeout = () => { cleanup(); reject(new Error('对象存储分片上传超时')) }
    signal?.addEventListener('abort', abort, { once: true })
    try { xhr.send(part) } catch (error) { cleanup(); reject(error) }
  })
}

export async function executeDirectUpload<T extends DirectSession>(
  client: DirectUploadClient,
  session: T,
  file: Pick<File, 'size' | 'slice'>,
  progress?: (percent: number, message?: string) => void,
  signal?: AbortSignal,
  send = putDirectPart,
): Promise<T> {
  if (session.uploadMode !== 'DIRECT' || ['COMPLETED', 'QUEUED', 'PROCESSING'].includes(session.status)) return session
  const controller = new AbortController()
  const abort = () => controller.abort(signal?.reason)
  signal?.addEventListener('abort', abort, { once: true })
  if (signal?.aborted) abort()
  let fallbackAllowed = false
  try {
    const info = await client.info(session.id, controller.signal)
    fallbackAllowed = info.fallbackAllowed
    const received = new Set(info.received)
    const loaded = new Map<number, number>()
    const bytes = (index: number) => Math.min(session.chunkSize, Math.max(0, file.size - index * session.chunkSize))
    for (const index of received) loaded.set(index, bytes(index))
    const notify = () => progress?.(Math.min(99, Math.floor([...loaded.values()].reduce((a, b) => a + b, 0) / Math.max(1, file.size) * 100)), '正在直传对象存储')
    let next = 0
    const worker = async () => {
      while (next < session.chunkCount) {
        const index = next++
        if (received.has(index)) continue
        const part = file.slice(index * session.chunkSize, Math.min(file.size, (index + 1) * session.chunkSize))
        await withExponentialBackoff(async () => {
          const signed = await client.presign(session.id, index, controller.signal)
          loaded.set(index, 0)
          await send(signed.uploadUrl, part, value => { loaded.set(index, Math.min(value, part.size)); notify() }, controller.signal)
        }, { signal: controller.signal })
        loaded.set(index, part.size)
        received.add(index)
        notify()
      }
    }
    const results = await Promise.allSettled(Array.from({ length: Math.max(1, Math.min(6, info.concurrency)) }, () =>
      worker().catch(error => { controller.abort(); throw error })))
    const failure = results.find((result): result is PromiseRejectedResult => result.status === 'rejected')
    if (failure) throw failure.reason
    return { ...session, received: [...received] }
  } catch (error) {
    if (signal?.aborted || !fallbackAllowed) throw error
    progress?.(0, '直传失败，正在切换后端中转，需要重新上传')
    return { ...session, ...await client.fallback(session.id, signal) }
  } finally {
    controller.abort()
    signal?.removeEventListener('abort', abort)
  }
}
