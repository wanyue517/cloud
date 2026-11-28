export interface PlaybackAddress {
  streamUrl: string
  expiresInSeconds: number
  expiresAt?: number
  mode?: 'DIRECT' | 'PROXY'
  proxyFallbackAllowed?: boolean
}

/** Owns URL renewal, a single direct-to-proxy fallback, and the metadata/decode watchdog. */
export function createMediaPlaybackSession(deps: {
  requestAddress: (forceProxy: boolean) => Promise<PlaybackAddress>
  applyAddress: (address: PlaybackAddress, position: number, playing: boolean) => Promise<void>
  getPosition: () => number
  isPlaying: () => boolean
  onFailure: (message: string) => void
  now?: () => number
  schedule?: (callback: () => void, ms: number) => unknown
  cancel?: (timer: unknown) => void
}) {
  const now = deps.now ?? Date.now
  const schedule = deps.schedule ?? ((callback, ms) => setTimeout(callback, ms))
  const cancel = deps.cancel ?? (timer => clearTimeout(timer as ReturnType<typeof setTimeout>))
  let disposed = false
  let busy = false
  let recoveryPending = false
  let failed = false
  let forceProxy = false
  let current: PlaybackAddress | undefined
  let renewal: unknown
  let watchdog: unknown
  let expiresAt = 0

  function clearTimers() {
    if (renewal !== undefined) cancel(renewal)
    if (watchdog !== undefined) cancel(watchdog)
    renewal = watchdog = undefined
  }

  async function load() {
    if (disposed || busy || failed) return
    busy = true
    clearTimers()
    try {
      const address = await deps.requestAddress(forceProxy)
      if (disposed) return
      current = address
      expiresAt = address.expiresAt ?? now() + address.expiresInSeconds * 1000
      const position = deps.getPosition()
      const playing = deps.isPlaying()
      // Register before applying: a cached media source can become ready immediately.
      watchdog = schedule(() => { void recover() }, 20000)
      await deps.applyAddress(address, Number.isFinite(position) ? position : 0, playing)
      if (disposed) return
      renewal = schedule(() => { void load() }, Math.max(1000, expiresAt - now() - 60000))
    } catch (error) {
      if (!disposed) fail(error instanceof Error ? error.message : '视频地址获取失败，请重试')
    } finally {
      busy = false
      if (recoveryPending) {
        recoveryPending = false
        void recover()
      }
    }
  }

  function fail(message: string) {
    clearTimers()
    if (failed || disposed) return
    failed = true
    deps.onFailure(message)
  }

  async function recover() {
    if (disposed || failed) return
    if (busy) {
      recoveryPending = true
      return
    }
    if (current && now() >= expiresAt) { await load(); return }
    if (current?.mode === 'DIRECT' && current.proxyFallbackAllowed && !forceProxy) {
      forceProxy = true
      await load()
      return
    }
    fail('视频加载或解码失败，请重试；也可尝试兼容转码或下载原件')
  }

  return {
    start: load,
    recover,
    ready() {
      if (watchdog !== undefined) cancel(watchdog)
      watchdog = undefined
      recoveryPending = false
    },
    dispose() { disposed = true; clearTimers() },
  }
}
