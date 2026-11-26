<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ArrowUpDown, CheckCircle2, PauseCircle, RefreshCw, Trash2, UploadCloud, XCircle } from '@lucide/vue'
import { Button } from '@repo/ui/button'
import { Separator } from '@repo/ui/separator'
import { statusLabel, type UploadQueueItem } from '@/lib/upload-queue'
import { request } from '@/utils/request'

interface ServerUploadSession {
  id: string
  spaceId: string
  name: string
  sizeBytes: number
  chunkSize: number
  chunkCount: number
  receivedCount: number
  status: string
  fileId: string | null
  createdAt: string
  expiresAt: string
}

const props = defineProps<{
  tasks: UploadQueueItem[]
  uploading: boolean
}>()

const emit = defineEmits<{
  pause: [id: string]
  resume: [id: string]
  cancel: [id: string]
  'pause-all': []
  'clear-finished': []
}>()

type TaskTab = 'all' | 'active' | 'completed' | 'failed' | 'server'
const activeTab = ref<TaskTab>('all')
const serverSessions = ref<ServerUploadSession[]>([])
const serverLoading = ref(false)
const error = ref('')
const notice = ref('')

const activeCount = computed(() => props.tasks.filter(t => t.status === 'uploading' || t.status === 'queued').length)
const pausedCount = computed(() => props.tasks.filter(t => t.status === 'paused').length)
const completedCount = computed(() => props.tasks.filter(t => t.status === 'completed').length)
const errorCount = computed(() => props.tasks.filter(t => t.status === 'error' || t.status === 'cancelled').length)

const filteredLocalTasks = computed(() => {
  switch (activeTab.value) {
    case 'active': return props.tasks.filter(t => t.status === 'uploading' || t.status === 'queued')
    case 'completed': return props.tasks.filter(t => t.status === 'completed')
    case 'failed': return props.tasks.filter(t => t.status === 'error' || t.status === 'paused' || t.status === 'cancelled')
    default: return props.tasks
  }
})

const serverStatusLabels: Record<string, string> = {
  UPLOADING: '断点续传中',
  COMPLETED: '已完成',
  CANCELLED: '已取消',
  EXPIRED: '已过期',
}

function formatBytes(bytes: number) {
  if (!bytes) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB']
  const index = Math.min(3, Math.floor(Math.log(bytes) / Math.log(1024)))
  return `${(bytes / 1024 ** index).toFixed(index ? 1 : 0)} ${units[index]}`
}

async function loadServerSessions() {
  serverLoading.value = true
  error.value = ''
  try {
    serverSessions.value = await request.get<ServerUploadSession[]>('/v1/uploads')
  } catch (e) {
    error.value = e instanceof Error ? e.message : '加载服务端上传记录失败'
  } finally {
    serverLoading.value = false
  }
}

async function cancelServerSession(id: string) {
  error.value = ''
  notice.value = ''
  try {
    await request.delete(`/v1/uploads/${id}`)
    notice.value = '已取消该上传会话并清理临时分片'
    await loadServerSessions()
  } catch (e) {
    error.value = e instanceof Error ? e.message : '取消会话失败'
  }
}

onMounted(() => { void loadServerSessions() })
</script>

<template>
  <div class="space-y-6 max-w-5xl">
    <div class="flex flex-wrap items-center justify-between gap-3">
      <div>
        <h1 class="text-2xl font-semibold">传输与任务中心</h1>
        <p class="text-sm text-muted-foreground mt-1">统一管理当前上传队列、断点续传进度与服务端历史上传会话。</p>
      </div>
      <div class="flex items-center gap-2">
        <Button v-if="uploading" variant="outline" size="sm" @click="emit('pause-all')">
          <PauseCircle :size="15" /> 全部暂停
        </Button>
        <Button v-if="completedCount > 0" variant="outline" size="sm" @click="emit('clear-finished')">
          <Trash2 :size="15" /> 清理已完成
        </Button>
        <Button variant="outline" size="sm" :disabled="serverLoading" @click="loadServerSessions">
          <RefreshCw :size="15" :class="{ 'animate-spin': serverLoading }" /> 刷新记录
        </Button>
      </div>
    </div>

    <p v-if="error" class="drive-error" role="alert">{{ error }}</p>
    <p v-if="notice" class="drive-notice" role="status">{{ notice }}</p>

    <div class="grid grid-cols-2 lg:grid-cols-4 gap-4">
      <section class="rounded-xl border border-border bg-card p-5">
        <div class="flex items-center justify-between text-sm text-muted-foreground"><span>传输中</span><UploadCloud :size="16" /></div>
        <p class="text-3xl font-semibold mt-2 tabular-nums">{{ activeCount }}</p>
      </section>
      <section class="rounded-xl border border-border bg-card p-5">
        <div class="flex items-center justify-between text-sm text-muted-foreground"><span>已暂停</span><PauseCircle :size="16" /></div>
        <p class="text-3xl font-semibold mt-2 tabular-nums">{{ pausedCount }}</p>
      </section>
      <section class="rounded-xl border border-border bg-card p-5">
        <div class="flex items-center justify-between text-sm text-muted-foreground"><span>已完成</span><CheckCircle2 :size="16" /></div>
        <p class="text-3xl font-semibold mt-2 tabular-nums">{{ completedCount }}</p>
      </section>
      <section class="rounded-xl border border-border bg-card p-5">
        <div class="flex items-center justify-between text-sm text-muted-foreground"><span>失败 / 取消</span><XCircle :size="16" /></div>
        <p class="text-3xl font-semibold mt-2 tabular-nums">{{ errorCount }}</p>
      </section>
    </div>

    <div class="flex flex-wrap items-center gap-2" role="tablist" aria-label="任务筛选">
      <Button size="sm" :variant="activeTab === 'all' ? 'default' : 'outline'" @click="activeTab = 'all'">全部队列 ({{ tasks.length }})</Button>
      <Button size="sm" :variant="activeTab === 'active' ? 'default' : 'outline'" @click="activeTab = 'active'">进行中 ({{ activeCount }})</Button>
      <Button size="sm" :variant="activeTab === 'completed' ? 'default' : 'outline'" @click="activeTab = 'completed'">已完成 ({{ completedCount }})</Button>
      <Button size="sm" :variant="activeTab === 'failed' ? 'default' : 'outline'" @click="activeTab = 'failed'">暂停与异常 ({{ pausedCount + errorCount }})</Button>
      <Button size="sm" :variant="activeTab === 'server' ? 'default' : 'outline'" @click="activeTab = 'server'; loadServerSessions()">
        <ArrowUpDown :size="14" /> 服务端会话记录 ({{ serverSessions.length }})
      </Button>
    </div>

    <!-- 本地实时传输队列 -->
    <section v-if="activeTab !== 'server'" class="rounded-xl border border-border bg-card overflow-hidden">
      <div v-if="!filteredLocalTasks.length" class="p-12 text-center text-sm text-muted-foreground">
        当前分类下暂无本地传输任务；在文件工作台上传文件后，可在此集中查看进度与控制续传。
      </div>
      <div v-else class="divide-y divide-border">
        <div v-for="task in filteredLocalTasks" :key="task.id" class="p-4 flex flex-col gap-2">
          <div class="flex items-center justify-between gap-3">
            <div class="min-w-0">
              <strong class="block text-sm truncate">{{ task.name }}</strong>
              <span class="text-xs text-muted-foreground">{{ statusLabel(task.status, task.message) }} · {{ task.percent }}%</span>
            </div>
            <div class="flex items-center gap-2 shrink-0">
              <Button v-if="task.status === 'uploading' || task.status === 'queued'" variant="outline" size="sm" @click="emit('pause', task.id)">暂停</Button>
              <Button v-if="task.status === 'paused' || task.status === 'error'" variant="outline" size="sm" @click="emit('resume', task.id)">{{ task.status === 'error' ? '重试' : '继续' }}</Button>
              <Button v-if="task.status !== 'completed' && task.status !== 'cancelled'" variant="outline" size="sm" class="text-destructive" @click="emit('cancel', task.id)">取消</Button>
            </div>
          </div>
          <progress :value="task.percent" max="100" class="w-full h-1.5 accent-primary" />
        </div>
      </div>
    </section>

    <!-- 服务端持久化的上传会话历史 -->
    <section v-else class="rounded-xl border border-border bg-card overflow-x-auto">
      <header class="px-4 py-3 flex items-center justify-between">
        <span class="text-sm font-medium">服务端持久化上传会话（最近 100 条）</span>
        <span class="text-xs text-muted-foreground">未完成会话在有效期内重新选择同名同大小文件可自动断点续传</span>
      </header>
      <Separator />
      <table class="w-full text-sm text-left">
        <thead class="text-muted-foreground bg-muted/50">
          <tr>
            <th class="px-4 py-3 font-medium">文件名</th>
            <th class="px-4 py-3 font-medium">大小</th>
            <th class="px-4 py-3 font-medium">分片进度</th>
            <th class="px-4 py-3 font-medium">状态</th>
            <th class="px-4 py-3 font-medium">创建时间</th>
            <th class="px-4 py-3 font-medium text-right">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="!serverLoading && !serverSessions.length" class="border-t border-border">
            <td colspan="6" class="px-4 py-10 text-center text-muted-foreground">暂无服务端上传会话记录</td>
          </tr>
          <tr v-for="item in serverSessions" :key="item.id" class="border-t border-border">
            <td class="px-4 py-3 font-medium">{{ item.name }}</td>
            <td class="px-4 py-3 text-muted-foreground">{{ formatBytes(item.sizeBytes) }}</td>
            <td class="px-4 py-3 text-muted-foreground">{{ item.receivedCount }} / {{ item.chunkCount }} 片</td>
            <td class="px-4 py-3">
              <span class="px-2 py-0.5 rounded-full text-xs bg-muted">{{ serverStatusLabels[item.status] || item.status }}</span>
            </td>
            <td class="px-4 py-3 text-muted-foreground">{{ new Date(item.createdAt).toLocaleString('zh-CN') }}</td>
            <td class="px-4 py-3 text-right">
              <Button v-if="item.status === 'UPLOADING'" variant="outline" size="sm" class="text-destructive" @click="cancelServerSession(item.id)">取消并清理</Button>
              <span v-else class="text-xs text-muted-foreground">—</span>
            </td>
          </tr>
        </tbody>
      </table>
    </section>
  </div>
</template>
