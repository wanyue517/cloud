import { request } from '@/utils/request'
import type { ApiSchemas, ResourceId } from '@repo/api-client'

export type Space = Required<ApiSchemas['DriveModels_Space']>
export type DriveNode = Omit<Required<ApiSchemas['DriveModels_Node']>, 'nodeType' | 'currentVersionId'> & {
  nodeType: 'FILE' | 'FOLDER'
  currentVersionId: ResourceId | null
}
export type DirectoryStats = { folders: number; files: number; categories: Record<string, number> }
export type TextChunk = { content: string; offset: number; bytesRead: number; totalBytes: number; hasMore: boolean; binaryDetected: boolean }
export type PreviewAsset = {
  id: string; fileId: string; versionId: string; assetType: string
  status: 'PENDING' | 'PROCESSING' | 'READY' | 'FAILED'
  mimeType: string | null; sizeBytes: number; errorMessage: string | null; attemptCount: number; updatedAt: string
}
export type ExcelSheetInfo = { index: number; name: string; rowCount: number; colCount: number }
export type ExcelMetadata = { sheets: ExcelSheetInfo[] }
export type ExcelRowsPage = {
  sheetIndex: number; sheetName: string; rowOffset: number; rowLimit: number
  totalRows: number; colCount: number; hasMore: boolean; rows: string[][]
}
export type VideoProbe = {
  container: string; videoCodec: string; audioCodec: string; durationSeconds: number
  width: number; height: number; bitrate: number
  strategy: 'DIRECT_PLAY' | 'REMUX' | 'TRANSCODE'; reason: string
}
export type StreamTicket = { ticket: string; streamUrl: string; expiresInSeconds: number }
export type ArchiveEntry = { path: string; name: string; directory: boolean; size: number; compressedSize: number; modifiedAt: string }
export type ArchiveListing = { format: string; totalEntries: number; truncated: boolean; entries: ArchiveEntry[] }
export type NodePage = Omit<Required<ApiSchemas['DriveModels_Page']>, 'items'> & { items: DriveNode[] }
export type Version = Required<ApiSchemas['DriveModels_Version']>
export type Upload = Omit<Required<ApiSchemas['DriveModels_Upload']>, 'fileId'> & { fileId: ResourceId | null }
export const drive = {
  spaces: () => request.get<Space[]>('/v1/spaces'),
  createSpace: (name: string, extra: { kind?: 'PERSONAL' | 'SHARED' | 'PRIVATE'; description?: string; color?: string; icon?: string; sortOrder?: number } = {}) =>
    request.post<string>('/v1/spaces', { name, ...extra }),
  updateSpace: (id: string, name: string, sortOrder = 0, extra: { description?: string; color?: string; icon?: string } = {}) =>
    request.put(`/v1/spaces/${id}`, { name, sortOrder, ...extra }),
  deleteSpace: (id: string, confirmation: string) => request.delete(`/v1/spaces/${id}`, { confirmation }),
  stats: (space: string, parentId = '0', hidden = false, includeSubdirs = false) =>
    request.get<DirectoryStats>(`/v1/spaces/${space}/stats`, { parentId, hidden, includeSubdirs }),
  transfer: (node: DriveNode, targetSpaceId: string, targetParentId: string, mode: 'MOVE' | 'COPY') =>
    request.post<DriveNode>(`/v1/spaces/${node.spaceId}/nodes/${node.id}/transfer`, { targetSpaceId, targetParentId, mode, revision: node.revision }),
  nodes: (space: string, params: Record<string, unknown> = {}) => request.get<NodePage>(`/v1/spaces/${space}/nodes`, params),
  ancestors: (space: string, id: string) => request.get<DriveNode[]>(`/v1/spaces/${space}/nodes/${id}/ancestors`),
  folder: (space: string, parentId: string, name: string) => request.post<DriveNode>(`/v1/spaces/${space}/folders`, { parentId, name }),
  change: (node: DriveNode, name: string, parentId = node.parentId) => request.patch<DriveNode>(`/v1/spaces/${node.spaceId}/nodes/${node.id}`, { name, parentId, revision: node.revision }),
  trash: (node: DriveNode) => request.delete(`/v1/spaces/${node.spaceId}/nodes/${node.id}`, { revision: node.revision }),
  restore: (node: DriveNode) => request.post(`/v1/spaces/${node.spaceId}/nodes/${node.id}/restore`),
  purgeTrashNode: (node: DriveNode) => request.delete<number>(`/v1/spaces/${node.spaceId}/trash/${node.id}`),
  emptyTrash: (space: string) => request.delete<number>(`/v1/spaces/${space}/trash`),
  content: (node: DriveNode, versionId?: string) => request.download(`/v1/spaces/${node.spaceId}/nodes/${node.id}/content`, { versionId }),
  textChunk: (node: DriveNode, params: { versionId?: string; offset?: number; limit?: number } = {}) =>
    request.get<TextChunk>(`/v1/spaces/${node.spaceId}/nodes/${node.id}/text`, params),
  previewAsset: (node: DriveNode, assetType: 'OFFICE_PDF' | 'VIDEO_MP4', versionId?: string) =>
    request.get<PreviewAsset>(`/v1/spaces/${node.spaceId}/nodes/${node.id}/preview/assets/${assetType}`, { versionId }),
  retryPreviewAsset: (node: DriveNode, assetType: 'OFFICE_PDF' | 'VIDEO_MP4', versionId?: string) =>
    request.post<PreviewAsset>(`/v1/spaces/${node.spaceId}/nodes/${node.id}/preview/assets/${assetType}/retry`, undefined, { params: { versionId } }),
  previewAssetContent: (node: DriveNode, assetType: 'OFFICE_PDF' | 'VIDEO_MP4', versionId?: string) =>
    request.download(`/v1/spaces/${node.spaceId}/nodes/${node.id}/preview/assets/${assetType}/content`, { versionId }),
  excelSheets: (node: DriveNode, versionId?: string) =>
    request.get<ExcelMetadata>(`/v1/spaces/${node.spaceId}/nodes/${node.id}/preview/excel/sheets`, { versionId }),
  excelRows: (node: DriveNode, params: { versionId?: string; sheetIndex?: number; rowOffset?: number; rowLimit?: number } = {}) =>
    request.get<ExcelRowsPage>(`/v1/spaces/${node.spaceId}/nodes/${node.id}/preview/excel/rows`, params),
  videoProbe: (node: DriveNode, versionId?: string) =>
    request.get<VideoProbe>(`/v1/spaces/${node.spaceId}/nodes/${node.id}/preview/video/probe`, { versionId }),
  streamTicket: (node: DriveNode, params: { versionId?: string; assetType?: 'ORIGINAL' | 'VIDEO_MP4' } = {}) =>
    request.post<StreamTicket>(`/v1/spaces/${node.spaceId}/nodes/${node.id}/preview/stream-ticket`, undefined, { params }),
  archiveEntries: (node: DriveNode, versionId?: string) =>
    request.get<ArchiveListing>(`/v1/spaces/${node.spaceId}/nodes/${node.id}/preview/archive/entries`, { versionId }),
  archiveExtract: (node: DriveNode, entryPath: string, versionId?: string) =>
    request.download(`/v1/spaces/${node.spaceId}/nodes/${node.id}/preview/archive/extract`, { entryPath, versionId }),
  thumbnail: (node: DriveNode, width = 320, versionId?: string) =>
    request.download(`/v1/spaces/${node.spaceId}/nodes/${node.id}/preview/thumbnail`, { width, versionId }, { silent: true }),
  versions: (node: DriveNode) => request.get<Version[]>(`/v1/spaces/${node.spaceId}/nodes/${node.id}/versions`),
  restoreVersion: (node: DriveNode, version: string) => request.post(`/v1/spaces/${node.spaceId}/nodes/${node.id}/versions/${version}/restore`, undefined, { params: { revision: node.revision } }),
  favorite: (node: DriveNode, enabled: boolean) => request.put(`/v1/spaces/${node.spaceId}/nodes/${node.id}/favorite`, undefined, { params: { enabled } }),
  replace: (node: DriveNode, file: File) => request.upload(`/v1/spaces/${node.spaceId}/files`, file, { extraData: { parentId: node.parentId, replaceId: node.id, revision: node.revision } }),
}

export async function uploadFile(spaceId: string, parentId: string, file: File, progress: (percent: number) => void, signal?: AbortSignal): Promise<string> {
  const key = `cloudlane.upload.${spaceId}.${parentId}.${file.name}.${file.size}.${file.lastModified}`
  let upload: Upload | undefined
  const previous = localStorage.getItem(key)
  if (previous) {
    try { upload = await request.get<Upload>(`/v1/uploads/${previous}`, undefined, { signal, silent: true }) }
    catch (error) { if (signal?.aborted) throw error; localStorage.removeItem(key) }
  }
  if (!upload || upload.status === 'CANCELLED') {
    upload = await request.post<Upload>('/v1/uploads', { spaceId, parentId, name: file.name, size: file.size, mimeType: file.type }, { signal })
    localStorage.setItem(key, upload.id)
  }
  let fileId = upload.fileId || ''
  if (upload.status !== 'COMPLETED') {
    const received = new Set(upload.received ?? [])
    for (let index = 0; index < upload.chunkCount; index++) {
      if (!received.has(index)) {
        const chunkBlob = file.slice(index * upload.chunkSize, Math.min(file.size, (index + 1) * upload.chunkSize))
        let lastErr: unknown
        for (let attempt = 0; attempt < 3; attempt++) {
          if (signal?.aborted) throw new Error('上传已暂停')
          try {
            const form = new FormData()
            form.append('file', chunkBlob, file.name)
            await request.put(`/v1/uploads/${upload.id}/chunks/${index}`, form, { signal, timeout: 120_000 })
            lastErr = undefined
            break
          } catch (err) {
            lastErr = err
            if (signal?.aborted || attempt === 2) break
            if (typeof setTimeout === 'function') {
              await new Promise(resolve => setTimeout(resolve, 300 * 2 ** attempt))
            }
          }
        }
        if (lastErr) throw lastErr
      }
      progress(Math.round((index + 1) / upload.chunkCount * 100))
    }
    fileId = await request.post<string>(`/v1/uploads/${upload.id}/complete`, undefined, { signal, timeout: 120_000 })
  }
  localStorage.removeItem(key)
  return fileId
}
