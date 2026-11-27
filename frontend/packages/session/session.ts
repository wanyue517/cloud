import { request, setApiBaseUrl, setupRequest } from './request'
import {
  clearAccessToken,
  clearRefreshToken,
  configureTokenStorage,
  getAccessToken,
  getRefreshToken,
  saveAccessToken,
  saveRefreshToken,
  type TokenStorage,
} from './auth'

export type ClientType = 'WEB' | 'MOBILE_H5' | 'IOS' | 'ANDROID'

export interface SetupSessionOptions {
  clientType?: ClientType
  deviceName?: string
  storage?: TokenStorage
  useHeaderRefresh?: boolean
  baseURL?: string
}

export function setupSession(onUnauthorized: () => void, options: SetupSessionOptions = {}) {
  if (options.storage) {
    configureTokenStorage(options.storage)
  }
  if (options.baseURL) {
    setApiBaseUrl(options.baseURL)
  }
  const defaultHeaders: Record<string, string> = {}
  if (options.clientType) defaultHeaders['X-Client-Type'] = options.clientType
  if (options.deviceName) defaultHeaders['X-Device-Name'] = options.deviceName

  setupRequest({
    defaultHeaders,
    getAccessToken,
    refreshAccessToken: async () => {
      const headers: Record<string, string> = { 'X-Requested-With': 'Cloudlane' }
      if (options.useHeaderRefresh) {
        const refreshToken = getRefreshToken()
        if (!refreshToken) return null
        headers['X-Refresh-Token'] = refreshToken
      }
      const result = await request.post<{ accessToken: string; refreshToken?: string }>('/v1/auth/refresh', undefined, {
        skipAuth: true,
        skipRefresh: true,
        silent: true,
        withCredentials: !options.useHeaderRefresh,
        headers,
      })
      saveAccessToken(result.accessToken)
      if (options.useHeaderRefresh && result.refreshToken) {
        saveRefreshToken(result.refreshToken)
      }
      return result.accessToken
    },
    onUnauthorized: () => {
      clearAccessToken()
      clearRefreshToken()
      onUnauthorized()
    },
  })
}

export async function logout() {
  try {
    await request.post('/v1/auth/logout')
  } finally {
    clearAccessToken()
    clearRefreshToken()
  }
}
