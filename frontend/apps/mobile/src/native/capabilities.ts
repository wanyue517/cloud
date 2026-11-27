function detectNativeRuntime(): boolean {
  try {
    const cap = (globalThis as unknown as { Capacitor?: { isNativePlatform?: () => boolean } }).Capacitor
    return cap?.isNativePlatform?.() === true
  } catch {
    return false
  }
}

export interface MobileCapabilities {
  /** 是否运行在 Capacitor 原生 App 容器中（Android / iOS） */
  readonly isNative: boolean
  /** 自托管服务器地址配置（H5 同源部署不需要） */
  readonly customServer: boolean
  /** 原生生物识别安全锁（FaceID / 指纹） */
  readonly biometricLock: boolean
  /** 应用启动锁与后台防偷窥高斯模糊遮罩 */
  readonly appSecurityShield: boolean
  /** 原生后台保活分片传输（M040） */
  readonly backgroundTransfer: boolean
  /** 手机相册自动增量备份（M040） */
  readonly photoAutoBackup: boolean
  /** 原生沙箱离线缓存与无网阅读（M042） */
  readonly offlineCache: boolean
  /** 系统级「分享到 Cloudlane」接收（M043） */
  readonly nativeShareReceive: boolean
  /** 调用系统其他 App 打开文件（M043） */
  readonly openWithExternalApp: boolean
  /** 原生文档扫描转 PDF（M043） */
  readonly documentScanner: boolean
}

/**
 * 统一能力矩阵：H5 浏览器部署时所有原生专属能力全部返回 false，
 * 前端据此彻底隐藏 H5 无法使用的入口，避免出现无效按钮。
 */
export function getMobileCapabilities(nativeOverride?: boolean): MobileCapabilities {
  const native = typeof nativeOverride === 'boolean' ? nativeOverride : detectNativeRuntime()
  return {
    isNative: native,
    customServer: native,
    biometricLock: native,
    appSecurityShield: native,
    backgroundTransfer: native,
    photoAutoBackup: native,
    offlineCache: native,
    nativeShareReceive: native,
    openWithExternalApp: native,
    documentScanner: native,
  }
}

export const mobileCapabilities: MobileCapabilities = getMobileCapabilities()
