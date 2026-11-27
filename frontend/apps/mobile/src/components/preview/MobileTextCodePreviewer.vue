<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { Button as VanButton } from 'vant'
import { Minus, Plus, Search, WrapText, X } from '@lucide/vue'
import { normalizeErrorMessage } from '@repo/drive-core/errors'
import { formatBytes } from '@repo/drive-core/format'
import { detectCodeLanguage } from '@repo/drive-core/preview-registry'
import { mobileDriveApi, type DriveNode } from '@/api/drive'
import { escapeHtml, highlightCodeSnippet } from '@/lib/markdown'
import { ErrorState } from '@/components/common'

const props = defineProps<{
  node: DriveNode
  versionId?: string
  isCode?: boolean
}>()

const emit = defineEmits<{
  'toggle-chrome': []
  download: []
}>()

const loading = ref(true)
const loadingMore = ref(false)
const errorText = ref('')
const binaryDetected = ref(false)
const content = ref('')
const offset = ref(0)
const totalBytes = ref(0)
const hasMore = ref(false)

// Viewer controls
const wordWrap = ref(true)
const fontSize = ref(13)
const searchOpen = ref(false)
const keyword = ref('')

let abortCtrl: AbortController | null = null

const language = computed(() => (props.isCode ? detectCodeLanguage(props.node.name) : 'plaintext'))

const matchCount = computed(() => {
  const q = keyword.value.trim()
  if (!q || !content.value) return 0
  const escaped = q.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
  return (content.value.match(new RegExp(escaped, 'gi')) ?? []).length
})

const highlightedHtml = computed(() => {
  const q = keyword.value.trim()
  if (q) {
    const safe = escapeHtml(content.value)
    const escapedQ = escapeHtml(q).replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
    return safe.replace(
      new RegExp(escapedQ, 'gi'),
      m => `<mark class="bg-amber-300/80 dark:bg-amber-500/50 text-foreground rounded px-0.5">${m}</mark>`,
    )
  }
  if (props.isCode && content.value.length <= 200_000) {
    return highlightCodeSnippet(content.value, language.value)
  }
  return escapeHtml(content.value)
})

async function fetchChunk(reset: boolean) {
  if (reset) {
    abortCtrl?.abort()
    abortCtrl = new AbortController()
    loading.value = true
    errorText.value = ''
    binaryDetected.value = false
    content.value = ''
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
    content.value += chunk.content
    offset.value = chunk.offset + chunk.bytesRead
    totalBytes.value = chunk.totalBytes
    hasMore.value = chunk.hasMore
  } catch (err) {
    if (signal?.aborted) return
    errorText.value = normalizeErrorMessage(err, '加载文本内容失败')
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
    keyword.value = ''
    searchOpen.value = false
    void fetchChunk(true)
  },
  { immediate: true },
)

onBeforeUnmount(() => {
  abortCtrl?.abort()
  abortCtrl = null
})

function decreaseFont() {
  fontSize.value = Math.max(11, fontSize.value - 1)
}

function increaseFont() {
  fontSize.value = Math.min(18, fontSize.value + 1)
}
</script>

<template>
  <div class="flex-1 min-h-0 flex flex-col bg-background text-foreground">
    <!-- 文本/代码工具条 -->
    <div class="flex items-center justify-between gap-2 border-b border-border bg-card/80 px-3 py-1.5 shrink-0 text-xs">
      <div class="flex items-center gap-1.5">
        <span class="rounded bg-muted px-2 py-0.5 text-[11px] font-mono text-muted-foreground">
          {{ language }}
        </span>
        <button
          type="button"
          class="inline-flex items-center gap-1 rounded-lg px-2 py-1"
          :class="wordWrap ? 'bg-primary/10 text-primary font-medium' : 'text-muted-foreground'"
          @click="wordWrap = !wordWrap"
        >
          <WrapText :size="13" />
          <span>换行</span>
        </button>
      </div>

      <div class="flex items-center gap-1">
        <button
          type="button"
          class="inline-flex h-7 w-7 items-center justify-center rounded-lg text-muted-foreground active:bg-accent"
          aria-label="减小字号"
          @click="decreaseFont"
        >
          <Minus :size="13" />
        </button>
        <span class="w-6 text-center text-[11px] text-muted-foreground">{{ fontSize }}</span>
        <button
          type="button"
          class="inline-flex h-7 w-7 items-center justify-center rounded-lg text-muted-foreground active:bg-accent"
          aria-label="增大字号"
          @click="increaseFont"
        >
          <Plus :size="13" />
        </button>
        <button
          type="button"
          class="inline-flex h-7 w-7 items-center justify-center rounded-lg active:bg-accent"
          :class="searchOpen ? 'text-primary bg-primary/10' : 'text-muted-foreground'"
          aria-label="页内搜索"
          @click="searchOpen = !searchOpen"
        >
          <Search :size="14" />
        </button>
      </div>
    </div>

    <!-- 页内关键词搜索栏 -->
    <div
      v-if="searchOpen"
      class="flex items-center gap-2 border-b border-border bg-muted/40 px-3 py-1.5 shrink-0"
    >
      <input
        v-model="keyword"
        type="text"
        placeholder="输入页内搜索关键词…"
        class="flex-1 min-w-0 rounded-lg border border-input bg-background px-2.5 py-1 text-xs text-foreground focus:outline-none focus:ring-2 focus:ring-primary/40"
      />
      <span v-if="keyword.trim()" class="text-[11px] text-muted-foreground shrink-0">
        {{ matchCount }} 处
      </span>
      <button
        type="button"
        class="inline-flex h-6 w-6 items-center justify-center rounded-full text-muted-foreground"
        @click="keyword = ''; searchOpen = false"
      >
        <X :size="14" />
      </button>
    </div>

    <!-- 正文内容区 -->
    <div class="flex-1 min-h-0 overflow-auto p-3.5 pb-24" @click="emit('toggle-chrome')">
      <div v-if="loading" class="py-16 text-center text-sm text-muted-foreground">正在加载文本…</div>
      <ErrorState v-else-if="errorText" :message="errorText" @retry="fetchChunk(true)" />
      <div v-else-if="binaryDetected" class="py-16 text-center space-y-3" @click.stop>
        <p class="text-sm font-medium text-foreground">该文件包含二进制数据，无法以纯文本展示</p>
        <p class="text-xs text-muted-foreground">请直接下载到本地使用对应应用打开</p>
        <VanButton size="small" round type="primary" @click="emit('download')">下载文件</VanButton>
      </div>
      <template v-else>
        <!-- eslint-disable-next-line vue/no-v-html -->
        <pre
          class="font-mono leading-relaxed text-foreground"
          :class="wordWrap ? 'whitespace-pre-wrap break-all' : 'whitespace-pre'"
          :style="{ fontSize: `${fontSize}px` }"
          v-html="highlightedHtml"
        />

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
  </div>
</template>
