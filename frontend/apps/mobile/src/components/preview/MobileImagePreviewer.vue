<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { showToast } from 'vant'
import { ChevronLeft, ChevronRight, Download, RotateCcw, RotateCw, ZoomIn } from '@lucide/vue'
import { normalizeErrorMessage } from '@repo/drive-core/errors'
import { mobileDriveApi, type DriveNode } from '@/api/drive'
import LivePhotoOverlay from '@repo/ui/LivePhotoOverlay.vue'
import { triggerBlobDownload } from '@/lib/file-actions'
import { supportsNativeLivePhoto, saveNativeLivePhoto } from '@/native/live-photo'

const props = defineProps<{
  node: DriveNode
  versionId?: string
  hasPrev?: boolean
  hasNext?: boolean
}>()

const emit = defineEmits<{
  'toggle-chrome': []
  prev: []
  next: []
}>()

const stageRef = ref<HTMLElement | null>(null)
const imgRef = ref<HTMLImageElement | null>(null)
const loading = ref(true)
const errorText = ref('')
const imageUrl = ref<string | null>(null)
const currentBlob = ref<Blob | null>(null)

async function downloadMotionPhoto() {
  const node = props.node
  const blob = await mobileDriveApi.motionPhoto(node, props.versionId ?? node.currentVersionId ?? undefined)
  triggerBlobDownload(blob, `${node.name.replace(/\.[^.]+$/, '')}MP.jpg`)
}

// Transform state
const scale = ref(1)
const rotateDeg = ref(0)
const offsetX = ref(0)
const offsetY = ref(0)
// 交互中关闭 CSS transition，消除每帧 touchmove 重启过渡动画导致的抖动
const isInteracting = ref(false)

// Gesture tracking
let loadSeq = 0
let lastTapAt = 0
let lastTouchEndAt = 0
let singleTapTimer: ReturnType<typeof setTimeout> | null = null
let startTouches: { x: number; y: number }[] = []
let startScale = 1
let startOffsetX = 0
let startOffsetY = 0
let startPinchDist = 0
let startMidX = 0
let startMidY = 0
let moved = false
let wasPinching = false

function clearSingleTapTimer() {
  if (singleTapTimer) {
    clearTimeout(singleTapTimer)
    singleTapTimer = null
  }
}

function revokeCurrentUrl() {
  if (imageUrl.value) {
    URL.revokeObjectURL(imageUrl.value)
    imageUrl.value = null
  }
  currentBlob.value = null
}

function resetTransform() {
  isInteracting.value = false
  scale.value = 1
  rotateDeg.value = 0
  offsetX.value = 0
  offsetY.value = 0
}

function clampOffsets(nextScale: number, rawX: number, rawY: number): [number, number] {
  if (nextScale <= 1.01) return [0, 0]
  const stage = stageRef.value
  const img = imgRef.value
  if (!stage || !img) return [rawX, rawY]
  const stageW = stage.clientWidth
  const stageH = stage.clientHeight
  const rotated = Math.abs(rotateDeg.value % 180) === 90
  const baseW = rotated ? img.offsetHeight : img.offsetWidth
  const baseH = rotated ? img.offsetWidth : img.offsetHeight
  const maxX = Math.max(0, (baseW * nextScale - stageW) / 2)
  const maxY = Math.max(0, (baseH * nextScale - stageH) / 2)
  return [
    Math.min(maxX, Math.max(-maxX, rawX)),
    Math.min(maxY, Math.max(-maxY, rawY)),
  ]
}

async function loadImage() {
  const seq = ++loadSeq
  loading.value = true
  errorText.value = ''
  resetTransform()
  revokeCurrentUrl()
  try {
    const blob = await (/\.hei[cf]$/i.test(props.node.name)
      ? mobileDriveApi.livePhotoPart(props.node, 'poster', props.versionId ?? props.node.currentVersionId ?? undefined)
          .catch(() => mobileDriveApi.downloadContent(props.node.spaceId, props.node.id, props.versionId ?? props.node.currentVersionId ?? undefined))
      : mobileDriveApi.downloadContent(
      props.node.spaceId,
      props.node.id,
      props.versionId ?? props.node.currentVersionId ?? undefined,
    ))
    if (seq !== loadSeq) return
    currentBlob.value = blob
    imageUrl.value = URL.createObjectURL(blob)
  } catch (err) {
    if (seq !== loadSeq) return
    errorText.value = normalizeErrorMessage(err, '加载图片失败')
  } finally {
    if (seq === loadSeq) loading.value = false
  }
}

watch(
  () => [props.node.spaceId, props.node.id, props.versionId ?? props.node.currentVersionId] as const,
  () => {
    void loadImage()
  },
  { immediate: true },
)

onBeforeUnmount(() => {
  loadSeq += 1
  clearSingleTapTimer()
  revokeCurrentUrl()
})

const imageStyle = computed(() => ({
  transform: `translate3d(${offsetX.value}px, ${offsetY.value}px, 0) scale(${scale.value}) rotate(${rotateDeg.value}deg)`,
}))

function distanceBetween(a: { x: number; y: number }, b: { x: number; y: number }): number {
  return Math.hypot(a.x - b.x, a.y - b.y)
}

function stageRelativePoint(clientX: number, clientY: number): { x: number; y: number } {
  const rect = stageRef.value?.getBoundingClientRect()
  if (!rect) return { x: 0, y: 0 }
  return {
    x: clientX - (rect.left + rect.width / 2),
    y: clientY - (rect.top + rect.height / 2),
  }
}

function captureTouchBaseline(touches: TouchList) {
  startTouches = Array.from(touches).map(t => ({ x: t.clientX, y: t.clientY }))
  startScale = scale.value
  startOffsetX = offsetX.value
  startOffsetY = offsetY.value
  if (startTouches.length >= 2) {
    wasPinching = true
    startPinchDist = distanceBetween(startTouches[0]!, startTouches[1]!)
    const mid = stageRelativePoint(
      (startTouches[0]!.x + startTouches[1]!.x) / 2,
      (startTouches[0]!.y + startTouches[1]!.y) / 2,
    )
    startMidX = mid.x
    startMidY = mid.y
  } else {
    startPinchDist = 0
  }
}

function onTouchStart(e: TouchEvent) {
  clearSingleTapTimer()
  isInteracting.value = true
  if (e.touches.length === 1) {
    moved = false
    wasPinching = false
  } else {
    moved = true
  }
  captureTouchBaseline(e.touches)
}

function onTouchMove(e: TouchEvent) {
  if (e.touches.length >= 2 && startPinchDist > 0) {
    moved = true
    const currA = { x: e.touches[0]!.clientX, y: e.touches[0]!.clientY }
    const currB = { x: e.touches[1]!.clientX, y: e.touches[1]!.clientY }
    const ratio = distanceBetween(currA, currB) / startPinchDist
    // 允许轻微橡皮筋缩放（0.85x–4.5x），松手再平滑回弹，避免边界硬跳
    const nextScale = Math.min(4.5, Math.max(0.85, startScale * ratio))
    const currMid = stageRelativePoint((currA.x + currB.x) / 2, (currA.y + currB.y) / 2)
    const scaleFactor = nextScale / startScale
    scale.value = nextScale
    offsetX.value = currMid.x - (startMidX - startOffsetX) * scaleFactor
    offsetY.value = currMid.y - (startMidY - startOffsetY) * scaleFactor
    return
  }

  if (e.touches.length === 1 && startTouches.length === 1) {
    const dx = e.touches[0]!.clientX - startTouches[0]!.x
    const dy = e.touches[0]!.clientY - startTouches[0]!.y
    if (Math.abs(dx) > 6 || Math.abs(dy) > 6) moved = true
    if (scale.value > 1.02) {
      offsetX.value = startOffsetX + dx
      offsetY.value = startOffsetY + dy
    }
  }
}

function onTouchEnd(e: TouchEvent) {
  lastTouchEndAt = Date.now()

  // 双指抬起一只手指变为单指时，立即重置基准点防止画面突跳
  if (e.touches.length > 0) {
    captureTouchBaseline(e.touches)
    return
  }

  isInteracting.value = false

  // 松手后平滑回弹至合法缩放与边界范围
  const clampedScale = Math.min(4, Math.max(1, scale.value))
  scale.value = clampedScale
  const [cx, cy] = clampOffsets(clampedScale, offsetX.value, offsetY.value)
  offsetX.value = cx
  offsetY.value = cy

  // 1x 状态下左右轻滑切换上一张/下一张
  if (!wasPinching && startTouches.length === 1 && clampedScale <= 1.02 && e.changedTouches.length === 1) {
    const dx = e.changedTouches[0]!.clientX - startTouches[0]!.x
    const dy = e.changedTouches[0]!.clientY - startTouches[0]!.y
    if (Math.abs(dx) > 65 && Math.abs(dx) > Math.abs(dy) * 1.5) {
      if (dx > 0 && props.hasPrev) emit('prev')
      else if (dx < 0 && props.hasNext) emit('next')
      return
    }
  }

  if (!moved && !wasPinching) {
    const now = Date.now()
    if (now - lastTapAt < 260) {
      lastTapAt = 0
      clearSingleTapTimer()
      if (scale.value > 1.2) {
        scale.value = 1
        offsetX.value = 0
        offsetY.value = 0
      } else {
        const tap = e.changedTouches[0]
          ? stageRelativePoint(e.changedTouches[0].clientX, e.changedTouches[0].clientY)
          : { x: 0, y: 0 }
        const targetScale = 2.2
        scale.value = targetScale
        const [bx, by] = clampOffsets(targetScale, -tap.x * (targetScale - 1), -tap.y * (targetScale - 1))
        offsetX.value = bx
        offsetY.value = by
      }
    } else {
      lastTapAt = now
      clearSingleTapTimer()
      singleTapTimer = setTimeout(() => {
        if (lastTapAt === now) {
          emit('toggle-chrome')
        }
      }, 270)
    }
  }
}

function onStageClick() {
  // 忽略由触控刚刚派发的合成 click，避免单次轻点触发两次 toggle-chrome 闪烁
  if (Date.now() - lastTouchEndAt < 450) return
  emit('toggle-chrome')
}

function onWheel(e: WheelEvent) {
  isInteracting.value = false
  const delta = e.deltaY < 0 ? 0.25 : -0.25
  const nextScale = Math.min(4, Math.max(1, Number((scale.value + delta).toFixed(2))))
  scale.value = nextScale
  const [cx, cy] = clampOffsets(nextScale, offsetX.value, offsetY.value)
  offsetX.value = cx
  offsetY.value = cy
}

function rotateLeft() {
  isInteracting.value = false
  rotateDeg.value -= 90
  const [cx, cy] = clampOffsets(scale.value, offsetX.value, offsetY.value)
  offsetX.value = cx
  offsetY.value = cy
}

function rotateRight() {
  isInteracting.value = false
  rotateDeg.value += 90
  const [cx, cy] = clampOffsets(scale.value, offsetX.value, offsetY.value)
  offsetX.value = cx
  offsetY.value = cy
}

function toggleZoom() {
  isInteracting.value = false
  if (scale.value > 1.2) {
    scale.value = 1
    offsetX.value = 0
    offsetY.value = 0
  } else {
    scale.value = 2
  }
}

async function downloadOriginal() {
  if (/\.hei[cf]$/i.test(props.node.name)) {
    try {
      const blob = await mobileDriveApi.downloadContent(props.node.spaceId, props.node.id, props.versionId ?? props.node.currentVersionId ?? undefined)
      triggerBlobDownload(blob, props.node.name)
    } catch (error) { showToast(normalizeErrorMessage(error, '下载原件失败')) }
    return
  }
  if (!currentBlob.value) {
    showToast('图片尚未加载完成')
    return
  }
  triggerBlobDownload(currentBlob.value, props.node.name)
}
</script>

<template>
  <div
    ref="stageRef"
    class="relative flex-1 min-h-0 w-full h-full flex items-center justify-center bg-black overflow-hidden touch-none select-none"
    @touchstart="onTouchStart"
    @touchmove.prevent="onTouchMove"
    @touchend="onTouchEnd"
    @touchcancel="onTouchEnd"
    @wheel.prevent="onWheel"
    @click="onStageClick"
  >
    <div v-if="loading" class="text-white/80 text-sm">正在加载原图…</div>

    <div v-else-if="errorText" class="px-6 text-center text-white/85 space-y-3" @click.stop>
      <p class="text-sm">{{ errorText }}</p>
      <button
        type="button"
        class="rounded-full bg-white/15 px-4 py-1.5 text-xs text-white"
        @click="loadImage"
      >
        重试
      </button>
    </div>

    <img
      v-else-if="imageUrl"
      ref="imgRef"
      :src="imageUrl"
      :alt="props.node.name"
      class="max-h-full max-w-full object-contain will-change-transform"
      :class="isInteracting ? '' : 'transition-transform duration-200 ease-out'"
      :style="imageStyle"
      draggable="false"
    />

    <LivePhotoOverlay v-if="!loading && !errorText && imageUrl"
      :identity="`${props.node.spaceId}:${props.node.id}:${props.versionId ?? props.node.currentVersionId}`"
      :inspect="retry => mobileDriveApi.livePhoto(props.node, props.versionId, retry)"
      :address="() => mobileDriveApi.livePhotoAddress(props.node, props.versionId)"
      controls-offset="calc(env(safe-area-inset-top) + 3.5rem)"
      :save-native="supportsNativeLivePhoto() ? () => saveNativeLivePhoto(props.node, props.versionId) : undefined"
      :export-motion="downloadMotionPhoto"
      :upload-video="props.versionId ? undefined : file => mobileDriveApi.attachLiveVideo(props.node, file)" />

    <!-- 左右切换按钮（大屏/点击兜底） -->
    <button
      v-if="props.hasPrev"
      type="button"
      class="absolute left-2 top-1/2 -translate-y-1/2 z-10 flex h-9 w-9 items-center justify-center rounded-full bg-black/45 text-white active:bg-black/70"
      aria-label="上一张"
      @click.stop="emit('prev')"
    >
      <ChevronLeft :size="20" />
    </button>
    <button
      v-if="props.hasNext"
      type="button"
      class="absolute right-2 top-1/2 -translate-y-1/2 z-10 flex h-9 w-9 items-center justify-center rounded-full bg-black/45 text-white active:bg-black/70"
      aria-label="下一张"
      @click.stop="emit('next')"
    >
      <ChevronRight :size="20" />
    </button>

    <!-- 图片操作悬浮条 -->
    <div
      class="absolute bottom-20 left-1/2 -translate-x-1/2 z-10 flex items-center gap-1 rounded-full bg-black/65 px-2.5 py-1.5 text-white backdrop-blur"
      @click.stop
    >
      <button
        type="button"
        class="inline-flex h-8 w-8 items-center justify-center rounded-full active:bg-white/15"
        aria-label="左旋 90 度"
        @click="rotateLeft"
      >
        <RotateCcw :size="16" />
      </button>
      <button
        type="button"
        class="inline-flex h-8 w-8 items-center justify-center rounded-full active:bg-white/15"
        aria-label="右旋 90 度"
        @click="rotateRight"
      >
        <RotateCw :size="16" />
      </button>
      <button
        type="button"
        class="inline-flex h-8 w-8 items-center justify-center rounded-full active:bg-white/15"
        aria-label="缩放"
        @click="toggleZoom"
      >
        <ZoomIn :size="16" />
      </button>
      <button
        type="button"
        class="inline-flex h-8 w-8 items-center justify-center rounded-full active:bg-white/15"
        aria-label="下载原图"
        @click="downloadOriginal"
      >
        <Download :size="16" />
      </button>
    </div>
  </div>
</template>
