import { computed, reactive } from 'vue'
import { showConfirmDialog, showToast } from 'vant'
import { formatBytes } from '@repo/drive-core/format'
import { createUploadQueue, type UploadQueueItem } from '@repo/drive-core/upload-queue'
import { mobileDriveApi } from '@/api/drive'
import { networkPrefs, networkStatus, shouldConfirmUploadOnNetwork } from '@/lib/network'
import { executeChunkedUpload } from '@/lib/upload-engine'
import { setActiveUploadCount } from '@/stores/mobile-ui'

export interface UploadCompleteEvent {
  spaceId: string
  parentId: string
  name: string
}

type CompleteListener = (event: UploadCompleteEvent) => void

const completeListeners = new Set<CompleteListener>()

export const uploadState = reactive<{
  items: UploadQueueItem[]
  offlinePausedIds: string[]
}>({
  items: [],
  offlinePausedIds: [],
})

const storage =
  typeof globalThis.localStorage !== 'undefined' ? globalThis.localStorage : null

const queue = createUploadQueue(
  async (item, onProgress, signal) => {
    await executeChunkedUpload(
      {
        getSession: (id, sig) => mobileDriveApi.getUploadSession(id, sig),
        createSession: (payload, sig) => mobileDriveApi.createUploadSession(payload, sig),
        uploadChunk: (id, idx, blob, name, onChunk, sig) =>
          mobileDriveApi.uploadChunk(id, idx, blob, name, onChunk, sig),
        completeSession: (id, sig) => mobileDriveApi.completeUploadSession(id, sig),
        uploadDirect: (session, file, progress, sig) => mobileDriveApi.uploadDirect(session, file, progress, sig),
        cancelSession: id => mobileDriveApi.cancelUploadSession(id),
        storage,
      },
      {
        spaceId: item.spaceId,
        parentId: item.parentId,
        file: item.file,
        name: item.name,
        onProgress,
        signal,
      },
    )
    for (const listener of completeListeners) {
      try {
        listener({ spaceId: item.spaceId, parentId: item.parentId, name: item.name })
      } catch {
        // isolate subscriber errors
      }
    }
  },
  items => {
    uploadState.items = items
    const active = items.filter(i => i.status === 'uploading' || i.status === 'queued').length
    setActiveUploadCount(active)
  },
)

export const activeUploadItems = computed(() =>
  uploadState.items.filter(i => i.status === 'uploading' || i.status === 'queued'),
)

export const pausedOrFailedUploadItems = computed(() =>
  uploadState.items.filter(i => i.status === 'paused' || i.status === 'error'),
)

export const completedUploadItems = computed(() =>
  uploadState.items.filter(i => i.status === 'completed' || i.status === 'cancelled'),
)

export function onUploadCompleted(listener: CompleteListener): () => void {
  completeListeners.add(listener)
  return () => completeListeners.delete(listener)
}

export async function enqueueFilesWithGuard(
  spaceId: string,
  parentId: string,
  files: File[],
): Promise<boolean> {
  if (files.length === 0) return false
  if (!networkStatus.online) {
    showToast('当前处于离线状态，恢复网络后再上传')
    return false
  }
  if (shouldConfirmUploadOnNetwork(networkPrefs, networkStatus)) {
    const totalBytes = files.reduce((sum, f) => sum + f.size, 0)
    try {
      await showConfirmDialog({
        title: '移动网络上传提醒',
        message: `当前为移动网络，仍要上传 ${files.length} 个文件（共 ${formatBytes(totalBytes)}）吗？`,
        confirmButtonText: '继续上传',
        cancelButtonText: '取消',
      })
    } catch {
      return false
    }
  }
  queue.enqueue(files.map(file => ({ file, name: file.name, spaceId, parentId })))
  return true
}

export function enqueueNamedFile(spaceId: string, parentId: string, file: File, customName?: string) {
  queue.enqueue([{ file, name: customName || file.name, spaceId, parentId }])
}

export function pauseUploadTask(id: string) {
  queue.pause(id)
}

export function resumeUploadTask(id: string) {
  uploadState.offlinePausedIds = uploadState.offlinePausedIds.filter(i => i !== id)
  queue.resume(id)
}

export function cancelUploadTask(id: string) {
  uploadState.offlinePausedIds = uploadState.offlinePausedIds.filter(i => i !== id)
  queue.cancel(id)
}

export function pauseAllUploadTasks() {
  queue.abortAll()
}

export function resumeOfflinePausedTasks() {
  const ids = [...uploadState.offlinePausedIds]
  uploadState.offlinePausedIds = []
  for (const id of ids) {
    queue.resume(id)
  }
}

export function clearFinishedUploadTasks() {
  queue.clearFinished()
}

let networkListenerBound = false

export function initUploadNetworkBridge() {
  if (networkListenerBound || typeof window === 'undefined') return
  networkListenerBound = true

  window.addEventListener('offline', () => {
    const running = uploadState.items
      .filter(i => i.status === 'uploading' || i.status === 'queued')
      .map(i => i.id)
    if (running.length > 0) {
      uploadState.offlinePausedIds = Array.from(new Set([...uploadState.offlinePausedIds, ...running]))
      queue.abortAll()
      showToast('网络已断开，上传任务已自动暂停')
    }
  })

  window.addEventListener('online', () => {
    if (uploadState.offlinePausedIds.length > 0) {
      showToast('网络已恢复，可点击底部胶囊一键续传')
    }
  })
}
