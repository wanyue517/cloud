<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { RefreshCw, RotateCcw, Trash2 } from '@lucide/vue'
import { Button } from '@repo/ui/button'
import { request } from '@repo/session/request'

interface AdminTaskItem {
  id: string
  name: string
  sizeBytes: number
  chunkCount: number
  status: string
  createdAt: string
  expiresAt: string
  ownerName: string
  spaceName: string
}

interface AdminTasksPayload {
  summary: Record<string, number>
  items: AdminTaskItem[]
}

interface AsyncJobItem {
  id: string
  consumerGroup: string
  jobType: string
  status: string
  retryCount: number
  maxRetries: number
  lastError?: string | null
  createdAt: string
}

interface AsyncJobsPayload {
  summary: Record<string, number>
  items: AsyncJobItem[]
}

const activeTab = ref<'uploads' | 'jobs'>('uploads')
const statusFilter = ref('ALL')
const jobStatusFilter = ref('ALL')
const summary = ref<Record<string, number>>({ UPLOADING: 0, COMPLETED: 0, CANCELLED: 0, EXPIRED: 0 })
const items = ref<AdminTaskItem[]>([])
const jobSummary = ref<Record<string, number>>({ PENDING: 0, RUNNING: 0, SUCCEEDED: 0, DEAD_LETTER: 0 })
const jobs = ref<AsyncJobItem[]>([])
const loading = ref(false)
const cleaning = ref(false)
const retryingId = ref('')
const error = ref('')
const notice = ref('')

const statusLabels: Record<string, string> = {
  ALL: '全部',
  UPLOADING: '上传中',
  COMPLETED: '已完成',
  CANCELLED: '已取消',
  EXPIRED: '已过期',
}

const jobStatusLabels: Record<string, string> = {
  ALL: '全部',
  PENDING: '待执行',
  RUNNING: '执行中',
  SUCCEEDED: '已成功',
  DEAD_LETTER: '死信队列',
}

function formatBytes(bytes: number) {
  if (!bytes) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB']
  const index = Math.min(3, Math.floor(Math.log(bytes) / Math.log(1024)))
  return `${(bytes / 1024 ** index).toFixed(index ? 1 : 0)} ${units[index]}`
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    if (activeTab.value === 'uploads') {
      const res = await request.get<AdminTasksPayload>('/v1/admin/tasks', statusFilter.value === 'ALL' ? undefined : { status: statusFilter.value })
      summary.value = res.summary || {}
      items.value = res.items || []
    } else {
      const res = await request.get<AsyncJobsPayload>('/v1/admin/jobs', jobStatusFilter.value === 'ALL' ? undefined : { status: jobStatusFilter.value })
      jobSummary.value = res.summary || {}
      jobs.value = res.items || []
    }
  } catch (e) {
    error.value = e instanceof Error ? e.message : '加载后台任务失败'
  } finally {
    loading.value = false
  }
}

async function cleanupExpired() {
  cleaning.value = true
  error.value = ''
  notice.value = ''
  try {
    const res = await request.post<{ cleaned: number }>('/v1/admin/tasks/cleanup-expired')
    notice.value = `已完成过期分片清理，共回收 ${res.cleaned} 个过期上传会话`
    await load()
  } catch (e) {
    error.value = e instanceof Error ? e.message : '清理失败'
  } finally {
    cleaning.value = false
  }
}

async function retryJob(id: string) {
  retryingId.value = id
  error.value = ''
  notice.value = ''
  try {
    await request.post(`/v1/admin/jobs/${id}/retry`)
    notice.value = `任务 ${id} 已重新放回待执行队列`
    await load()
  } catch (e) {
    error.value = e instanceof Error ? e.message : '重试失败'
  } finally {
    retryingId.value = ''
  }
}

function switchTab(next: 'uploads' | 'jobs') {
  activeTab.value = next
  void load()
}

function switchFilter(next: string) {
  if (activeTab.value === 'uploads') statusFilter.value = next
  else jobStatusFilter.value = next
  void load()
}

onMounted(() => { void load() })
</script>

<template>
  <div class="space-y-6">
    <div class="flex flex-wrap items-center justify-between gap-3">
      <div>
        <h2 class="text-xl font-semibold">后台任务中心</h2>
        <p class="text-sm text-muted-foreground mt-1">统一管理全站分片上传会话与 Worker 异步消费者组作业，支持死信任务追踪与一键重试。</p>
      </div>
      <div class="flex items-center gap-2">
        <Button variant="outline" size="sm" :disabled="loading" @click="load">
          <RefreshCw class="size-4" :class="{ 'animate-spin': loading }" /> 刷新
        </Button>
        <Button v-if="activeTab === 'uploads'" variant="outline" size="sm" :disabled="cleaning" @click="cleanupExpired">
          <Trash2 class="size-4" /> {{ cleaning ? '正在清理…' : '清理过期分片' }}
        </Button>
      </div>
    </div>

    <div class="flex items-center gap-2 border-b border-border pb-2">
      <Button size="sm" :variant="activeTab === 'uploads' ? 'default' : 'ghost'" @click="switchTab('uploads')">
        分片上传会话
      </Button>
      <Button size="sm" :variant="activeTab === 'jobs' ? 'default' : 'ghost'" @click="switchTab('jobs')">
        Worker 异步作业与死信
      </Button>
    </div>

    <p v-if="error" role="alert" class="rounded-lg border border-destructive/30 bg-destructive/10 px-4 py-3 text-sm text-destructive">{{ error }}</p>
    <p v-if="notice" role="status" class="rounded-lg border border-emerald-500/30 bg-emerald-500/10 px-4 py-3 text-sm text-emerald-700 dark:text-emerald-300">{{ notice }}</p>

    <template v-if="activeTab === 'uploads'">
      <div class="grid grid-cols-2 lg:grid-cols-4 gap-4">
        <section v-for="key in ['UPLOADING', 'COMPLETED', 'CANCELLED', 'EXPIRED']" :key="key" class="rounded-xl border border-border bg-card p-5">
          <p class="text-sm text-muted-foreground">{{ statusLabels[key] }}</p>
          <p class="text-3xl font-semibold mt-2 tabular-nums">{{ (summary[key] || 0).toLocaleString() }}</p>
        </section>
      </div>

      <div class="flex flex-wrap items-center gap-2">
        <Button
          v-for="key in ['ALL', 'UPLOADING', 'COMPLETED', 'CANCELLED', 'EXPIRED']"
          :key="key"
          size="sm"
          :variant="statusFilter === key ? 'default' : 'outline'"
          @click="switchFilter(key)"
        >
          {{ statusLabels[key] }}
        </Button>
      </div>

      <section class="rounded-xl border border-border bg-card overflow-x-auto">
        <table class="w-full text-sm text-left">
          <thead class="text-muted-foreground bg-muted/50">
            <tr>
              <th class="px-4 py-3 font-medium">文件名</th>
              <th class="px-4 py-3 font-medium">所属用户 / 空间</th>
              <th class="px-4 py-3 font-medium">大小 / 分片</th>
              <th class="px-4 py-3 font-medium">状态</th>
              <th class="px-4 py-3 font-medium">创建时间</th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="!loading && items.length === 0" class="border-t border-border">
              <td colspan="5" class="px-4 py-10 text-center text-muted-foreground">当前筛选条件下暂无任务记录</td>
            </tr>
            <tr v-for="task in items" :key="task.id" class="border-t border-border">
              <td class="px-4 py-3 font-medium">{{ task.name }}</td>
              <td class="px-4 py-3 text-muted-foreground">{{ task.ownerName || '—' }} / {{ task.spaceName || '—' }}</td>
              <td class="px-4 py-3 text-muted-foreground">{{ formatBytes(task.sizeBytes) }} · {{ task.chunkCount }} 片</td>
              <td class="px-4 py-3">
                <span class="px-2 py-0.5 rounded-full text-xs bg-muted">{{ statusLabels[task.status] || task.status }}</span>
              </td>
              <td class="px-4 py-3 text-muted-foreground">{{ new Date(task.createdAt).toLocaleString('zh-CN') }}</td>
            </tr>
          </tbody>
        </table>
      </section>
    </template>

    <template v-else>
      <div class="grid grid-cols-2 lg:grid-cols-4 gap-4">
        <section v-for="key in ['PENDING', 'RUNNING', 'SUCCEEDED', 'DEAD_LETTER']" :key="key" class="rounded-xl border border-border bg-card p-5">
          <p class="text-sm text-muted-foreground">{{ jobStatusLabels[key] }}</p>
          <p class="text-3xl font-semibold mt-2 tabular-nums">{{ (jobSummary[key] || 0).toLocaleString() }}</p>
        </section>
      </div>

      <div class="flex flex-wrap items-center gap-2">
        <Button
          v-for="key in ['ALL', 'PENDING', 'RUNNING', 'SUCCEEDED', 'DEAD_LETTER']"
          :key="key"
          size="sm"
          :variant="jobStatusFilter === key ? 'default' : 'outline'"
          @click="switchFilter(key)"
        >
          {{ jobStatusLabels[key] }}
        </Button>
      </div>

      <section class="rounded-xl border border-border bg-card overflow-x-auto">
        <table class="w-full text-sm text-left">
          <thead class="text-muted-foreground bg-muted/50">
            <tr>
              <th class="px-4 py-3 font-medium">消费者组 / 类型</th>
              <th class="px-4 py-3 font-medium">状态 / 重试</th>
              <th class="px-4 py-3 font-medium">最后错误</th>
              <th class="px-4 py-3 font-medium text-right">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="!loading && jobs.length === 0" class="border-t border-border">
              <td colspan="4" class="px-4 py-10 text-center text-muted-foreground">当前筛选条件下暂无异步作业</td>
            </tr>
            <tr v-for="job in jobs" :key="job.id" class="border-t border-border">
              <td class="px-4 py-3">
                <p class="font-medium font-mono text-xs">{{ job.consumerGroup }}</p>
                <p class="text-xs text-muted-foreground">{{ job.jobType }} · #{{ job.id }}</p>
              </td>
              <td class="px-4 py-3">
                <span
                  class="px-2 py-0.5 rounded-full text-xs"
                  :class="job.status === 'DEAD_LETTER' ? 'bg-destructive/15 text-destructive' : 'bg-muted'"
                >
                  {{ jobStatusLabels[job.status] || job.status }} ({{ job.retryCount }}/{{ job.maxRetries }})
                </span>
              </td>
              <td class="px-4 py-3 text-xs text-muted-foreground max-w-md truncate">{{ job.lastError || '—' }}</td>
              <td class="px-4 py-3 text-right">
                <Button
                  v-if="job.status === 'DEAD_LETTER' || job.status === 'PENDING'"
                  size="sm"
                  variant="outline"
                  :disabled="retryingId === job.id"
                  @click="retryJob(job.id)"
                >
                  <RotateCcw class="size-3.5" /> 重试
                </Button>
              </td>
            </tr>
          </tbody>
        </table>
      </section>
    </template>
  </div>
</template>
