<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import hljs from 'highlight.js'
import 'highlight.js/styles/github.css'
import { ChevronDown, ChevronUp, Code2, Eye, Search, WrapText } from '@lucide/vue'
import { Button } from '@repo/ui/button'
import { Input } from '@repo/ui/input'
import { drive, type DriveNode, type Version } from '@/api/drive'
import { detectCodeLanguage } from '@/lib/preview-registry'

const props = defineProps<{ node: DriveNode; version?: Version }>()

const loading = ref(true)
const loadingMore = ref(false)
const error = ref('')
const binaryDetected = ref(false)
const rawText = ref('')
const loadedBytes = ref(0)
const totalBytes = ref(0)
const hasMore = ref(false)
const wrapLines = ref(false)
const searchQuery = ref('')
const activeMatchIdx = ref(0)
const scrollContainer = ref<HTMLElement | null>(null)
let loadSeq = 0

const language = computed(() => detectCodeLanguage(props.node.name))
const isHtml = computed(() => /\.(html?|xhtml)$/i.test(props.node.name) || props.node.mimeType === 'text/html')
const htmlViewMode = ref<'render' | 'source'>('render')

const lines = computed(() => rawText.value.split(/\r?\n/))

const highlightedLines = computed(() => {
  const src = rawText.value
  if (!src) return []
  try {
    const html = language.value !== 'plaintext' && hljs.getLanguage(language.value)
      ? hljs.highlight(src, { language: language.value, ignoreIllegals: true }).value
      : src.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
    return html.split(/\r?\n/)
  } catch {
    return lines.value.map(l => l.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;'))
  }
})

const matchedLineIndexes = computed(() => {
  const q = searchQuery.value.trim().toLowerCase()
  if (!q) return []
  const matches: number[] = []
  lines.value.forEach((line, idx) => {
    if (line.toLowerCase().includes(q)) matches.push(idx)
  })
  return matches
})

const activeLineIndex = computed(() => {
  if (!matchedLineIndexes.value.length) return -1
  const safe = activeMatchIdx.value % matchedLineIndexes.value.length
  return matchedLineIndexes.value[safe] ?? -1
})

function scrollToLine(lineIdx: number) {
  if (lineIdx < 0) return
  const el = scrollContainer.value?.querySelector(`[data-line-index="${lineIdx}"]`)
  el?.scrollIntoView({ behavior: 'smooth', block: 'center' })
}

function stepMatch(delta: number) {
  if (!matchedLineIndexes.value.length) return
  activeMatchIdx.value = (activeMatchIdx.value + delta + matchedLineIndexes.value.length) % matchedLineIndexes.value.length
  scrollToLine(activeLineIndex.value)
}

watch(searchQuery, () => {
  activeMatchIdx.value = 0
  if (matchedLineIndexes.value.length) {
    scrollToLine(matchedLineIndexes.value[0]!)
  }
})

async function loadInitial() {
  const seq = ++loadSeq
  loading.value = true
  error.value = ''
  binaryDetected.value = false
  rawText.value = ''
  loadedBytes.value = 0
  totalBytes.value = 0
  hasMore.value = false
  try {
    const chunk = await drive.textChunk(props.node, { versionId: props.version?.id, offset: 0, limit: 131072 })
    if (seq !== loadSeq) return
    binaryDetected.value = chunk.binaryDetected
    rawText.value = chunk.content
    loadedBytes.value = chunk.bytesRead
    totalBytes.value = chunk.totalBytes
    hasMore.value = chunk.hasMore
    // HTML 效果预览需完整文档（避免超过 128KB 时尾部脚本/样式被截断），2MB 内自动续读完整
    if (isHtml.value && !chunk.binaryDetected && chunk.totalBytes <= 2 * 1024 * 1024) {
      while (hasMore.value && loadedBytes.value < 2 * 1024 * 1024) {
        const next = await drive.textChunk(props.node, {
          versionId: props.version?.id,
          offset: loadedBytes.value,
          limit: 131072,
        })
        if (seq !== loadSeq) return
        if (!next.bytesRead) break
        rawText.value += next.content
        loadedBytes.value += next.bytesRead
        totalBytes.value = next.totalBytes
        hasMore.value = next.hasMore
      }
    }
  } catch (e) {
    if (seq !== loadSeq) return
    error.value = e instanceof Error ? e.message : '文本加载失败'
  } finally {
    if (seq === loadSeq) loading.value = false
  }
}

async function loadMore() {
  if (!hasMore.value || loadingMore.value) return
  loadingMore.value = true
  try {
    const chunk = await drive.textChunk(props.node, { versionId: props.version?.id, offset: loadedBytes.value, limit: 131072 })
    rawText.value += chunk.content
    loadedBytes.value += chunk.bytesRead
    totalBytes.value = chunk.totalBytes
    hasMore.value = chunk.hasMore
  } catch (e) {
    error.value = e instanceof Error ? e.message : '加载更多内容失败'
  } finally {
    loadingMore.value = false
  }
}

watch(() => [props.node.id, props.version?.id], () => {
  htmlViewMode.value = 'render'
  void loadInitial()
})
onMounted(() => { void loadInitial() })
</script>

<template>
  <div class="flex h-full min-h-0 flex-1 flex-col bg-background">
    <div class="flex flex-wrap items-center justify-between gap-2 border-b border-border px-4 py-2">
      <div class="flex items-center gap-2 text-xs text-muted-foreground">
        <span class="rounded bg-muted px-2 py-0.5 font-mono uppercase text-foreground">{{ language }}</span>
        <span>{{ lines.length }} 行</span>
        <span>已加载 {{ Math.round(loadedBytes / 1024) }} KB / {{ Math.max(1, Math.round(totalBytes / 1024)) }} KB</span>
      </div>

      <div class="flex items-center gap-1.5">
        <div v-if="isHtml" class="inline-flex rounded-md border border-input p-0.5 mr-1" role="tablist" aria-label="HTML 预览模式">
          <Button
            type="button"
            size="sm"
            class="h-6 px-2.5 text-xs"
            :variant="htmlViewMode === 'render' ? 'default' : 'ghost'"
            role="tab"
            :aria-selected="htmlViewMode === 'render'"
            @click="htmlViewMode = 'render'"
          >
            <Eye :size="13" /> 效果预览
          </Button>
          <Button
            type="button"
            size="sm"
            class="h-6 px-2.5 text-xs"
            :variant="htmlViewMode === 'source' ? 'default' : 'ghost'"
            role="tab"
            :aria-selected="htmlViewMode === 'source'"
            @click="htmlViewMode = 'source'"
          >
            <Code2 :size="13" /> 源码模式
          </Button>
        </div>
        <template v-if="!isHtml || htmlViewMode === 'source'">
          <div class="relative">
            <Search :size="14" class="pointer-events-none absolute left-2 top-1/2 -translate-y-1/2 text-muted-foreground" />
            <Input v-model="searchQuery" class="h-7 w-44 pl-7 text-xs" placeholder="在已加载内容中搜索…" />
          </div>
          <template v-if="searchQuery.trim()">
            <span class="min-w-[3.5rem] text-center text-xs text-muted-foreground">
              {{ matchedLineIndexes.length ? `${activeMatchIdx + 1}/${matchedLineIndexes.length}` : '0 处' }}
            </span>
            <Button variant="outline" size="icon-sm" :disabled="!matchedLineIndexes.length" aria-label="上一处" @click="stepMatch(-1)">
              <ChevronUp :size="14" />
            </Button>
            <Button variant="outline" size="icon-sm" :disabled="!matchedLineIndexes.length" aria-label="下一处" @click="stepMatch(1)">
              <ChevronDown :size="14" />
            </Button>
          </template>
          <Button variant="outline" size="sm" :class="wrapLines ? 'border-primary text-primary' : ''" @click="wrapLines = !wrapLines">
            <WrapText :size="14" /> 换行
          </Button>
        </template>
      </div>
    </div>

    <div v-if="loading" role="status" class="flex flex-1 items-center justify-center text-sm text-muted-foreground">正在分段读取文本…</div>
    <div v-else-if="error" role="alert" class="flex flex-1 items-center justify-center text-sm text-destructive">{{ error }}</div>
    <div v-else-if="binaryDetected" class="flex flex-1 flex-col items-center justify-center gap-2 p-8 text-center">
      <p class="text-sm font-medium">检测到二进制数据</p>
      <p class="text-xs text-muted-foreground">该文件不适合作为纯文本阅读，请点击右上角「下载」到本地查看。</p>
    </div>
    <div v-else-if="isHtml && htmlViewMode === 'render'" class="min-h-0 flex-1 bg-white">
      <iframe
        :srcdoc="rawText"
        sandbox="allow-scripts allow-forms allow-popups"
        referrerpolicy="no-referrer"
        title="HTML 渲染预览"
        class="h-full w-full border-0"
      />
    </div>
    <div v-else ref="scrollContainer" class="min-h-0 flex-1 overflow-auto font-mono text-xs leading-6">
      <table class="w-full border-collapse">
        <tbody>
          <tr
            v-for="(lineHtml, idx) in highlightedLines"
            :key="idx"
            :data-line-index="idx"
            :class="idx === activeLineIndex ? 'bg-primary/15' : matchedLineIndexes.includes(idx) ? 'bg-amber-500/10' : 'hover:bg-muted/40'"
          >
            <td class="w-12 select-none border-r border-border bg-muted/20 px-2.5 text-right text-muted-foreground align-top">
              {{ idx + 1 }}
            </td>
            <td class="px-3 align-top" :class="wrapLines ? 'whitespace-pre-wrap break-all' : 'whitespace-pre'">
              <span v-html="lineHtml || '&nbsp;'" />
            </td>
          </tr>
        </tbody>
      </table>
      <div v-if="hasMore" class="flex items-center justify-center gap-3 border-t border-border bg-muted/20 py-3">
        <span class="text-xs text-muted-foreground">为避免大文件卡顿，按 128 KB 分段加载</span>
        <Button variant="outline" size="sm" :disabled="loadingMore" @click="loadMore">
          {{ loadingMore ? '正在读取下一段…' : '加载下一段 (128 KB)' }}
        </Button>
      </div>
    </div>
  </div>
</template>
