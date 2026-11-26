<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { Clock, Eye, File, FolderOpen, RefreshCw } from '@lucide/vue'
import { Button } from '@repo/ui/button'
import { opsApi, type RecentAccessItem } from '@/api/ops'

const emit = defineEmits<{
  locate: [item: RecentAccessItem]
  preview: [item: RecentAccessItem]
}>()

const loading = ref(true)
const error = ref('')
const items = ref<RecentAccessItem[]>([])

function formatSize(bytes: number) {
  if (!bytes) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB']
  const i = Math.min(3, Math.floor(Math.log(bytes) / Math.log(1024)))
  return `${(bytes / 1024 ** i).toFixed(i ? 1 : 0)} ${units[i]}`
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    items.value = await opsApi.listRecent()
  } catch (e) {
    error.value = e instanceof Error ? e.message : '加载最近使用记录失败'
  } finally {
    loading.value = false
  }
}

onMounted(() => { void load() })
</script>

<template>
  <div class="flex h-full min-h-0 flex-1 flex-col gap-4">
    <header class="flex items-center justify-between gap-3">
      <div>
        <h1 class="flex items-center gap-2 text-xl font-semibold"><Clock :size="20" /> 最近使用</h1>
        <p class="text-xs text-muted-foreground">快速返回最近打开或预览过的文件（私密空间与隐藏项不会出现在此处）。</p>
      </div>
      <Button variant="outline" size="sm" :disabled="loading" @click="load"><RefreshCw :size="14" /> 刷新</Button>
    </header>

    <p v-if="error" role="alert" class="rounded-md bg-destructive/10 px-3 py-2 text-xs text-destructive">{{ error }}</p>
    <div v-if="loading" class="py-12 text-center text-sm text-muted-foreground">正在加载最近记录…</div>
    <div v-else-if="!items.length" class="rounded-xl border border-dashed border-border p-10 text-center text-sm text-muted-foreground">
      暂无最近使用记录
    </div>
    <div v-else class="divide-y divide-border rounded-xl border border-border bg-card">
      <div
        v-for="item in items"
        :key="item.id"
        class="flex flex-wrap items-center justify-between gap-3 px-4 py-3 hover:bg-muted/30"
        @dblclick="emit('preview', item)"
      >
        <div class="min-w-0">
          <div class="flex items-center gap-2 font-medium">
            <File :size="16" class="shrink-0 text-primary" />
            <span class="truncate">{{ item.name }}</span>
            <span class="rounded bg-muted px-1.5 py-0.5 text-[11px] text-muted-foreground">{{ item.spaceName }}</span>
          </div>
          <div class="text-xs text-muted-foreground">
            {{ formatSize(item.size) }} · 最近访问：{{ new Date(item.accessedAt).toLocaleString('zh-CN') }}
          </div>
        </div>
        <div class="flex items-center gap-2">
          <Button variant="outline" size="sm" @click="emit('preview', item)"><Eye :size="13" /> 预览</Button>
          <Button variant="outline" size="sm" @click="emit('locate', item)"><FolderOpen :size="13" /> 定位目录</Button>
        </div>
      </div>
    </div>
  </div>
</template>
