<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { ChevronDown, ChevronUp, ListTree, Minus, Plus, Search, X } from '@lucide/vue'
import { normalizeErrorMessage } from '@repo/drive-core/errors'
import { createProgressReporter, formatResumePrompt, shouldPromptResume } from '@repo/drive-core/progress'
import * as pdfjsLib from 'pdfjs-dist'
import { mobileDriveApi, type DriveNode } from '@/api/drive'
import { clampZoomScale, computeVisiblePageWindow } from '@/lib/pdf-window'
import { BottomSheet, ErrorState } from '@/components/common'

pdfjsLib.GlobalWorkerOptions.workerSrc = new URL(
  'pdfjs-dist/build/pdf.worker.min.mjs',
  import.meta.url,
).toString()

interface OutlineItem {
  title: string
  page: number
  level: number
}

const props = withDefaults(
  defineProps<{
    node: DriveNode
    versionId?: string
    /** 若由 Office 转 PDF 复用，可直接传入已下载的 PDF Blob 避免重复下载 */
    pdfBlob?: Blob | null
    /** PPT 横屏翻页提示与底部幻灯片页码条 */
    slideMode?: boolean
  }>(),
  {
    versionId: undefined,
    pdfBlob: null,
    slideMode: false,
  },
)

const emit = defineEmits<{
  'toggle-chrome': []
  'page-change': [page: number, total: number]
}>()

const loading = ref(true)
const loadingHint = ref('正在连接文档流…')
const errorText = ref('')
const totalPages = ref(0)
const currentPage = ref(1)
const zoomScale = ref(1)
const outline = ref<OutlineItem[]>([])
const outlineOpen = ref(false)
const resumePromptPage = ref<number | null>(null)
const progressReporter = createProgressReporter(
  (page) => mobileDriveApi.saveProgress(props.node.spaceId, props.node.id, 'PDF', page),
  { intervalMs: 10_000 },
)

// Cross-page search
const searchOpen = ref(false)
const keyword = ref('')
const searching = ref(false)
const matchedPages = ref<number[]>([])
const activeMatchIdx = ref(0)

const scrollEl = ref<HTMLElement | null>(null)
const pageElements = new Map<number, HTMLElement>()
const canvasElements = new Map<number, HTMLCanvasElement>()
const renderedPages = new Map<number, number>() // pageNum -> rendered zoomScale
const activeRenderTasks = new Map<number, { cancel: () => void }>()

let pdfDoc: pdfjsLib.PDFDocumentProxy | null = null
let pdfLoadingTask: pdfjsLib.PDFDocumentLoadingTask | null = null
let loadSeq = 0
let scrollRaf = 0

const pageNumbers = computed(() => Array.from({ length: totalPages.value }, (_, i) => i + 1))

function setPageEl(pageNum: number, el: unknown) {
  if (el instanceof HTMLElement) pageElements.set(pageNum, el)
  else pageElements.delete(pageNum)
}

function setCanvasEl(pageNum: number, el: unknown) {
  if (el instanceof HTMLCanvasElement) canvasElements.set(pageNum, el)
  else canvasElements.delete(pageNum)
}

function releaseCanvas(pageNum: number) {
  activeRenderTasks.get(pageNum)?.cancel()
  activeRenderTasks.delete(pageNum)
  const canvas = canvasElements.get(pageNum)
  if (canvas) {
    canvas.width = 0
    canvas.height = 0
  }
  renderedPages.delete(pageNum)
}

function cleanupPdf() {
  progressReporter.flush()
  progressReporter.reset()
  resumePromptPage.value = null
  for (const pageNum of Array.from(renderedPages.keys())) {
    releaseCanvas(pageNum)
  }
  activeRenderTasks.clear()
  if (pdfDoc) {
    void pdfDoc.cleanup()
    pdfDoc = null
  }
  if (pdfLoadingTask) {
    void pdfLoadingTask.destroy()
    pdfLoadingTask = null
  }
  totalPages.value = 0
  currentPage.value = 1
  outline.value = []
  matchedPages.value = []
}

async function renderPage(pageNum: number) {
  const doc = pdfDoc
  const canvas = canvasElements.get(pageNum)
  if (!doc || !canvas) return
  if (renderedPages.get(pageNum) === zoomScale.value) return

  activeRenderTasks.get(pageNum)?.cancel()
  activeRenderTasks.delete(pageNum)

  try {
    const page = await doc.getPage(pageNum)
    if (pdfDoc !== doc) return
    const containerWidth = Math.max(280, (scrollEl.value?.clientWidth ?? window.innerWidth) - 24)
    const baseViewport = page.getViewport({ scale: 1 })
    const fitScale = containerWidth / baseViewport.width
    const effectiveScale = fitScale * zoomScale.value
    const dpr = Math.min(window.devicePixelRatio || 1, 2)
    const viewport = page.getViewport({ scale: effectiveScale * dpr })

    canvas.width = Math.floor(viewport.width)
    canvas.height = Math.floor(viewport.height)
    canvas.style.width = `${Math.floor(viewport.width / dpr)}px`
    canvas.style.height = `${Math.floor(viewport.height / dpr)}px`

    const ctx = canvas.getContext('2d')
    if (!ctx) return
    const task = page.render({ canvasContext: ctx, viewport, canvas })
    activeRenderTasks.set(pageNum, task)
    await task.promise
    activeRenderTasks.delete(pageNum)
    renderedPages.set(pageNum, zoomScale.value)
  } catch {
    // cancelled or destroyed
  }
}

async function syncPageWindow() {
  if (!pdfDoc || totalPages.value <= 0) return
  const windowPages = new Set(computeVisiblePageWindow(currentPage.value, totalPages.value, 2))
  for (const rendered of Array.from(renderedPages.keys())) {
    if (!windowPages.has(rendered)) {
      releaseCanvas(rendered)
    }
  }
  for (const p of windowPages) {
    void renderPage(p)
  }
}

async function extractOutline(doc: pdfjsLib.PDFDocumentProxy) {
  try {
    const raw = await doc.getOutline()
    if (!raw || raw.length === 0) return
    const items: OutlineItem[] = []
    async function walk(list: typeof raw, level: number) {
      if (!list) return
      for (const entry of list) {
        let pageNum = 1
        try {
          const dest = typeof entry.dest === 'string' ? await doc.getDestination(entry.dest) : entry.dest
          if (Array.isArray(dest) && dest[0]) {
            const idx = await doc.getPageIndex(dest[0])
            pageNum = idx + 1
          }
        } catch {
          // fallback page 1
        }
        items.push({ title: entry.title || `第 ${pageNum} 页`, page: pageNum, level })
        if (entry.items?.length) {
          await walk(entry.items, level + 1)
        }
      }
    }
    await walk(raw, 0)
    outline.value = items
  } catch {
    outline.value = []
  }
}

async function loadPdf() {
  const seq = ++loadSeq
  loading.value = true
  loadingHint.value = '正在连接文档流…'
  errorText.value = ''
  cleanupPdf()
  try {
    let loadingTask: pdfjsLib.PDFDocumentLoadingTask

    if (props.pdfBlob) {
      loadingHint.value = '正在解析 PDF 文档…'
      const data = await props.pdfBlob.arrayBuffer()
      if (seq !== loadSeq) return
      loadingTask = pdfjsLib.getDocument({ data: new Uint8Array(data) })
    } else {
      // 优先走后端短期票据 HTTP Range 流式分片加载（200+ 页大 PDF 仅拉取目录与当前可视页，秒开不超时）
      const ticket = await mobileDriveApi
        .streamTicket(props.node.spaceId, props.node.id, {
          versionId: props.versionId ?? props.node.currentVersionId ?? undefined,
          assetType: 'ORIGINAL',
        })
        .catch(() => null)
      if (seq !== loadSeq) return

      if (ticket?.streamUrl) {
        loadingHint.value = '正在按需流式加载首页…'
        loadingTask = pdfjsLib.getDocument({
          url: ticket.streamUrl,
          rangeChunkSize: 131_072, // 128 KB Range 分片
          disableAutoFetch: true, // 禁止后台抢先拉取全部 200+ 页，仅按可视页按需请求
          disableStream: false,
        })
      } else {
        loadingHint.value = '正在下载 PDF 文档…'
        const blob = await mobileDriveApi.downloadContent(
          props.node.spaceId,
          props.node.id,
          props.versionId ?? props.node.currentVersionId ?? undefined,
        )
        if (seq !== loadSeq) return
        const data = await blob.arrayBuffer()
        if (seq !== loadSeq) return
        loadingTask = pdfjsLib.getDocument({ data: new Uint8Array(data) })
      }
    }

    loadingTask.onProgress = ({ loaded, total }: { loaded: number; total: number }) => {
      if (seq !== loadSeq) return
      if (total > 0) {
        const pct = Math.min(99, Math.round((loaded / total) * 100))
        loadingHint.value = `正在加载文档目录（${pct}%）…`
      }
    }

    pdfLoadingTask = loadingTask
    const doc = await loadingTask.promise
    if (seq !== loadSeq) {
      void loadingTask.destroy()
      return
    }
    pdfDoc = doc
    totalPages.value = doc.numPages
    currentPage.value = 1
    emit('page-change', 1, doc.numPages)
    void extractOutline(doc)
    // 必须先关闭 loading 渲染出 canvas 占位节点，再在 nextTick 后绘制可视窗口页
    loading.value = false
    await nextTick()
    if (seq !== loadSeq) return
    void syncPageWindow()
    void mobileDriveApi
      .getProgress(props.node.spaceId, props.node.id)
      .then((saved) => {
        if (seq !== loadSeq || !saved || saved.progressType !== 'PDF') return
        if (shouldPromptResume('PDF', saved.positionValue, doc.numPages)) {
          resumePromptPage.value = Math.round(saved.positionValue)
        }
      })
      .catch(() => undefined)
  } catch (err) {
    if (seq !== loadSeq) return
    errorText.value = normalizeErrorMessage(err, 'PDF 文档加载失败')
  } finally {
    if (seq === loadSeq) loading.value = false
  }
}

watch(
  () => [props.node.spaceId, props.node.id, props.versionId ?? props.node.currentVersionId, props.pdfBlob] as const,
  () => {
    zoomScale.value = 1
    searchOpen.value = false
    keyword.value = ''
    void loadPdf()
  },
  { immediate: true },
)

watch(zoomScale, () => {
  renderedPages.clear()
  void syncPageWindow()
})

onBeforeUnmount(() => {
  loadSeq += 1
  if (scrollRaf) cancelAnimationFrame(scrollRaf)
  cleanupPdf()
})

function onScroll() {
  if (scrollRaf) return
  scrollRaf = requestAnimationFrame(() => {
    scrollRaf = 0
    const container = scrollEl.value
    if (!container || totalPages.value <= 0) return
    const midY = container.scrollTop + container.clientHeight * 0.4
    let bestPage = currentPage.value
    let bestDist = Infinity
    for (const [pageNum, el] of pageElements.entries()) {
      const dist = Math.abs(el.offsetTop + el.offsetHeight / 2 - midY)
      if (dist < bestDist) {
        bestDist = dist
        bestPage = pageNum
      }
    }
    if (bestPage !== currentPage.value) {
      currentPage.value = bestPage
      emit('page-change', bestPage, totalPages.value)
      progressReporter.report(bestPage)
      void syncPageWindow()
    }
  })
}

function jumpToPage(pageNum: number) {
  const clamped = Math.min(totalPages.value, Math.max(1, pageNum))
  currentPage.value = clamped
  emit('page-change', clamped, totalPages.value)
  progressReporter.report(clamped)
  resumePromptPage.value = null
  outlineOpen.value = false
  const el = pageElements.get(clamped)
  el?.scrollIntoView({ behavior: 'smooth', block: 'start' })
  void syncPageWindow()
}

function adjustZoom(delta: number) {
  zoomScale.value = clampZoomScale(zoomScale.value + delta)
}

async function runSearch() {
  const q = keyword.value.trim().toLowerCase()
  const doc = pdfDoc
  if (!q || !doc) {
    matchedPages.value = []
    activeMatchIdx.value = 0
    return
  }
  searching.value = true
  const hits: number[] = []
  try {
    for (let p = 1; p <= doc.numPages; p++) {
      if (pdfDoc !== doc) return
      const page = await doc.getPage(p)
      const textContent = await page.getTextContent()
      const joined = textContent.items
        .map(item => ('str' in item ? item.str : ''))
        .join(' ')
        .toLowerCase()
      if (joined.includes(q)) hits.push(p)
    }
    matchedPages.value = hits
    activeMatchIdx.value = 0
    if (hits.length > 0) jumpToPage(hits[0]!)
  } finally {
    searching.value = false
  }
}

function stepMatch(delta: number) {
  if (matchedPages.value.length === 0) return
  const len = matchedPages.value.length
  activeMatchIdx.value = (activeMatchIdx.value + delta + len) % len
  jumpToPage(matchedPages.value[activeMatchIdx.value]!)
}
</script>

<template>
  <div class="flex-1 min-h-0 flex flex-col bg-muted/40 text-foreground">
    <!-- PDF 顶部工具条 -->
    <div class="flex items-center justify-between gap-2 border-b border-border bg-card/90 px-3 py-1.5 shrink-0 text-xs">
      <div class="flex items-center gap-1.5">
        <span class="font-medium text-foreground tabular-nums">
          {{ totalPages > 0 ? `${currentPage} / ${totalPages}` : '—' }}
        </span>
        <span v-if="props.slideMode" class="rounded bg-primary/10 px-1.5 py-0.5 text-[10px] text-primary">
          建议横屏阅读
        </span>
      </div>

      <div class="flex items-center gap-1">
        <button
          type="button"
          class="inline-flex h-7 w-7 items-center justify-center rounded-lg text-muted-foreground active:bg-accent"
          aria-label="缩小"
          @click="adjustZoom(-0.25)"
        >
          <Minus :size="13" />
        </button>
        <span class="w-10 text-center text-[11px] text-muted-foreground tabular-nums">
          {{ Math.round(zoomScale * 100) }}%
        </span>
        <button
          type="button"
          class="inline-flex h-7 w-7 items-center justify-center rounded-lg text-muted-foreground active:bg-accent"
          aria-label="放大"
          @click="adjustZoom(0.25)"
        >
          <Plus :size="13" />
        </button>

        <button
          v-if="outline.length > 0"
          type="button"
          class="inline-flex h-7 w-7 items-center justify-center rounded-lg text-muted-foreground active:bg-accent"
          aria-label="目录大纲"
          @click="outlineOpen = true"
        >
          <ListTree :size="14" />
        </button>

        <button
          type="button"
          class="inline-flex h-7 w-7 items-center justify-center rounded-lg active:bg-accent"
          :class="searchOpen ? 'text-primary bg-primary/10' : 'text-muted-foreground'"
          aria-label="全文搜索"
          @click="searchOpen = !searchOpen"
        >
          <Search :size="14" />
        </button>
      </div>
    </div>

    <!-- 跨页全文搜索条 -->
    <form
      v-if="searchOpen"
      class="flex items-center gap-1.5 border-b border-border bg-card px-3 py-1.5 shrink-0"
      @submit.prevent="runSearch"
    >
      <input
        v-model="keyword"
        type="text"
        placeholder="搜索 PDF 全文关键词…"
        class="flex-1 min-w-0 rounded-lg border border-input bg-background px-2.5 py-1 text-xs text-foreground focus:outline-none focus:ring-2 focus:ring-primary/40"
      />
      <button
        type="submit"
        class="rounded-lg bg-primary px-2.5 py-1 text-xs font-medium text-primary-foreground shrink-0"
        :disabled="searching"
      >
        {{ searching ? '搜索中' : '搜索' }}
      </button>
      <template v-if="matchedPages.length > 0">
        <span class="text-[11px] text-muted-foreground shrink-0 tabular-nums">
          {{ activeMatchIdx + 1 }}/{{ matchedPages.length }}
        </span>
        <button type="button" class="p-1 text-muted-foreground" @click="stepMatch(-1)">
          <ChevronUp :size="14" />
        </button>
        <button type="button" class="p-1 text-muted-foreground" @click="stepMatch(1)">
          <ChevronDown :size="14" />
        </button>
      </template>
      <button
        type="button"
        class="p-1 text-muted-foreground"
        @click="keyword = ''; matchedPages = []; searchOpen = false"
      >
        <X :size="14" />
      </button>
    </form>

    <!-- 跨端阅读进度接力提示条 -->
    <div
      v-if="resumePromptPage !== null"
      class="flex items-center justify-between gap-2 border-b border-primary/20 bg-primary/10 px-3 py-2 text-xs text-foreground shrink-0"
      @click.stop
    >
      <span class="truncate">{{ formatResumePrompt('PDF', resumePromptPage) }}</span>
      <div class="flex items-center gap-1.5 shrink-0">
        <button
          type="button"
          class="rounded-lg bg-primary px-2.5 py-1 text-[11px] font-medium text-primary-foreground"
          @click="jumpToPage(resumePromptPage)"
        >
          跳转
        </button>
        <button
          type="button"
          class="p-1 text-muted-foreground"
          aria-label="关闭提示"
          @click="resumePromptPage = null"
        >
          <X :size="13" />
        </button>
      </div>
    </div>

    <!-- 连续滚动分页容器 -->
    <div
      ref="scrollEl"
      class="flex-1 min-h-0 overflow-auto px-3 py-3 pb-24 space-y-3"
      @scroll.passive="onScroll"
      @click="emit('toggle-chrome')"
    >
      <div v-if="loading" class="py-16 text-center text-sm text-muted-foreground">{{ loadingHint }}</div>
      <ErrorState v-else-if="errorText" :message="errorText" @retry="loadPdf" />
      <template v-else>
        <div
          v-for="p in pageNumbers"
          :key="p"
          :ref="el => setPageEl(p, el)"
          class="mx-auto flex flex-col items-center rounded-lg bg-white shadow-xs overflow-hidden min-h-[240px] justify-center"
          :class="matchedPages.includes(p) ? 'ring-2 ring-amber-400' : ''"
        >
          <canvas :ref="el => setCanvasEl(p, el)" class="block max-w-full" />
        </div>
      </template>
    </div>

    <!-- 底部页码跳转滑块 / PPT 缩略页条 -->
    <div
      v-if="totalPages > 1"
      class="border-t border-border bg-card/95 px-4 py-2 shrink-0 flex items-center gap-3 text-xs"
      @click.stop
    >
      <template v-if="props.slideMode">
        <div class="flex items-center gap-1.5 overflow-x-auto no-scrollbar w-full">
          <button
            v-for="p in pageNumbers"
            :key="p"
            type="button"
            class="shrink-0 rounded-lg border px-2.5 py-1 text-xs tabular-nums"
            :class="
              currentPage === p
                ? 'border-primary bg-primary text-primary-foreground font-medium'
                : 'border-border bg-background text-muted-foreground'
            "
            @click="jumpToPage(p)"
          >
            P{{ p }}
          </button>
        </div>
      </template>
      <template v-else>
        <span class="text-muted-foreground shrink-0 tabular-nums">第 {{ currentPage }} 页</span>
        <input
          type="range"
          :min="1"
          :max="totalPages"
          :value="currentPage"
          class="flex-1 accent-primary"
          @change="e => jumpToPage(Number((e.target as HTMLInputElement).value))"
        />
        <span class="text-muted-foreground shrink-0 tabular-nums">{{ totalPages }}</span>
      </template>
    </div>

    <!-- 大纲目录 BottomSheet -->
    <BottomSheet v-model:open="outlineOpen" title="PDF 目录">
      <div class="space-y-1 pb-2">
        <button
          v-for="(item, idx) in outline"
          :key="`${item.page}-${idx}`"
          type="button"
          class="w-full flex items-center justify-between gap-2 rounded-lg px-2.5 py-2 text-left text-sm text-foreground active:bg-accent"
          :style="{ paddingLeft: `${item.level * 14 + 10}px` }"
          @click="jumpToPage(item.page)"
        >
          <span class="truncate">{{ item.title }}</span>
          <span class="text-xs text-muted-foreground shrink-0">P{{ item.page }}</span>
        </button>
      </div>
    </BottomSheet>
  </div>
</template>
