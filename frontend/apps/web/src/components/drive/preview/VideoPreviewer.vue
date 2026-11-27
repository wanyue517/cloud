<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import Player from 'xgplayer'
import 'xgplayer/dist/index.min.css'
import { AlertCircle, Download, Loader2, RefreshCw } from '@lucide/vue'
import { Button } from '@repo/ui/button'
import { drive, type DriveNode, type PreviewAsset, type Version, type VideoProbe } from '@/api/drive'

const props = defineProps<{ node: DriveNode; version?: Version }>()
const emit = defineEmits<{ download: [] }>()

const containerRef = ref<HTMLElement | null>(null)
const loading = ref(true)
const error = ref('')
const probe = ref<VideoProbe | null>(null)
const transcodeAsset = ref<PreviewAsset | null>(null)
const retrying = ref(false)
const streamUrl = ref('')

let player: Player | null = null
let pollTimer: number | null = null
let seq = 0

function destroyPlayer() {
  if (player) {
    try { player.destroy() } catch { /* ignore */ }
    player = null
  }
}

function clearPoll() {
  if (pollTimer !== null) {
    window.clearTimeout(pollTimer)
    pollTimer = null
  }
}

async function mountPlayer(url: string) {
  streamUrl.value = url
  loading.value = false
  await nextTick()
  destroyPlayer()
  if (!containerRef.value) return
  player = new Player({
    el: containerRef.value,
    url,
    fluid: false,
    width: '100%',
    height: '100%',
    playbackRate: [0.5, 0.75, 1, 1.25, 1.5, 2],
    defaultPlaybackRate: 1,
    pip: true,
    lang: 'zh-cn',
  })
}

async function pollTranscode(currentSeq: number) {
  try {
    const asset = await drive.previewAsset(props.node, 'VIDEO_MP4', props.version?.id)
    if (currentSeq !== seq) return
    transcodeAsset.value = asset
    if (asset.status === 'READY') {
      const ticket = await drive.streamTicket(props.node, { versionId: props.version?.id, assetType: 'VIDEO_MP4' })
      if (currentSeq !== seq) return
      await mountPlayer(ticket.streamUrl)
      return
    }
    if (asset.status === 'PENDING' || asset.status === 'PROCESSING') {
      loading.value = false
      pollTimer = window.setTimeout(() => { void pollTranscode(currentSeq) }, 2500)
    } else {
      loading.value = false
    }
  } catch (e) {
    if (currentSeq !== seq) return
    error.value = e instanceof Error ? e.message : '视频转码状态查询失败'
    loading.value = false
  }
}

async function retryTranscode() {
  retrying.value = true
  error.value = ''
  clearPoll()
  const currentSeq = ++seq
  try {
    transcodeAsset.value = await drive.retryPreviewAsset(props.node, 'VIDEO_MP4', props.version?.id)
    pollTimer = window.setTimeout(() => { void pollTranscode(currentSeq) }, 1500)
  } catch (e) {
    error.value = e instanceof Error ? e.message : '重试转码失败'
  } finally {
    retrying.value = false
  }
}

async function loadVideo() {
  clearPoll()
  destroyPlayer()
  streamUrl.value = ''
  const currentSeq = ++seq
  loading.value = true
  error.value = ''
  probe.value = null
  transcodeAsset.value = null
  try {
    const p = await drive.videoProbe(props.node, props.version?.id)
    if (currentSeq !== seq) return
    probe.value = p
    if (p.strategy === 'DIRECT_PLAY') {
      const ticket = await drive.streamTicket(props.node, { versionId: props.version?.id, assetType: 'ORIGINAL' })
      if (currentSeq !== seq) return
      await mountPlayer(ticket.streamUrl)
    } else {
      await pollTranscode(currentSeq)
    }
  } catch (e) {
    if (currentSeq !== seq) return
    error.value = e instanceof Error ? e.message : '视频加载失败'
    loading.value = false
  }
}

watch(() => [props.node.id, props.version?.id], () => { void loadVideo() })
onMounted(() => { void loadVideo() })
onBeforeUnmount(() => {
  seq++
  clearPoll()
  destroyPlayer()
  streamUrl.value = ''
})
</script>

<template>
  <div class="flex h-full min-h-0 flex-1 flex-col bg-black/95">
    <div v-if="probe" class="flex flex-wrap items-center justify-between gap-2 border-b border-white/10 bg-zinc-900/90 px-4 py-1.5 text-xs text-zinc-300">
      <span>
        编码：{{ probe.videoCodec || '未知' }} / {{ probe.audioCodec || '无音轨' }}
        <template v-if="probe.width && probe.height"> · {{ probe.width }}×{{ probe.height }}</template>
      </span>
      <span class="rounded bg-white/10 px-2 py-0.5">{{ probe.strategy }} · {{ probe.reason }}</span>
    </div>

    <div class="relative flex min-h-0 flex-1 items-center justify-center">
      <p v-if="loading" role="status" class="text-sm text-white/80">正在探测并加载视频…</p>
      <div v-else-if="error || transcodeAsset?.status === 'FAILED'" class="flex max-w-md flex-col items-center gap-3 rounded-xl bg-zinc-900 p-6 text-center text-zinc-100">
        <AlertCircle :size="32" class="text-red-400" />
        <p class="text-sm font-medium">视频转码或加载失败</p>
        <p class="text-xs text-zinc-400">{{ error || transcodeAsset?.errorMessage }}</p>
        <div class="flex gap-2">
          <Button v-if="probe?.strategy !== 'DIRECT_PLAY'" variant="outline" size="sm" :disabled="retrying" @click="retryTranscode">
            <RefreshCw :size="14" /> 重试转码
          </Button>
          <Button size="sm" @click="emit('download')"><Download :size="14" /> 下载原件</Button>
        </div>
      </div>
      <div v-else-if="transcodeAsset && transcodeAsset.status !== 'READY'" class="flex flex-col items-center gap-3 text-center text-zinc-200">
        <Loader2 :size="32" class="animate-spin text-primary" />
        <p class="text-sm">正在后台转码为浏览器兼容 MP4（{{ transcodeAsset.status }}）…</p>
      </div>
      <div v-show="streamUrl && !loading && !error" ref="containerRef" class="h-full w-full" />
    </div>
  </div>
</template>
