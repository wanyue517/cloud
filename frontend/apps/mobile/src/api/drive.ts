import { request } from '@repo/session/request'
import type { ApiSchemas, ResourceId } from '@repo/api-client'
import { waitForUploadCompletion } from '@repo/drive-core/upload-queue'

export type Space = Required<ApiSchemas['DriveModels_Space']>
export type DriveNode = Omit<Required<ApiSchemas['DriveModels_Node']>, 'nodeType' | 'currentVersionId'> & {
  nodeType: 'FILE' | 'FOLDER'
  currentVersionId: ResourceId | null
}
export type NodePage = Omit<Required<ApiSchemas['DriveModels_Page']>, 'items'> & { items: DriveNode[] }
export type FileVersion = Required<ApiSchemas['DriveModels_Version']>
export type DirectoryStats = { folders: number; files: number; categories: Record<string, number> }

export interface SearchHit {
  id: string
  spaceId: string
  spaceName: string
  parentId: string
  name: string
  nodeType: 'FILE' | 'FOLDER'
  category: string
  size: number
  mimeType: string
  updatedAt: string
  matchSource: 'NAME' | 'CONTENT' | 'BOTH'
  snippet: string
}

export interface SearchResponse {
  items: SearchHit[]
  total: number
  page: number
  pageSize: number
  hasMore: boolean
}

export interface QuickAccessItem {
  id: string
  spaceId: string
  spaceName: string
  nodeId: string
  name: string
  nodeType: 'FILE' | 'FOLDER'
  size: number
  updatedAt: string
}

export interface RecentAccessItem {
  id: string
  spaceId: string
  spaceName: string
  parentId: string
  name: string
  nodeType: 'FILE' | 'FOLDER'
  size: number
  mimeType: string
  accessedAt: string
}

export interface UndoTokenReceipt {
  tokenId: string
  actionType: string
  summary: string
  expiresAt: string
}

export interface BatchExecuteResult {
  id: string
  jobType: string
  status: string
  totalCount: number
  successCount: number
  failedCount: number
  outcomes?: { nodeId: string; ok: boolean; message: string }[]
  errorMessage: string | null
}

export interface UnlockPrivateResult {
  privateToken: string
  expiresAt: string
  idleMinutes: number
}

export interface PrivateSpaceItem {
  id: string
  name: string
  kind: 'PERSONAL' | 'PRIVATE'
  color: string
  icon?: string
  privateEnabled: boolean
}

export interface PrivateNodeItem {
  id: string
  spaceId: string
  spaceName: string
  parentId: string
  name: string
  nodeType: 'FILE' | 'FOLDER'
  currentVersionId: string | null
  revision: number
  size: number
  mimeType: string
  updatedAt: string
}

export interface PrivateOverview {
  spaces: PrivateSpaceItem[]
  items: PrivateNodeItem[]
}

export interface UploadSession {
  id: string
  spaceId: string
  parentId: string
  name: string
  size: number
  chunkSize: number
  chunkCount: number
  received?: number[]
  status: string
  fileId?: string | null
  expiresAt?: string
  errorMessage?: string | null
}

export interface TextChunk {
  content: string
  offset: number
  bytesRead: number
  totalBytes: number
  hasMore: boolean
  binaryDetected: boolean
}

export interface PreviewAsset {
  id: string
  fileId: string
  versionId: string
  assetType: string
  status: 'PENDING' | 'PROCESSING' | 'READY' | 'FAILED'
  mimeType: string | null
  sizeBytes: number
  errorMessage: string | null
  attemptCount: number
  updatedAt: string
}

export interface ExcelSheetInfo {
  index: number
  name: string
  rowCount: number
  colCount: number
}

export interface ExcelMetadata {
  sheets: ExcelSheetInfo[]
}

export interface ExcelRowsPage {
  sheetIndex: number
  sheetName: string
  rowOffset: number
  rowLimit: number
  totalRows: number
  colCount: number
  hasMore: boolean
  rows: string[][]
}

export interface VideoProbe {
  container: string
  videoCodec: string
  audioCodec: string
  durationSeconds: number
  width: number
  height: number
  bitrate: number
  strategy: 'DIRECT_PLAY' | 'REMUX' | 'TRANSCODE'
  reason: string
}

export interface StreamTicket {
  ticket: string
  streamUrl: string
  expiresInSeconds: number
}

export interface ArchiveEntry {
  path: string
  name: string
  directory: boolean
  size: number
  compressedSize: number
  modifiedAt: string
}

export interface ArchiveListing {
  format: string
  totalEntries: number
  truncated: boolean
  entries: ArchiveEntry[]
}

export const mobileDriveApi = {
  // Spaces
  spaces: () => request.get<Space[]>('/v1/spaces'),
  createSpace: (payload: {
    name: string
    kind?: 'PERSONAL' | 'SHARED' | 'PRIVATE'
    description?: string
    color?: string
    icon?: string
    sortOrder?: number
  }) => request.post<string>('/v1/spaces', payload),
  updateSpace: (
    id: string,
    payload: { name: string; sortOrder?: number; description?: string; color?: string; icon?: string },
  ) => request.put<void>(`/v1/spaces/${id}`, payload),
  deleteSpace: (id: string, confirmation: string) =>
    request.delete<void>(`/v1/spaces/${id}`, { confirmation }),
  convertToShared: (spaceId: string) => request.post<void>(`/v1/spaces/${spaceId}/convert-shared`),
  convertToPersonal: (spaceId: string, keepMembers = true) =>
    request.post<void>(`/v1/spaces/${spaceId}/convert-personal`, undefined, { params: { keepMembers } }),

  // Nodes & directory browsing
  nodes: (spaceId: string, params: Record<string, unknown> = {}, signal?: AbortSignal) =>
    request.get<NodePage>(`/v1/spaces/${spaceId}/nodes`, params, { signal }),
  stats: (spaceId: string, parentId = '0', hidden = false, signal?: AbortSignal) =>
    request.get<DirectoryStats>(`/v1/spaces/${spaceId}/stats`, { parentId, hidden }, { signal }),
  ancestors: (spaceId: string, nodeId: string, signal?: AbortSignal) =>
    request.get<DriveNode[]>(`/v1/spaces/${spaceId}/nodes/${nodeId}/ancestors`, undefined, { signal }),
  createFolder: (spaceId: string, parentId: string, name: string) =>
    request.post<DriveNode>(`/v1/spaces/${spaceId}/folders`, { parentId, name }),
  updateNode: (node: Pick<DriveNode, 'id' | 'spaceId' | 'parentId' | 'revision'>, name: string, parentId = node.parentId) =>
    request.patch<DriveNode>(`/v1/spaces/${node.spaceId}/nodes/${node.id}`, {
      name,
      parentId,
      revision: node.revision,
    }),
  transferNode: (
    node: Pick<DriveNode, 'id' | 'spaceId' | 'revision'>,
    targetSpaceId: string,
    targetParentId: string,
    mode: 'MOVE' | 'COPY',
  ) =>
    request.post<DriveNode>(`/v1/spaces/${node.spaceId}/nodes/${node.id}/transfer`, {
      targetSpaceId,
      targetParentId,
      mode,
      revision: node.revision,
    }),
  trashNode: (node: Pick<DriveNode, 'id' | 'spaceId' | 'revision'>) =>
    request.delete<void>(`/v1/spaces/${node.spaceId}/nodes/${node.id}`, { revision: node.revision }),
  restoreNode: (node: Pick<DriveNode, 'id' | 'spaceId'>) =>
    request.post<void>(`/v1/spaces/${node.spaceId}/nodes/${node.id}/restore`),
  purgeTrashNode: (spaceId: string, nodeId: string) =>
    request.delete<number>(`/v1/spaces/${spaceId}/trash/${nodeId}`),
  emptyTrash: (spaceId: string) => request.delete<number>(`/v1/spaces/${spaceId}/trash`),

  // Flags & quick access
  setFavorite: (spaceId: string, nodeId: string, enabled: boolean) =>
    request.put<void>(`/v1/spaces/${spaceId}/nodes/${nodeId}/favorite`, undefined, { params: { enabled } }),
  setPin: (spaceId: string, nodeId: string, enabled: boolean) =>
    request.put<void>(`/v1/spaces/${spaceId}/nodes/${nodeId}/pin`, undefined, { params: { enabled } }),
  setHidden: (spaceId: string, nodeId: string, enabled: boolean) =>
    request.put<void>(`/v1/spaces/${spaceId}/nodes/${nodeId}/hidden`, undefined, { params: { enabled } }),
  listQuickAccess: () => request.get<QuickAccessItem[]>('/v1/quick-access'),
  listRecent: () => request.get<RecentAccessItem[]>('/v1/recent'),
  recordAccess: (spaceId: string, nodeId: string) =>
    request.post<void>(`/v1/spaces/${spaceId}/nodes/${nodeId}/access`, undefined, { silent: true }),
  getProgress: (spaceId: string, nodeId: string) =>
    request.get<{ nodeId: string; progressType: 'VIDEO' | 'PDF' | 'AUDIO'; positionValue: number; updatedAt: string } | null>(
      `/v1/spaces/${spaceId}/nodes/${nodeId}/progress`,
      undefined,
      { silent: true },
    ),
  saveProgress: (spaceId: string, nodeId: string, progressType: 'VIDEO' | 'PDF' | 'AUDIO', positionValue: number) =>
    request.put<{ nodeId: string; progressType: 'VIDEO' | 'PDF' | 'AUDIO'; positionValue: number; updatedAt: string }>(
      `/v1/spaces/${spaceId}/nodes/${nodeId}/progress`,
      { progressType, positionValue },
      { silent: true },
    ),

  // Undo & Batch
  issueUndo: (payload: {
    spaceId: string
    actionType: 'RENAME' | 'MOVE' | 'TRASH'
    summary: string
    nodeId: string
    oldName?: string
    oldParentId?: string
  }) => request.post<UndoTokenReceipt>('/v1/undo/issue', payload, { silent: true }),
  executeUndo: (tokenId: string) => request.post<void>(`/v1/undo/${tokenId}`),
  executeBatch: (
    spaceId: string,
    payload: {
      action: 'MOVE' | 'COPY' | 'DELETE'
      nodeIds: string[]
      targetSpaceId?: number
      targetParentId?: number
    },
  ) => request.post<BatchExecuteResult>(`/v1/spaces/${spaceId}/batch/execute`, payload),
  createZipBatch: (spaceId: string, nodeIds: string[]) =>
    request.post<BatchExecuteResult>(`/v1/spaces/${spaceId}/batch/zip`, { nodeIds }),
  downloadZipBatch: (jobId: string) => request.download(`/v1/batch/jobs/${jobId}/download`),

  // Content & thumbnail
  downloadContent: (spaceId: string, nodeId: string, versionId?: string) =>
    request.download(
      `/v1/spaces/${spaceId}/nodes/${nodeId}/content`,
      versionId ? { versionId } : undefined,
      { timeout: 180_000 },
    ),
  textChunk: (
    spaceId: string,
    nodeId: string,
    params: { versionId?: string; offset?: number; limit?: number } = {},
    signal?: AbortSignal,
  ) => request.get<TextChunk>(`/v1/spaces/${spaceId}/nodes/${nodeId}/text`, params, { signal }),
  previewAsset: (
    spaceId: string,
    nodeId: string,
    assetType: 'OFFICE_PDF' | 'VIDEO_MP4',
    versionId?: string,
  ) =>
    request.get<PreviewAsset>(`/v1/spaces/${spaceId}/nodes/${nodeId}/preview/assets/${assetType}`, {
      versionId,
    }),
  retryPreviewAsset: (
    spaceId: string,
    nodeId: string,
    assetType: 'OFFICE_PDF' | 'VIDEO_MP4',
    versionId?: string,
  ) =>
    request.post<PreviewAsset>(
      `/v1/spaces/${spaceId}/nodes/${nodeId}/preview/assets/${assetType}/retry`,
      undefined,
      { params: { versionId } },
    ),
  previewAssetContent: (
    spaceId: string,
    nodeId: string,
    assetType: 'OFFICE_PDF' | 'VIDEO_MP4',
    versionId?: string,
  ) =>
    request.download(
      `/v1/spaces/${spaceId}/nodes/${nodeId}/preview/assets/${assetType}/content`,
      { versionId },
      { timeout: 180_000 },
    ),
  excelSheets: (spaceId: string, nodeId: string, versionId?: string) =>
    request.get<ExcelMetadata>(`/v1/spaces/${spaceId}/nodes/${nodeId}/preview/excel/sheets`, {
      versionId,
    }),
  excelRows: (
    spaceId: string,
    nodeId: string,
    params: { versionId?: string; sheetIndex?: number; rowOffset?: number; rowLimit?: number } = {},
  ) =>
    request.get<ExcelRowsPage>(`/v1/spaces/${spaceId}/nodes/${nodeId}/preview/excel/rows`, params),
  videoProbe: (spaceId: string, nodeId: string, versionId?: string) =>
    request.get<VideoProbe>(`/v1/spaces/${spaceId}/nodes/${nodeId}/preview/video/probe`, {
      versionId,
    }),
  streamTicket: (
    spaceId: string,
    nodeId: string,
    params: { versionId?: string; assetType?: 'ORIGINAL' | 'VIDEO_MP4' } = {},
  ) =>
    request.post<StreamTicket>(
      `/v1/spaces/${spaceId}/nodes/${nodeId}/preview/stream-ticket`,
      undefined,
      { params },
    ),
  archiveEntries: (spaceId: string, nodeId: string, versionId?: string) =>
    request.get<ArchiveListing>(`/v1/spaces/${spaceId}/nodes/${nodeId}/preview/archive/entries`, {
      versionId,
    }),
  archiveExtract: (spaceId: string, nodeId: string, entryPath: string, versionId?: string) =>
    request.download(`/v1/spaces/${spaceId}/nodes/${nodeId}/preview/archive/extract`, {
      entryPath,
      versionId,
    }),
  thumbnail: (spaceId: string, nodeId: string, width = 240, versionId?: string) =>
    request.download(
      `/v1/spaces/${spaceId}/nodes/${nodeId}/preview/thumbnail`,
      { width, ...(versionId ? { versionId } : {}) },
      { silent: true },
    ),
  replaceFile: (node: Pick<DriveNode, 'id' | 'spaceId' | 'parentId' | 'revision'>, file: File) =>
    request.upload(`/v1/spaces/${node.spaceId}/files`, file, {
      extraData: { parentId: node.parentId, replaceId: node.id, revision: node.revision },
    }),

  // Versions & global search
  versions: (spaceId: string, nodeId: string) =>
    request.get<FileVersion[]>(`/v1/spaces/${spaceId}/nodes/${nodeId}/versions`),
  restoreVersion: (spaceId: string, nodeId: string, versionId: string, revision: number) =>
    request.post<void>(
      `/v1/spaces/${spaceId}/nodes/${nodeId}/versions/${versionId}/restore`,
      undefined,
      { params: { revision } },
    ),
  search: (
    params: {
      query?: string
      spaceId?: string
      category?: string
      extension?: string
      includeContent?: boolean
      page?: number
      pageSize?: number
    },
    signal?: AbortSignal,
  ) => request.get<SearchResponse>('/v1/search', params, { signal }),

  // Private / hidden unlock & Vault
  unlockPrivateSpace: (spaceId: string, password: string, idleMinutes = 15) =>
    request.post<UnlockPrivateResult>(`/v1/spaces/${spaceId}/private/unlock`, { password, idleMinutes }),
  lockPrivateSpace: (spaceId: string) =>
    request.post<void>(`/v1/spaces/${spaceId}/private/lock`),
  unlockVault: (password: string, idleMinutes = 15) =>
    request.post<UnlockPrivateResult>('/v1/me/private/unlock', { password, idleMinutes }),
  lockVault: () => request.post<void>('/v1/me/private/lock'),
  privateOverview: () => request.get<PrivateOverview>('/v1/me/private/overview', undefined, { silent: true }),
  setSpacePrivate: (spaceId: string, enabled: boolean) =>
    request.put<void>(`/v1/me/private/spaces/${spaceId}`, undefined, { params: { enabled } }),

  // Super-admin read-only stats
  adminStats: () => request.get<Record<string, unknown>>('/v1/admin/stats', undefined, { silent: true }),

  // Media timeline (M039 / M041)
  mediaTimeline: (params: { spaceId?: string; mediaType?: 'ALL' | 'IMAGE' | 'VIDEO'; page?: number; pageSize?: number } = {}) =>
    request.get<{
      items: Array<{
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
      }>
      total: number
      page: number
      pageSize: number
      hasMore: boolean
    }>('/v1/media/timeline', params),

  // Server upload sessions
  listUploads: () => request.get<UploadSession[]>('/v1/uploads'),
  getUploadSession: (uploadId: string, signal?: AbortSignal) =>
    request.get<UploadSession>(`/v1/uploads/${uploadId}`, undefined, { signal, silent: true }),
  createUploadSession: (
    payload: { spaceId: string; parentId: string; name: string; size: number; mimeType: string },
    signal?: AbortSignal,
  ) => request.post<UploadSession>('/v1/uploads', payload, { signal }),
  uploadChunk: (
    uploadId: string,
    chunkIndex: number,
    chunkBlob: Blob,
    filename: string,
    onChunkProgress: (loaded: number, total: number) => void,
    signal?: AbortSignal,
  ) => {
    const form = new FormData()
    form.append('file', chunkBlob, filename)
    return request.put<void>(`/v1/uploads/${uploadId}/chunks/${chunkIndex}`, form, {
      signal,
      timeout: 300_000,
      onUploadProgress: evt => onChunkProgress(evt.loaded ?? 0, evt.total || chunkBlob.size || 1),
    })
  },
  completeUploadSession: (uploadId: string, signal?: AbortSignal) =>
    waitForUploadCompletion({
      signal,
      requestCompletion: () => request.post<UploadSession>(`/v1/uploads/${uploadId}/finalize`, undefined, { signal, silent: true }),
      getSession: () => request.get<UploadSession>(`/v1/uploads/${uploadId}`, undefined, { signal, silent: true }),
    }),
  cancelUploadSession: (uploadId: string) => request.delete<void>(`/v1/uploads/${uploadId}`),
}

export async function uploadFileDirect(
  spaceId: string,
  parentId: string,
  file: File,
  onProgress?: (percent: number, message?: string) => void,
  signal?: AbortSignal,
): Promise<string> {
  const upload = await request.post<UploadSession>(
    '/v1/uploads',
    { spaceId, parentId, name: file.name, size: file.size, mimeType: file.type || 'application/octet-stream' },
    { signal },
  )
  try {
    let fileId = upload.fileId || ''
    if (upload.status !== 'COMPLETED') {
      const received = new Set(upload.received ?? [])
      for (let index = 0; index < upload.chunkCount; index++) {
        if (!received.has(index)) {
          const chunkBlob = file.slice(index * upload.chunkSize, Math.min(file.size, (index + 1) * upload.chunkSize))
          const form = new FormData()
          form.append('file', chunkBlob, file.name)
          await request.put(`/v1/uploads/${upload.id}/chunks/${index}`, form, {
            signal,
            timeout: 300_000,
            onUploadProgress: evt => {
              if (!onProgress) return
              const loaded = evt.loaded ?? 0
              const total = evt.total || chunkBlob.size || 1
              const fraction = Math.min(1, loaded / total)
              onProgress(Math.min(99, Math.round(((index + fraction) / upload.chunkCount) * 100)))
            },
          })
        }
        onProgress?.(Math.min(99, Math.round(((index + 1) / upload.chunkCount) * 100)))
      }
      onProgress?.(99, '正在合并与校验')
      fileId = await mobileDriveApi.completeUploadSession(upload.id, signal)
    }
    onProgress?.(100, '')
    return fileId
  } catch (error) {
    if (signal?.aborted && signal.reason === 'cancelled') await mobileDriveApi.cancelUploadSession(upload.id)
    throw error
  }
}
