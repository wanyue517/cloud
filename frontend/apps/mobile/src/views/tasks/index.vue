<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { Button as VanButton, showConfirmDialog, showToast } from 'vant'
import { Pause, Play, RefreshCw, RotateCcw, Trash2, X } from '@lucide/vue'
import { normalizeErrorMessage } from '@repo/drive-core/errors'
import { formatBytes, formatDateTime } from '@repo/drive-core/format'
import { statusLabel } from '@repo/drive-core/upload-queue'
import { mobileDriveApi, type UploadSession } from '@/api/drive'
import {
  activeUploadItems,
  cancelUploadTask,
  clearFinishedUploadTasks,
  completedUploadItems,
  pauseAllUploadTasks,
  pausedOrFailedUploadItems,
  pauseUploadTask,
  resumeOfflinePausedTasks,
  resumeUploadTask,
  uploadState,
} from '@/stores/uploads'
import { EmptyState, ErrorState, MobileShell, PullRefresh, SkeletonList } from '@/components/common'

type TaskTab = 'active' | 'completed' | 'paused' | 'server'

const activeTab = ref<TaskTab>('active')
const serverSessions = ref<UploadSession[]>([])
const serverLoading = ref(false)
const serverRefreshing = ref(false)
const serverError = ref('')
const cancellingId = ref('')

const tabs = computed<{ key: TaskTab; label: string; count: number }[]>(() => [
  { key: 'active', label: '进行中', count: activeUploadItems.value.length },
  { key: 'completed', label: '已完成', count: completedUploadItems.value.length },
  { key: 'paused', label: '暂停/失败', count: pausedOrFailedUploadItems.value.length },
  { key: 'server', label: '服务端', count: serverSessions.value.length },
])

async function loadServerSessions() {
  if (!serverRefreshing.value) serverLoading.value = true
  serverError.value = ''
  try {
    serverSessions.value = await mobileDriveApi.listUploads()
  } catch (err) {
    serverError.value = normalizeErrorMessage(err, '加载服务端上传记录失败')
  } finally {
    serverLoading.value = false
    serverRefreshing.value = false
  }
}

onMounted(() => {
  void loadServerSessions()
})

async function onRefreshServer() {
  serverRefreshing.value = true
  await loadServerSessions()
}

async function cancelServerSession(session: UploadSession) {
  try {
    await showConfirmDialog({
      title: '清理未完成上传',
      message: `确定取消「${session.name}」的服务端断点记录并释放临时分片吗？`,
      confirmButtonText: '确认清理',
      confirmButtonColor: 'var(--destructive)',
    })
  } catch {
    return
  }

  cancellingId.value = session.id
  try {
    await mobileDriveApi.cancelUploadSession(session.id)
    showToast('已清理服务端上传会话')
    await loadServerSessions()
  } catch (err) {
    showToast(normalizeErrorMessage(err, '清理失败'))
  } finally {
    cancellingId.value = ''
  }
}

function sessionPercent(s: UploadSession): number {
  if (s.status === 'COMPLETED') return 100
  const received = s.received?.length ?? 0
  const total = Math.max(1, s.chunkCount || 1)
  return Math.min(99, Math.round((received / total) * 100))
}
</script>

<template>
  <MobileShell title="传输任务" back-to="/home">
    <template #right>
      <button
        v-if="activeTab === 'active' && activeUploadItems.length > 0"
        type="button"
        class="rounded-lg px-2 py-1 text-xs font-medium text-primary active:bg-accent"
        @click="pauseAllUploadTasks"
      >
        全部暂停
      </button>
      <button
        v-else-if="activeTab === 'completed' && completedUploadItems.length > 0"
        type="button"
        class="rounded-lg px-2 py-1 text-xs font-medium text-muted-foreground active:bg-accent"
        @click="clearFinishedUploadTasks"
      >
        清理记录
      </button>
      <button
        v-else-if="activeTab === 'server'"
        type="button"
        class="inline-flex h-8 w-8 items-center justify-center rounded-full text-foreground active:bg-accent"
        aria-label="刷新"
        @click="loadServerSessions"
      >
        <RefreshCw :size="16" :class="serverLoading ? 'animate-spin' : ''" />
      </button>
    </template>

    <div class="flex-1 min-h-0 flex flex-col">
      <!-- 顶部 Segmented Tab -->
      <div class="border-b border-border bg-background px-3 py-2">
        <div class="grid grid-cols-4 gap-1 rounded-xl bg-muted p-1">
          <button
            v-for="tab in tabs"
            :key="tab.key"
            type="button"
            class="rounded-lg py-1.5 text-xs font-medium transition-colors"
            :class="
              activeTab === tab.key
                ? 'bg-background text-foreground shadow-xs'
                : 'text-muted-foreground'
            "
            @click="activeTab = tab.key"
          >
            {{ tab.label }}
            <span v-if="tab.count > 0" class="ml-0.5 text-[10px] opacity-80">({{ tab.count }})</span>
          </button>
        </div>
      </div>

      <!-- 离线暂停一键续传提示条 -->
      <div
        v-if="activeTab === 'paused' && uploadState.offlinePausedIds.length > 0"
        class="flex items-center justify-between gap-2 bg-primary/10 px-4 py-2 text-xs text-primary border-b border-primary/20"
      >
        <span>有 {{ uploadState.offlinePausedIds.length }} 个任务因断网暂停</span>
        <VanButton size="mini" round type="primary" @click="resumeOfflinePausedTasks">
          一键续传
        </VanButton>
      </div>

      <!-- 进行中 Tab -->
      <div v-if="activeTab === 'active'" class="flex-1 overflow-y-auto px-3.5 py-2 pb-16">
        <EmptyState
          v-if="activeUploadItems.length === 0"
          title="没有进行中的任务"
          description="在文件页面点击右下角「+」即可上传文件、拍照或录音"
        />
        <div v-else class="space-y-2.5">
          <div
            v-for="item in activeUploadItems"
            :key="item.id"
            class="rounded-2xl border border-border bg-card p-3.5 space-y-2"
          >
            <div class="flex items-start justify-between gap-2">
              <div class="min-w-0 flex-1">
                <p class="text-sm font-medium truncate text-foreground">{{ item.name }}</p>
                <p class="text-xs text-muted-foreground mt-0.5">
                  {{ formatBytes(item.file.size) }} · {{ statusLabel(item.status, item.message) }}
                </p>
              </div>
              <div class="flex items-center gap-1 shrink-0">
                <button
                  type="button"
                  class="inline-flex h-8 w-8 items-center justify-center rounded-full text-foreground active:bg-accent"
                  aria-label="暂停"
                  @click="pauseUploadTask(item.id)"
                >
                  <Pause :size="15" />
                </button>
                <button
                  type="button"
                  class="inline-flex h-8 w-8 items-center justify-center rounded-full text-destructive active:bg-destructive/10"
                  aria-label="取消"
                  @click="cancelUploadTask(item.id)"
                >
                  <X :size="15" />
                </button>
              </div>
            </div>
            <div class="flex items-center gap-2">
              <div class="h-1.5 flex-1 rounded-full bg-muted overflow-hidden">
                <div
                  class="h-full rounded-full bg-primary transition-all duration-150"
                  :style="{ width: `${item.percent}%` }"
                />
              </div>
              <span class="text-xs font-semibold text-primary w-9 text-right">{{ item.percent }}%</span>
            </div>
          </div>
        </div>
      </div>

      <!-- 已完成 Tab -->
      <div v-else-if="activeTab === 'completed'" class="flex-1 overflow-y-auto px-3.5 py-2 pb-16">
        <EmptyState
          v-if="completedUploadItems.length === 0"
          title="暂无已完成记录"
          description="本次会话完成或取消的上传任务会显示在这里"
        />
        <div v-else class="divide-y divide-border">
          <div
            v-for="item in completedUploadItems"
            :key="item.id"
            class="flex items-center justify-between gap-3 py-3"
          >
            <div class="min-w-0 flex-1">
              <p class="text-sm font-medium truncate text-foreground">{{ item.name }}</p>
              <p class="text-xs text-muted-foreground mt-0.5">
                {{ formatBytes(item.file.size) }} · {{ statusLabel(item.status, item.message) }}
              </p>
            </div>
            <span
              class="rounded-full px-2 py-0.5 text-[11px] font-medium shrink-0"
              :class="
                item.status === 'completed'
                  ? 'bg-emerald-500/10 text-emerald-600 dark:text-emerald-400'
                  : 'bg-muted text-muted-foreground'
              "
            >
              {{ item.status === 'completed' ? '已完成' : '已取消' }}
            </span>
          </div>
        </div>
      </div>

      <!-- 失败与暂停 Tab -->
      <div v-else-if="activeTab === 'paused'" class="flex-1 overflow-y-auto px-3.5 py-2 pb-16">
        <EmptyState
          v-if="pausedOrFailedUploadItems.length === 0"
          title="没有暂停或失败的任务"
          description="断网或手动暂停的任务支持从断点无缝续传"
        />
        <div v-else class="space-y-2.5">
          <div
            v-for="item in pausedOrFailedUploadItems"
            :key="item.id"
            class="rounded-2xl border border-border bg-card p-3.5 space-y-2"
          >
            <div class="flex items-start justify-between gap-2">
              <div class="min-w-0 flex-1">
                <p class="text-sm font-medium truncate text-foreground">{{ item.name }}</p>
                <p
                  class="text-xs mt-0.5"
                  :class="item.status === 'error' ? 'text-destructive' : 'text-muted-foreground'"
                >
                  {{ formatBytes(item.file.size) }} · {{ statusLabel(item.status, item.message) }}
                </p>
              </div>
              <div class="flex items-center gap-1 shrink-0">
                <button
                  type="button"
                  class="inline-flex h-8 w-8 items-center justify-center rounded-full text-primary active:bg-primary/10"
                  :aria-label="item.status === 'error' ? '重试' : '继续'"
                  @click="resumeUploadTask(item.id)"
                >
                  <RotateCcw v-if="item.status === 'error'" :size="15" />
                  <Play v-else :size="15" />
                </button>
                <button
                  type="button"
                  class="inline-flex h-8 w-8 items-center justify-center rounded-full text-destructive active:bg-destructive/10"
                  aria-label="取消"
                  @click="cancelUploadTask(item.id)"
                >
                  <X :size="15" />
                </button>
              </div>
            </div>
            <div class="flex items-center gap-2">
              <div class="h-1.5 flex-1 rounded-full bg-muted overflow-hidden">
                <div
                  class="h-full rounded-full"
                  :class="item.status === 'error' ? 'bg-destructive' : 'bg-amber-500'"
                  :style="{ width: `${item.percent}%` }"
                />
              </div>
              <span class="text-xs text-muted-foreground w-9 text-right">{{ item.percent }}%</span>
            </div>
          </div>
        </div>
      </div>

      <!-- 服务端记录 Tab -->
      <PullRefresh v-else v-model="serverRefreshing" @refresh="onRefreshServer">
        <SkeletonList v-if="serverLoading" :count="5" />
        <ErrorState v-else-if="serverError" :message="serverError" @retry="loadServerSessions" />
        <EmptyState
          v-else-if="serverSessions.length === 0"
          title="服务端无上传会话记录"
          description="大文件分片上传的断点会话会显示在这里"
        />
        <div v-else class="divide-y divide-border px-3.5 pb-16">
          <div
            v-for="session in serverSessions"
            :key="session.id"
            class="py-3 space-y-1.5"
          >
            <div class="flex items-start justify-between gap-2">
              <div class="min-w-0 flex-1">
                <p class="text-sm font-medium truncate text-foreground">{{ session.name }}</p>
                <p class="text-xs text-muted-foreground mt-0.5">
                  {{ formatBytes(session.size) }} · 分片 {{ session.received?.length ?? 0 }}/{{ session.chunkCount }} · {{ session.status }}
                </p>
                <p v-if="session.expiresAt" class="text-[11px] text-muted-foreground mt-0.5">
                  过期时间：{{ formatDateTime(session.expiresAt) }}
                </p>
              </div>
              <button
                v-if="session.status !== 'COMPLETED' && session.status !== 'CANCELLED'"
                type="button"
                class="inline-flex h-8 w-8 items-center justify-center rounded-full text-destructive active:bg-destructive/10 shrink-0"
                aria-label="清理会话"
                :disabled="cancellingId === session.id"
                @click="cancelServerSession(session)"
              >
                <Trash2 :size="15" />
              </button>
            </div>
            <div class="h-1 w-full rounded-full bg-muted overflow-hidden">
              <div
                class="h-full rounded-full bg-primary"
                :style="{ width: `${sessionPercent(session)}%` }"
              />
            </div>
          </div>
        </div>
      </PullRefresh>
    </div>
  </MobileShell>
</template>
