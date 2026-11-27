<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { Button as VanButton } from 'vant'
import { AlertTriangle, ExternalLink, Files, HardDrive, Users } from '@lucide/vue'
import { saveClientModePreference } from '@repo/drive-core/client-mode'
import { normalizeErrorMessage } from '@repo/drive-core/errors'
import { formatBytes } from '@repo/drive-core/format'
import { mobileDriveApi } from '@/api/drive'
import { fetchUserProfile, userState } from '@/stores/user'
import { ErrorState, MobileShell, SkeletonList } from '@/components/common'
import NotFoundView from '@/views/not-found.vue'

const checking = ref(true)
const loading = ref(false)
const error = ref('')
const stats = ref<Record<string, unknown> | null>(null)

const isAdmin = computed(() => Boolean(userState.profile?.admin))

function readNumber(obj: Record<string, unknown> | null, keys: string[]): number {
  if (!obj) return 0
  for (const k of keys) {
    const val = obj[k]
    if (typeof val === 'number' && Number.isFinite(val)) return val
    if (typeof val === 'string' && val.trim() && Number.isFinite(Number(val))) return Number(val)
  }
  return 0
}

const metrics = computed(() => [
  {
    label: '注册用户数',
    value: String(readNumber(stats.value, ['users', 'userCount', 'totalUsers'])),
    icon: Users,
    cls: 'bg-primary/10 text-primary',
  },
  {
    label: '文件总数',
    value: String(readNumber(stats.value, ['files', 'fileCount', 'totalFiles'])),
    icon: Files,
    cls: 'bg-emerald-500/10 text-emerald-600 dark:text-emerald-400',
  },
  {
    label: '总存储用量',
    value: formatBytes(readNumber(stats.value, ['usedBytes', 'storageBytes', 'totalBytes'])),
    icon: HardDrive,
    cls: 'bg-sky-500/10 text-sky-600 dark:text-sky-400',
  },
  {
    label: '失败异步任务',
    value: String(readNumber(stats.value, ['failedJobs', 'failedJobCount', 'failedTasks'])),
    icon: AlertTriangle,
    cls: 'bg-amber-500/10 text-amber-600 dark:text-amber-400',
  },
])

async function loadStats() {
  loading.value = true
  error.value = ''
  try {
    stats.value = await mobileDriveApi.adminStats()
  } catch (err) {
    error.value = normalizeErrorMessage(err, '加载系统概览指标失败')
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  await fetchUserProfile()
  checking.value = false
  if (isAdmin.value) {
    await loadStats()
  }
})

function openDesktopAdmin() {
  saveClientModePreference('desktop')
  window.location.href = '/admin'
}
</script>

<template>
  <div v-if="checking" class="min-h-dvh bg-background p-4">
    <SkeletonList :count="4" />
  </div>

  <!-- 严格权限隔离：普通用户访问直接展示 404 且绝不请求管理员 API -->
  <NotFoundView v-else-if="!isAdmin" />

  <MobileShell v-else title="系统概览（只读）" back-to="/me">
    <div class="p-4 space-y-4">
      <SkeletonList v-if="loading" :count="4" />
      <ErrorState v-else-if="error" :message="error" @retry="loadStats" />
      <template v-else>
        <div class="grid grid-cols-2 gap-3">
          <div
            v-for="card in metrics"
            :key="card.label"
            class="rounded-2xl border border-border bg-card p-4 space-y-2.5"
          >
            <span class="flex h-9 w-9 items-center justify-center rounded-xl" :class="card.cls">
              <component :is="card.icon" :size="18" />
            </span>
            <div>
              <p class="text-lg font-semibold text-foreground tabular-nums">{{ card.value }}</p>
              <p class="text-xs text-muted-foreground mt-0.5">{{ card.label }}</p>
            </div>
          </div>
        </div>

        <div class="rounded-2xl border border-border bg-card p-4 space-y-3">
          <p class="text-xs text-muted-foreground">
            移动端仅提供核心运维指标只读查看；用户治理、存储路由、日志审计与系统参数配置请使用桌面端完整管理后台。
          </p>
          <VanButton block round type="primary" @click="openDesktopAdmin">
            <span class="inline-flex items-center gap-1.5">
              <ExternalLink :size="15" />
              <span>前往桌面版完整管理后台</span>
            </span>
          </VanButton>
        </div>
      </template>
    </div>
  </MobileShell>
</template>
