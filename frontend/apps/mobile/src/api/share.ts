import { getAccessToken } from '@repo/session/auth'
import { request } from '@repo/session/request'

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

export const mobileShareApi = {
  spaceMembers: (spaceId: string) => request.get<SpaceMembersOverview>(`/v1/spaces/${spaceId}/members`),
  inviteToSpace: (
    spaceId: string,
    payload: { target: string; role: 'MANAGER' | 'EDITOR' | 'VIEWER'; message?: string },
  ) => request.post<string>(`/v1/spaces/${spaceId}/invitations`, payload),
  updateMemberRole: (spaceId: string, memberUserId: string, role: 'MANAGER' | 'EDITOR' | 'VIEWER') =>
    request.put<void>(`/v1/spaces/${spaceId}/members/${memberUserId}`, { role }),
  removeMember: (spaceId: string, memberUserId: string) =>
    request.delete<void>(`/v1/spaces/${spaceId}/members/${memberUserId}`),
  receivedInvitations: () => request.get<InvitationItem[]>('/v1/shares/invitations'),
  acceptInvitation: (id: string) => request.post<void>(`/v1/shares/invitations/${id}/accept`),
  rejectInvitation: (id: string) => request.post<void>(`/v1/shares/invitations/${id}/reject`),
  cancelInvitation: (id: string) => request.delete<void>(`/v1/shares/invitations/${id}`),
  shareFolder: (
    spaceId: string,
    nodeId: string,
    payload: { target: string; permission: 'VIEWER' | 'EDITOR' },
  ) => request.post<string>(`/v1/spaces/${spaceId}/nodes/${nodeId}/shares`, payload),
  revokeResourceShare: (shareId: string) => request.delete<void>(`/v1/shares/resources/${shareId}`),
  sharedWithMe: () => request.get<ResourceShareItem[]>('/v1/shares/with-me'),
  sharedByMe: () => request.get<ResourceShareItem[]>('/v1/shares/by-me'),
  createShareLink: (
    spaceId: string,
    nodeId: string,
    payload: { password?: string; expireDays?: number; allowDownload?: boolean; maxDownloads?: number },
  ) => request.post<CreatedShareLink>(`/v1/spaces/${spaceId}/nodes/${nodeId}/share-links`, payload),
  listMyShareLinks: () => request.get<ShareLinkItem[]>('/v1/share-links'),
  disableShareLink: (id: string) => request.post<void>(`/v1/share-links/${id}/disable`),
  publicShareMeta: (token: string, password?: string) =>
    request.get<PublicShareMeta>(
      `/v1/public/shares/${encodeURIComponent(token)}`,
      password ? { password } : undefined,
      { skipAuth: true, skipRefresh: true },
    ),
  verifySharePassword: (token: string, password: string) =>
    request.post<boolean>(
      `/v1/public/shares/${encodeURIComponent(token)}/verify`,
      { password },
      { skipAuth: true, skipRefresh: true },
    ),
  publicShareNodes: (token: string, password?: string, parentId?: string) =>
    request.get<{ items: PublicNode[] }>(
      `/v1/public/shares/${encodeURIComponent(token)}/nodes`,
      {
        ...(password ? { password } : {}),
        ...(parentId ? { parentId } : {}),
      },
      { skipAuth: true, skipRefresh: true },
    ),
  publicShareContent: (token: string, params: { password?: string; nodeId?: string; download?: boolean } = {}) =>
    request.download(
      `/v1/public/shares/${encodeURIComponent(token)}/content`,
      {
        ...(params.password ? { password: params.password } : {}),
        ...(params.nodeId ? { nodeId: params.nodeId } : {}),
        download: params.download ?? false,
      },
      { skipAuth: true, skipRefresh: true },
    ),
  savePublicShare: (
    token: string,
    payload: { targetSpaceId: number; targetParentId: number; nodeId?: number; password?: string },
  ) =>
    request.post<{ id: string; spaceId: string; name: string }>(
      `/v1/public/shares/${encodeURIComponent(token)}/save`,
      payload,
      payload.password ? { headers: { 'X-Share-Password': payload.password } } : undefined,
    ),
  inboxMessages: () => request.get<InboxResponse>('/v1/messages'),
  markMessageRead: (id: string) => request.put<void>(`/v1/messages/${id}/read`),
  markAllMessagesRead: () => request.post<void>('/v1/messages/read-all'),
}

/**
 * Fetch-based authenticated SSE subscriber with exponential backoff reconnect.
 * Returns a disposer function that cleanly aborts the stream and timers.
 */
export function subscribeSseWithBackoff(
  url: string,
  onEvent: (eventName: string, data: string) => void,
): () => void {
  let disposed = false
  let attempt = 0
  let controller: AbortController | null = null
  let reconnectTimer: ReturnType<typeof setTimeout> | null = null

  async function connect() {
    if (disposed) return
    const token = getAccessToken()
    if (!token) return

    controller = new AbortController()
    try {
      const resp = await fetch(url, {
        method: 'GET',
        headers: {
          Accept: 'text/event-stream',
          Authorization: `Bearer ${token}`,
        },
        signal: controller.signal,
      })
      if (!resp.ok || !resp.body) {
        throw new Error(`SSE HTTP ${resp.status}`)
      }
      attempt = 0
      const reader = resp.body.getReader()
      const decoder = new TextDecoder()
      let buffer = ''
      let currentEvent = 'message'
      let currentData: string[] = []

      while (!disposed) {
        const { done, value } = await reader.read()
        if (done) break
        buffer += decoder.decode(value, { stream: true })
        const lines = buffer.split(/\r?\n/)
        buffer = lines.pop() ?? ''
        for (const line of lines) {
          if (line.startsWith('event:')) {
            currentEvent = line.slice(6).trim() || 'message'
          } else if (line.startsWith('data:')) {
            currentData.push(line.slice(5).trim())
          } else if (line === '') {
            if (currentData.length > 0) {
              onEvent(currentEvent, currentData.join('\n'))
            }
            currentEvent = 'message'
            currentData = []
          }
        }
      }
    } catch {
      // reconnect with exponential backoff below
    }

    if (!disposed) {
      const delay = Math.min(30_000, 1000 * 2 ** Math.min(attempt, 5))
      attempt += 1
      reconnectTimer = setTimeout(() => void connect(), delay)
    }
  }

  void connect()

  return () => {
    disposed = true
    if (reconnectTimer) clearTimeout(reconnectTimer)
    controller?.abort()
  }
}
