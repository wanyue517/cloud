import { request } from '@/utils/request'

export interface UserVersionPolicy {
  maxVersions: number
  systemMaxVersions: number
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

export interface UndoTokenReceipt {
  tokenId: string
  actionType: string
  summary: string
  expiresAt: string
}

export interface BatchRenameRequest {
  nodeIds: string[]
  mode: 'PREFIX' | 'SUFFIX' | 'REPLACE' | 'SEQUENCE'
  prefix?: string
  suffix?: string
  find?: string
  replace?: string
  template?: string
  startNumber?: number
  keepExtension?: boolean
  conflictPolicy?: 'ABORT' | 'SKIP' | 'AUTO_NUMBER'
}

export interface BatchRenamePreviewItem {
  nodeId: string
  oldName: string
  newName: string
  conflict: boolean
  reason: string
}

export interface BatchRenameApplyResult {
  renamedCount: number
  skippedCount: number
  undo: UndoTokenReceipt | null
}

export interface BatchJobView {
  id: string
  jobType: string
  status: string
  totalCount: number
  successCount: number
  failedCount: number
  errorMessage: string | null
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

export const opsApi = {
  unlockVault: (password: string, idleMinutes = 15) =>
    request.post<UnlockPrivateResult>('/v1/me/private/unlock', { password, idleMinutes }),
  lockVault: () => request.post<void>('/v1/me/private/lock'),
  privateOverview: () => request.get<PrivateOverview>('/v1/me/private/overview', undefined, { silent: true }),
  setSpacePrivate: (spaceId: string, enabled: boolean) =>
    request.put<void>(`/v1/me/private/spaces/${spaceId}`, undefined, { params: { enabled } }),
  getVersionPolicy: () => request.get<UserVersionPolicy>('/v1/me/version-policy'),
  updateVersionPolicy: (maxVersions: number) => request.put<UserVersionPolicy>('/v1/me/version-policy', { maxVersions }),
  listQuickAccess: () => request.get<QuickAccessItem[]>('/v1/quick-access'),
  setPin: (spaceId: string, nodeId: string, enabled: boolean) =>
    request.put<void>(`/v1/spaces/${spaceId}/nodes/${nodeId}/pin`, undefined, { params: { enabled } }),
  setHidden: (spaceId: string, nodeId: string, enabled: boolean) =>
    request.put<void>(`/v1/spaces/${spaceId}/nodes/${nodeId}/hidden`, undefined, { params: { enabled } }),
  unlockPrivate: (spaceId: string, password: string, idleMinutes = 15) =>
    request.post<UnlockPrivateResult>(`/v1/spaces/${spaceId}/private/unlock`, { password, idleMinutes }),
  lockPrivate: (spaceId: string) => request.post<void>(`/v1/spaces/${spaceId}/private/lock`),
  createZipBatch: (spaceId: string, nodeIds: string[]) =>
    request.post<BatchJobView>(`/v1/spaces/${spaceId}/batch/zip`, { nodeIds }),
  downloadZipBatch: (jobId: string) => request.download(`/v1/batch/jobs/${jobId}/download`),
  recordAccess: (spaceId: string, nodeId: string) =>
    request.post<void>(`/v1/spaces/${spaceId}/nodes/${nodeId}/access`),
  listRecent: () => request.get<RecentAccessItem[]>('/v1/recent'),
  issueUndo: (payload: { spaceId: string; actionType: 'RENAME' | 'MOVE' | 'TRASH'; summary: string; nodeId: string; oldName?: string; oldParentId?: string }) =>
    request.post<UndoTokenReceipt>('/v1/undo/issue', payload),
  executeUndo: (tokenId: string) => request.post<void>(`/v1/undo/${tokenId}`),
  previewBatchRename: (spaceId: string, payload: BatchRenameRequest) =>
    request.post<BatchRenamePreviewItem[]>(`/v1/spaces/${spaceId}/nodes/batch-rename/preview`, payload),
  applyBatchRename: (spaceId: string, payload: BatchRenameRequest) =>
    request.post<BatchRenameApplyResult>(`/v1/spaces/${spaceId}/nodes/batch-rename/apply`, payload),
}
