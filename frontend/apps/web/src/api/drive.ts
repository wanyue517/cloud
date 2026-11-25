import { request } from '@/utils/request'

export interface Space { id: string; name: string; kind: string; color: string; sortOrder: number }
export interface DriveNode {
  id: string; spaceId: string; parentId: string; name: string; nodeType: 'FILE' | 'FOLDER'
  currentVersionId: string | null; revision: number; size: number; mimeType: string
  hidden: boolean; updatedAt: string; hasChildren: boolean
}
export interface NodePage { items: DriveNode[]; total: number; page: number; pageSize: number }
export interface Version { id: string; number: number; size: number; mimeType: string; createdAt: string }
export interface Upload { id: string; chunkSize: number; chunkCount: number; received: number[]; status: string; fileId: string | null }
export const drive = {
  spaces: () => request.get<Space[]>('/v1/spaces'),
  createSpace: (name: string) => request.post<string>('/v1/spaces', { name }),
  updateSpace: (id: string, name: string, sortOrder = 0) => request.put(`/v1/spaces/${id}`, { name, sortOrder }),
  deleteSpace: (id: string, confirmation: string) => request.delete(`/v1/spaces/${id}`, { confirmation }),
  nodes: (space: string, params: Record<string, unknown> = {}) => request.get<NodePage>(`/v1/spaces/${space}/nodes`, params),
  ancestors: (space: string, id: string) => request.get<DriveNode[]>(`/v1/spaces/${space}/nodes/${id}/ancestors`),
  folder: (space: string, parentId: string, name: string) => request.post<DriveNode>(`/v1/spaces/${space}/folders`, { parentId, name }),
  change: (node: DriveNode, name: string, parentId = node.parentId) => request.patch<DriveNode>(`/v1/spaces/${node.spaceId}/nodes/${node.id}`, { name, parentId, revision: node.revision }),
  trash: (node: DriveNode) => request.delete(`/v1/spaces/${node.spaceId}/nodes/${node.id}`, { revision: node.revision }),
  restore: (node: DriveNode) => request.post(`/v1/spaces/${node.spaceId}/nodes/${node.id}/restore`),
  content: (node: DriveNode, versionId?: string) => request.download(`/v1/spaces/${node.spaceId}/nodes/${node.id}/content`, { versionId }),
  versions: (node: DriveNode) => request.get<Version[]>(`/v1/spaces/${node.spaceId}/nodes/${node.id}/versions`),
  restoreVersion: (node: DriveNode, version: string) => request.post(`/v1/spaces/${node.spaceId}/nodes/${node.id}/versions/${version}/restore`, undefined, { params: { revision: node.revision } }),
  favorite: (node: DriveNode, enabled: boolean) => request.put(`/v1/spaces/${node.spaceId}/nodes/${node.id}/favorite`, undefined, { params: { enabled } }),
  replace: (node: DriveNode, file: File) => request.upload(`/v1/spaces/${node.spaceId}/files`, file, { extraData: { parentId: node.parentId, replaceId: node.id, revision: node.revision } }),
}

export async function uploadFile(spaceId: string, parentId: string, file: File, progress: (percent: number) => void, signal?: AbortSignal) {
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
  if (upload.status !== 'COMPLETED') {
    for (let index = 0; index < upload.chunkCount; index++) {
      if (!upload.received.includes(index)) {
        const form = new FormData()
        form.append('file', file.slice(index * upload.chunkSize, Math.min(file.size, (index + 1) * upload.chunkSize)), file.name)
        await request.put(`/v1/uploads/${upload.id}/chunks/${index}`, form, { signal, timeout: 120_000 })
      }
      progress(Math.round((index + 1) / upload.chunkCount * 100))
    }
    await request.post(`/v1/uploads/${upload.id}/complete`, undefined, { signal, timeout: 120_000 })
  }
  localStorage.removeItem(key)
}
