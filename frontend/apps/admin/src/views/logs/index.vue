<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { Activity, AlertTriangle, CheckCircle2, ChevronDown, ChevronUp, Database, RefreshCw, SearchCheck, Trash2 } from '@lucide/vue'
import { Button } from '@repo/ui/button'
import { confirm } from '@repo/ui/confirm-dialog'
import { toast } from '@repo/ui/toast'
import { request } from '@repo/session/request'

interface RequestLogEntry {
  traceId: string
  method: string
  path: string
  status: number
  durationMs: number
  at: string
}

interface LogsPayload {
  metrics: {
    totalRequests: number
    errorRequests: number
    errorRate: number
    avgLatencyMs: number
    p95LatencyMs: number
  }
  recentRequests: RequestLogEntry[]
}

interface ErrorReportItem {
  id: string
  traceId: string
  level: string
  httpStatus: number
  errorCode: number
  method: string
  path: string
  userId?: string | null
  exceptionClass: string
  message: string
  stackTrace?: string | null
  resolved: boolean
  resolvedAt?: string | null
  createdAt: string
}

interface ErrorReportsPayload {
  summary: { total: number; unresolved: number; serverErrors: number }
  items: ErrorReportItem[]
}

interface ConsistencyPayload {
  totalFiles: number
  indexedFiles: number
  pendingOutbox: number
  orphanChunks: number
  consistent: boolean
}

interface GcPayload {
  dryRun: boolean
  graceMinutes: number
  orphanCount: number
  deletedCount: number
  reclaimedBytes: number
}

const activeTab = ref<'errors' | 'requests'>('errors')
const errorFilter = ref<'unresolved' | 'all' | 'resolved'>('unresolved')
const expandedId = ref<string | null>(null)

const errorReports = ref<ErrorReportsPayload | null>(null)
const logsData = ref<LogsPayload | null>(null)
const consistency = ref<ConsistencyPayload | null>(null)
const gcReport = ref<GcPayload | null>(null)
const loading = ref(false)
const busy = ref(false)
const error = ref('')
const notice = ref('')

function formatBytes(bytes: number) {
  if (!bytes) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB']
  const i = Math.min(3, Math.floor(Math.log(bytes) / Math.log(1024)))
  return `${(bytes / 1024 ** i).toFixed(i ? 1 : 0)} ${units[i]}`
}

async function loadAll() {
  loading.value = true
  error.value = ''
  try {
    const resolvedParam = errorFilter.value === 'all' ? undefined : (errorFilter.value === 'resolved')
    const [errRes, logsRes, consRes] = await Promise.all([
      request.get<ErrorReportsPayload>('/v1/admin/error-reports', resolvedParam === undefined ? undefined : { resolved: resolvedParam }),
      request.get<LogsPayload>('/v1/admin/logs'),
      request.get<ConsistencyPayload>('/v1/admin/search/consistency'),
    ])
    errorReports.value = errRes
    logsData.value = logsRes
    consistency.value = consRes
  } catch (e) {
    error.value = e instanceof Error ? e.message : '加载错误上报与观测数据失败'
  } finally {
    loading.value = false
  }
}

async function resolveError(id: string) {
  busy.value = true
  error.value = ''
  notice.value = ''
  try {
    await request.put(`/v1/admin/error-reports/${id}/resolve`)
    notice.value = `已将异常记录 #${id} 标记为已处理`
    await loadAll()
  } catch (e) {
    error.value = e instanceof Error ? e.message : '标记失败'
  } finally {
    busy.value = false
  }
}

async function clearResolvedErrors() {
  const ok = await confirm({
    title: '清理已处理异常记录',
    description: '将永久删除所有已标记为“已处理”的后台错误上报记录，确定清理吗？',
    confirmText: '确认清理',
    variant: 'destructive',
  })
  if (!ok) return
  busy.value = true
  error.value = ''
  notice.value = ''
  try {
    const res = await request.delete<{ deleted: number }>('/v1/admin/error-reports/resolved')
    toast.success(`已清理 ${res.deleted} 条已处理的异常记录`)
    await loadAll()
  } catch (e) {
    const msg = e instanceof Error ? e.message : '清理失败'
    error.value = msg
    toast.error(msg)
  } finally {
    busy.value = false
  }
}

async function runRebuildSearch() {
  if (busy.value) return
  busy.value = true
  error.value = ''
  notice.value = ''
  try {
    await request.post('/v1/admin/search/rebuild')
    toast.success('已触发全站搜索索引重建任务，后台 Outbox 将自动同步。')
    await loadAll()
  } catch (e) {
    const msg = e instanceof Error ? e.message : '触发索引重建失败'
    error.value = msg
    toast.error(msg)
  } finally {
    busy.value = false
  }
}

async function runStorageGc(dryRun: boolean) {
  if (busy.value) return
  if (!dryRun) {
    const ok = await confirm({
      title: '高危操作：执行物理对象延迟回收',
      description: '将真实物理删除无引用且超过 60 分钟宽限期的孤儿存储对象，删除后不可恢复。建议先执行“孤儿演练扫描”确认范围。确定继续吗？',
      confirmText: '确认执行回收',
      variant: 'destructive',
    })
    if (!ok) return
  }
  busy.value = true
  error.value = ''
  notice.value = ''
  try {
    const res = await request.post<GcPayload>(`/v1/admin/storage/gc?dryRun=${dryRun ? 'true' : 'false'}&graceMinutes=60`)
    gcReport.value = res
    toast.success(dryRun
      ? `孤儿扫描演练完成：发现 ${res.orphanCount} 个孤儿对象（可回收 ${formatBytes(res.reclaimedBytes)}）`
      : `孤儿回收完成：安全清理 ${res.deletedCount} 个孤儿对象，释放 ${formatBytes(res.reclaimedBytes)}`)
  } catch (e) {
    const msg = e instanceof Error ? e.message : '执行孤儿扫描失败'
    error.value = msg
    toast.error(msg)
  } finally {
    busy.value = false
  }
}

function switchErrorFilter(next: 'unresolved' | 'all' | 'resolved') {
  errorFilter.value = next
  void loadAll()
}

onMounted(() => { void loadAll() })
</script>

<template>
  <div class="space-y-6">
    <div class="flex flex-wrap items-center justify-between gap-3">
      <div>
        <h2 class="text-xl font-semibold">错误上报与可观测性</h2>
        <p class="text-sm text-muted-foreground mt-1">后端所有抛出的业务与系统异常均自动持久记录，同时提供 API 链路耗时、搜索一致性与存储孤儿回收。</p>
      </div>
      <div class="flex items-center gap-2 shrink-0">
        <Button variant="outline" size="sm" :disabled="loading" @click="loadAll">
          <RefreshCw class="size-4" :class="{ 'animate-spin': loading }" /> 刷新
        </Button>
      </div>
    </div>

    <p v-if="error" role="alert" class="rounded-lg border border-destructive/30 bg-destructive/10 px-4 py-3 text-sm text-destructive">{{ error }}</p>
    <p v-if="notice" role="status" class="rounded-lg border border-emerald-500/30 bg-emerald-500/10 px-4 py-3 text-sm text-emerald-700 dark:text-emerald-300">{{ notice }}</p>

    <div class="grid grid-cols-2 lg:grid-cols-4 gap-4">
      <section class="rounded-xl border border-border bg-card p-5">
        <p class="text-sm text-muted-foreground">待处理异常</p>
        <p class="text-3xl font-semibold mt-2 tabular-nums text-destructive">{{ (errorReports?.summary.unresolved ?? 0).toLocaleString() }}</p>
      </section>
      <section class="rounded-xl border border-border bg-card p-5">
        <p class="text-sm text-muted-foreground">累计上报异常</p>
        <p class="text-3xl font-semibold mt-2 tabular-nums">{{ (errorReports?.summary.total ?? 0).toLocaleString() }}</p>
      </section>
      <section class="rounded-xl border border-border bg-card p-5">
        <p class="text-sm text-muted-foreground">API 异常率 / 累计请求</p>
        <p class="text-2xl font-semibold mt-2 tabular-nums">{{ logsData?.metrics.errorRate ?? 0 }}% · {{ (logsData?.metrics.totalRequests ?? 0).toLocaleString() }}</p>
      </section>
      <section class="rounded-xl border border-border bg-card p-5">
        <p class="text-sm text-muted-foreground">P95 延迟 / 平均</p>
        <p class="text-2xl font-semibold mt-2 tabular-nums">{{ logsData?.metrics.p95LatencyMs ?? 0 }} / {{ logsData?.metrics.avgLatencyMs ?? 0 }} ms</p>
      </section>
    </div>

    <div class="flex flex-wrap items-center justify-between gap-2 border-b border-border pb-2">
      <div class="flex items-center gap-2">
        <Button size="sm" :variant="activeTab === 'errors' ? 'default' : 'ghost'" @click="activeTab = 'errors'">
          <AlertTriangle class="size-4" /> 后台错误上报 ({{ errorReports?.summary.unresolved ?? 0 }})
        </Button>
        <Button size="sm" :variant="activeTab === 'requests' ? 'default' : 'ghost'" @click="activeTab = 'requests'">
          <Activity class="size-4" /> 请求链路日志与运维
        </Button>
      </div>
      <div v-if="activeTab === 'errors'" class="flex items-center gap-1.5">
        <Button size="sm" :variant="errorFilter === 'unresolved' ? 'default' : 'outline'" @click="switchErrorFilter('unresolved')">待处理</Button>
        <Button size="sm" :variant="errorFilter === 'all' ? 'default' : 'outline'" @click="switchErrorFilter('all')">全部</Button>
        <Button size="sm" :variant="errorFilter === 'resolved' ? 'default' : 'outline'" @click="switchErrorFilter('resolved')">已处理</Button>
        <Button size="sm" variant="outline" :disabled="busy" @click="clearResolvedErrors">
          <Trash2 class="size-3.5" /> 清理已处理
        </Button>
      </div>
    </div>

    <section v-if="activeTab === 'errors'" class="rounded-xl border border-border bg-card overflow-x-auto">
      <table class="w-full min-w-[760px] text-sm text-left">
        <thead class="text-muted-foreground bg-muted/50">
          <tr>
            <th class="px-4 py-2.5 font-medium whitespace-nowrap">时间 / 状态</th>
            <th class="px-4 py-2.5 font-medium whitespace-nowrap">接口路径</th>
            <th class="px-4 py-2.5 font-medium">错误详情</th>
            <th class="px-4 py-2.5 font-medium text-right whitespace-nowrap">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="!loading && !(errorReports?.items?.length)" class="border-t border-border">
            <td colspan="4" class="px-4 py-10 text-center text-muted-foreground">当前筛选条件下无异常上报记录</td>
          </tr>
          <template v-for="item in errorReports?.items || []" :key="item.id">
            <tr class="border-t border-border hover:bg-muted/20">
              <td class="px-4 py-2.5 whitespace-nowrap">
                <div class="flex items-center gap-1.5">
                  <span
                    class="px-2 py-0.5 rounded-full text-xs font-mono whitespace-nowrap shrink-0"
                    :class="item.httpStatus >= 500 ? 'bg-destructive/15 text-destructive' : 'bg-amber-500/15 text-amber-700 dark:text-amber-300'"
                  >
                    {{ item.httpStatus }}
                  </span>
                  <span
                    v-if="item.resolved"
                    class="px-1.5 py-0.5 rounded bg-emerald-500/15 text-emerald-700 dark:text-emerald-300 text-[11px] whitespace-nowrap shrink-0"
                  >
                    已处理
                  </span>
                </div>
                <p class="text-[11px] text-muted-foreground mt-1">{{ new Date(item.createdAt).toLocaleString('zh-CN') }}</p>
              </td>
              <td class="px-4 py-2.5 font-mono text-xs whitespace-nowrap">
                <span class="font-semibold mr-1">{{ item.method }}</span>
                <span class="max-w-[220px] inline-block truncate align-bottom" :title="item.path">{{ item.path }}</span>
                <p class="text-[11px] text-muted-foreground mt-0.5">Trace: {{ item.traceId || '—' }}</p>
              </td>
              <td class="px-4 py-2.5 max-w-[340px]">
                <p class="text-sm font-medium truncate" :title="item.message">{{ item.message }}</p>
                <p class="text-[11px] text-muted-foreground font-mono truncate" :title="item.exceptionClass">{{ item.exceptionClass }}</p>
              </td>
              <td class="px-4 py-2.5 text-right whitespace-nowrap">
                <div class="inline-flex items-center gap-1.5">
                  <Button
                    v-if="item.stackTrace"
                    size="sm"
                    variant="outline"
                    @click="expandedId = expandedId === item.id ? null : item.id"
                  >
                    <ChevronUp v-if="expandedId === item.id" class="size-3.5" />
                    <ChevronDown v-else class="size-3.5" /> 堆栈
                  </Button>
                  <Button v-if="!item.resolved" size="sm" variant="outline" :disabled="busy" @click="resolveError(item.id)">
                    <CheckCircle2 class="size-3.5" /> 标为已处理
                  </Button>
                </div>
              </td>
            </tr>
            <tr v-if="expandedId === item.id && item.stackTrace" class="border-t border-border bg-muted/30">
              <td colspan="4" class="px-4 py-3">
                <pre class="max-h-64 overflow-auto rounded-md bg-background p-3 text-[11px] font-mono leading-relaxed border border-border">{{ item.stackTrace }}</pre>
              </td>
            </tr>
          </template>
        </tbody>
      </table>
    </section>

    <template v-else>
      <div class="grid grid-cols-1 lg:grid-cols-2 gap-4">
        <section class="rounded-xl border border-border bg-card p-5 space-y-3">
          <div class="flex items-center justify-between gap-2">
            <div class="flex items-center gap-2 font-medium">
              <SearchCheck class="size-4 text-primary" />
              <span>搜索索引一致性校验</span>
            </div>
            <Button size="sm" variant="outline" :disabled="busy" @click="runRebuildSearch">
              重建全站索引
            </Button>
          </div>
          <div v-if="consistency" class="grid grid-cols-2 gap-3 text-sm">
            <div class="rounded-lg bg-muted/40 p-3">
              <p class="text-xs text-muted-foreground">未删除文件 / 已建索引</p>
              <p class="font-semibold mt-1 tabular-nums">{{ consistency.totalFiles }} / {{ consistency.indexedFiles }}</p>
            </div>
            <div class="rounded-lg bg-muted/40 p-3">
              <p class="text-xs text-muted-foreground">待消费 Outbox / 孤儿分片</p>
              <p class="font-semibold mt-1 tabular-nums">{{ consistency.pendingOutbox }} / {{ consistency.orphanChunks }}</p>
            </div>
          </div>
        </section>

        <section class="rounded-xl border border-border bg-card p-5 space-y-3">
          <div class="flex items-center justify-between gap-2">
            <div class="flex items-center gap-2 font-medium">
              <Database class="size-4 text-primary" />
              <span>存储对象孤儿扫描与延迟回收</span>
            </div>
            <div class="flex items-center gap-2">
              <Button size="sm" variant="outline" :disabled="busy" @click="runStorageGc(true)">孤儿演练扫描</Button>
              <Button size="sm" variant="destructive" :disabled="busy" @click="runStorageGc(false)">
                <Trash2 class="size-3.5" /> 执行延迟回收
              </Button>
            </div>
          </div>
          <p class="text-xs text-muted-foreground">
            仅回收无任何文件版本引用且超过 60 分钟宽限期的物理对象，二次引用保护防止并发误删。
          </p>
          <p v-if="gcReport" class="text-xs bg-muted/40 rounded px-3 py-2">
            最近结果：模式「{{ gcReport.dryRun ? '演练扫描' : '正式回收' }}」 · 发现孤儿 {{ gcReport.orphanCount }} 个 · 已清理 {{ gcReport.deletedCount }} 个 · 释放空间 {{ formatBytes(gcReport.reclaimedBytes) }}
          </p>
        </section>
      </div>

      <section class="rounded-xl border border-border bg-card overflow-x-auto">
        <div class="px-4 py-3 border-b border-border flex items-center gap-2 font-medium text-sm">
          <Activity class="size-4 text-primary" />
          <span>最近 API 请求链路日志（含 X-Trace-Id）</span>
        </div>
        <table class="w-full min-w-[700px] text-sm text-left">
          <thead class="text-muted-foreground bg-muted/50">
            <tr>
              <th class="px-4 py-2.5 font-medium whitespace-nowrap">时间</th>
              <th class="px-4 py-2.5 font-medium whitespace-nowrap">方法 / 路径</th>
              <th class="px-4 py-2.5 font-medium whitespace-nowrap">状态</th>
              <th class="px-4 py-2.5 font-medium whitespace-nowrap">耗时</th>
              <th class="px-4 py-2.5 font-medium whitespace-nowrap">Trace ID</th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="!loading && !(logsData?.recentRequests?.length)" class="border-t border-border">
              <td colspan="5" class="px-4 py-8 text-center text-muted-foreground">暂无请求记录</td>
            </tr>
            <tr v-for="(item, idx) in logsData?.recentRequests || []" :key="`${item.traceId}-${idx}`" class="border-t border-border">
              <td class="px-4 py-2 text-xs text-muted-foreground whitespace-nowrap">{{ new Date(item.at).toLocaleTimeString('zh-CN') }}</td>
              <td class="px-4 py-2 font-mono text-xs whitespace-nowrap"><span class="font-semibold mr-1.5">{{ item.method }}</span>{{ item.path }}</td>
              <td class="px-4 py-2 whitespace-nowrap">
                <span
                  class="px-2 py-0.5 rounded-full text-xs font-mono"
                  :class="item.status >= 400 ? 'bg-destructive/15 text-destructive' : 'bg-emerald-500/15 text-emerald-700 dark:text-emerald-300'"
                >
                  {{ item.status }}
                </span>
              </td>
              <td class="px-4 py-2 text-xs tabular-nums whitespace-nowrap">{{ item.durationMs }} ms</td>
              <td class="px-4 py-2 font-mono text-xs text-muted-foreground whitespace-nowrap">{{ item.traceId || '—' }}</td>
            </tr>
          </tbody>
        </table>
      </section>
    </template>
  </div>
</template>
