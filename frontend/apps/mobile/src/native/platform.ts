import { Capacitor } from '@capacitor/core'
import type { ClientType } from '@repo/session/session'

export type NativePlatform = 'web' | 'ios' | 'android'

export function isNativeApp(): boolean {
  try {
    return Capacitor.isNativePlatform()
  } catch {
    return false
  }
}

export function getNativePlatform(): NativePlatform {
  try {
    const p = Capacitor.getPlatform()
    if (p === 'ios' || p === 'android') return p
    return 'web'
  } catch {
    return 'web'
  }
}

export function getSessionClientType(): ClientType {
  const p = getNativePlatform()
  if (p === 'ios') return 'IOS'
  if (p === 'android') return 'ANDROID'
  return 'MOBILE_H5'
}

export function getDefaultDeviceName(): string {
  const p = getNativePlatform()
  if (p === 'ios') return 'Cloudlane iOS App'
  if (p === 'android') return 'Cloudlane Android App'
  return 'Cloudlane 移动浏览器'
}
