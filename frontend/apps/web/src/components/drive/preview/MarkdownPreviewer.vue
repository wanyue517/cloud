<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import MarkdownIt from 'markdown-it'
import DOMPurify from 'dompurify'
import hljs from 'highlight.js'
import 'highlight.js/styles/github.css'
import { Code2, Eye, ListTree } from '@lucide/vue'
import { Button } from '@repo/ui/button'
import { drive, type DriveNode, type Version } from '@/api/drive'
import { extractMarkdownToc, slugifyHeading, type TocHeading } from '@/lib/preview-registry'

const props = defineProps<{ node: DriveNode; version?: Version }>()

const loading = ref(true)
const loadingMore = ref(false)
const error = ref('')
const rawText = ref('')
const loadedBytes = ref(0)
const totalBytes = ref(0)
const hasMore = ref(false)
const viewMode = ref<'rendered' | 'source'>('rendered')
const showToc = ref(true)
const contentEl = ref<HTMLElement | null>(null)
let loadSeq = 0

function escapeHtml(str: string): string {
  return str.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
}

type MarkdownItInstance = InstanceType<typeof MarkdownIt>
type HeadingRule = NonNullable<MarkdownItInstance['renderer']['rules']['heading_open']>

function createMarkdownRenderer(): MarkdownItInstance {
  let headingCounter = 0
  const md: MarkdownItInstance = new MarkdownIt({
    html: false,
    linkify: true,
    typographer: true,
    highlight(str: string, lang: string): string {
      if (lang && hljs.getLanguage(lang)) {
        try {
          return `<pre class="hljs"><code>${hljs.highlight(str, { language: lang, ignoreIllegals: true }).value}</code></pre>`
        } catch { /* fallback */ }
      }
      return `<pre class="hljs"><code>${escapeHtml(str)}</code></pre>`
    },
  })
  const defaultHeadingOpen = md.renderer.rules.heading_open
  const headingOpen: HeadingRule = (tokens, idx, options, env, self) => {
    const nextToken = tokens[idx + 1]
    const text = nextToken?.content ?? ''
    headingCounter += 1
    tokens[idx]?.attrSet('id', slugifyHeading(text, headingCounter))
    return defaultHeadingOpen ? defaultHeadingOpen(tokens, idx, options, env, self) : self.renderToken(tokens, idx, options)
  }
  md.renderer.rules.heading_open = headingOpen
  return md
}

const toc = computed<TocHeading[]>(() => extractMarkdownToc(rawText.value))

const safeHtml = computed(() => {
  const md = createMarkdownRenderer()
  const rendered = md.render(rawText.value)
  return DOMPurify.sanitize(rendered, {
    USE_PROFILES: { html: true },
    ADD_ATTR: ['id', 'class'],
  })
})

async function loadInitial() {
  const seq = ++loadSeq
  loading.value = true
  error.value = ''
  rawText.value = ''
  loadedBytes.value = 0
  totalBytes.value = 0
  hasMore.value = false
  try {
    const chunk = await drive.textChunk(props.node, { versionId: props.version?.id, offset: 0, limit: 131072 })
    if (seq !== loadSeq) return
    if (chunk.binaryDetected) {
      error.value = '检测到二进制内容，请下载后查看'
      return
    }
    rawText.value = chunk.content
    loadedBytes.value = chunk.bytesRead
    totalBytes.value = chunk.totalBytes
    hasMore.value = chunk.hasMore
  } catch (e) {
    if (seq !== loadSeq) return
    error.value = e instanceof Error ? e.message : 'Markdown 加载失败'
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

function jumpToHeading(id: string) {
  viewMode.value = 'rendered'
  const target = contentEl.value?.querySelector(`[id="${CSS.escape(id)}"]`)
  target?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

watch(() => [props.node.id, props.version?.id], () => { void loadInitial() })
onMounted(() => { void loadInitial() })
</script>

<template>
  <div class="flex h-full min-h-0 flex-1 flex-col bg-background">
    <div class="flex flex-wrap items-center justify-between gap-2 border-b border-border px-4 py-2">
      <div class="flex items-center gap-1.5">
        <Button variant="outline" size="sm" :class="viewMode === 'rendered' ? 'border-primary text-primary' : ''" @click="viewMode = 'rendered'">
          <Eye :size="14" /> 渲染视图
        </Button>
        <Button variant="outline" size="sm" :class="viewMode === 'source' ? 'border-primary text-primary' : ''" @click="viewMode = 'source'">
          <Code2 :size="14" /> 源码
        </Button>
        <Button v-if="toc.length" variant="outline" size="sm" @click="showToc = !showToc">
          <ListTree :size="14" /> 目录 ({{ toc.length }})
        </Button>
      </div>
      <span class="text-xs text-muted-foreground">
        已加载 {{ Math.round(loadedBytes / 1024) }} KB / {{ Math.max(1, Math.round(totalBytes / 1024)) }} KB · 已开启安全净化
      </span>
    </div>

    <div v-if="loading" role="status" class="flex flex-1 items-center justify-center text-sm text-muted-foreground">正在加载 Markdown…</div>
    <div v-else-if="error" role="alert" class="flex flex-1 items-center justify-center text-sm text-destructive">{{ error }}</div>
    <div v-else class="flex min-h-0 flex-1 overflow-hidden">
      <div ref="contentEl" class="min-h-0 flex-1 overflow-y-auto p-6">
        <article v-if="viewMode === 'rendered'" class="markdown-body mx-auto max-w-3xl" v-html="safeHtml" />
        <pre v-else class="mx-auto max-w-4xl whitespace-pre-wrap break-words rounded-lg bg-muted/40 p-4 font-mono text-xs leading-6">{{ rawText }}</pre>
        <div v-if="hasMore" class="mt-6 flex justify-center">
          <Button variant="outline" size="sm" :disabled="loadingMore" @click="loadMore">
            {{ loadingMore ? '正在加载下一段…' : '加载更多内容' }}
          </Button>
        </div>
      </div>

      <aside v-if="showToc && toc.length && viewMode === 'rendered'" class="hidden w-60 shrink-0 overflow-y-auto border-l border-border bg-muted/10 p-3 md:block">
        <div class="mb-2 text-xs font-semibold text-muted-foreground">文档大纲</div>
        <ul class="space-y-1">
          <li v-for="h in toc" :key="h.id">
            <button
              type="button"
              class="block w-full truncate rounded px-2 py-1 text-left text-xs text-foreground/85 hover:bg-muted"
              :style="{ paddingLeft: `${8 + (h.level - 1) * 10}px` }"
              @click="jumpToHeading(h.id)"
            >
              {{ h.text }}
            </button>
          </li>
        </ul>
      </aside>
    </div>
  </div>
</template>

<style scoped>
.markdown-body :deep(h1) { font-size: 1.65rem; font-weight: 700; margin: 1.2rem 0 0.75rem; padding-bottom: 0.35rem; border-bottom: 1px solid var(--border); }
.markdown-body :deep(h2) { font-size: 1.35rem; font-weight: 600; margin: 1.1rem 0 0.6rem; }
.markdown-body :deep(h3) { font-size: 1.1rem; font-weight: 600; margin: 0.9rem 0 0.5rem; }
.markdown-body :deep(p) { margin: 0.65rem 0; line-height: 1.75; }
.markdown-body :deep(ul), .markdown-body :deep(ol) { margin: 0.6rem 0; padding-left: 1.5rem; list-style: revert; }
.markdown-body :deep(blockquote) { margin: 0.75rem 0; padding: 0.25rem 1rem; border-left: 3px solid var(--primary); color: var(--muted-foreground); background: color-mix(in oklab, var(--muted) 45%, transparent); }
.markdown-body :deep(table) { width: 100%; border-collapse: collapse; margin: 0.85rem 0; font-size: 0.875rem; }
.markdown-body :deep(th), .markdown-body :deep(td) { border: 1px solid var(--border); padding: 0.45rem 0.7rem; text-align: left; }
.markdown-body :deep(th) { background: color-mix(in oklab, var(--muted) 60%, transparent); font-weight: 600; }
.markdown-body :deep(pre) { margin: 0.75rem 0; padding: 0.85rem 1rem; border-radius: 0.5rem; overflow-x: auto; font-size: 0.82rem; line-height: 1.6; border: 1px solid var(--border); }
.markdown-body :deep(code) { font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace; }
.markdown-body :deep(:not(pre) > code) { padding: 0.1rem 0.35rem; border-radius: 0.25rem; background: var(--muted); font-size: 0.85em; }
.markdown-body :deep(a) { color: var(--primary); text-decoration: underline; }
</style>
