<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import Player from 'xgplayer'
import 'xgplayer/dist/index.min.css'
import { AlertCircle, Download, Loader2, RefreshCw } from '@lucide/vue'
import { Button } from '@repo/ui/button'
import { createProgressReporter, formatResumePrompt, shouldPromptResume } from '@repo/drive-core/progress'
import { createMediaPlaybackSession, hasDecodedVideoFrame, needsVideoCompatibility } from '@repo/drive-core/media-playback'
import { attachHlsPlayback, type VideoAssetType, type VideoStreamState } from '@repo/drive-core/video-streaming'
import { drive, type DriveNode, type PreviewAsset, type Version } from '@/api/drive'

const props = defineProps<{ node: DriveNode; version?: Version }>()
const emit = defineEmits<{ download: [] }>()
const progressiveVideo = import.meta.env.VITE_PROGRESSIVE_VIDEO === 'true'

const containerRef = ref<HTMLElement | null>(null)
const loading = ref(true)
const error = ref('')
const transcodeAsset = ref<VideoStreamState | PreviewAsset | null>(null)
const retrying = ref(false)
const streamUrl = ref('')
const playbackRate = ref(1)
const playbackRates = [0.5, 0.75, 1, 1.25, 1.5, 2]
const resumePromptSec = ref<number | null>(null)
const progressReporter = createProgressReporter(
  (sec) => drive.saveProgress(props.node, 'VIDEO', sec),
  { intervalMs: 10_000 },
)

let player: Player | null = null
let pollTimer: number | null = null
let seq = 0
let playbackSession: ReturnType<typeof createMediaPlaybackSession> | undefined
let probeTimer: number | null = null
let compatibilityStarted = false
let compatibilityPosition = 0
let compatibilityPlaying = false
let activeAsset: VideoAssetType = 'ORIGINAL'
let disposeHls: (() => void) | undefined

function destroyPlayer() {
  disposeHls?.()
  disposeHls = undefined
  progressReporter.flush()
  progressReporter.reset()
  resumePromptSec.value = null
  if (player) {
    try { player.destroy() } catch { /* ignore */ }
    player = null
  }
}

function clearPoll() {
  if (probeTimer !== null) window.clearTimeout(probeTimer)
  probeTimer = null
  if (pollTimer !== null) {
    window.clearTimeout(pollTimer)
    pollTimer = null
  }
}

async function mountPlayer(url: string, currentSeq: number, position = 0, playing = false, hls = false) {
  streamUrl.value = url
  loading.value = false
  error.value = ''
  await nextTick()
  if (currentSeq !== seq) return
  destroyPlayer()
  if (!containerRef.value) return
  player = new Player({
    el: containerRef.value,
    url: hls ? '' : url,
    preload: 'auto',
    fluid: false,
    width: '100%',
    height: '100%',
    playbackRate: playbackRates,
    defaultPlaybackRate: playbackRate.value,
    pip: true,
    lang: 'zh-cn',
  })
  const instance = player
  let savedResumePosition: number | null = null
  let resumePromptEvaluated = false
  const updateResumePrompt = () => {
    if (player !== instance || currentSeq !== seq || savedResumePosition === null || resumePromptEvaluated) return
    const duration = Number(instance.duration)
    if (!Number.isFinite(duration) || duration <= 0) return
    resumePromptEvaluated = true
    if (shouldPromptResume('VIDEO', savedResumePosition, duration)) resumePromptSec.value = savedResumePosition
  }
  player.on('loadedmetadata', updateResumePrompt)
  player.on('durationchange', updateResumePrompt)
  if (hls) instance.handleSource = false
  player.on('canplay', () => { if (player === instance) playbackSession?.ready() })
  player.on('progress', () => { if (player === instance) playbackSession?.progress() })
  player.on('error', () => {
    if (player !== instance) return
    const media = (instance as unknown as { media?: HTMLMediaElement }).media
    // HTMLMediaElement error 2 is a transport failure, not an unsupported codec.
    void playbackSession?.recover(media?.error?.code === 2 ? 'network' : 'media')
  })
  player.once('loadedmetadata', () => {
    if (player !== instance) return
    if (position > 0) instance.currentTime = position
    if (playing) void instance.play()?.catch(() => undefined)
  })
  player.on('ratechange', () => {
    if (player && Number.isFinite(player.playbackRate)) {
      playbackRate.value = player.playbackRate
    }
  })
  player.on('timeupdate', () => {
    if (!player) return
    const sec = Number(player.currentTime)
    if (Number.isFinite(sec) && sec >= 1) progressReporter.report(sec)
    const media = containerRef.value?.querySelector('video')
    // Audio can play without a decodable picture and may never raise an error.
    if (activeAsset === 'ORIGINAL' && sec >= 2 && media && media.readyState >= 2 && !hasDecodedVideoFrame(media)) {
      void beginTranscode(currentSeq)
    }
  })
  player.on('pause', () => { progressReporter.flush() })
  void drive.getProgress(props.node).then((saved) => {
    if (currentSeq !== seq || player !== instance || !saved || saved.progressType !== 'VIDEO') return
    savedResumePosition = saved.positionValue
    updateResumePrompt()
  }).catch(() => undefined)
  if (hls) {
    const media = instance.media as HTMLVideoElement
    const dispose = await attachHlsPlayback(media, url, reason => {
      if (player === instance) void playbackSession?.recover(reason)
    })
    if (currentSeq !== seq || player !== instance) dispose()
    else disposeHls = dispose
  }
}

async function startPlayback(assetType: VideoAssetType, currentSeq: number) {
  playbackSession?.dispose()
  activeAsset = assetType
  const versionId = props.version?.id ?? props.node.currentVersionId ?? undefined
  playbackSession = createMediaPlaybackSession({
    requestAddress: forceProxy => drive.streamTicket(props.node, { versionId, assetType, forceProxy }),
    applyAddress: async (address, position, playing) => {
      if (currentSeq === seq && address.assetType && address.assetType !== 'ORIGINAL') {
        activeAsset = address.assetType
        compatibilityStarted = true
        clearPoll()
      }
      if (currentSeq === seq) await mountPlayer(address.streamUrl, currentSeq, position, playing, address.assetType === 'VIDEO_HLS')
    },
    getPosition: () => Number(player?.currentTime ?? compatibilityPosition),
    isPlaying: () => player ? !player.paused : compatibilityPlaying,
    onStartupTimeout: () => {
      if (currentSeq !== seq) return
      if (activeAsset === 'ORIGINAL') void beginTranscode(currentSeq)
      else void playbackSession?.recover('timeout')
    },
    onFailure: message => {
      if (currentSeq !== seq) return
      if (assetType === 'ORIGINAL' && activeAsset === 'ORIGINAL') {
        void beginTranscode(currentSeq)
      } else { error.value = message; loading.value = false }
    },
  })
  await playbackSession.start()
}

async function beginTranscode(currentSeq: number) {
  if (currentSeq !== seq || compatibilityStarted) return
  compatibilityStarted = true
  compatibilityPosition = Number(player?.currentTime || 0)
  compatibilityPlaying = Boolean(player && !player.paused)
  clearPoll()
  playbackSession?.dispose()
  destroyPlayer()
  streamUrl.value = ''
  error.value = ''
  loading.value = true
  await pollTranscode(currentSeq)
}

async function pollProbe(currentSeq: number) {
  if (compatibilityStarted || currentSeq !== seq) return
  try {
    const result = await drive.videoProbe(props.node, props.version?.id ?? props.node.currentVersionId ?? undefined)
    if (currentSeq !== seq) return
    if (result.status === 'PENDING' || result.status === 'PROCESSING') {
      probeTimer = window.setTimeout(() => { void pollProbe(currentSeq) }, 2500)
    } else if (needsVideoCompatibility(result, containerRef.value?.querySelector('video'))) {
      await beginTranscode(currentSeq)
    }
  } catch {
    // Playback remains independent of probe availability.
  }
}

function resumeVideoFromSaved() {
  if (!player || resumePromptSec.value === null) return
  player.currentTime = resumePromptSec.value
  resumePromptSec.value = null
  void player.play()
}

async function pollTranscode(currentSeq: number) {
  try {
    const versionId = props.version?.id ?? props.node.currentVersionId ?? undefined
    const asset = progressiveVideo ? await drive.videoSession(props.node, versionId)
      : await drive.previewAsset(props.node, 'VIDEO_MP4', versionId)
    if (currentSeq !== seq) return
    transcodeAsset.value = asset
    if (asset.status === 'READY' || asset.status === 'PLAYABLE') {
      const target = progressiveVideo ? 'VIDEO_HLS' : 'VIDEO_MP4'
      if (activeAsset !== target) await startPlayback(target, currentSeq)
      if (asset.status === 'PLAYABLE') pollTimer = window.setTimeout(() => { void pollTranscode(currentSeq) }, 3000)
      return
    }
    if (asset.status === 'PENDING' || asset.status === 'PROCESSING') {
      loading.value = false
      pollTimer = window.setTimeout(() => { void pollTranscode(currentSeq) }, 1000)
    } else {
      playbackSession?.dispose()
      destroyPlayer()
      streamUrl.value = ''
      error.value = asset.errorMessage || '视频处理失败，请重试'
      loading.value = false
    }
  } catch (e) {
    if (currentSeq !== seq) return
    error.value = e instanceof Error ? e.message : '视频转码状态查询失败'
    loading.value = false
  }
}

async function retryTranscode() {
  playbackSession?.dispose()
  destroyPlayer()
  streamUrl.value = ''
  retrying.value = true
  error.value = ''
  clearPoll()
  const currentSeq = ++seq
  try {
    compatibilityStarted = true
    activeAsset = 'ORIGINAL'
    const versionId = props.version?.id ?? props.node.currentVersionId ?? undefined
    transcodeAsset.value = progressiveVideo ? await drive.videoSession(props.node, versionId, true)
      : await drive.retryPreviewAsset(props.node, 'VIDEO_MP4', versionId)
    pollTimer = window.setTimeout(() => { void pollTranscode(currentSeq) }, 1500)
  } catch (e) {
    error.value = e instanceof Error ? e.message : '重试转码失败'
  } finally {
    retrying.value = false
  }
}

async function loadVideo() {
  playbackSession?.dispose()
  compatibilityStarted = false
  compatibilityPosition = 0
  compatibilityPlaying = false
  clearPoll()
  destroyPlayer()
  streamUrl.value = ''
  const currentSeq = ++seq
  loading.value = true
  error.value = ''
  transcodeAsset.value = null
  try {
    await startPlayback('ORIGINAL', currentSeq)
    if (currentSeq === seq) void pollProbe(currentSeq)
  } catch (e) {
    if (currentSeq !== seq) return
    error.value = e instanceof Error ? e.message : '视频加载失败'
    loading.value = false
  }
}

watch(() => [props.node.id, props.version?.id], () => { void loadVideo() })
onMounted(() => { void loadVideo() })
onBeforeUnmount(() => {
  playbackSession?.dispose()
  seq++
  clearPoll()
  destroyPlayer()
  streamUrl.value = ''
})
</script>

<template>
  <div class="flex h-full min-h-0 flex-1 flex-col bg-black/95">
    <div class="relative flex min-h-0 flex-1 items-center justify-center">
      <p v-if="loading" role="status" class="text-sm text-white/80">正在探测并加载视频…</p>
      <div v-else-if="error || transcodeAsset?.status === 'FAILED'" class="flex max-w-md flex-col items-center gap-3 rounded-xl bg-zinc-900 p-6 text-center text-zinc-100">
        <AlertCircle :size="32" class="text-red-400" />
        <p class="text-sm font-medium">视频转码或加载失败</p>
        <p class="text-xs text-zinc-400">{{ error || transcodeAsset?.errorMessage }}</p>
        <div class="flex gap-2">
          <Button variant="outline" size="sm" :disabled="retrying" @click="retryTranscode">
            <RefreshCw :size="14" /> 重试转码
          </Button>
          <Button size="sm" @click="emit('download')"><Download :size="14" /> 下载原件</Button>
        </div>
      </div>
      <div v-else-if="transcodeAsset?.status === 'PENDING' || transcodeAsset?.status === 'PROCESSING'" class="flex flex-col items-center gap-3 text-center text-zinc-200">
        <Loader2 :size="32" class="animate-spin text-primary" />
        <p class="text-sm">{{ progressiveVideo ? '正在准备首段视频…' : '正在准备兼容视频…' }}</p>
      </div>
      <div v-show="streamUrl && !loading && !error" ref="containerRef" class="h-full w-full" />
      <div
        v-if="resumePromptSec !== null && streamUrl && !loading && !error"
        class="absolute bottom-16 right-4 z-30 flex max-w-[calc(100%-2rem)] flex-wrap items-center justify-end gap-2 rounded-xl border border-white/15 bg-black/80 px-3.5 py-2 text-xs text-white shadow-lg backdrop-blur"
        role="status"
        @click.stop
      >
        <span>{{ formatResumePrompt('VIDEO', resumePromptSec) }}</span>
        <Button size="sm" class="h-6 px-2.5 text-xs" @click="resumeVideoFromSaved">跳转播放</Button>
        <button type="button" class="text-white/70 hover:text-white" @click="resumePromptSec = null">忽略</button>
      </div>
    </div>
  </div>
</template>
