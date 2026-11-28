export type VideoAssetType = 'ORIGINAL' | 'VIDEO_MP4' | 'VIDEO_HLS'

export interface VideoStreamState {
  status: 'PENDING' | 'PROCESSING' | 'PLAYABLE' | 'READY' | 'FAILED'
  availableSeconds: number
  errorMessage: string | null
}

/** HLS remains lazy-loaded so ordinary MP4 previews pay no extra startup cost. */
export async function attachHlsPlayback(
  media: HTMLVideoElement,
  url: string,
  onFailure: (reason: 'network' | 'media') => void,
): Promise<() => void> {
  if (media.canPlayType('application/vnd.apple.mpegurl')) {
    media.src = url
    return () => { media.removeAttribute('src'); media.load() }
  }
  const { default: Hls } = await import('hls.js')
  if (!Hls.isSupported()) throw new Error('当前浏览器不支持流式视频播放，请下载后查看')
  const hls = new Hls({
    startPosition: 0,
    initialLiveManifestSize: 1,
    lowLatencyMode: false,
    maxBufferLength: 20,
    backBufferLength: 60,
    liveDurationInfinity: false,
  })
  hls.on(Hls.Events.ERROR, (_, error) => {
    if (error.fatal) onFailure(error.type === Hls.ErrorTypes.NETWORK_ERROR ? 'network' : 'media')
  })
  hls.loadSource(url)
  hls.attachMedia(media)
  return () => hls.destroy()
}
