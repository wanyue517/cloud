<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { ChevronLeft, ChevronRight, Download, Maximize2, Minimize2, X } from '@lucide/vue'
import { Button } from '@repo/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogTitle } from '@repo/ui/dialog'
import { drive, type DriveNode, type Version } from '@/api/drive'
import { saveBlob } from '@/utils/request'
import { previewKindLabel, resolvePreviewKind } from '@/lib/preview-registry'
import { getPreviewComponent } from './preview/registry'

const props = defineProps<{
  node: DriveNode
  version?: Version
  siblings?: DriveNode[]
}>()

const emit = defineEmits<{
  close: []
  select: [node: DriveNode]
}>()

const shellRef = ref<HTMLElement | null>(null)
const fullscreen = ref(false)
const downloading = ref(false)
const shellError = ref('')

const mime = computed(() => props.version?.mimeType || props.node.mimeType || '')
const size = computed(() => props.version?.size ?? props.node.size)
const kind = computed(() => resolvePreviewKind(props.node.name, mime.value))
const activeComponent = computed(() => getPreviewComponent(kind.value))

const fileSiblings = computed(() => (props.siblings ?? []).filter(n => n.nodeType === 'FILE'))
const currentIndex = computed(() => fileSiblings.value.findIndex(n => n.id === props.node.id))
const canPrev = computed(() => !props.version && currentIndex.value > 0)
const canNext = computed(() => !props.version && currentIndex.value >= 0 && currentIndex.value < fileSiblings.value.length - 1)

function formatSize(bytes: number) {
  if (!bytes) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB']
  const i = Math.min(3, Math.floor(Math.log(bytes) / Math.log(1024)))
  return `${(bytes / 1024 ** i).toFixed(i ? 1 : 0)} ${units[i]}`
}

function goPrev() {
  if (!canPrev.value) return
  const target = fileSiblings.value[currentIndex.value - 1]
  if (target) emit('select', target)
}

function goNext() {
  if (!canNext.value) return
  const target = fileSiblings.value[currentIndex.value + 1]
  if (target) emit('select', target)
}

async function download() {
  downloading.value = true
  shellError.value = ''
  try {
    saveBlob(await drive.content(props.node, props.version?.id), props.node.name)
  } catch (e) {
    shellError.value = e instanceof Error ? e.message : '下载失败'
  } finally {
    downloading.value = false
  }
}

async function toggleFullscreen() {
  const el = shellRef.value
  if (typeof document !== 'undefined' && document.fullscreenElement) {
    try { await document.exitFullscreen() } catch { /* ignore */ }
    fullscreen.value = false
    return
  }
  if (el && typeof el.requestFullscreen === 'function') {
    try {
      await el.requestFullscreen()
      fullscreen.value = true
      return
    } catch { /* fallback to CSS fullscreen */ }
  }
  fullscreen.value = !fullscreen.value
}

function onFullscreenChange() {
  if (typeof document !== 'undefined') {
    fullscreen.value = Boolean(document.fullscreenElement)
  }
}

function onKeydown(event: KeyboardEvent) {
  const tag = (event.target as HTMLElement | null)?.tagName
  if (tag === 'INPUT' || tag === 'TEXTAREA') return
  if (event.key === 'Escape') {
    emit('close')
  } else if (event.key === 'ArrowLeft' && canPrev.value) {
    goPrev()
  } else if (event.key === 'ArrowRight' && canNext.value) {
    goNext()
  }
}

onMounted(() => {
  document.addEventListener('keydown', onKeydown)
  document.addEventListener('fullscreenchange', onFullscreenChange)
})

onBeforeUnmount(() => {
  document.removeEventListener('keydown', onKeydown)
  document.removeEventListener('fullscreenchange', onFullscreenChange)
})
</script>

<template>
  <Dialog :open="true" @update:open="value => { if (!value) emit('close') }">
    <DialogContent
      class="preview-dialog !flex !flex-col !gap-0 !p-0 overflow-hidden"
      :class="fullscreen ? '!h-screen !max-h-screen !w-screen !max-w-none !rounded-none' : '!h-[min(88vh,860px)] !w-[min(1200px,96vw)] !max-w-[min(1200px,96vw)]'"
      :show-close-button="false"
    >
      <div ref="shellRef" class="flex h-full min-h-0 flex-1 flex-col bg-background">
        <DialogDescription class="sr-only">文件只读预览外框，支持全屏、切换相邻文件与下载原文件。</DialogDescription>
        <header class="flex shrink-0 items-center justify-between gap-3 border-b border-border bg-card px-4 py-2.5">
          <div class="flex min-w-0 flex-1 items-center gap-2.5">
            <DialogTitle class="truncate text-sm font-semibold">
              {{ node.name }}<small v-if="version" class="ml-1.5 font-normal text-muted-foreground">· 版本 {{ version.number }}</small>
            </DialogTitle>
            <span class="hidden shrink-0 rounded-full bg-muted px-2 py-0.5 text-[11px] text-muted-foreground sm:inline">
              {{ previewKindLabel(kind) }}
            </span>
            <span class="hidden shrink-0 text-xs text-muted-foreground md:inline">{{ formatSize(size) }}</span>
          </div>

          <div class="flex shrink-0 items-center gap-1.5">
            <template v-if="canPrev || canNext">
              <Button variant="outline" size="icon-sm" :disabled="!canPrev" title="上一个文件 (←)" aria-label="上一个文件" @click="goPrev">
                <ChevronLeft :size="16" />
              </Button>
              <Button variant="outline" size="icon-sm" :disabled="!canNext" title="下一个文件 (→)" aria-label="下一个文件" @click="goNext">
                <ChevronRight :size="16" />
              </Button>
            </template>
            <Button variant="outline" size="sm" :disabled="downloading" @click="download">
              <Download :size="15" /> {{ downloading ? '下载中…' : '下载' }}
            </Button>
            <Button variant="outline" size="icon-sm" :title="fullscreen ? '退出全屏' : '全屏预览'" :aria-label="fullscreen ? '退出全屏' : '全屏预览'" @click="toggleFullscreen">
              <Minimize2 v-if="fullscreen" :size="16" />
              <Maximize2 v-else :size="16" />
            </Button>
            <Button variant="outline" size="icon-sm" autofocus aria-label="关闭预览" @click="emit('close')">
              <X :size="17" />
            </Button>
          </div>
        </header>

        <p v-if="shellError" role="alert" class="border-b border-destructive/30 bg-destructive/10 px-4 py-1.5 text-xs text-destructive">
          {{ shellError }}
        </p>

        <div class="flex min-h-0 flex-1 flex-col overflow-hidden">
          <component
            :is="activeComponent"
            :key="`${node.id}-${version?.id ?? 'cur'}`"
            :node="node"
            :version="version"
            :can-prev="canPrev"
            :can-next="canNext"
            @prev="goPrev"
            @next="goNext"
            @download="download"
          />
        </div>
      </div>
    </DialogContent>
  </Dialog>
</template>
