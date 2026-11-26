<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { RefreshCw } from '@lucide/vue'
import { Button } from '@repo/ui/button'
import { request } from '@repo/session/request'
import { ADMIN_NAV_ITEMS } from '@/layout/navigation'

const stats = ref<Record<string, number>>({})
const loading = ref(false)
const error = ref('')

const metrics = [
  { key: 'users', label: '注册用户' },
  { key: 'spaces', label: '存储空间' },
  { key: 'files', label: '文件总数' },
  { key: 'bytes', label: '物理存储（字节）' },
] as const

function formatBytes(bytes: number) {
  if (!bytes) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB', 'TB']
  const index = Math.min(units.length - 1, Math.floor(Math.log(bytes) / Math.log(1024)))
  return `${(bytes / 1024 ** index).toFixed(index ? 1 : 0)} ${units[index]}`
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    stats.value = await request.get<Record<string, number>>('/v1/admin/stats')
  } catch (e) {
    error.value = e instanceof Error ? e.message : '加载统计失败'
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  void load()
})
</script>

<template>
  <div class="space-y-6">
    <div class="flex flex-wrap items-center justify-between gap-3">
      <div>
        <h2 class="text-xl font-semibold">系统概览</h2>
        <p class="text-sm text-muted-foreground mt-1">实时汇总当前实例的用户、空间、文件与物理存储占用情况。</p>
      </div>
      <Button variant="outline" size="sm" :disabled="loading" @click="load">
        <RefreshCw class="size-4" :class="{ 'animate-spin': loading }" />
        刷新数据
      </Button>
    </div>

    <p v-if="error" role="alert" class="rounded-lg border border-destructive/30 bg-destructive/10 px-4 py-3 text-sm text-destructive">
      {{ error }}
    </p>

    <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
      <section v-for="metric in metrics" :key="metric.key" class="rounded-xl border border-border bg-card p-5">
        <p class="text-sm text-muted-foreground">{{ metric.label }}</p>
        <p class="text-3xl font-semibold mt-3 tabular-nums">
          {{ (stats[metric.key] || 0).toLocaleString() }}
        </p>
        <p v-if="metric.key === 'bytes'" class="text-xs text-muted-foreground mt-2">
          约 {{ formatBytes(stats.bytes || 0) }}
        </p>
      </section>
    </div>

    <section class="rounded-xl border border-border bg-card p-5 space-y-4">
      <div class="flex items-center justify-between gap-2">
        <h3 class="font-semibold text-base">后台模块入口</h3>
        <span class="text-xs text-muted-foreground">遵循 T005 后台独立路由清单</span>
      </div>
      <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-3">
        <RouterLink
          v-for="item in ADMIN_NAV_ITEMS.filter(n => n.name !== 'admin-dashboard')"
          :key="item.name"
          :to="item.to"
          class="rounded-lg border border-border p-4 hover:bg-muted/60 transition-colors flex flex-col gap-1.5"
        >
          <div class="flex items-center justify-between gap-2">
            <span class="font-medium text-sm">{{ item.label }}</span>
            <span class="text-[11px] px-1.5 py-0.5 rounded bg-emerald-500/15 text-emerald-700 dark:text-emerald-300">
              可用
            </span>
          </div>
          <p class="text-xs text-muted-foreground leading-relaxed">{{ item.description }}</p>
        </RouterLink>
      </div>
    </section>
  </div>
</template>
