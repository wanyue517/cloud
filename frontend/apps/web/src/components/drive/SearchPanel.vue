<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { Eye, File, Folder, FolderOpen, Globe, Search } from '@lucide/vue'
import { Button } from '@repo/ui/button'
import { Input } from '@repo/ui/input'
import type { Space } from '@/api/drive'
import { searchApi, type SearchHit } from '@/api/search'

const props = defineProps<{
  spaces: Space[]
  /** 进入搜索页时所在空间，默认按此空间检索 */
  currentSpaceId: string
  initialQuery?: string
  initialScope?: 'space' | 'global'
}>()
const emit = defineEmits<{
  locate: [hit: SearchHit]
  preview: [hit: SearchHit]
}>()

const query = ref(props.initialQuery || '')
const scope = ref<'space' | 'global'>(props.initialScope === 'global' || !props.currentSpaceId ? 'global' : 'space')
const scopeSpaceId = ref(props.currentSpaceId || props.spaces[0]?.id || '')
const category = ref('')
const extension = ref('')
const includeContent = ref(true)
const loading = ref(false)
const error = ref('')
const total = ref(0)
const page = ref(1)
const PAGE_SIZE = 50
const items = ref<SearchHit[]>([])
const inputRef = ref<InstanceType<typeof Input>>()
let searchGeneration = 0

const scopeSpace = computed(() => props.spaces.find(s => s.id === scopeSpaceId.value))

const CATEGORIES = [
  { key: '', label: '全部类型' },
  { key: 'CODE', label: '代码/文本' },
  { key: 'PDF', label: 'PDF' },
  { key: 'WORD', label: 'Word' },
  { key: 'EXCEL', label: '表格' },
  { key: 'PPT', label: '演示' },
  { key: 'IMAGE', label: '图片' },
  { key: 'VIDEO', label: '视频' },
  { key: 'AUDIO', label: '音频' },
  { key: 'ARCHIVE', label: '压缩包' },
]

let debounceTimer: ReturnType<typeof setTimeout> | undefined

function formatSize(bytes: number) {
  if (!bytes) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB']
  const i = Math.min(3, Math.floor(Math.log(bytes) / Math.log(1024)))
  return `${(bytes / 1024 ** i).toFixed(i ? 1 : 0)} ${units[i]}`
}

function highlightParts(text: string, keyword: string): { text: string; hit: boolean }[] {
  const kw = keyword.trim()
  if (!kw || !text) return [{ text, hit: false }]
  const escaped = kw.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
  const parts = text.split(new RegExp(`(${escaped})`, 'gi'))
  return parts.filter(Boolean).map(p => ({ text: p, hit: p.toLowerCase() === kw.toLowerCase() }))
}

function matchBadge(hit: SearchHit): string {
  if (hit.nodeType === 'FOLDER') return '文件夹'
  if (hit.matchSource === 'BOTH') return '名称+正文命中'
  if (hit.matchSource === 'CONTENT') return '正文命中'
  return '文件名命中'
}

async function runSearch(targetPage = page.value) {
  if (!query.value.trim() && !category.value && !extension.value.trim()) {
    items.value = []
    total.value = 0
    return
  }
  const gen = ++searchGeneration
  loading.value = true
  error.value = ''
  try {
    const res = await searchApi.search({
      query: query.value.trim() || undefined,
      spaceId: scope.value === 'space' ? scopeSpaceId.value || undefined : undefined,
      category: category.value || undefined,
      extension: extension.value.trim() || undefined,
      includeContent: includeContent.value,
      page: targetPage,
      pageSize: PAGE_SIZE,
    })
    if (gen !== searchGeneration) return
    items.value = res.items
    total.value = res.total
    page.value = targetPage
  } catch (e) {
    if (gen !== searchGeneration) return
    error.value = e instanceof Error ? e.message : '搜索失败'
  } finally {
    if (gen === searchGeneration) loading.value = false
  }
}

watch([query, scope, scopeSpaceId, category, extension, includeContent], () => {
  page.value = 1
  if (debounceTimer) clearTimeout(debounceTimer)
  debounceTimer = setTimeout(() => { void runSearch(1) }, 250)
})
watch(() => props.currentSpaceId, id => { if (id) scopeSpaceId.value = id })

onMounted(async () => {
  await nextTick()
  ;(inputRef.value?.$el as HTMLInputElement | undefined)?.focus?.()
  if (query.value.trim()) void runSearch()
})
</script>

<template>
  <div class="search-page space-y-4">
    <div class="space-y-3 rounded-xl border border-border bg-card p-4 shadow-xs">
      <div class="flex flex-wrap items-center gap-2">
        <div class="relative min-w-[240px] flex-1">
          <Search :size="16" class="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-muted-foreground" />
          <Input
            ref="inputRef"
            v-model="query"
            class="pl-9"
            :placeholder="scope === 'space' ? `在「${scopeSpace?.name || '当前空间'}」中搜索文件名或正文…` : '全局跨空间搜索文件名或正文…'"
            aria-label="搜索关键词"
            autofocus
          />
        </div>
        <div class="inline-flex shrink-0 rounded-md border border-input p-0.5" role="tablist" aria-label="搜索范围">
          <Button
            type="button"
            size="sm"
            :variant="scope === 'space' ? 'default' : 'ghost'"
            class="h-8 whitespace-nowrap px-3 text-xs"
            role="tab"
            :aria-selected="scope === 'space'"
            :disabled="!spaces.length"
            @click="scope = 'space'"
          >
            <Folder :size="13" /> 本空间
          </Button>
          <Button
            type="button"
            size="sm"
            :variant="scope === 'global' ? 'default' : 'ghost'"
            class="h-8 whitespace-nowrap px-3 text-xs"
            role="tab"
            :aria-selected="scope === 'global'"
            @click="scope = 'global'"
          >
            <Globe :size="13" /> 全局检索
          </Button>
        </div>
        <select
          v-if="scope === 'space'"
          v-model="scopeSpaceId"
          class="h-9 shrink-0 rounded-md border border-input bg-background px-2.5 text-xs"
          aria-label="选择搜索空间"
        >
          <option v-for="s in spaces" :key="s.id" :value="s.id">{{ s.name }}</option>
        </select>
        <Input v-model="extension" class="w-24 shrink-0 text-xs" placeholder="后缀 如 md" aria-label="文件后缀" />
      </div>
      <div class="flex flex-wrap items-center justify-between gap-2">
        <div class="flex flex-wrap items-center gap-1">
          <Button
            v-for="c in CATEGORIES"
            :key="c.key"
            variant="outline"
            size="sm"
            class="h-7 whitespace-nowrap px-2 text-xs"
            :class="category === c.key ? 'border-primary bg-primary/10 text-primary' : ''"
            @click="category = c.key"
          >
            {{ c.label }}
          </Button>
        </div>
        <label class="flex items-center gap-1.5 text-xs text-muted-foreground">
          <input v-model="includeContent" type="checkbox" />
          <span>含正文全文</span>
        </label>
      </div>
    </div>

    <div class="min-h-[240px]">
      <p v-if="error" role="alert" class="p-4 text-center text-xs text-destructive">{{ error }}</p>
      <p v-else-if="loading" class="p-8 text-center text-xs text-muted-foreground">正在检索…</p>
      <p v-else-if="!items.length" class="p-10 text-center text-sm text-muted-foreground">
        {{ query.trim() || category || extension.trim()
          ? '未找到匹配的有权限资源'
          : scope === 'space' ? '输入关键词，在当前空间内搜索；也可切换为「全局检索」跨空间查找' : '输入关键词或选择分类开始跨空间检索' }}
      </p>
      <div v-else class="divide-y divide-border rounded-lg border border-border bg-card">
        <div class="bg-muted/40 px-3 py-1.5 text-xs text-muted-foreground">
          {{ scope === 'space' ? `「${scopeSpace?.name || '当前空间'}」内` : '全部空间' }}共匹配 {{ total }} 项
        </div>
        <div v-for="hit in items" :key="hit.id" class="flex items-start justify-between gap-3 px-3.5 py-2.5 hover:bg-muted/30">
          <div class="min-w-0 flex-1 space-y-1">
            <div class="flex min-w-0 items-center gap-2">
              <Folder v-if="hit.nodeType === 'FOLDER'" :size="15" class="shrink-0 text-amber-500" />
              <File v-else :size="15" class="shrink-0 text-primary" />
              <span class="truncate text-sm font-medium">
                <template v-for="(seg, idx) in highlightParts(hit.name, query)" :key="idx">
                  <mark v-if="seg.hit" class="rounded bg-amber-300/60 px-0.5 text-foreground dark:bg-amber-500/40">{{ seg.text }}</mark>
                  <template v-else>{{ seg.text }}</template>
                </template>
              </span>
              <span v-if="scope === 'global'" class="shrink-0 whitespace-nowrap rounded bg-muted px-1.5 py-0.5 text-[10px] text-muted-foreground">{{ hit.spaceName }}</span>
              <span class="shrink-0 whitespace-nowrap rounded bg-primary/10 px-1.5 py-0.5 text-[10px] text-primary">{{ matchBadge(hit) }}</span>
            </div>
            <p v-if="hit.snippet" class="line-clamp-2 text-xs text-muted-foreground">
              <template v-for="(seg, idx) in highlightParts(hit.snippet, query)" :key="idx">
                <mark v-if="seg.hit" class="rounded bg-amber-300/60 px-0.5 text-foreground dark:bg-amber-500/40">{{ seg.text }}</mark>
                <template v-else>{{ seg.text }}</template>
              </template>
            </p>
            <div class="text-[11px] text-muted-foreground">
              {{ hit.nodeType === 'FOLDER' ? '文件夹' : formatSize(hit.size) }} · {{ new Date(hit.updatedAt).toLocaleString('zh-CN') }}
            </div>
          </div>
          <div class="flex shrink-0 items-center gap-1.5">
            <Button v-if="hit.nodeType === 'FILE'" variant="outline" size="sm" class="whitespace-nowrap" @click="emit('preview', hit)">
              <Eye :size="13" /> 预览
            </Button>
            <Button variant="outline" size="sm" class="whitespace-nowrap" @click="emit('locate', hit)">
              <FolderOpen :size="13" /> 定位目录
            </Button>
          </div>
        </div>
        <div v-if="total > PAGE_SIZE" class="flex items-center justify-between bg-muted/20 px-3.5 py-2 text-xs text-muted-foreground">
          <Button variant="outline" size="sm" :disabled="page <= 1 || loading" @click="runSearch(page - 1)">上一页</Button>
          <span>第 {{ page }} / {{ Math.ceil(total / PAGE_SIZE) }} 页（共 {{ total }} 项）</span>
          <Button variant="outline" size="sm" :disabled="page * PAGE_SIZE >= total || loading" @click="runSearch(page + 1)">下一页</Button>
        </div>
      </div>
    </div>
  </div>
</template>
