<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { Button as VanButton } from 'vant'
import { Code2, FileText, ListTree } from '@lucide/vue'
import { normalizeErrorMessage } from '@repo/drive-core/errors'
import { formatBytes } from '@repo/drive-core/format'
import type { TocHeading } from '@repo/drive-core/preview-registry'
import { mobileDriveApi, type DriveNode } from '@/api/drive'
import { renderSafeMarkdown } from '@/lib/markdown'
import { BottomSheet, ErrorState } from '@/components/common'
import 'katex/dist/katex.min.css'

const props = defineProps<{
  node: DriveNode
  versionId?: string
}>()

const emit = defineEmits<{
  'toggle-chrome': []
  download: []
}>()

const loading = ref(true)
const loadingMore = ref(false)
const errorText = ref('')
const binaryDetected = ref(false)
const source = ref('')
const offset = ref(0)
const totalBytes = ref(0)
const hasMore = ref(false)
const mode = ref<'rendered' | 'source'>('rendered')
const tocOpen = ref(false)
const renderedContainer = ref<HTMLElement | null>(null)

let abortCtrl: AbortController | null = null

const rendered = computed<{ html: string; toc: TocHeading[] }>(() => {
  if (!source.value) return { html: '', toc: [] }
  return renderSafeMarkdown(source.value)
})

async function renderMermaidDiagrams() {
  if (mode.value !== 'rendered' || !renderedContainer.value) return
  const blocks = renderedContainer.value.querySelectorAll<HTMLElement>('.mermaid-block[data-mermaid]')
  if (blocks.length === 0) return
  try {
    const mermaidMod = await import('mermaid')
    const mermaid = mermaidMod.default
    mermaid.initialize({ startOnLoad: false, securityLevel: 'strict' })
    for (let i = 0; i < blocks.length; i++) {
      const el = blocks[i]!
      const raw = decodeURIComponent(el.dataset.mermaid || '')
      if (!raw) continue
      try {
        const { svg } = await mermaid.render(`m-mermaid-${Date.now()}-${i}`, raw)
        el.innerHTML = svg
      } catch {
        // keep code fallback
      }
    }
  } catch {
    // mermaid optional fallback
  }
}

async function fetchChunk(reset: boolean) {
  if (reset) {
    abortCtrl?.abort()
    abortCtrl = new AbortController()
    loading.value = true
    errorText.value = ''
    binaryDetected.value = false
    source.value = ''
    offset.value = 0
  } else {
    loadingMore.value = true
  }

  const signal = abortCtrl?.signal
  try {
    const chunk = await mobileDriveApi.textChunk(
      props.node.spaceId,
      props.node.id,
      {
        versionId: props.versionId ?? props.node.currentVersionId ?? undefined,
        offset: offset.value,
        limit: 131_072,
      },
      signal,
    )
    if (signal?.aborted) return
    if (chunk.binaryDetected) {
      binaryDetected.value = true
      hasMore.value = false
      return
    }
    source.value += chunk.content
    offset.value = chunk.offset + chunk.bytesRead
    totalBytes.value = chunk.totalBytes
    hasMore.value = chunk.hasMore
    await nextTick()
    void renderMermaidDiagrams()
  } catch (err) {
    if (signal?.aborted) return
    errorText.value = normalizeErrorMessage(err, '加载 Markdown 失败')
  } finally {
    if (!signal?.aborted) {
      loading.value = false
      loadingMore.value = false
    }
  }
}

watch(
  () => [props.node.spaceId, props.node.id, props.versionId ?? props.node.currentVersionId] as const,
  () => {
    mode.value = 'rendered'
    void fetchChunk(true)
  },
  { immediate: true },
)

watch(mode, async next => {
  if (next === 'rendered') {
    await nextTick()
    void renderMermaidDiagrams()
  }
})

onBeforeUnmount(() => {
  abortCtrl?.abort()
  abortCtrl = null
})

function jumpToHeading(id: string) {
  tocOpen.value = false
  const el = renderedContainer.value?.querySelector<HTMLElement>(`#${CSS.escape(id)}`)
  el?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}
</script>

<template>
  <div class="flex-1 min-h-0 flex flex-col bg-background text-foreground">
    <!-- 二级功能栏：渲染/源码切换 + 目录大纲 -->
    <div class="flex items-center justify-between gap-2 border-b border-border bg-card/80 px-3.5 py-2 shrink-0">
      <div class="inline-flex rounded-lg bg-muted p-0.5 text-xs">
        <button
          type="button"
          class="inline-flex items-center gap-1 rounded-md px-2.5 py-1 font-medium"
          :class="mode === 'rendered' ? 'bg-background text-foreground shadow-xs' : 'text-muted-foreground'"
          @click="mode = 'rendered'"
        >
          <FileText :size="13" />
          <span>渲染</span>
        </button>
        <button
          type="button"
          class="inline-flex items-center gap-1 rounded-md px-2.5 py-1 font-medium"
          :class="mode === 'source' ? 'bg-background text-foreground shadow-xs' : 'text-muted-foreground'"
          @click="mode = 'source'"
        >
          <Code2 :size="13" />
          <span>源码</span>
        </button>
      </div>

      <button
        v-if="mode === 'rendered' && rendered.toc.length > 0"
        type="button"
        class="inline-flex items-center gap-1 rounded-lg border border-border px-2.5 py-1 text-xs text-foreground active:bg-accent"
        @click="tocOpen = true"
      >
        <ListTree :size="13" />
        <span>目录 ({{ rendered.toc.length }})</span>
      </button>
    </div>

    <div class="flex-1 min-h-0 overflow-y-auto px-4 py-4 pb-24" @click="emit('toggle-chrome')">
      <div v-if="loading" class="py-16 text-center text-sm text-muted-foreground">正在加载文档…</div>
      <ErrorState v-else-if="errorText" :message="errorText" @retry="fetchChunk(true)" />
      <div v-else-if="binaryDetected" class="py-16 text-center space-y-3" @click.stop>
        <p class="text-sm font-medium text-foreground">检测到二进制内容，无法作为 Markdown 文本预览</p>
        <VanButton size="small" round type="primary" @click="emit('download')">下载文件</VanButton>
      </div>
      <template v-else>
        <!-- eslint-disable-next-line vue/no-v-html -->
        <article
          v-if="mode === 'rendered'"
          ref="renderedContainer"
          class="prose prose-sm dark:prose-invert max-w-none break-words [&_pre]:overflow-x-auto [&_table]:block [&_table]:overflow-x-auto"
          v-html="rendered.html"
        />
        <pre
          v-else
          class="text-xs font-mono leading-relaxed whitespace-pre-wrap break-all text-foreground"
        >{{ source }}</pre>

        <div v-if="hasMore" class="mt-6 text-center" @click.stop>
          <VanButton
            size="small"
            round
            plain
            type="primary"
            :loading="loadingMore"
            @click="fetchChunk(false)"
          >
            加载更多（已读 {{ formatBytes(offset) }} / {{ formatBytes(totalBytes) }}）
          </VanButton>
        </div>
      </template>
    </div>

    <BottomSheet v-model:open="tocOpen" title="文档目录">
      <div class="space-y-1 pb-2">
        <button
          v-for="item in rendered.toc"
          :key="item.id"
          type="button"
          class="w-full text-left rounded-lg px-2.5 py-2 text-sm text-foreground active:bg-accent truncate"
          :style="{ paddingLeft: `${Math.max(0, item.level - 1) * 14 + 10}px` }"
          @click="jumpToHeading(item.id)"
        >
          {{ item.text }}
        </button>
      </div>
    </BottomSheet>
  </div>
</template>
