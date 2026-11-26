<script setup lang="ts">
import { ref, watch } from 'vue'
import { Eye, File, Folder, FolderOpen, Search } from '@lucide/vue'
import { Button } from '@repo/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from '@repo/ui/dialog'
import { Input } from '@repo/ui/input'
import type { Space } from '@/api/drive'
import { searchApi, type SearchHit } from '@/api/search'

const props = defineProps<{ spaces: Space[] }>()
const emit = defineEmits<{
  close: []
  locate: [hit: SearchHit]
  preview: [hit: SearchHit]
}>()

const query = ref('')
const spaceId = ref('')
const category = ref('')
const extension = ref('')
const includeContent = ref(true)
const loading = ref(false)
const error = ref('')
const total = ref(0)
const items = ref<SearchHit[]>([])

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

async function runSearch() {
  if (!query.value.trim() && !category.value && !extension.value.trim()) {
    items.value = []
    total.value = 0
    return
  }
  loading.value = true
  error.value = ''
  try {
    const res = await searchApi.search({
      query: query.value.trim() || undefined,
      spaceId: spaceId.value || undefined,
      category: category.value || undefined,
      extension: extension.value.trim() || undefined,
      includeContent: includeContent.value,
      page: 1,
      pageSize: 30,
    })
    items.value = res.items
    total.value = res.total
  } catch (e) {
    error.value = e instanceof Error ? e.message : '搜索失败'
  } finally {
    loading.value = false
  }
}

watch([query, spaceId, category, extension, includeContent], () => {
  if (debounceTimer) clearTimeout(debounceTimer)
  debounceTimer = setTimeout(() => { void runSearch() }, 250)
})
</script>

<template>
  <Dialog :open="true" @update:open="v => { if (!v) emit('close') }">
    <DialogContent class="!max-w-3xl !p-0 overflow-hidden">
      <DialogHeader class="border-b border-border px-5 pt-4 pb-3">
        <DialogTitle class="flex items-center gap-2 text-base"><Search :size="17" /> 全局跨空间检索</DialogTitle>
        <DialogDescription class="text-xs">按文件名、扩展名、文件分类与文档正文跨空间搜索，自动受权限控制。</DialogDescription>
      </DialogHeader>

      <div class="space-y-3 border-b border-border bg-muted/20 px-5 py-3">
        <div class="flex flex-wrap items-center gap-2">
          <Input v-model="query" class="min-w-[220px] flex-1" placeholder="输入文件名或文档正文关键词…" autofocus />
          <select v-model="spaceId" class="h-9 rounded-md border border-input bg-background px-2.5 text-xs">
            <option value="">全部空间</option>
            <option v-for="s in spaces" :key="s.id" :value="s.id">{{ s.name }}</option>
          </select>
          <Input v-model="extension" class="w-24 text-xs" placeholder="后缀 如 md" />
        </div>
        <div class="flex flex-wrap items-center justify-between gap-2">
          <div class="flex flex-wrap items-center gap-1">
            <Button
              v-for="c in CATEGORIES"
              :key="c.key"
              variant="outline"
              size="sm"
              class="h-7 px-2 text-xs"
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

      <div class="max-h-[55vh] min-h-[220px] overflow-y-auto p-3">
        <p v-if="error" role="alert" class="p-4 text-center text-xs text-destructive">{{ error }}</p>
        <p v-else-if="loading" class="p-8 text-center text-xs text-muted-foreground">正在检索…</p>
        <p v-else-if="!items.length" class="p-10 text-center text-xs text-muted-foreground">
          {{ query.trim() || category || extension.trim() ? '未找到匹配的有权限资源' : '输入关键词或选择分类开始跨空间检索' }}
        </p>
        <div v-else class="divide-y divide-border rounded-lg border border-border bg-card">
          <div class="bg-muted/40 px-3 py-1.5 text-xs text-muted-foreground">共匹配 {{ total }} 项</div>
          <div v-for="hit in items" :key="hit.id" class="flex items-start justify-between gap-3 px-3.5 py-2.5 hover:bg-muted/30">
            <div class="min-w-0 flex-1 space-y-1">
              <div class="flex items-center gap-2">
                <Folder v-if="hit.nodeType === 'FOLDER'" :size="15" class="shrink-0 text-amber-500" />
                <File v-else :size="15" class="shrink-0 text-primary" />
                <span class="truncate text-sm font-medium">
                  <template v-for="(seg, idx) in highlightParts(hit.name, query)" :key="idx">
                    <mark v-if="seg.hit" class="rounded bg-amber-300/60 px-0.5 text-foreground dark:bg-amber-500/40">{{ seg.text }}</mark>
                    <template v-else>{{ seg.text }}</template>
                  </template>
                </span>
                <span class="shrink-0 rounded bg-muted px-1.5 py-0.5 text-[10px] text-muted-foreground">{{ hit.spaceName }}</span>
                <span class="shrink-0 rounded bg-primary/10 px-1.5 py-0.5 text-[10px] text-primary">{{ matchBadge(hit) }}</span>
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
              <Button v-if="hit.nodeType === 'FILE'" variant="outline" size="sm" @click="emit('preview', hit)">
                <Eye :size="13" /> 预览
              </Button>
              <Button variant="outline" size="sm" @click="emit('locate', hit)">
                <FolderOpen :size="13" /> 定位目录
              </Button>
            </div>
          </div>
        </div>
      </div>
    </DialogContent>
  </Dialog>
</template>
