export type UploadTaskStatus = 'queued' | 'uploading' | 'paused' | 'completed' | 'error' | 'cancelled'

export interface UploadQueueItem {
  id: string
  name: string
  file: File
  spaceId: string
  parentId: string
  percent: number
  status: UploadTaskStatus
  message: string
  attempts: number
}

export interface RetryOptions {
  maxRetries?: number
  baseDelayMs?: number
  signal?: AbortSignal
  sleep?: (ms: number, signal?: AbortSignal) => Promise<void>
}

export function defaultSleep(ms: number, signal?: AbortSignal): Promise<void> {
  return new Promise((resolve, reject) => {
    if (signal?.aborted) { reject(new Error('aborted')); return }
    const timer = setTimeout(() => { signal?.removeEventListener('abort', onAbort); resolve() }, ms)
    const onAbort = () => { clearTimeout(timer); reject(new Error('aborted')) }
    signal?.addEventListener('abort', onAbort, { once: true })
  })
}

/**
 * 指数退避重试执行器（对齐需求文档 7.3 节：默认重试 3 次，指数退避）。
 */
export async function withExponentialBackoff<T>(
  operation: (attempt: number) => Promise<T>,
  options: RetryOptions = {},
): Promise<T> {
  const maxRetries = options.maxRetries ?? 3
  const baseDelayMs = options.baseDelayMs ?? 300
  const sleepFn = options.sleep ?? defaultSleep
  let lastError: unknown
  for (let attempt = 0; attempt <= maxRetries; attempt++) {
    if (options.signal?.aborted) throw new Error('aborted')
    try {
      return await operation(attempt)
    } catch (err) {
      lastError = err
      if (options.signal?.aborted || attempt >= maxRetries) break
      await sleepFn(baseDelayMs * 2 ** attempt, options.signal)
    }
  }
  throw lastError
}

export function statusLabel(status: UploadTaskStatus, message?: string): string {
  if (message) return message
  switch (status) {
    case 'queued': return '等待上传'
    case 'uploading': return '上传中'
    case 'paused': return '已暂停'
    case 'completed': return '已完成'
    case 'cancelled': return '已取消'
    case 'error': return '上传失败'
  }
}

export interface UploadCompletionState {
  status: string
  fileId?: string | null
  errorMessage?: string | null
}

/** Short requests keep completion independent of file size; the session survives client disconnects. */
export async function waitForUploadCompletion(deps: {
  requestCompletion: () => Promise<UploadCompletionState>
  getSession: () => Promise<UploadCompletionState>
  initial?: UploadCompletionState
  signal?: AbortSignal
  sleep?: (ms: number, signal?: AbortSignal) => Promise<void>
}): Promise<string> {
  const sleep = deps.sleep ?? defaultSleep
  const retry = async <T>(operation: () => Promise<T>): Promise<T> => {
    for (let attempt = 0; ; attempt++) {
      if (deps.signal?.aborted) throw new Error('已停止等待上传处理')
      try { return await operation() }
      catch (error) {
        const status = (error as { httpStatus?: number })?.httpStatus
        if (deps.signal?.aborted || attempt >= 3 || (status && status < 500 && status !== 429)) throw error
        await sleep(500 * 2 ** attempt, deps.signal)
      }
    }
  }
  let state = deps.initial ?? await retry(deps.getSession)
  if (state.status === 'UPLOADING' || state.status === 'FAILED') {
    // Retrying submission is safe: the server queues each session only once.
    state = await retry(deps.requestCompletion)
  }
  while (true) {
    if (deps.signal?.aborted) throw new Error('已停止等待上传处理')
    if (state.status === 'COMPLETED') {
      if (!state.fileId) throw new Error('上传已完成，但缺少文件标识')
      return state.fileId
    }
    if (state.status === 'FAILED') throw new Error(state.errorMessage || '文件处理失败，请重试')
    if (state.status !== 'QUEUED' && state.status !== 'PROCESSING') throw new Error('上传任务已取消或过期')
    await sleep(2000, deps.signal)
    state = await retry(deps.getSession)
  }
}

/**
 * 轻量任务队列状态机（类 Uppy Core 语义）：支持单任务暂停、继续、重试与取消。
 */
export function createUploadQueue(
  executor: (item: UploadQueueItem, onProgress: (percent: number, message?: string) => void, signal: AbortSignal) => Promise<void>,
  onStateChange?: (items: UploadQueueItem[]) => void,
) {
  const items: UploadQueueItem[] = []
  const controllers = new Map<string, AbortController>()
  let running = false
  let seq = 0

  function notify() {
    onStateChange?.(items.map(i => ({ ...i })))
  }

  async function pump() {
    if (running) return
    running = true
    try {
      while (true) {
        const next = items.find(i => i.status === 'queued')
        if (!next) break
        const ctrl = new AbortController()
        controllers.set(next.id, ctrl)
        next.status = 'uploading'
        next.message = ''
        next.attempts++
        notify()
        try {
          await executor(next, (percent, message) => {
            next.percent = percent
            if (message !== undefined) next.message = message
            notify()
          }, ctrl.signal)
          if (next.status === 'uploading') {
            next.percent = 100
            next.status = 'completed'
            next.message = ''
          }
        } catch (err) {
          const currentStatus = next.status as UploadTaskStatus
          if (currentStatus === 'paused' || currentStatus === 'cancelled') {
            // 保持用户主动触发的状态
          } else if (ctrl.signal.aborted) {
            next.status = 'paused'
            next.message = '已暂停，可点击继续续传'
          } else {
            next.status = 'error'
            next.message = err instanceof Error ? err.message : '上传失败'
          }
        } finally {
          controllers.delete(next.id)
          notify()
        }
      }
    } finally {
      running = false
    }
  }

  return {
    enqueue(entries: { file: File; name: string; spaceId: string; parentId: string }[]) {
      for (const entry of entries) {
        items.push({
          id: `up-${++seq}`,
          name: entry.name,
          file: entry.file,
          spaceId: entry.spaceId,
          parentId: entry.parentId,
          percent: 0,
          status: 'queued',
          message: '',
          attempts: 0,
        })
      }
      notify()
      void pump()
    },
    pause(id: string) {
      const item = items.find(i => i.id === id)
      if (!item) return
      if (item.status === 'uploading' || item.status === 'queued') {
        item.status = 'paused'
        item.message = item.message === '正在合并与校验' ? '已停止等待，服务器仍在处理，可点击继续查看' : '已暂停，可点击继续续传'
        controllers.get(id)?.abort('paused')
        notify()
      }
    },
    resume(id: string) {
      const item = items.find(i => i.id === id)
      if (!item || (item.status !== 'paused' && item.status !== 'error')) return
      item.status = 'queued'
      item.message = ''
      notify()
      void pump()
    },
    cancel(id: string) {
      const item = items.find(i => i.id === id)
      if (!item || item.status === 'completed') return
      item.status = 'cancelled'
      item.message = '已取消'
      controllers.get(id)?.abort('cancelled')
      notify()
    },
    clearFinished() {
      for (let i = items.length - 1; i >= 0; i--) {
        if (items[i]!.status === 'completed' || items[i]!.status === 'cancelled') {
          items.splice(i, 1)
        }
      }
      notify()
    },
    abortAll() {
      for (const item of items) {
        if (item.status === 'uploading' || item.status === 'queued') {
          item.status = 'paused'
          item.message = item.message === '正在合并与校验' ? '已停止等待，服务器仍在处理，可点击继续查看' : '已暂停，可点击继续续传'
          controllers.get(item.id)?.abort('paused')
        }
      }
      notify()
    },
    snapshot: () => items.map(i => ({ ...i })),
  }
}
