<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowLeft, FolderOpen, Search, Trash2, X } from '@lucide/vue'
import { CATEGORY_LABELS } from '@repo/drive-core/categories'
import { normalizeErrorMessage } from '@repo/drive-core/errors'
import { formatBytes, formatRelativeTime } from '@repo/drive-core/format'
import { mobileDriveApi, type SearchHit } from '@/api/drive'
import {
  clearSearchHistory,
  loadSearchHistory,
  pushSearchHistory,
  removeSearchHistoryItem,
  tokenizeHighlight,
} from '@/lib/search-history'
import { fetchSpaces, visibleSpaces } from '@/stores/spaces'
import { EmptyState, ErrorState, InfiniteList, SkeletonList } from '@/components/common'
import NodeThumb from '@/components/drive/NodeThumb.vue'

const PAGE_SIZE = 30
const router = useRouter()

const inputRef = ref<HTMLInputElement | null>(null)
const query = ref('')
const selectedSpaceId = ref('')
const selectedCategory = ref('')
const extension = ref('')
const includeContent = ref(true)

const history = ref<string[]>(loadSearchHistory())
const hits = ref<SearchHit[]>([])
const total = ref(0)
const page = ref(1)
const loading = ref(false)
const loadingMore = ref(false)
const hasMore = ref(false)
const error = ref('')
const searchedOnce = ref(false)

let debounceTimer: ReturnType<typeof setTimeout> | null = null
let abortCtrl: AbortController | null = null
let searchSeq = 0

const categoryOptions = computed(() => [
  { key: '', label: '全部分类' },
  ...Object.entries(CATEGORY_LABELS).map(([key, label]) => ({ key, label })),
])

const hasFilterCriteria = computed(
  () => Boolean(query.value.trim() || selectedCategory.value || extension.value.trim()),
)

async function executeSearch(reset: boolean) {
  if (!hasFilterCriteria.value) {
    abortCtrl?.abort()
    hits.value = []
    total.value = 0
    hasMore.value = false
    searchedOnce.value = false
    error.value = ''
    return
  }

  if (reset) {
    abortCtrl?.abort()
    abortCtrl = new AbortController()
    searchSeq += 1
    page.value = 1
    loading.value = true
    error.value = ''
    if (query.value.trim()) {
      history.value = pushSearchHistory(query.value.trim())
    }
  } else {
    loadingMore.value = true
  }

  const seq = searchSeq
  const signal = abortCtrl?.signal
  try {
    const res = await mobileDriveApi.search(
      {
        query: query.value.trim() || undefined,
        spaceId: selectedSpaceId.value || undefined,
        category: selectedCategory.value || undefined,
        extension: extension.value.trim().replace(/^\./, '') || undefined,
        includeContent: includeContent.value,
        page: page.value,
        pageSize: PAGE_SIZE,
      },
      signal,
    )
    if (signal?.aborted || seq !== searchSeq) return
    hits.value = reset ? res.items : [...hits.value, ...res.items]
    total.value = res.total
    hasMore.value = res.hasMore
    page.value += 1
    searchedOnce.value = true
  } catch (err) {
    if (signal?.aborted || seq !== searchSeq) return
    error.value = normalizeErrorMessage(err, '搜索失败，请稍后重试')
  } finally {
    if (!signal?.aborted && seq === searchSeq) {
      loading.value = false
      loadingMore.value = false
    }
  }
}

function scheduleSearch() {
  if (debounceTimer) clearTimeout(debounceTimer)
  debounceTimer = setTimeout(() => {
    void executeSearch(true)
  }, 300)
}

watch([query, selectedSpaceId, selectedCategory, extension, includeContent], () => {
  scheduleSearch()
})

onMounted(async () => {
  await fetchSpaces()
  await nextTick()
  inputRef.value?.focus()
})

onBeforeUnmount(() => {
  if (debounceTimer) clearTimeout(debounceTimer)
  abortCtrl?.abort()
})

function applyHistory(item: string) {
  query.value = item
  if (debounceTimer) clearTimeout(debounceTimer)
  void executeSearch(true)
}

function deleteHistory(item: string) {
  history.value = removeSearchHistoryItem(item)
}

function wipeHistory() {
  history.value = clearSearchHistory()
}

function locateFolder(hit: SearchHit) {
  if (hit.nodeType === 'FOLDER') {
    void router.push(`/files/${hit.spaceId}/folders/${hit.id}`)
  } else if (hit.parentId && hit.parentId !== '0') {
    void router.push(`/files/${hit.spaceId}/folders/${hit.parentId}`)
  } else {
    void router.push(`/files/${hit.spaceId}`)
  }
}

function openHit(hit: SearchHit) {
  if (hit.nodeType === 'FOLDER') {
    void router.push(`/files/${hit.spaceId}/folders/${hit.id}`)
    return
  }
  const base =
    hit.parentId && hit.parentId !== '0'
      ? `/files/${hit.spaceId}/folders/${hit.parentId}`
      : `/files/${hit.spaceId}`
  void router.push({ path: base, query: { preview: hit.id } })
}

function matchBadge(source: SearchHit['matchSource']): string {
  if (source === 'CONTENT') return '正文命中'
  if (source === 'BOTH') return '名称+正文'
  return '文件名'
}
</script>

<template>
  <div class="min-h-dvh flex flex-col bg-background text-foreground">
    <!-- 顶部搜索栏 -->
    <header class="sticky top-0 z-20 border-b border-border bg-background/95 backdrop-blur px-3 py-2.5 safe-top space-y-2">
      <form class="flex items-center gap-2" @submit.prevent="executeSearch(true)">
        <button
          type="button"
          class="inline-flex h-9 w-9 items-center justify-center rounded-full text-foreground active:bg-accent shrink-0"
          aria-label="返回"
          @click="router.back()"
        >
          <ArrowLeft :size="20" />
        </button>

        <div class="flex-1 flex items-center gap-2 rounded-full bg-muted px-3.5 py-2">
          <Search :size="16" class="text-muted-foreground shrink-0" />
          <input
            ref="inputRef"
            v-model="query"
            type="search"
            placeholder="搜索文件名或文档正文…"
            class="flex-1 min-w-0 bg-transparent text-sm text-foreground placeholder:text-muted-foreground focus:outline-none"
          />
          <button
            v-if="query"
            type="button"
            class="text-muted-foreground"
            aria-label="清空输入"
            @click="query = ''"
          >
            <X :size="15" />
          </button>
        </div>
      </form>

      <!-- 筛选胶囊条 -->
      <div class="flex items-center gap-1.5 overflow-x-auto no-scrollbar pb-0.5 text-xs">
        <button
          type="button"
          class="shrink-0 rounded-full px-2.5 py-1 border"
          :class="
            includeContent
              ? 'border-primary bg-primary/10 text-primary font-medium'
              : 'border-border bg-background text-muted-foreground'
          "
          @click="includeContent = !includeContent"
        >
          {{ includeContent ? '含正文检索' : '仅搜文件名' }}
        </button>

        <select
          v-model="selectedSpaceId"
          class="shrink-0 rounded-full border border-border bg-background px-2.5 py-1 text-xs text-foreground"
        >
          <option value="">全部空间</option>
          <option v-for="s in visibleSpaces" :key="s.id" :value="s.id">{{ s.name }}</option>
        </select>

        <select
          v-model="selectedCategory"
          class="shrink-0 rounded-full border border-border bg-background px-2.5 py-1 text-xs text-foreground"
        >
          <option v-for="c in categoryOptions" :key="c.key" :value="c.key">{{ c.label }}</option>
        </select>

        <input
          v-model="extension"
          type="text"
          maxlength="12"
          placeholder="后缀如 pdf"
          class="shrink-0 w-24 rounded-full border border-border bg-background px-2.5 py-1 text-xs text-foreground placeholder:text-muted-foreground focus:outline-none"
        />
      </div>
    </header>

    <main class="flex-1 min-h-0 overflow-y-auto">
      <!-- 搜索历史 -->
      <div v-if="!hasFilterCriteria" class="px-4 py-4 space-y-3">
        <div v-if="history.length > 0" class="space-y-2.5">
          <div class="flex items-center justify-between">
            <span class="text-xs font-medium text-muted-foreground">最近搜索</span>
            <button
              type="button"
              class="inline-flex items-center gap-1 text-xs text-muted-foreground active:text-destructive"
              @click="wipeHistory"
            >
              <Trash2 :size="12" />
              <span>清空</span>
            </button>
          </div>
          <div class="flex flex-wrap gap-2">
            <span
              v-for="item in history"
              :key="item"
              class="inline-flex items-center gap-1 rounded-full bg-muted px-3 py-1.5 text-xs text-foreground"
            >
              <button type="button" @click="applyHistory(item)">{{ item }}</button>
              <button
                type="button"
                class="text-muted-foreground hover:text-foreground"
                aria-label="删除历史"
                @click="deleteHistory(item)"
              >
                <X :size="12" />
              </button>
            </span>
          </div>
        </div>

        <EmptyState
          v-else
          title="全局快速检索"
          description="支持按文件名、正文内容、空间、分类或扩展名组合搜索（不含私密与隐藏项）"
        />
      </div>

      <!-- 结果区 -->
      <SkeletonList v-else-if="loading" :count="6" />
      <ErrorState v-else-if="error" :message="error" @retry="executeSearch(true)" />
      <EmptyState
        v-else-if="searchedOnce && hits.length === 0"
        title="未找到匹配结果"
        description="可尝试更换关键词、放宽分类或开启正文检索"
      />

      <InfiniteList
        v-else
        v-model:loading="loadingMore"
        :finished="!hasMore"
        :finished-text="total > 0 ? `共找到 ${total} 条结果` : ''"
        @load="executeSearch(false)"
      >
        <div class="divide-y divide-border px-3.5 pb-12">
          <div
            v-for="hit in hits"
            :key="`${hit.spaceId}:${hit.id}`"
            class="py-3 space-y-1.5 active:bg-accent/40 rounded-lg"
            @click="openHit(hit)"
          >
            <div class="flex items-start gap-3">
              <NodeThumb :node="hit" variant="list" />

              <div class="flex-1 min-w-0">
                <p class="text-sm font-medium text-foreground break-all line-clamp-2">
                  <template v-for="(seg, idx) in tokenizeHighlight(hit.name, query)" :key="idx">
                    <mark
                      v-if="seg.hit"
                      class="bg-amber-300/75 dark:bg-amber-500/45 text-foreground rounded px-0.5"
                    >{{ seg.text }}</mark>
                    <span v-else>{{ seg.text }}</span>
                  </template>
                </p>

                <div class="flex items-center flex-wrap gap-1.5 mt-1 text-[11px] text-muted-foreground">
                  <span class="rounded bg-primary/10 px-1.5 py-0.5 text-[10px] text-primary font-medium">
                    {{ matchBadge(hit.matchSource) }}
                  </span>
                  <span>{{ hit.spaceName }}</span>
                  <span>·</span>
                  <span>{{ hit.nodeType === 'FOLDER' ? '文件夹' : formatBytes(hit.size) }}</span>
                  <span>·</span>
                  <span>{{ formatRelativeTime(hit.updatedAt) }}</span>
                </div>
              </div>

              <button
                type="button"
                class="inline-flex h-8 w-8 items-center justify-center rounded-full text-muted-foreground active:bg-accent shrink-0"
                aria-label="定位目录"
                title="定位所在目录"
                @click.stop="locateFolder(hit)"
              >
                <FolderOpen :size="16" />
              </button>
            </div>

            <p
              v-if="hit.snippet"
              class="ml-12 rounded-lg bg-muted/50 px-2.5 py-1.5 text-xs text-muted-foreground line-clamp-2 break-words"
            >
              <template v-for="(seg, idx) in tokenizeHighlight(hit.snippet, query)" :key="idx">
                <mark
                  v-if="seg.hit"
                  class="bg-amber-300/75 dark:bg-amber-500/45 text-foreground rounded px-0.5"
                >{{ seg.text }}</mark>
                <span v-else>{{ seg.text }}</span>
              </template>
            </p>
          </div>
        </div>
      </InfiniteList>
    </main>
  </div>
</template>
