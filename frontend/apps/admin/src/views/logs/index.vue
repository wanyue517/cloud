<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { Activity, Database, RefreshCw, SearchCheck, Trash2 } from '@lucide/vue'
import { Button } from '@repo/ui/button'
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
    const [logsRes, consRes] = await Promise.all([
      request.get<LogsPayload>('/v1/admin/logs'),
      request.get<ConsistencyPayload>('/v1/admin/search/consistency'),
    ])
    logsData.value = logsRes
    consistency.value = consRes
  } catch (e) {
    error.value = e instanceof Error ? e.message : '加载链路观测数据失败'
  } finally {
    loading.value = false
  }
}

async function runRebuildSearch() {
  if (busy.value) return
  busy.value = true
  error.value = ''
  notice.value = ''
  try {
    await request.post('/v1/admin/search/rebuild')
    notice.value = '已触发全站搜索索引重建任务，后台 Outbox 将自动同步。'
    await loadAll()
  } catch (e) {
    error.value = e instanceof Error ? e.message : '触发索引重建失败'
  } finally {
    busy.value = false
  }
}

async function runStorageGc(dryRun: boolean) {
  if (busy.value) return
  busy.value = true
  error.value = ''
  notice.value = ''
  try {
    const res = await request.post<GcPayload>(`/v1/admin/storage/gc?dryRun=${dryRun ? 'true' : 'false'}&graceMinutes=60`)
    gcReport.value = res
    notice.value = dryRun
      ? `孤儿扫描演练完成：发现 ${res.orphanCount} 个无引用孤儿对象（可回收 ${formatBytes(res.reclaimedBytes)}）。`
      : `孤儿回收完成：安全清理 ${res.deletedCount} 个孤儿对象，释放 ${formatBytes(res.reclaimedBytes)}。`
  } catch (e) {
    error.value = e instanceof Error ? e.message : '执行孤儿扫描失败'
  } finally {
    busy.value = false
  }
}

onMounted(() => { void loadAll() })
</script>

<template>
  <div class="space-y-6">
    <div class="flex flex-wrap items-center justify-between gap-3">
      <div>
        <h2 class="text-xl font-semibold">审计日志与可观测性</h2>
        <p class="text-sm text-muted-foreground mt-1">实时监测接口链路耗时（TraceId / P95 / 错误率）、全文搜索索引一致性及存储孤儿对象延迟回收。</p>
      </div>
      <Button variant="outline" size="sm" :disabled="loading" @click="loadAll">
        <RefreshCw class="size-4" :class="{ 'animate-spin': loading }" /> 刷新
      </Button>
    </div>

    <p v-if="error" role="alert" class="rounded-lg border border-destructive/30 bg-destructive/10 px-4 py-3 text-sm text-destructive">{{ error }}</p>
    <p v-if="notice" role="status" class="rounded-lg border border-emerald-500/30 bg-emerald-500/10 px-4 py-3 text-sm text-emerald-700 dark:text-emerald-300">{{ notice }}</p>

    <div class="grid grid-cols-2 lg:grid-cols-4 gap-4">
      <section class="rounded-xl border border-border bg-card p-5">
        <p class="text-sm text-muted-foreground">累计 API 请求</p>
        <p class="text-3xl font-semibold mt-2 tabular-nums">{{ (logsData?.metrics.totalRequests ?? 0).toLocaleString() }}</p>
      </section>
      <section class="rounded-xl border border-border bg-card p-5">
        <p class="text-sm text-muted-foreground">异常率 (4xx/5xx)</p>
        <p class="text-3xl font-semibold mt-2 tabular-nums">{{ logsData?.metrics.errorRate ?? 0 }}%</p>
      </section>
      <section class="rounded-xl border border-border bg-card p-5">
        <p class="text-sm text-muted-foreground">平均响应耗时</p>
        <p class="text-3xl font-semibold mt-2 tabular-nums">{{ logsData?.metrics.avgLatencyMs ?? 0 }} ms</p>
      </section>
      <section class="rounded-xl border border-border bg-card p-5">
        <p class="text-sm text-muted-foreground">P95 延迟</p>
        <p class="text-3xl font-semibold mt-2 tabular-nums">{{ logsData?.metrics.p95LatencyMs ?? 0 }} ms</p>
      </section>
    </div>

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
            <Button size="sm" variant="outline" :disabled="busy" @click="runStorageGc(true)">
              孤儿演练扫描
            </Button>
            <Button size="sm" variant="outline" :disabled="busy" @click="runStorageGc(false)">
              <Trash2 class="size-3.5" /> 执行延迟回收
            </Button>
          </div>
        </div>
        <p class="text-xs text-muted-foreground">
          仅回收无任何文件版本引用且超过 60 分钟宽限期的物理对象，二次引用保护防止并发误删。
        </p>
        <p v-if="gcReport" class="text-xs font-mono bg-muted/40 rounded px-3 py-2">
          最近结果：mode={{ gcReport.dryRun ? 'DRY_RUN' : 'EXECUTE' }} · orphans={{ gcReport.orphanCount }} · deleted={{ gcReport.deletedCount }} · bytes={{ formatBytes(gcReport.reclaimedBytes) }}
        </p>
      </section>
    </div>

    <section class="rounded-xl border border-border bg-card overflow-x-auto">
      <div class="px-4 py-3 border-b border-border flex items-center gap-2 font-medium text-sm">
        <Activity class="size-4 text-primary" />
        <span>最近 API 请求链路日志（含 X-Trace-Id）</span>
      </div>
      <table class="w-full text-sm text-left">
        <thead class="text-muted-foreground bg-muted/50">
          <tr>
            <th class="px-4 py-2.5 font-medium">时间</th>
            <th class="px-4 py-2.5 font-medium">方法 / 路径</th>
            <th class="px-4 py-2.5 font-medium">状态</th>
            <th class="px-4 py-2.5 font-medium">耗时</th>
            <th class="px-4 py-2.5 font-medium">Trace ID</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="!loading && !(logsData?.recentRequests?.length)" class="border-t border-border">
            <td colspan="5" class="px-4 py-8 text-center text-muted-foreground">暂无请求记录</td>
          </tr>
          <tr v-for="(item, idx) in logsData?.recentRequests || []" :key="`${item.traceId}-${idx}`" class="border-t border-border">
            <td class="px-4 py-2 text-xs text-muted-foreground whitespace-nowrap">{{ new Date(item.at).toLocaleTimeString('zh-CN') }}</td>
            <td class="px-4 py-2 font-mono text-xs"><span class="font-semibold mr-1.5">{{ item.method }}</span>{{ item.path }}</td>
            <td class="px-4 py-2">
              <span
                class="px-2 py-0.5 rounded-full text-xs font-mono"
                :class="item.status >= 400 ? 'bg-destructive/15 text-destructive' : 'bg-emerald-500/15 text-emerald-700 dark:text-emerald-300'"
              >
                {{ item.status }}
              </span>
            </td>
            <td class="px-4 py-2 text-xs tabular-nums">{{ item.durationMs }} ms</td>
            <td class="px-4 py-2 font-mono text-xs text-muted-foreground">{{ item.traceId || '—' }}</td>
          </tr>
        </tbody>
      </table>
    </section>
  </div>
</template>
