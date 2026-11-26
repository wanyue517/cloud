import { request } from '@/utils/request'

export type SpaceRole = 'OWNER' | 'MANAGER' | 'EDITOR' | 'VIEWER'

export interface SpaceMemberItem {
  id: string
  userId: string
  displayName: string
  email: string
  role: SpaceRole
  joinedAt: string
  expiresAt: string
}

export interface InvitationItem {
  id: string
  targetType: 'SPACE' | 'FOLDER'
  spaceId: string
  spaceName: string
  nodeId: string | null
  nodeName: string
  inviterUserId: string
  inviterName: string
  inviteeUserId: string
  inviteeName: string
  role: SpaceRole
  status: string
  message: string
  createdAt: string
}

export interface SpaceMembersOverview {
  spaceId: string
  kind: 'PERSONAL' | 'SHARED' | 'PRIVATE'
  myRole: SpaceRole
  members: SpaceMemberItem[]
  pendingInvitations: InvitationItem[]
}

export interface ResourceShareItem {
  id: string
  spaceId: string
  spaceName: string
  nodeId: string
  nodeName: string
  nodeType: string
  ownerUserId: string
  ownerName: string
  targetUserId: string
  targetName: string
  permission: 'VIEWER' | 'EDITOR'
  sourceType: string
  createdAt: string
}

export function roleLabel(role: string): string {
  switch (role) {
    case 'OWNER': return '所有者'
    case 'MANAGER': return '管理员'
    case 'EDITOR': return '可编辑'
    case 'VIEWER': return '仅查看'
    default: return role
  }
}

export interface CreatedShareLink {
  id: string
  token: string
  urlPath: string
  hasPassword: boolean
  allowDownload: boolean
  maxDownloads: number | null
  expiresAt: string
}

export interface ShareLinkItem {
  id: string
  spaceId: string
  nodeId: string
  nodeName: string
  nodeType: string
  tokenPrefix: string
  hasPassword: boolean
  allowDownload: boolean
  maxDownloads: number | null
  downloadCount: number
  viewCount: number
  expiresAt: string
  disabledAt: string
  createdAt: string
}

export interface PublicShareMeta {
  nodeId: string
  nodeName: string
  nodeType: string
  size: number
  mimeType: string
  ownerName: string
  requiresPassword: boolean
  unlocked: boolean
  allowDownload: boolean
  expiresAt: string
}

export interface PublicNode {
  id: string
  parentId: string
  name: string
  nodeType: string
  size: number
  mimeType: string
  updatedAt: string
}

export interface MessageItem {
  id: string
  category: string
  scope: string
  title: string
  content: string
  actionUrl: string
  read: boolean
  createdAt: string
}

export interface InboxResponse {
  unreadCount: number
  items: MessageItem[]
}

export const shareApi = {
  convertToShared: (spaceId: string) => request.post<void>(`/v1/spaces/${spaceId}/convert-shared`),
  convertToPersonal: (spaceId: string, keepMembers = true) =>
    request.post<void>(`/v1/spaces/${spaceId}/convert-personal`, undefined, { params: { keepMembers } }),
  spaceMembers: (spaceId: string) => request.get<SpaceMembersOverview>(`/v1/spaces/${spaceId}/members`),
  inviteToSpace: (spaceId: string, payload: { target: string; role: 'MANAGER' | 'EDITOR' | 'VIEWER'; message?: string }) =>
    request.post<string>(`/v1/spaces/${spaceId}/invitations`, payload),
  updateMemberRole: (spaceId: string, memberUserId: string, role: 'MANAGER' | 'EDITOR' | 'VIEWER') =>
    request.put<void>(`/v1/spaces/${spaceId}/members/${memberUserId}`, { role }),
  removeMember: (spaceId: string, memberUserId: string) =>
    request.delete<void>(`/v1/spaces/${spaceId}/members/${memberUserId}`),
  receivedInvitations: () => request.get<InvitationItem[]>('/v1/shares/invitations'),
  acceptInvitation: (id: string) => request.post<void>(`/v1/shares/invitations/${id}/accept`),
  rejectInvitation: (id: string) => request.post<void>(`/v1/shares/invitations/${id}/reject`),
  cancelInvitation: (id: string) => request.delete<void>(`/v1/shares/invitations/${id}`),
  shareFolder: (spaceId: string, nodeId: string, payload: { target: string; permission: 'VIEWER' | 'EDITOR' }) =>
    request.post<string>(`/v1/spaces/${spaceId}/nodes/${nodeId}/shares`, payload),
  revokeResourceShare: (shareId: string) => request.delete<void>(`/v1/shares/resources/${shareId}`),
  sharedWithMe: () => request.get<ResourceShareItem[]>('/v1/shares/with-me'),
  sharedByMe: () => request.get<ResourceShareItem[]>('/v1/shares/by-me'),
  createShareLink: (spaceId: string, nodeId: string, payload: { password?: string; expireDays?: number; allowDownload?: boolean; maxDownloads?: number }) =>
    request.post<CreatedShareLink>(`/v1/spaces/${spaceId}/nodes/${nodeId}/share-links`, payload),
  listMyShareLinks: () => request.get<ShareLinkItem[]>('/v1/share-links'),
  disableShareLink: (id: string) => request.post<void>(`/v1/share-links/${id}/disable`),
  publicShareMeta: (token: string, password?: string) =>
    request.get<PublicShareMeta>(`/v1/public/shares/${encodeURIComponent(token)}`, password ? { password } : undefined),
  verifySharePassword: (token: string, password: string) =>
    request.post<boolean>(`/v1/public/shares/${encodeURIComponent(token)}/verify`, { password }),
  publicShareNodes: (token: string, password?: string, parentId?: string) =>
    request.get<{ items: PublicNode[] }>(`/v1/public/shares/${encodeURIComponent(token)}/nodes`, {
      ...(password ? { password } : {}),
      ...(parentId ? { parentId } : {}),
    }),
  inboxMessages: () => request.get<InboxResponse>('/v1/messages'),
  markMessageRead: (id: string) => request.put<void>(`/v1/messages/${id}/read`),
  markAllMessagesRead: () => request.post<void>('/v1/messages/read-all'),
}
