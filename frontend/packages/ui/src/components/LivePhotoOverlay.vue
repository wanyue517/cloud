<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'

type State = { livePhoto: boolean; status: string; errorMessage?: string | null }
const props = defineProps<{
  identity: string
  inspect: (retry?: boolean) => Promise<State>
  address: () => Promise<string>
  uploadVideo?: (file: File) => Promise<State>
  saveNative?: () => Promise<void>
  exportMotion?: () => Promise<void>
  controlsOffset?: string
}>()
const state = ref<State | null>(null)
const message = ref('')
const busy = ref(false)
const playing = ref(false)
const video = ref<HTMLVideoElement | null>(null)
const input = ref<HTMLInputElement | null>(null)
const controls = ref<HTMLElement | null>(null)
const url = ref('')
const muted = ref(true)
const saving = ref(false)
const exporting = ref(false)
let generation = 0
let timer: ReturnType<typeof setTimeout> | undefined
let attempts = 0
let disposed = false
let playbackRequest = 0
let holdTimer: ReturnType<typeof setTimeout> | undefined
let held = false
let startX = 0
let startY = 0
let suppressTapUntil = 0
let stage: HTMLElement | null = null

function cancelHold() { clearTimeout(holdTimer) }
function pointerDown(event: PointerEvent) {
  cancelHold()
  if (!event.isPrimary && held) { held = false; stop() }
  if (!event.isPrimary || event.button !== 0 || state.value?.status !== 'READY' || playing.value
    || (event.target as Element).closest('button,input')) return
  startX = event.clientX
  startY = event.clientY
  holdTimer = setTimeout(() => { held = true; suppressTapUntil = Date.now() + 1000; void play() }, 400)
}
function pointerMove(event: PointerEvent) {
  if (Math.hypot(event.clientX - startX, event.clientY - startY) > 8) {
    cancelHold()
    if (held) { held = false; stop() }
  }
}
function pointerUp() {
  cancelHold()
  if (held) { held = false; suppressTapUntil = Date.now() + 500; stop() }
}
function suppressTap(event: Event) {
  if (held || Date.now() < suppressTapUntil) event.stopPropagation()
}
onMounted(() => {
  stage = controls.value?.parentElement ?? null
  stage?.addEventListener('pointerdown', pointerDown, true)
  stage?.addEventListener('pointermove', pointerMove, true)
  stage?.addEventListener('pointerup', pointerUp, true)
  stage?.addEventListener('pointercancel', pointerUp, true)
  window.addEventListener('pointerup', pointerUp, true)
  stage?.addEventListener('touchend', suppressTap, true)
  stage?.addEventListener('click', suppressTap, true)
})

function stop() {
  playbackRequest++
  video.value?.pause()
  playing.value = false
  busy.value = false
}

async function saveNative() {
  if (!props.saveNative || saving.value) return
  const current = generation
  saving.value = true
  message.value = '正在下载原件并保存实况照片…'
  try {
    await props.saveNative()
    if (current === generation && !disposed) message.value = '已保存到系统相册'
  } catch (error) {
    if (current === generation && !disposed) message.value = error instanceof Error ? error.message : '实况照片保存失败'
  } finally { saving.value = false }
}

async function exportMotion() {
  if (!props.exportMotion || exporting.value) return
  const current = generation
  exporting.value = true
  message.value = '正在生成安卓动态照片…'
  try {
    await props.exportMotion()
    if (current === generation && !disposed) message.value = '已开始下载动态照片，请用支持 Motion Photo 的相册打开'
  } catch (error) {
    if (current === generation && !disposed) message.value = error instanceof Error ? error.message : '动态照片下载失败'
  } finally { exporting.value = false }
}

async function inspect(retry = false) {
  const current = generation
  try {
    const result = await props.inspect(retry)
    if (current !== generation || disposed) return
    state.value = result
    if (['PENDING', 'PROCESSING'].includes(result.status) && attempts++ < 120) {
      timer = setTimeout(() => { void inspect() }, 2000)
    } else if (['PENDING', 'PROCESSING'].includes(result.status)) {
      message.value = '实况视频仍在处理，可点击重新查询'
    }
  } catch (e) {
    if (current === generation && !disposed) message.value = e instanceof Error ? e.message : '实况信息加载失败'
  }
}

async function play() {
  if (busy.value) return
  if (playing.value) { stop(); return }
  if (state.value?.status !== 'READY') {
    clearTimeout(timer)
    attempts = 0
    message.value = ''
    await inspect(state.value?.status === 'FAILED')
    return
  }
  const current = generation
  const request = ++playbackRequest
  busy.value = true
  message.value = ''
  try {
    // Obtain a fresh ticket for each playback, including after a long idle.
    const address = await props.address()
    if (current !== generation || request !== playbackRequest || disposed) return
    url.value = address
    playing.value = true
    await nextTick()
    await video.value?.play()
  } catch {
    if (current === generation && request === playbackRequest && !disposed) {
      stop()
      message.value = '实况播放失败，请再次点击 LIVE 重试'
    }
  } finally { if (current === generation && request === playbackRequest && !disposed) busy.value = false }
}

async function upload(event: Event) {
  const file = (event.target as HTMLInputElement).files?.[0]
  if (input.value) input.value.value = ''
  if (!file || !props.uploadVideo || busy.value) return
  if (file.size > 64 * 1024 * 1024 || !/\.(mov|mp4|m4v)$/i.test(file.name)) {
    message.value = '请选择小于 64 MB 的 MOV 或 MP4 配套视频'
    return
  }
  const current = generation
  busy.value = true
  message.value = ''
  try {
    const result = await props.uploadVideo(file)
    if (current !== generation || disposed) return
    state.value = result
    attempts = 0
    await inspect()
  } catch (e) {
    if (current === generation && !disposed) message.value = e instanceof Error ? e.message : '配套视频上传失败'
  } finally { if (current === generation && !disposed) busy.value = false }
}

watch(() => props.identity, () => {
  generation++
  cancelHold()
  held = false
  suppressTapUntil = 0
  clearTimeout(timer)
  stop()
  url.value = ''
  state.value = null
  message.value = ''
  busy.value = false
  attempts = 0
  void inspect()
}, { immediate: true })
onBeforeUnmount(() => {
  disposed = true; generation++; clearTimeout(timer); cancelHold(); stop()
  stage?.removeEventListener('pointerdown', pointerDown, true)
  stage?.removeEventListener('pointermove', pointerMove, true)
  stage?.removeEventListener('pointerup', pointerUp, true)
  stage?.removeEventListener('pointercancel', pointerUp, true)
  window.removeEventListener('pointerup', pointerUp, true)
  stage?.removeEventListener('touchend', suppressTap, true)
  stage?.removeEventListener('click', suppressTap, true)
})
</script>

<template>
  <video
    v-if="playing" ref="video" :src="url" :muted="muted" playsinline preload="none"
    class="absolute inset-0 z-20 h-full w-full bg-black object-contain"
    aria-label="实况照片视频" @ended="stop" @click.stop="stop"
    @touchstart.stop @touchmove.stop @touchend.stop
    @error="stop(); message = '实况视频无法播放，请重试'"
  />
  <div ref="controls" :style="{ top: controlsOffset ?? '0.75rem' }" class="absolute left-3 z-30 flex max-w-[calc(100%-1.5rem)] flex-col items-start gap-2 text-white"
    @click.stop @pointerdown.stop @touchstart.stop @touchmove.stop @touchend.stop>
    <button v-if="state?.livePhoto" type="button" :disabled="busy"
      :aria-pressed="playing" class="rounded-full bg-black/65 px-3 py-2 text-xs shadow backdrop-blur disabled:opacity-60"
      @click="play">
      {{ busy ? '正在加载…' : playing ? '◉ LIVE · 停止' : state.status === 'READY' ? '◉ LIVE · 播放' : state.status === 'FAILED' ? '◉ LIVE · 重试' : '◉ LIVE · 处理中' }}
    </button>
    <button v-if="playing" type="button" class="rounded-full bg-black/65 px-3 py-2 text-xs" @click="muted = !muted">
      {{ muted ? '开启原声' : '静音' }}
    </button>
    <button v-if="state?.livePhoto && saveNative" type="button" :disabled="saving"
      class="rounded-full bg-black/65 px-3 py-2 text-xs disabled:opacity-60" @click="saveNative">
      {{ saving ? '正在保存实况…' : '保存实况到相册' }}
    </button>
    <button v-if="state?.status === 'READY' && exportMotion" type="button" :disabled="exporting"
      class="rounded-full bg-black/65 px-3 py-2 text-xs disabled:opacity-60" @click="exportMotion">
      {{ exporting ? '正在生成动态照片…' : '下载安卓动态照片' }}
    </button>
    <template v-if="state && !state.livePhoto && uploadVideo">
      <input ref="input" type="file" accept=".mov,.mp4,.m4v,video/quicktime,video/mp4" class="hidden" @change="upload" />
      <button type="button" :disabled="busy" class="rounded-full bg-black/65 px-3 py-2 text-xs disabled:opacity-60"
        title="上传配套视频并绑定当前照片版本，视频原件保留在当前目录" @click="input?.click()">
        {{ busy ? '正在上传配套视频…' : '添加实况视频' }}
      </button>
    </template>
    <p v-if="message || state?.status === 'FAILED'" role="status" class="max-w-xs rounded-lg bg-black/75 p-2 text-xs">
      {{ message || state?.errorMessage || '实况视频处理失败，请重试' }}
    </p>
  </div>
</template>
