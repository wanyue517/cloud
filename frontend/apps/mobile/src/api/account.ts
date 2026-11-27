import { request } from '@repo/session/request'
import type { ApiSchemas } from '@repo/api-client'

export type SpaceSummary = Required<ApiSchemas['DriveModels_Space']>

export interface UserProfile {
  id: string
  displayName: string
  avatarUrl: string | null
  admin: boolean
  usedBytes: number
  quotaBytes: number
}

export interface DeviceSessionItem {
  id: string
  deviceName: string
  clientType: 'WEB' | 'MOBILE_H5' | 'IOS' | 'ANDROID' | string
  ipAddress: string
  createdAt: string
  lastUsedAt: string
  expiresAt: string
  current: boolean
}

export interface UserPreferences {
  theme: string
  density: string
  fileView: 'list' | 'grid' | string
  fileSort: 'name' | 'updatedAt' | 'size' | string
  defaultSpaceId: string
}

export interface UserVersionPolicy {
  maxVersions: number
  systemMaxVersions: number
}

export const accountApi = {
  me: () => request.get<UserProfile>('/v1/me'),
  updateMe: (payload: { displayName: string; avatarUrl: string | null }) =>
    request.put<void>('/v1/me', payload),
  uploadAvatar: (file: File) =>
    request.upload<{ id: string; url: string }>('/v1/assets/images', file, {
      extraData: { category: 'avatar' },
    }),
  changePassword: (payload: { currentPassword: string; newPassword: string }) =>
    request.put<void>('/v1/me/password', payload),
  listSessions: () => request.get<DeviceSessionItem[]>('/v1/auth/sessions'),
  revokeSession: (id: string) => request.delete<void>(`/v1/auth/sessions/${id}`),
  getPreferences: () => request.get<UserPreferences>('/v1/me/preferences'),
  updatePreferences: (payload: Partial<UserPreferences>) =>
    request.put<UserPreferences>('/v1/me/preferences', payload),
  getVersionPolicy: () => request.get<UserVersionPolicy>('/v1/me/version-policy'),
  updateVersionPolicy: (maxVersions: number) =>
    request.put<UserVersionPolicy>('/v1/me/version-policy', { maxVersions }),
  listSpaces: () => request.get<SpaceSummary[]>('/v1/spaces'),
}
