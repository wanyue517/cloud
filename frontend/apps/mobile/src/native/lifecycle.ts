import { App as CapApp } from '@capacitor/app'
import { Network as CapNetwork } from '@capacitor/network'
import { StatusBar, Style } from '@capacitor/status-bar'
import { isNativeApp } from './platform'

/**
 * 统一监听前后台切换：
 * - Capacitor 原生 App：订阅 App.addListener('appStateChange')
 * - Web H5：监听 document.visibilitychange
 */
export function onAppStateChange(callback: (isActive: boolean) => void): () => void {
  const cleanups: Array<() => void> = []

  if (typeof document !== 'undefined') {
    const onVis = () => {
      callback(document.visibilityState === 'visible')
    }
    document.addEventListener('visibilitychange', onVis)
    cleanups.push(() => document.removeEventListener('visibilitychange', onVis))
  }

  if (isNativeApp()) {
    let removed = false
    void CapApp.addListener('appStateChange', ({ isActive }) => {
      if (!removed) callback(isActive)
    }).then((handle) => {
      if (removed) {
        void handle.remove()
      } else {
        cleanups.push(() => {
          void handle.remove()
        })
      }
    })
    cleanups.push(() => {
      removed = true
    })
  }

  return () => {
    for (const fn of cleanups) {
      try {
        fn()
      } catch {
        // ignore
      }
    }
  }
}

export async function syncNativeStatusBar(isDark: boolean): Promise<void> {
  if (!isNativeApp()) return
  try {
    await StatusBar.setStyle({ style: isDark ? Style.Dark : Style.Light })
  } catch {
    // ignore on unsupported platforms
  }
}

export function subscribeNativeNetwork(
  callback: (connected: boolean, connectionType: string) => void,
): () => void {
  if (!isNativeApp()) return () => {}
  let removed = false
  let removeHandle: (() => Promise<void>) | null = null

  void CapNetwork.getStatus()
    .then((st) => {
      if (!removed) callback(st.connected, st.connectionType)
    })
    .catch(() => undefined)

  void CapNetwork.addListener('networkStatusChange', (st) => {
    if (!removed) callback(st.connected, st.connectionType)
  }).then((handle) => {
    if (removed) void handle.remove()
    else removeHandle = () => handle.remove()
  })

  return () => {
    removed = true
    if (removeHandle) void removeHandle()
  }
}
