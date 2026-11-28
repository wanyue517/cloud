export interface PlaybackAddress {
  streamUrl: string
  expiresInSeconds: number
  expiresAt?: number
  mode?: 'DIRECT' | 'PROXY'
  proxyFallbackAllowed?: boolean
  assetType?: 'ORIGINAL' | 'VIDEO_MP4' | 'VIDEO_HLS'
}

/** Owns URL renewal, a single direct-to-proxy fallback, and the metadata/decode watchdog. */
export function createMediaPlaybackSession(deps: {
  requestAddress: (forceProxy: boolean) => Promise<PlaybackAddress>
  applyAddress: (address: PlaybackAddress, position: number, playing: boolean) => Promise<void>
  getPosition: () => number
  isPlaying: () => boolean
  onFailure: (message: string) => void
  onStartupTimeout?: () => void
  now?: () => number
  schedule?: (callback: () => void, ms: number) => unknown
  cancel?: (timer: unknown) => void
}) {
  const now = deps.now ?? Date.now
  const schedule = deps.schedule ?? ((callback, ms) => setTimeout(callback, ms))
  const cancel = deps.cancel ?? (timer => clearTimeout(timer as ReturnType<typeof setTimeout>))
  let disposed = false
  let busy = false
  let recoveryPending: 'network' | 'media' | 'timeout' | undefined
  let failed = false
  let forceProxy = false
  let current: PlaybackAddress | undefined
  let renewal: unknown
  let watchdog: unknown
  let startupDeadline: unknown
  let expiresAt = 0
  let retries = 0
  const startupIdleMs = 60_000
  const maxRetries = 1

  function clearTimers() {
    if (renewal !== undefined) cancel(renewal)
    if (watchdog !== undefined) cancel(watchdog)
    if (startupDeadline !== undefined) cancel(startupDeadline)
    startupDeadline = undefined
    renewal = watchdog = undefined
  }

  function armWatchdog() {
    if (watchdog !== undefined) cancel(watchdog)
    watchdog = schedule(() => {
      watchdog = undefined
      void recover('timeout')
    }, startupIdleMs)
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
      armWatchdog()
      // Download progress does not prove that metadata or a picture can be decoded.
      startupDeadline = schedule(() => {
        startupDeadline = undefined
        if (disposed || failed) return
        if (deps.onStartupTimeout) deps.onStartupTimeout()
        else void recover('timeout')
      }, 20_000)
      await deps.applyAddress(address, Number.isFinite(position) ? position : 0, playing)
      if (disposed) return
      renewal = schedule(() => { void load() }, Math.max(1000, expiresAt - now() - 60000))
    } catch (error) {
      if (!disposed) fail(error instanceof Error ? error.message : '视频地址获取失败，请重试')
    } finally {
      busy = false
      if (recoveryPending) {
        const reason = recoveryPending
        recoveryPending = undefined
        void recover(reason)
      }
    }
  }

  function fail(message: string) {
    clearTimers()
    if (failed || disposed) return
    failed = true
    deps.onFailure(message)
  }

  async function recover(reason: 'network' | 'media' | 'timeout' = 'media') {
    if (disposed || failed) return
    if (busy) {
      recoveryPending = reason
      return
    }
    if (current && now() >= expiresAt) { await load(); return }
    if (current?.mode === 'DIRECT' && current.proxyFallbackAllowed && !forceProxy) {
      forceProxy = true
      retries = 0
      await load()
      return
    }
    if (reason !== 'media' && retries < maxRetries) {
      retries++
      await load()
      return
    }
    fail(reason === 'media' ? '视频加载或解码失败，请重试；也可尝试兼容转码或下载原件' : '视频数据长时间未到达，请检查网络或存储连接后重试')
  }

  return {
    start: load,
    recover,
    progress() {
      // Only extend the startup deadline while real media progress is being reported.
      if (!disposed && !failed && watchdog !== undefined) armWatchdog()
    },
    ready() {
      if (watchdog !== undefined) cancel(watchdog)
      if (startupDeadline !== undefined) cancel(startupDeadline)
      startupDeadline = undefined
      watchdog = undefined
      recoveryPending = undefined
      retries = 0
    },
    dispose() { disposed = true; clearTimers() },
  }
}

export function hasDecodedVideoFrame(media: HTMLVideoElement): boolean {
  if (media.videoWidth === 0 || media.videoHeight === 0) return false
  if (typeof media.getVideoPlaybackQuality !== 'function') return true
  const quality = media.getVideoPlaybackQuality()
  return quality.totalVideoFrames - (quality.droppedVideoFrames ?? 0) > 0
}

export function needsVideoCompatibility(probe: { strategy: string; videoCodec: string; audioCodec: string }, media?: HTMLVideoElement | null): boolean {
  if (probe.strategy !== 'REMUX' && probe.strategy !== 'TRANSCODE') return false
  // Keep successful native/hardware decoding, including HEVC-capable browsers.
  // An unknown fragmented source still needs its container normalized.
  return !(probe.videoCodec && media && hasDecodedVideoFrame(media)
    && ['', 'aac', 'mp3', 'opus', 'vorbis', 'flac'].includes(probe.audioCodec))
}
