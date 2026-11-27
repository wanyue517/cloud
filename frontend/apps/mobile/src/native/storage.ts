import { Preferences } from '@capacitor/preferences'
import type { TokenStorage } from '@repo/session/auth'
import { isNativeApp } from './platform'

const MANAGED_KEYS = ['cloudlane.accessToken', 'cloudlane.refreshToken'] as const

const memoryCache = new Map<string, string>()

/**
 * 在 Capacitor 原生 App 启动挂载 Vue 前调用，将持久化在原生 Preferences 中的
 * Access / Refresh Token 预热到同步内存镜像，保证 @repo/session 同步读取零延迟。
 */
export async function hydrateNativeTokenStorage(): Promise<void> {
  if (!isNativeApp()) return
  for (const key of MANAGED_KEYS) {
    try {
      const { value } = await Preferences.get({ key })
      if (value) {
        memoryCache.set(key, value)
      }
    } catch {
      // ignore read failure
    }
  }
}

/**
 * 原生环境：同步读写内存镜像 + 异步落盘 @capacitor/preferences（无 Cookie Refresh 模式）。
 * Web H5 环境：继续使用 sessionStorage / localStorage 默认行为，零 Web 回归。
 */
export function createNativeTokenStorage(): TokenStorage | undefined {
  if (!isNativeApp()) return undefined
  return {
    getItem(key: string): string | null {
      return memoryCache.get(key) ?? null
    },
    setItem(key: string, value: string): void {
      memoryCache.set(key, value)
      void Preferences.set({ key, value }).catch(() => undefined)
    },
    removeItem(key: string): void {
      memoryCache.delete(key)
      void Preferences.remove({ key }).catch(() => undefined)
    },
  }
}
