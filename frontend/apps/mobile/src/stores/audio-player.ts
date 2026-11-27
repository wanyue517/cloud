import { reactive } from 'vue'
import { normalizeErrorMessage } from '@repo/drive-core/errors'
import { mobileDriveApi, type DriveNode } from '@/api/drive'

export interface AudioTrack {
  spaceId: string
  nodeId: string
  versionId?: string
  name: string
  size: number
  mimeType: string
}

export const AUDIO_RATES = [0.5, 0.75, 1, 1.25, 1.5, 2] as const

export const audioState = reactive<{
  track: AudioTrack | null
  playing: boolean
  loading: boolean
  error: string
  currentTime: number
  duration: number
  playbackRate: number
  minimized: boolean
}>({
  track: null,
  playing: false,
  loading: false,
  error: '',
  currentTime: 0,
  duration: 0,
  playbackRate: 1,
  minimized: false,
})

let audioEl: HTMLAudioElement | null = null
let activeObjectUrl: string | null = null
let loadSeq = 0

function ensureAudioElement(): HTMLAudioElement | null {
  if (typeof Audio === 'undefined') return null
  if (audioEl) return audioEl
  const el = new Audio()
  el.preload = 'metadata'
  el.addEventListener('play', () => {
    audioState.playing = true
    updateMediaSessionPlaybackState('playing')
  })
  el.addEventListener('pause', () => {
    audioState.playing = false
    updateMediaSessionPlaybackState('paused')
  })
  el.addEventListener('ended', () => {
    audioState.playing = false
    updateMediaSessionPlaybackState('paused')
  })
  el.addEventListener('loadedmetadata', () => {
    audioState.duration = Number.isFinite(el.duration) ? el.duration : 0
  })
  el.addEventListener('timeupdate', () => {
    audioState.currentTime = el.currentTime || 0
    if (Number.isFinite(el.duration) && el.duration > 0) {
      audioState.duration = el.duration
    }
  })
  el.addEventListener('error', () => {
    audioState.playing = false
    audioState.loading = false
    audioState.error = '音频解码或加载失败'
  })
  audioEl = el
  return el
}

function updateMediaSessionPlaybackState(state: 'playing' | 'paused' | 'none') {
  if (typeof navigator === 'undefined' || !('mediaSession' in navigator)) return
  try {
    navigator.mediaSession.playbackState = state
  } catch {
    // ignore unsupported environments
  }
}

function syncMediaSessionMetadata(track: AudioTrack) {
  if (typeof navigator === 'undefined' || !('mediaSession' in navigator)) return
  try {
    if (typeof MediaMetadata !== 'undefined') {
      navigator.mediaSession.metadata = new MediaMetadata({
        title: track.name,
        artist: 'Cloudlane 云端音频',
      })
    }
    navigator.mediaSession.setActionHandler('play', () => void togglePlayAudio(true))
    navigator.mediaSession.setActionHandler('pause', () => void togglePlayAudio(false))
    navigator.mediaSession.setActionHandler('seekbackward', () => seekAudioBy(-15))
    navigator.mediaSession.setActionHandler('seekforward', () => seekAudioBy(15))
  } catch {
    // ignore unsupported actions
  }
}

function revokeAudioObjectUrl() {
  if (activeObjectUrl) {
    URL.revokeObjectURL(activeObjectUrl)
    activeObjectUrl = null
  }
}

export async function loadAndPlayAudioNode(node: DriveNode, versionId?: string) {
  const el = ensureAudioElement()
  if (!el) {
    audioState.error = '当前环境不支持音频播放'
    return
  }

  // If same track is already loaded, just keep playing / un-minimize
  if (
    audioState.track &&
    audioState.track.spaceId === node.spaceId &&
    audioState.track.nodeId === node.id &&
    audioState.track.versionId === (versionId ?? node.currentVersionId ?? undefined) &&
    el.src
  ) {
    audioState.minimized = false
    return
  }

  const seq = ++loadSeq
  audioState.loading = true
  audioState.error = ''
  audioState.currentTime = 0
  audioState.duration = 0
  audioState.minimized = false
  audioState.track = {
    spaceId: node.spaceId,
    nodeId: node.id,
    versionId: versionId ?? node.currentVersionId ?? undefined,
    name: node.name,
    size: node.size,
    mimeType: node.mimeType,
  }

  el.pause()
  revokeAudioObjectUrl()

  try {
    const ticket = await mobileDriveApi
      .streamTicket(node.spaceId, node.id, {
        versionId: versionId ?? node.currentVersionId ?? undefined,
        assetType: 'ORIGINAL',
      })
      .catch(() => null)
    if (seq !== loadSeq) return

    if (ticket?.streamUrl) {
      el.src = ticket.streamUrl
    } else {
      const blob = await mobileDriveApi.downloadContent(
        node.spaceId,
        node.id,
        versionId ?? node.currentVersionId ?? undefined,
      )
      if (seq !== loadSeq) return
      activeObjectUrl = URL.createObjectURL(blob)
      el.src = activeObjectUrl
    }

    el.playbackRate = audioState.playbackRate
    syncMediaSessionMetadata(audioState.track)
    await el.play().catch(() => undefined)
  } catch (err) {
    if (seq !== loadSeq) return
    audioState.error = normalizeErrorMessage(err, '加载音频失败')
  } finally {
    if (seq === loadSeq) audioState.loading = false
  }
}

export async function togglePlayAudio(forcePlay?: boolean) {
  const el = audioEl
  if (!el || !audioState.track) return
  const shouldPlay = forcePlay ?? el.paused
  if (shouldPlay) {
    await el.play().catch(() => undefined)
  } else {
    el.pause()
  }
}

export function seekAudioTo(seconds: number) {
  const el = audioEl
  if (!el) return
  const max = Number.isFinite(el.duration) ? el.duration : audioState.duration
  el.currentTime = Math.min(max || seconds, Math.max(0, seconds))
  audioState.currentTime = el.currentTime
}

export function seekAudioBy(deltaSeconds: number) {
  seekAudioTo((audioEl?.currentTime ?? audioState.currentTime) + deltaSeconds)
}

export function setAudioPlaybackRate(rate: number) {
  audioState.playbackRate = rate
  if (audioEl) audioEl.playbackRate = rate
}

export function minimizeAudioPlayer() {
  if (audioState.track && (audioState.playing || audioState.currentTime > 0)) {
    audioState.minimized = true
  }
}

export function stopAndCloseAudioPlayer() {
  loadSeq += 1
  if (audioEl) {
    audioEl.pause()
    audioEl.removeAttribute('src')
    audioEl.load()
  }
  revokeAudioObjectUrl()
  audioState.track = null
  audioState.playing = false
  audioState.loading = false
  audioState.error = ''
  audioState.currentTime = 0
  audioState.duration = 0
  audioState.minimized = false
  updateMediaSessionPlaybackState('none')
}
