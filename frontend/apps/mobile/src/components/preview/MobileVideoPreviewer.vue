<script setup lang="ts">
import { nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { Button as VanButton, showToast } from 'vant'
import { Loader2, Lock, RefreshCw, Unlock, WifiOff } from '@lucide/vue'
import Player from 'xgplayer'
import 'xgplayer/dist/index.min.css'
import { normalizeErrorMessage } from '@repo/drive-core/errors'
import { formatBytes } from '@repo/drive-core/format'
import { createProgressReporter, formatResumePrompt, shouldPromptResume } from '@repo/drive-core/progress'
import { createMediaPlaybackSession } from '@repo/drive-core/media-playback'
import { mobileDriveApi, type DriveNode, type PreviewAsset, type VideoProbe } from '@/api/drive'
import { formatMediaTime } from '@/lib/archive-tree'
import { networkPrefs, networkStatus, shouldWarnVideoOnNetwork } from '@/lib/network'

const props = defineProps<{
  node: DriveNode
  versionId?: string
}>()

const emit = defineEmits<{
  'toggle-chrome': []
  download: []
}>()

const containerRef = ref<HTMLElement | null>(null)
const needsCellularConfirm = ref(false)
const loading = ref(true)
const transcodeAsset = ref<PreviewAsset | null>(null)
const errorText = ref('')
const retrying = ref(false)
const streamReady = ref(false)

// Gesture, speed & lock states
const VIDEO_RATES = [0.5, 0.75, 1, 1.25, 1.5, 2] as const
const currentRate = ref(1)
const rateMenuOpen = ref(false)
const locked = ref(false)
const dimOpacity = ref(0) // 0..0.65 brightness mask
const gestureBadge = ref('')
const resumePromptSec = ref<number | null>(null)
const progressReporter = createProgressReporter(
  (sec) => mobileDriveApi.saveProgress(props.node.spaceId, props.node.id, 'VIDEO', sec),
  { intervalMs: 10_000 },
)

let player: Player | null = null
let pollTimer: ReturnType<typeof setTimeout> | null = null
let runSeq = 0
let playbackSession: ReturnType<typeof createMediaPlaybackSession> | undefined
let probeTimer: ReturnType<typeof setTimeout> | undefined
let probeResult: VideoProbe | null = null
let playbackFailed = false
let longPressTimer: ReturnType<typeof setTimeout> | null = null
let prevRate = 1
let speedBoosting = false
let touchStartX = 0
let touchStartY = 0
let gestureMode: '' | 'seek' | 'brightness' | 'volume' = ''
let gestureStartValue = 0
let seekTargetSec = 0

function clearPoll() {
  if (pollTimer) {
    clearTimeout(pollTimer)
    pollTimer = null
  }
}

function clearLongPress() {
  if (longPressTimer) {
    clearTimeout(longPressTimer)
    longPressTimer = null
  }
  window.removeEventListener('touchend', onGestureTouchEnd, true)
  window.removeEventListener('touchcancel', onGestureTouchEnd, true)
}

function destroyPlayer() {
  progressReporter.flush()
  progressReporter.reset()
  resumePromptSec.value = null
  clearPoll()
  clearLongPress()
  if (player) {
    try {
      player.destroy()
    } catch {
      // ignore destroy errors
    }
    player = null
  }
  streamReady.value = false
  gestureBadge.value = ''
}

function applyPlaybackRate(rate: number) {
  currentRate.value = rate
  rateMenuOpen.value = false
  if (!player) return
  player.playbackRate = rate
  const media = (player as unknown as { media?: HTMLMediaElement }).media
  if (media) {
    media.defaultPlaybackRate = rate
    media.playbackRate = rate
  }
}

async function mountPlayer(streamUrl: string, seq: number, position = 0, playing = false) {
  if (seq !== runSeq) return
  destroyPlayer()
  streamReady.value = true
  loading.value = false
  errorText.value = ''
  currentRate.value = 1
  rateMenuOpen.value = false
  await nextTick()
  if (seq !== runSeq || !containerRef.value) return
  player = new Player({
    el: containerRef.value,
    url: streamUrl,
    playsinline: true,
    fluid: true,
    width: '100%',
    height: '100%',
    autoplay: false,
    defaultPlaybackRate: 1,
    // 关闭 xgplayer 内置倍速与内置长按倍速，统一由移动端触控倍速胶囊和手势层控制，彻底避免默认 2x 与冲突
    playbackRate: false,
    mobile: {
      disablePress: true,
      gestureX: false,
      gestureY: false,
    },
    download: false,
    lang: 'zh-cn',
  })
  const instance = player
  player.on('canplay', () => { if (player === instance) playbackSession?.ready() })
  player.on('error', () => { if (player === instance) void playbackSession?.recover() })
  player.once('loadedmetadata', () => {
    if (player !== instance) return
    if (position > 0) instance.currentTime = position
    if (playing) void instance.play()?.catch(() => undefined)
  })
  const enforceInitialRate = () => {
    if (!player || speedBoosting) return
    const media = (player as unknown as { media?: HTMLMediaElement }).media
    if (media) {
      media.defaultPlaybackRate = currentRate.value
      if (media.playbackRate !== currentRate.value) {
        media.playbackRate = currentRate.value
      }
    }
    if (player.playbackRate !== currentRate.value) {
      player.playbackRate = currentRate.value
    }
  }
  player.on('ready', enforceInitialRate)
  player.on('loadedmetadata', enforceInitialRate)
  player.on('canplay', enforceInitialRate)
  player.on('play', enforceInitialRate)
  player.on('ratechange', () => {
    if (!player || speedBoosting) return
    const media = (player as unknown as { media?: HTMLMediaElement }).media
    const actual = Number(media?.playbackRate ?? player.playbackRate)
    if (Number.isFinite(actual) && actual > 0) {
      currentRate.value = actual
    }
  })
  player.on('timeupdate', () => {
    if (!player) return
    const sec = Number(player.currentTime)
    if (Number.isFinite(sec) && sec >= 1) {
      progressReporter.report(sec)
    }
  })
  player.on('pause', () => {
    progressReporter.flush()
  })
  void mobileDriveApi
    .getProgress(props.node.spaceId, props.node.id)
    .then((saved) => {
      if (seq !== runSeq || !saved || saved.progressType !== 'VIDEO') return
      const dur = Number(player?.duration || 0)
      if (shouldPromptResume('VIDEO', saved.positionValue, dur > 0 ? dur : undefined)) {
        resumePromptSec.value = saved.positionValue
      }
    })
    .catch(() => undefined)
}

async function startPlayback(assetType: 'ORIGINAL' | 'VIDEO_MP4', seq: number) {
  playbackSession?.dispose()
  playbackSession = createMediaPlaybackSession({
    requestAddress: forceProxy => mobileDriveApi.streamTicket(props.node.spaceId, props.node.id, {
      versionId: props.versionId ?? props.node.currentVersionId ?? undefined, assetType, forceProxy,
    }),
    applyAddress: async (address, position, playing) => {
      if (seq === runSeq) await mountPlayer(address.streamUrl, seq, position, playing)
    },
    getPosition: () => Number(player?.currentTime || 0),
    isPlaying: () => Boolean(player && !player.paused),
    onFailure: message => {
      if (seq !== runSeq) return
      playbackFailed = true
      if (assetType === 'ORIGINAL' && (probeResult?.strategy === 'TRANSCODE' || probeResult?.strategy === 'REMUX')) {
        void beginTranscode(seq)
      } else { errorText.value = message; loading.value = false }
    },
  })
  await playbackSession.start()
}

async function beginTranscode(seq: number) {
  playbackSession?.dispose()
  destroyPlayer()
  errorText.value = ''
  loading.value = true
  await pollTranscode(seq)
}

async function pollProbe(seq: number) {
  try {
    const result = await mobileDriveApi.videoProbe(props.node.spaceId, props.node.id, props.versionId ?? props.node.currentVersionId ?? undefined)
    if (seq !== runSeq) return
    probeResult = result
    if (result.status === 'PENDING' || result.status === 'PROCESSING') {
      probeTimer = setTimeout(() => { void pollProbe(seq) }, 2500)
    } else if (playbackFailed && (result.strategy === 'TRANSCODE' || result.strategy === 'REMUX')) {
      await beginTranscode(seq)
    }
  } catch { /* The original video can play even when probing is unavailable. */ }
}

function resumeVideoFromSaved() {
  if (!player || resumePromptSec.value === null) return
  player.currentTime = resumePromptSec.value
  resumePromptSec.value = null
  void player.play()
}

async function pollTranscode(seq: number) {
  clearPoll()
  try {
    const asset = await mobileDriveApi.previewAsset(
      props.node.spaceId,
      props.node.id,
      'VIDEO_MP4',
      props.versionId ?? props.node.currentVersionId ?? undefined,
    )
    if (seq !== runSeq) return
    transcodeAsset.value = asset
    if (asset.status === 'READY') {
      await startPlayback('VIDEO_MP4', seq)
    } else if (asset.status === 'PENDING' || asset.status === 'PROCESSING') {
      loading.value = false
      pollTimer = setTimeout(() => void pollTranscode(seq), 2500)
    } else {
      loading.value = false
    }
  } catch (err) {
    if (seq !== runSeq) return
    loading.value = false
    errorText.value = normalizeErrorMessage(err, '获取视频转码状态失败')
  }
}

async function startVideoFlow(bypassCellular = false) {
  const seq = ++runSeq
  playbackSession?.dispose()
  if (probeTimer) clearTimeout(probeTimer)
  probeResult = null
  playbackFailed = false
  destroyPlayer()
  errorText.value = ''
  transcodeAsset.value = null

  if (!bypassCellular && shouldWarnVideoOnNetwork(networkPrefs, networkStatus)) {
    needsCellularConfirm.value = true
    loading.value = false
    return
  }

  needsCellularConfirm.value = false
  loading.value = true
  try {
    await startPlayback('ORIGINAL', seq)
    if (seq === runSeq) void pollProbe(seq)
  } catch (err) {
    if (seq !== runSeq) return
    loading.value = false
    errorText.value = normalizeErrorMessage(err, '视频加载失败')
  }
}

async function handleRetryTranscode() {
  if (retrying.value) return
  retrying.value = true
  const seq = ++runSeq
  playbackSession?.dispose()
  if (probeTimer) clearTimeout(probeTimer)
  destroyPlayer()
  errorText.value = ''
  try {
    await mobileDriveApi.retryPreviewAsset(
      props.node.spaceId,
      props.node.id,
      'VIDEO_MP4',
      props.versionId ?? props.node.currentVersionId ?? undefined,
    )
    showToast('已重新提交转码任务')
    await pollTranscode(seq)
  } catch (err) {
    showToast(normalizeErrorMessage(err, '重试转码失败'))
  } finally {
    retrying.value = false
  }
}

watch(
  () => [props.node.spaceId, props.node.id, props.versionId ?? props.node.currentVersionId] as const,
  () => {
    locked.value = false
    dimOpacity.value = 0
    void startVideoFlow(false)
  },
  { immediate: true },
)

onBeforeUnmount(() => {
  playbackSession?.dispose()
  if (probeTimer) clearTimeout(probeTimer)
  runSeq += 1
  destroyPlayer()
})

// Touch gesture overlay handlers
function onGestureTouchStart(e: TouchEvent) {
  if (locked.value || !player || e.touches.length !== 1) return
  // 忽略点击在 xgplayer 任意控件（含中央播放键 xg-start、底部控制栏、图标、按钮）上的触控
  const target = e.target as HTMLElement | null
  if (
    target?.closest(
      '.xgplayer-controls, .xgplayer-start, xg-controls, xg-start, xg-icon, .xg-options-list, button',
    )
  ) {
    return
  }

  const t = e.touches[0]!
  touchStartX = t.clientX
  touchStartY = t.clientY
  gestureMode = ''
  speedBoosting = false

  clearLongPress()
  // 在 window 捕获阶段监听抬起/取消，防止子元素隐藏或 stopPropagation 导致长按定时器泄漏
  window.addEventListener('touchend', onGestureTouchEnd, true)
  window.addEventListener('touchcancel', onGestureTouchEnd, true)

  longPressTimer = setTimeout(() => {
    if (!player || player.paused || gestureMode) return
    speedBoosting = true
    prevRate = currentRate.value || 1
    player.playbackRate = 2
    const media = (player as unknown as { media?: HTMLMediaElement }).media
    if (media) media.playbackRate = 2
    gestureBadge.value = '2.0x 倍速播放中'
  }, 450)
}

function onGestureTouchMove(e: TouchEvent) {
  if (locked.value || !player || e.touches.length !== 1 || speedBoosting) return
  const t = e.touches[0]!
  const dx = t.clientX - touchStartX
  const dy = t.clientY - touchStartY
  if (!gestureMode) {
    if (Math.hypot(dx, dy) < 12) return
    clearLongPress()
    if (Math.abs(dx) > Math.abs(dy)) {
      gestureMode = 'seek'
      gestureStartValue = Number(player.currentTime || 0)
      seekTargetSec = gestureStartValue
    } else if (touchStartX < window.innerWidth / 2) {
      gestureMode = 'brightness'
      gestureStartValue = dimOpacity.value
    } else {
      gestureMode = 'volume'
      gestureStartValue = Number(player.volume ?? 1)
    }
  }

  if (gestureMode === 'seek') {
    const dur = Number(player.duration || 0)
    const delta = Math.round((dx / window.innerWidth) * 90)
    seekTargetSec = Math.min(dur || gestureStartValue + delta, Math.max(0, gestureStartValue + delta))
    gestureBadge.value = `${formatMediaTime(seekTargetSec)} / ${formatMediaTime(dur)}`
  } else if (gestureMode === 'brightness') {
    // Drag up -> brighter (lower dimOpacity), drag down -> darker
    const nextDim = Math.min(0.65, Math.max(0, gestureStartValue + dy / 280))
    dimOpacity.value = nextDim
    const pct = Math.round((1 - nextDim / 0.65) * 100)
    gestureBadge.value = `亮度 ${pct}%`
  } else if (gestureMode === 'volume') {
    const nextVol = Math.min(1, Math.max(0, gestureStartValue - dy / 220))
    player.volume = nextVol
    gestureBadge.value = `音量 ${Math.round(nextVol * 100)}%`
  }
}

function onGestureTouchEnd() {
  clearLongPress()
  if (speedBoosting && player) {
    speedBoosting = false
    const restore = prevRate || 1
    player.playbackRate = restore
    const media = (player as unknown as { media?: HTMLMediaElement }).media
    if (media) media.playbackRate = restore
    currentRate.value = restore
  }
  if (gestureMode === 'seek' && player) {
    player.currentTime = seekTargetSec
  }
  gestureMode = ''
  gestureBadge.value = ''
}
</script>

<template>
  <div class="relative flex-1 min-h-0 flex flex-col items-center justify-center bg-black text-white overflow-hidden">
    <!-- 蜂窝流量提醒 -->
    <div
      v-if="needsCellularConfirm"
      class="mx-6 rounded-2xl border border-white/15 bg-white/10 p-5 text-center max-w-xs space-y-3 backdrop-blur"
    >
      <div class="mx-auto flex h-11 w-11 items-center justify-center rounded-full bg-amber-500/20 text-amber-300">
        <WifiOff :size="20" />
      </div>
      <p class="text-sm font-semibold">当前处于移动网络</p>
      <p class="text-xs text-white/75">
        该视频大小为 {{ formatBytes(props.node.size) }}，继续播放将消耗移动流量。
      </p>
      <div class="flex gap-2 pt-1">
        <VanButton block size="small" round plain @click="emit('download')">下载</VanButton>
        <VanButton block size="small" round type="primary" @click="startVideoFlow(true)">
          继续播放
        </VanButton>
      </div>
    </div>

    <!-- 加载中 / 转码轮询 -->
    <div v-else-if="loading" class="flex flex-col items-center gap-2 text-white/80">
      <Loader2 :size="28" class="animate-spin text-primary" />
      <p class="text-sm">正在准备视频流…</p>
    </div>

    <div
      v-else-if="transcodeAsset?.status === 'PENDING' || transcodeAsset?.status === 'PROCESSING'"
      class="flex flex-col items-center gap-2.5 px-6 text-center max-w-xs"
    >
      <Loader2 :size="28" class="animate-spin text-primary" />
      <p class="text-sm font-medium">
        {{ transcodeAsset.status === 'PENDING' ? '视频排队转码中…' : '正在转码为移动兼容 MP4…' }}
      </p>
      <p class="text-xs text-white/70">转码完成后将自动播放，您也可以直接下载原视频</p>
      <VanButton size="small" round plain type="primary" @click="emit('download')">下载原视频</VanButton>
    </div>

    <div
      v-else-if="errorText || transcodeAsset?.status === 'FAILED'"
      class="mx-6 rounded-2xl border border-white/15 bg-white/10 p-5 text-center max-w-xs space-y-3"
    >
      <p class="text-sm font-semibold">视频预览失败</p>
      <p class="text-xs text-white/75 break-words">
        {{ transcodeAsset?.errorMessage || errorText || '视频编码不受支持或转码服务未就绪' }}
      </p>
      <div class="flex gap-2">
        <VanButton
          v-if="transcodeAsset?.status === 'FAILED' || errorText"
          block
          size="small"
          round
          plain
          type="primary"
          :loading="retrying"
          @click="handleRetryTranscode"
        >
          <span class="inline-flex items-center gap-1">
            <RefreshCw :size="13" />
            <span>重试转码</span>
          </span>
        </VanButton>
        <VanButton block size="small" round type="primary" @click="emit('download')">
          下载视频
        </VanButton>
      </div>
    </div>

    <!-- xgplayer 挂载容器 + 手势控制层 -->
    <div
      v-show="streamReady"
      class="relative w-full flex-1 min-h-0 flex items-center justify-center"
      @touchstart.passive="onGestureTouchStart"
      @touchmove.passive="onGestureTouchMove"
      @touchend.passive="onGestureTouchEnd"
      @touchcancel.passive="onGestureTouchEnd"
    >
      <div ref="containerRef" class="w-full max-h-full" />

      <!-- 亮度调节遮罩 -->
      <div
        v-if="dimOpacity > 0"
        class="pointer-events-none absolute inset-0 bg-black"
        :style="{ opacity: dimOpacity }"
      />

      <!-- 手势实时提示胶囊 -->
      <div
        v-if="gestureBadge"
        class="pointer-events-none absolute top-1/4 left-1/2 -translate-x-1/2 rounded-full bg-black/75 px-3.5 py-1.5 text-xs font-medium text-white backdrop-blur"
      >
        {{ gestureBadge }}
      </div>

      <!-- 跨端播放进度接力提示 -->
      <div
        v-if="resumePromptSec !== null"
        class="absolute bottom-20 left-1/2 -translate-x-1/2 z-20 flex items-center gap-2 rounded-full bg-black/80 px-3.5 py-2 text-xs text-white shadow-lg backdrop-blur border border-white/15 whitespace-nowrap"
        @click.stop
      >
        <span>{{ formatResumePrompt('VIDEO', resumePromptSec) }}</span>
        <button
          type="button"
          class="rounded-full bg-primary px-2.5 py-0.5 text-[11px] font-medium text-primary-foreground"
          @click="resumeVideoFromSaved"
        >
          跳转
        </button>
        <button
          type="button"
          class="text-white/70 px-1"
          aria-label="关闭提示"
          @click="resumePromptSec = null"
        >
          ×
        </button>
      </div>

      <!-- 防误触锁屏按钮 -->
      <button
        type="button"
        class="absolute left-3 top-1/2 -translate-y-1/2 z-10 flex h-9 w-9 items-center justify-center rounded-full bg-black/55 text-white backdrop-blur active:bg-black/80"
        :aria-label="locked ? '解锁手势' : '锁定手势防误触'"
        @click.stop="locked = !locked"
      >
        <Lock v-if="locked" :size="16" />
        <Unlock v-else :size="16" />
      </button>

      <!-- 移动端触控友好倍速切换按钮与弹层 -->
      <div class="absolute right-3 top-1/2 -translate-y-1/2 z-10 flex flex-col items-end gap-1.5" @click.stop>
        <button
          type="button"
          class="rounded-full bg-black/60 px-2.5 py-1 text-xs font-medium text-white backdrop-blur active:bg-black/80 tabular-nums"
          aria-label="切换播放倍速"
          @click="rateMenuOpen = !rateMenuOpen"
        >
          {{ currentRate }}x
        </button>

        <div
          v-if="rateMenuOpen"
          class="flex flex-col rounded-xl bg-black/80 p-1 text-xs text-white shadow-lg backdrop-blur border border-white/10"
        >
          <button
            v-for="r in VIDEO_RATES"
            :key="r"
            type="button"
            class="rounded-lg px-3 py-1.5 text-center tabular-nums"
            :class="currentRate === r ? 'bg-primary text-primary-foreground font-semibold' : 'active:bg-white/15'"
            @click="applyPlaybackRate(r)"
          >
            {{ r }}x
          </button>
        </div>
      </div>
    </div>
  </div>
</template>
