<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import Viewer from 'viewerjs'
import 'viewerjs/dist/viewer.css'
import { ChevronLeft, ChevronRight, FlipHorizontal2, FlipVertical2, RotateCcw, RotateCw, ZoomIn, ZoomOut, Maximize } from '@lucide/vue'
import { Button } from '@repo/ui/button'
import { drive, type DriveNode, type Version } from '@/api/drive'
import { getOrFetchPreviewBlob, matchPreviewBlob } from '@/lib/thumb-cache'

const props = defineProps<{
  node: DriveNode
  version?: Version
  canPrev?: boolean
  canNext?: boolean
}>()

const emit = defineEmits<{
  prev: []
  next: []
}>()

const loading = ref(true)
const error = ref('')
const objectUrl = ref('')
const imgRef = ref<HTMLImageElement | null>(null)
const zoomPercent = ref(100)
let viewer: Viewer | null = null
let scaleX = 1
let scaleY = 1
let loadSeq = 0

function destroyViewer() {
  if (viewer) {
    viewer.destroy()
    viewer = null
  }
}

function revokeUrl() {
  if (objectUrl.value) {
    URL.revokeObjectURL(objectUrl.value)
    objectUrl.value = ''
  }
}

async function initViewer() {
  await nextTick()
  destroyViewer()
  if (!imgRef.value) return
  scaleX = 1
  scaleY = 1
  viewer = new Viewer(imgRef.value, {
    inline: true,
    navbar: false,
    title: false,
    toolbar: false,
    tooltip: false,
    movable: true,
    zoomable: true,
    rotatable: true,
    scalable: true,
    transition: false,
    backdrop: false,
    button: false,
    zoom(event: CustomEvent<{ ratio: number }>) {
      zoomPercent.value = Math.round((event.detail?.ratio ?? 1) * 100)
    },
    viewed() {
      zoomPercent.value = 100
    },
  })
}

async function loadImage() {
  const seq = ++loadSeq
  error.value = ''
  destroyViewer()
  revokeUrl()
  try {
    const size = props.version?.size ?? props.node.size
    if (size > 64 * 1024 * 1024) {
      loading.value = false
      error.value = '图片超过 64 MB，请下载后查看'
      return
    }
    const cacheKey = `img:${props.node.id}:${props.version?.id || props.node.currentVersionId || ''}`
    const cached = await matchPreviewBlob(cacheKey)
    if (seq !== loadSeq) return
    let blob: Blob
    if (cached) {
      blob = cached
    } else {
      loading.value = true
      blob = await getOrFetchPreviewBlob(cacheKey, () => drive.content(props.node, props.version?.id))
      if (seq !== loadSeq) return
    }
    objectUrl.value = URL.createObjectURL(blob)
    loading.value = false
    await initViewer()
  } catch (e) {
    if (seq !== loadSeq) return
    error.value = e instanceof Error ? e.message : '图片加载失败'
  } finally {
    if (seq === loadSeq) loading.value = false
  }
}

function zoomIn() { viewer?.zoom(0.2) }
function zoomOut() { viewer?.zoom(-0.2) }
function resetView() { scaleX = 1; scaleY = 1; viewer?.reset() }
function rotateLeft() { viewer?.rotate(-90) }
function rotateRight() { viewer?.rotate(90) }
function flipHorizontal() { scaleX = -scaleX; viewer?.scaleX(scaleX) }
function flipVertical() { scaleY = -scaleY; viewer?.scaleY(scaleY) }

watch(() => [props.node.id, props.version?.id], () => { void loadImage() })
onMounted(() => { void loadImage() })
onBeforeUnmount(() => {
  loadSeq++
  destroyViewer()
  revokeUrl()
})
</script>

<template>
  <div class="flex h-full min-h-0 flex-1 flex-col bg-muted/20">
    <div class="flex flex-wrap items-center justify-between gap-2 border-b border-border bg-background/90 px-4 py-2">
      <div class="flex items-center gap-1">
        <Button v-if="canPrev || canNext" variant="outline" size="sm" :disabled="!canPrev" title="上一张 (←)" @click="emit('prev')">
          <ChevronLeft :size="15" /> 上一张
        </Button>
        <Button v-if="canPrev || canNext" variant="outline" size="sm" :disabled="!canNext" title="下一张 (→)" @click="emit('next')">
          下一张 <ChevronRight :size="15" />
        </Button>
      </div>
      <div class="flex flex-wrap items-center gap-1">
        <Button variant="outline" size="icon-sm" title="缩小" aria-label="缩小" @click="zoomOut"><ZoomOut :size="15" /></Button>
        <span class="min-w-[3.2rem] text-center text-xs tabular-nums text-muted-foreground">{{ zoomPercent }}%</span>
        <Button variant="outline" size="icon-sm" title="放大" aria-label="放大" @click="zoomIn"><ZoomIn :size="15" /></Button>
        <Button variant="outline" size="icon-sm" title="重置视图" aria-label="重置视图" @click="resetView"><Maximize :size="15" /></Button>
        <Button variant="outline" size="icon-sm" title="向左旋转 90°" aria-label="向左旋转" @click="rotateLeft"><RotateCcw :size="15" /></Button>
        <Button variant="outline" size="icon-sm" title="向右旋转 90°" aria-label="向右旋转" @click="rotateRight"><RotateCw :size="15" /></Button>
        <Button variant="outline" size="icon-sm" title="水平翻转" aria-label="水平翻转" @click="flipHorizontal"><FlipHorizontal2 :size="15" /></Button>
        <Button variant="outline" size="icon-sm" title="垂直翻转" aria-label="垂直翻转" @click="flipVertical"><FlipVertical2 :size="15" /></Button>
      </div>
      <span class="text-xs text-muted-foreground">滚轮缩放 · 拖拽平移 · 不修改原件</span>
    </div>

    <div class="relative flex min-h-0 flex-1 items-center justify-center overflow-hidden">
      <p v-if="loading" role="status" class="text-sm text-muted-foreground">正在加载图片…</p>
      <p v-else-if="error" role="alert" class="text-sm text-destructive">{{ error }}</p>
      <img v-show="!loading && !error && objectUrl" ref="imgRef" :src="objectUrl" :alt="node.name" class="hidden" />
    </div>
  </div>
</template>
