import { request, setupRequest } from './request'
import { clearAccessToken, getAccessToken, saveAccessToken } from './auth'
export function setupSession(onUnauthorized: () => void) {
  setupRequest({
    getAccessToken,
    refreshAccessToken: async () => {
      const result = await request.post<{ accessToken: string }>('/v1/auth/refresh', undefined, {
        skipAuth: true, skipRefresh: true, silent: true, withCredentials: true,
        headers: { 'X-Requested-With': 'Cloudlane' },
      })
      saveAccessToken(result.accessToken)
      return result.accessToken
    },
    onUnauthorized: () => { clearAccessToken(); onUnauthorized() },
  })
}
export async function logout() {
  try { await request.post('/v1/auth/logout') }
  finally { clearAccessToken() }
}
