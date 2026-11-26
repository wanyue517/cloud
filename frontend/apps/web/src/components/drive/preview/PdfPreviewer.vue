<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as pdfjsLib from 'pdfjs-dist'
import { ChevronLeft, ChevronRight, ListTree, Maximize, Search, ZoomIn, ZoomOut } from '@lucide/vue'
import { Button } from '@repo/ui/button'
import { Input } from '@repo/ui/input'
import { drive, type DriveNode, type Version } from '@/api/drive'

pdfjsLib.GlobalWorkerOptions.workerSrc = new URL('pdfjs-dist/build/pdf.worker.min.mjs', import.meta.url).toString()

interface OutlineEntry {
  title: string
  page: number
  depth: number
}

interface SearchHit {
  page: number
  snippet: string
}

const props = defineProps<{
  node: DriveNode
  version?: Version
  customLoader?: () => Promise<Blob>
  presentationMode?: boolean
}>()

const loading = ref(true)
const rendering = ref(false)
const error = ref('')
const canvasRef = ref<HTMLCanvasElement | null>(null)
const viewerRef = ref<HTMLElement | null>(null)
const currentPage = ref(1)
const pageInput = ref('1')
const totalPages = ref(0)
const scale = ref(1.25)
const showOutline = ref(false)
const outline = ref<OutlineEntry[]>([])
const searchQuery = ref('')
const searching = ref(false)
const searchHits = ref<SearchHit[]>([])
const activeHitIndex = ref(0)

let pdfDoc: pdfjsLib.PDFDocumentProxy | null = null
let activeLoadingTask: pdfjsLib.PDFDocumentLoadingTask | null = null
let activeRenderTask: { cancel: () => void; promise: Promise<unknown> } | null = null
let loadSeq = 0

async function releasePdf() {
  if (activeRenderTask) {
    try { activeRenderTask.cancel() } catch { /* ignore */ }
    activeRenderTask = null
  }
  if (pdfDoc) {
    try { await pdfDoc.cleanup() } catch { /* ignore */ }
    pdfDoc = null
  }
  if (activeLoadingTask) {
    try { await activeLoadingTask.destroy() } catch { /* ignore */ }
    activeLoadingTask = null
  }
}

const scalePercent = computed(() => Math.round(scale.value * 100))

async function flattenOutline(
  doc: pdfjsLib.PDFDocumentProxy,
  items: Awaited<ReturnType<pdfjsLib.PDFDocumentProxy['getOutline']>>,
  depth = 0,
): Promise<OutlineEntry[]> {
  if (!items || !items.length) return []
  const result: OutlineEntry[] = []
  for (const item of items) {
    let page = 1
    try {
      const dest = typeof item.dest === 'string' ? await doc.getDestination(item.dest) : item.dest
      if (Array.isArray(dest) && dest[0]) {
        const idx = await doc.getPageIndex(dest[0])
        page = idx + 1
      }
    } catch {
      page = 1
    }
    result.push({ title: item.title || '未命名章节', page, depth })
    if (item.items?.length) {
      result.push(...await flattenOutline(doc, item.items, depth + 1))
    }
  }
  return result
}

function readSavedPage(): number {
  try {
    const raw = localStorage.getItem(`cloudlane.pdf.page.${props.node.id}.${props.version?.id || 'cur'}`)
    const parsed = raw ? Number.parseInt(raw, 10) : 1
    return Number.isFinite(parsed) && parsed >= 1 ? parsed : 1
  } catch {
    return 1
  }
}

function saveCurrentPage(pageNum: number) {
  try {
    localStorage.setItem(`cloudlane.pdf.page.${props.node.id}.${props.version?.id || 'cur'}`, String(pageNum))
  } catch { /* ignore */ }
}

async function computeFitWidthScale(pageNum: number): Promise<number> {
  if (!pdfDoc || !viewerRef.value) return 1.25
  const page = await pdfDoc.getPage(pageNum)
  const base = page.getViewport({ scale: 1 })
  const available = Math.max(320, viewerRef.value.clientWidth - 48)
  const ratio = available / base.width
  return Math.max(0.75, Math.min(2.2, Number(ratio.toFixed(2))))
}

async function fitToWidth() {
  scale.value = await computeFitWidthScale(currentPage.value)
  await renderPage(currentPage.value)
}

async function renderPage(num: number) {
  if (!pdfDoc || !canvasRef.value) return
  const safePage = Math.max(1, Math.min(totalPages.value, num))
  currentPage.value = safePage
  pageInput.value = String(safePage)
  saveCurrentPage(safePage)
  if (activeRenderTask) {
    try { activeRenderTask.cancel() } catch { /* ignore */ }
    activeRenderTask = null
  }
  rendering.value = true
  try {
    const page = await pdfDoc.getPage(safePage)
    const viewport = page.getViewport({ scale: scale.value })
    const canvas = canvasRef.value
    const ctx = canvas.getContext('2d')
    if (!ctx) return
    const dpr = Math.min(window.devicePixelRatio || 1, 2)
    canvas.width = Math.floor(viewport.width * dpr)
    canvas.height = Math.floor(viewport.height * dpr)
    canvas.style.width = `${Math.floor(viewport.width)}px`
    canvas.style.height = `${Math.floor(viewport.height)}px`
    ctx.setTransform(1, 0, 0, 1, 0, 0)
    const transform = dpr !== 1 ? [dpr, 0, 0, dpr, 0, 0] : undefined
    const task = page.render({ canvasContext: ctx, viewport, transform, canvas })
    activeRenderTask = task
    await task.promise
  } catch (e) {
    const name = e && typeof e === 'object' && 'name' in e ? String((e as { name: unknown }).name) : ''
    if (name !== 'RenderingCancelledException') {
      error.value = e instanceof Error ? e.message : 'PDF 渲染失败'
    }
  } finally {
    rendering.value = false
  }
}

async function loadPdf() {
  const seq = ++loadSeq
  loading.value = true
  error.value = ''
  outline.value = []
  searchHits.value = []
  await releasePdf()
  try {
    const size = props.version?.size ?? props.node.size
    if (size > 64 * 1024 * 1024) {
      error.value = 'PDF 超过 64 MB，请下载后查看'
      return
    }
    const blob = props.customLoader ? await props.customLoader() : await drive.content(props.node, props.version?.id)
    if (seq !== loadSeq) return
    const data = new Uint8Array(await blob.arrayBuffer())
    const task = pdfjsLib.getDocument({ data })
    activeLoadingTask = task
    const doc = await task.promise
    if (seq !== loadSeq) {
      await task.destroy()
      return
    }
    pdfDoc = doc
    totalPages.value = doc.numPages
    const initialPage = Math.min(doc.numPages, readSavedPage())
    currentPage.value = initialPage
    pageInput.value = String(initialPage)
    const rawOutline = await doc.getOutline()
    outline.value = await flattenOutline(doc, rawOutline)
    showOutline.value = outline.value.length > 0
    loading.value = false
    await nextTick()
    scale.value = await computeFitWidthScale(initialPage)
    await renderPage(initialPage)
  } catch (e) {
    if (seq !== loadSeq) return
    error.value = e instanceof Error ? e.message : 'PDF 加载失败'
  } finally {
    if (seq === loadSeq) loading.value = false
  }
}

function jumpPage() {
  const parsed = Number.parseInt(pageInput.value, 10)
  if (Number.isFinite(parsed)) void renderPage(parsed)
  else pageInput.value = String(currentPage.value)
}

function zoomBy(delta: number) {
  scale.value = Math.max(0.5, Math.min(2.5, Number((scale.value + delta).toFixed(2))))
  void renderPage(currentPage.value)
}

async function runSearch() {
  const q = searchQuery.value.trim().toLowerCase()
  searchHits.value = []
  activeHitIndex.value = 0
  if (!q || !pdfDoc) return
  searching.value = true
  try {
    const hits: SearchHit[] = []
    for (let p = 1; p <= pdfDoc.numPages; p++) {
      const page = await pdfDoc.getPage(p)
      const content = await page.getTextContent()
      const text = content.items.map(it => ('str' in it ? it.str : '')).join(' ')
      const idx = text.toLowerCase().indexOf(q)
      if (idx >= 0) {
        const start = Math.max(0, idx - 24)
        const end = Math.min(text.length, idx + q.length + 36)
        hits.push({ page: p, snippet: text.slice(start, end).trim() })
      }
    }
    searchHits.value = hits
    if (hits.length > 0) {
      await renderPage(hits[0]!.page)
    }
  } finally {
    searching.value = false
  }
}

function goHit(step: number) {
  if (!searchHits.value.length) return
  activeHitIndex.value = (activeHitIndex.value + step + searchHits.value.length) % searchHits.value.length
  const target = searchHits.value[activeHitIndex.value]
  if (target) void renderPage(target.page)
}

watch(() => [props.node.id, props.version?.id], () => { void loadPdf() })
onMounted(() => { void loadPdf() })
onBeforeUnmount(() => {
  loadSeq++
  void releasePdf()
})
</script>

<template>
  <div class="flex h-full min-h-0 flex-1 flex-col bg-muted/20">
    <div class="flex flex-wrap items-center justify-between gap-2 border-b border-border bg-background/95 px-4 py-2">
      <div class="flex items-center gap-1.5">
        <Button variant="outline" size="sm" :disabled="!outline.length" @click="showOutline = !showOutline">
          <ListTree :size="15" /> 目录{{ outline.length ? ` (${outline.length})` : '' }}
        </Button>
        <Button variant="outline" size="icon-sm" :disabled="currentPage <= 1" aria-label="上一页" @click="renderPage(currentPage - 1)">
          <ChevronLeft :size="15" />
        </Button>
        <div class="flex items-center gap-1 text-xs">
          <Input v-model="pageInput" class="h-7 w-14 text-center text-xs" aria-label="页码" @keydown.enter="jumpPage" @blur="jumpPage" />
          <span class="text-muted-foreground">/ {{ totalPages || 1 }}</span>
        </div>
        <Button variant="outline" size="icon-sm" :disabled="currentPage >= totalPages" aria-label="下一页" @click="renderPage(currentPage + 1)">
          <ChevronRight :size="15" />
        </Button>
      </div>

      <div class="flex items-center gap-1.5">
        <Button variant="outline" size="icon-sm" aria-label="缩小" @click="zoomBy(-0.2)"><ZoomOut :size="15" /></Button>
        <span class="min-w-[3.2rem] text-center text-xs tabular-nums text-muted-foreground">{{ scalePercent }}%</span>
        <Button variant="outline" size="icon-sm" aria-label="放大" @click="zoomBy(0.2)"><ZoomIn :size="15" /></Button>
        <Button variant="outline" size="sm" title="适应窗口宽度" @click="fitToWidth"><Maximize :size="13" /> 适应宽度</Button>
      </div>

      <form class="flex items-center gap-1.5" @submit.prevent="runSearch">
        <div class="relative">
          <Search :size="14" class="pointer-events-none absolute left-2 top-1/2 -translate-y-1/2 text-muted-foreground" />
          <Input v-model="searchQuery" class="h-7 w-40 pl-7 text-xs" placeholder="搜索 PDF 正文…" />
        </div>
        <Button type="submit" variant="outline" size="sm" :disabled="searching">{{ searching ? '搜索中' : '搜索' }}</Button>
        <template v-if="searchHits.length">
          <span class="text-xs text-muted-foreground">{{ activeHitIndex + 1 }}/{{ searchHits.length }}</span>
          <Button type="button" variant="outline" size="icon-sm" aria-label="上一处匹配" @click="goHit(-1)"><ChevronLeft :size="14" /></Button>
          <Button type="button" variant="outline" size="icon-sm" aria-label="下一处匹配" @click="goHit(1)"><ChevronRight :size="14" /></Button>
        </template>
      </form>
    </div>

    <div class="flex min-h-0 flex-1 overflow-hidden">
      <aside v-if="presentationMode && totalPages > 0" class="w-40 shrink-0 overflow-y-auto border-r border-border bg-background p-2.5">
        <div class="mb-2 text-xs font-semibold text-muted-foreground">幻灯片 ({{ totalPages }})</div>
        <div class="space-y-2">
          <button
            v-for="p in totalPages"
            :key="p"
            type="button"
            class="flex w-full items-center justify-between rounded-md border px-2.5 py-2 text-xs transition"
            :class="currentPage === p ? 'border-primary bg-primary/10 font-medium text-primary' : 'border-border hover:bg-muted'"
            @click="renderPage(p)"
          >
            <span>第 {{ p }} 页</span>
            <span class="text-[10px] text-muted-foreground">Slide</span>
          </button>
        </div>
      </aside>
      <aside v-else-if="showOutline && outline.length" class="w-60 shrink-0 overflow-y-auto border-r border-border bg-background p-3">
        <div class="mb-2 text-xs font-semibold text-muted-foreground">文档目录</div>
        <ul class="space-y-1">
          <li v-for="(item, idx) in outline" :key="idx">
            <button
              type="button"
              class="flex w-full items-center justify-between gap-2 rounded px-2 py-1 text-left text-xs hover:bg-muted"
              :style="{ paddingLeft: `${8 + item.depth * 12}px` }"
              @click="renderPage(item.page)"
            >
              <span class="truncate">{{ item.title }}</span>
              <span class="shrink-0 text-muted-foreground">{{ item.page }}</span>
            </button>
          </li>
        </ul>
      </aside>

      <div ref="viewerRef" class="flex min-h-0 flex-1 flex-col items-center overflow-auto p-6">
        <p v-if="loading" role="status" class="my-auto text-sm text-muted-foreground">正在解析 PDF 文档…</p>
        <p v-else-if="error" role="alert" class="my-auto text-sm text-destructive">{{ error }}</p>
        <div v-else class="relative rounded-md border border-border bg-white shadow-sm">
          <canvas ref="canvasRef" class="block" />
          <div v-if="rendering" class="absolute inset-0 flex items-center justify-center bg-background/40 text-xs text-muted-foreground">
            渲染第 {{ currentPage }} 页…
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
