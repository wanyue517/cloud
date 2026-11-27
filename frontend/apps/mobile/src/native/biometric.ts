export type BiometryKind = 'face' | 'fingerprint' | 'platform' | 'none'

export interface BiometricAvailability {
  available: boolean
  biometryType: BiometryKind
  label: string
}

export interface BiometricAuthResult {
  ok: boolean
  cancelled?: boolean
  message?: string
}

export interface SecurityLockPrefs {
  biometricVaultEnabled: boolean
  appStartupLockEnabled: boolean
  backgroundBlurEnabled: boolean
}

const SECURITY_LOCK_PREFS_KEY = 'cloudlane.mobile.securityLockPrefs'

const DEFAULT_PREFS: SecurityLockPrefs = {
  biometricVaultEnabled: false,
  appStartupLockEnabled: false,
  backgroundBlurEnabled: true,
}

export function loadSecurityLockPrefs(): SecurityLockPrefs {
  try {
    const raw = globalThis.localStorage?.getItem(SECURITY_LOCK_PREFS_KEY)
    if (!raw) return { ...DEFAULT_PREFS }
    const parsed = JSON.parse(raw) as Partial<SecurityLockPrefs>
    return {
      biometricVaultEnabled: Boolean(parsed.biometricVaultEnabled),
      appStartupLockEnabled: Boolean(parsed.appStartupLockEnabled),
      backgroundBlurEnabled:
        typeof parsed.backgroundBlurEnabled === 'boolean'
          ? parsed.backgroundBlurEnabled
          : DEFAULT_PREFS.backgroundBlurEnabled,
    }
  } catch {
    return { ...DEFAULT_PREFS }
  }
}

export function saveSecurityLockPrefs(next: Partial<SecurityLockPrefs>): SecurityLockPrefs {
  const merged: SecurityLockPrefs = { ...loadSecurityLockPrefs(), ...next }
  try {
    globalThis.localStorage?.setItem(SECURITY_LOCK_PREFS_KEY, JSON.stringify(merged))
  } catch {
    // ignore storage errors
  }
  return merged
}

interface NativeBiometricPluginLike {
  isAvailable: () => Promise<{ isAvailable: boolean; biometryType?: number | string }>
  verifyIdentity: (options: {
    reason: string
    title?: string
    subtitle?: string
    useFallback?: boolean
  }) => Promise<void>
}

function getCapacitorBiometricPlugin(): NativeBiometricPluginLike | null {
  const win = globalThis as unknown as {
    Capacitor?: { Plugins?: { NativeBiometric?: NativeBiometricPluginLike; BiometricAuth?: NativeBiometricPluginLike } }
  }
  return win.Capacitor?.Plugins?.NativeBiometric ?? win.Capacitor?.Plugins?.BiometricAuth ?? null
}

/**
 * 检测设备是否支持生物识别：
 * 1. 优先检测 Capacitor 原生生物识别插件（FaceID / TouchID / Android BiometricPrompt）
 * 2. Web / PWA 环境下检测平台认证器（PublicKeyCredential.isUserVerifyingPlatformAuthenticatorAvailable）
 * 3. 不支持的设备返回 available: false，前端据此隐藏生物识别开关。
 */
export async function checkBiometricAvailability(): Promise<BiometricAvailability> {
  const nativePlugin = getCapacitorBiometricPlugin()
  if (nativePlugin) {
    try {
      const res = await nativePlugin.isAvailable()
      if (res?.isAvailable) {
        const rawType = String(res.biometryType ?? '').toLowerCase()
        if (rawType.includes('face') || rawType === '2') {
          return { available: true, biometryType: 'face', label: '面容 ID' }
        }
        return { available: true, biometryType: 'fingerprint', label: '指纹 / 生物识别' }
      }
    } catch {
      // fall through
    }
  }

  try {
    const pkc = (
      globalThis as unknown as {
        PublicKeyCredential?: { isUserVerifyingPlatformAuthenticatorAvailable?: () => Promise<boolean> }
      }
    ).PublicKeyCredential
    if (pkc?.isUserVerifyingPlatformAuthenticatorAvailable) {
      const ok = await pkc.isUserVerifyingPlatformAuthenticatorAvailable()
      if (ok) {
        return { available: true, biometryType: 'platform', label: '设备生物识别' }
      }
    }
  } catch {
    // unsupported
  }

  return { available: false, biometryType: 'none', label: '不支持' }
}

/**
 * 触发生物识别验证；失败或用户取消时返回 { ok: false, cancelled }，由调用方平滑降级到密码输入。
 */
export async function authenticateBiometric(reason = '验证身份以继续使用 Cloudlane'): Promise<BiometricAuthResult> {
  const nativePlugin = getCapacitorBiometricPlugin()
  if (nativePlugin) {
    try {
      await nativePlugin.verifyIdentity({
        reason,
        title: 'Cloudlane 安全验证',
        useFallback: false,
      })
      return { ok: true }
    } catch (err) {
      const msg = err instanceof Error ? err.message : String(err ?? '')
      const cancelled = /cancel|abort|user/i.test(msg)
      return {
        ok: false,
        cancelled,
        message: cancelled ? '已取消生物识别，请使用密码解锁' : '生物识别验证未通过，请使用密码解锁',
      }
    }
  }

  // WebAuthn 平台验证降级检测：若未配置原生生物插件，提示平滑降级密码输入
  return {
    ok: false,
    cancelled: false,
    message: '当前环境未挂载原生生物识别模块，请使用账户密码解锁',
  }
}
