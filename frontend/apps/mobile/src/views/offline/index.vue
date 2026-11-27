<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { Button as VanButton, showConfirmDialog, showToast } from 'vant'
import { DownloadCloud, FileText, RefreshCw, Trash2, WifiOff } from '@lucide/vue'
import { formatBytes, formatRelativeTime } from '@repo/drive-core/format'
import { normalizeErrorMessage } from '@repo/drive-core/errors'
import { mobileDriveApi } from '@/api/drive'
import { networkStatus } from '@/lib/network'
import {
  clearAllOfflineFiles,
  getOfflineLimitMb,
  getOfflineTotalBytes,
  listOfflineEntries,
  readOfflineFileBlob,
  reconcileOfflineEntries,
  removeOfflineFile,
  saveOfflineFile,
  setOfflineLimitMb,
  type OfflineEntry,
} from '@/native/offline-cache'
import { EmptyState, MobileShell } from '@/components/common'

const LIMIT_OPTIONS = [256, 512, 1024, 2048] as const

const entries = ref<OfflineEntry[]>([])
const limitMb = ref(getOfflineLimitMb())
const checking = ref(false)
const activePreviewUrl = ref('')
const activePreviewEntry = ref<OfflineEntry | null>(null)

const totalBytes = computed(() => getOfflineTotalBytes(entries.value))

function reload() {
  entries.value = listOfflineEntries()
}

function closeLocalPreview() {
  if (activePreviewUrl.value) {
    URL.revokeObjectURL(activePreviewUrl.value)
    activePreviewUrl.value = ''
  }
  activePreviewEntry.value = null
}

async function openOfflineEntry(entry: OfflineEntry) {
  closeLocalPreview()
  const blob = await readOfflineFileBlob(entry)
  if (!blob) {
    showToast('本地离线副本已损坏或缺失，请重新缓存')
    return
  }
  activePreviewEntry.value = entry
  activePreviewUrl.value = URL.createObjectURL(blob)
}

async function handleCheckUpdates() {
  if (!networkStatus.online) {
    showToast('当前处于离线状态，联网后可检查云端更新')
    return
  }
  checking.value = true
  try {
    const remoteState: Record<string, { revision: number } | null> = {}
    for (const entry of entries.value) {
      try {
        const versions = await mobileDriveApi.versions(entry.spaceId, entry.nodeId)
        remoteState[entry.nodeId] = { revision: versions.length || entry.revision }
      } catch (err) {
        const status = (err as { response?: { status?: number } })?.response?.status
        if (status === 404 || status === 403 || status === 423) {
          remoteState[entry.nodeId] = null
        }
      }
    }
    const { kept, removedIds, updatedIds } = reconcileOfflineEntries(entries.value, remoteState)
    for (const id of removedIds) {
      await removeOfflineFile(id)
    }
    entries.value = kept
    if (removedIds.length > 0) {
      showToast(`已自动清理 ${removedIds.length} 个云端已失效离线副本`)
    } else if (updatedIds.length > 0) {
      showToast(`发现 ${updatedIds.length} 个文件有新版本可更新`)
    } else {
      showToast('全部离线副本已是最新版本')
    }
  } finally {
    checking.value = false
  }
}

async function handleRefreshEntry(entry: OfflineEntry) {
  try {
    const blob = await mobileDriveApi.downloadContent(entry.spaceId, entry.nodeId)
    await saveOfflineFile(
      {
        nodeId: entry.nodeId,
        spaceId: entry.spaceId,
        name: entry.name,
        size: blob.size,
        mimeType: entry.mimeType,
        revision: entry.revision + 1,
        currentVersionId: entry.currentVersionId,
      },
      blob,
    )
    reload()
    showToast(`已更新「${entry.name}」`)
  } catch (err) {
    showToast(normalizeErrorMessage(err, '更新离线副本失败'))
  }
}

async function handleRemove(entry: OfflineEntry) {
  await removeOfflineFile(entry.nodeId)
  reload()
  showToast('已移除离线副本')
}

async function handleClearAll() {
  try {
    await showConfirmDialog({
      title: '清空全部离线文件',
      message: '清空后仅删除手机本地缓存副本，不会影响云端文件。',
      confirmButtonText: '确认清空',
      confirmButtonColor: 'var(--destructive)',
    })
  } catch {
    return
  }
  await clearAllOfflineFiles()
  reload()
  showToast('已清空全部离线缓存')
}

function changeLimit(mb: number) {
  limitMb.value = setOfflineLimitMb(mb)
  showToast(`离线缓存上限已调整为 ${mb >= 1024 ? `${mb / 1024} GB` : `${mb} MB`}`)
}

onMounted(reload)
onBeforeUnmount(closeLocalPreview)
</script>

<template>
  <MobileShell title="离线文件" fallback-path="/me" padded>
    <template #right>
      <button
        type="button"
        class="inline-flex items-center gap-1 rounded-full px-2.5 py-1 text-xs font-medium text-primary active:bg-accent"
        :disabled="checking"
        @click="handleCheckUpdates"
      >
        <RefreshCw :size="13" :class="checking ? 'animate-spin' : ''" />
        <span>检查更新</span>
      </button>
    </template>

    <div class="space-y-4 pb-8">
      <!-- 缓存概览与上限 -->
      <section class="rounded-2xl border border-border bg-card p-4 space-y-3">
        <div class="flex items-center justify-between text-xs">
          <span class="font-medium text-foreground inline-flex items-center gap-1.5">
            <WifiOff v-if="!networkStatus.online" :size="14" class="text-amber-500" />
            <span>本地已缓存 {{ formatBytes(totalBytes) }}</span>
          </span>
          <span class="text-muted-foreground">上限 {{ limitMb >= 1024 ? `${limitMb / 1024} GB` : `${limitMb} MB` }}</span>
        </div>

        <div class="flex flex-wrap gap-1.5">
          <button
            v-for="mb in LIMIT_OPTIONS"
            :key="mb"
            type="button"
            class="rounded-full border px-2.5 py-1 text-xs"
            :class="
              limitMb === mb
                ? 'border-primary bg-primary text-primary-foreground'
                : 'border-border bg-background text-muted-foreground'
            "
            @click="changeLimit(mb)"
          >
            {{ mb >= 1024 ? `${mb / 1024} GB` : `${mb} MB` }}
          </button>
        </div>

        <p class="text-[11px] text-muted-foreground">
          离线副本保存在应用私有沙箱中，无网飞行模式下可直接打开；私密空间与隐藏文件严格禁止离线缓存。
        </p>
      </section>

      <EmptyState
        v-if="entries.length === 0"
        :icon="DownloadCloud"
        title="暂无离线文件"
        description="在文件列表中点击文件菜单「设为离线可用」，即可在无网环境下随时阅读"
      />

      <div v-else class="space-y-2">
        <div
          v-for="entry in entries"
          :key="entry.nodeId"
          class="flex items-center gap-3 rounded-2xl border border-border bg-card p-3"
        >
          <span class="flex h-10 w-10 items-center justify-center rounded-xl bg-primary/10 text-primary shrink-0">
            <FileText :size="18" />
          </span>
          <button type="button" class="flex-1 min-w-0 text-left" @click="openOfflineEntry(entry)">
            <p class="text-sm font-medium text-foreground truncate">{{ entry.name }}</p>
            <p class="text-xs text-muted-foreground">
              {{ formatBytes(entry.size) }} · {{ formatRelativeTime(entry.cachedAt) }}
              <span v-if="entry.hasUpdate" class="ml-1 text-amber-600 dark:text-amber-400 font-medium">有云端更新</span>
            </p>
          </button>
          <div class="flex items-center gap-1 shrink-0">
            <button
              v-if="entry.hasUpdate"
              type="button"
              class="rounded-lg bg-primary/10 px-2 py-1 text-xs font-medium text-primary"
              @click="handleRefreshEntry(entry)"
            >
              更新
            </button>
            <button
              type="button"
              class="p-2 text-muted-foreground active:text-destructive"
              aria-label="删除离线副本"
              @click="handleRemove(entry)"
            >
              <Trash2 :size="15" />
            </button>
          </div>
        </div>

        <VanButton block round plain type="danger" class="!h-10 !mt-3" @click="handleClearAll">
          一键清空全部离线缓存
        </VanButton>
      </div>
    </div>

    <!-- 本地无网阅读器弹层 -->
    <div
      v-if="activePreviewEntry && activePreviewUrl"
      class="fixed inset-0 z-50 flex flex-col bg-background"
    >
      <header class="flex items-center justify-between border-b border-border px-4 py-3">
        <span class="text-sm font-semibold text-foreground truncate pr-2">{{ activePreviewEntry.name }}</span>
        <button type="button" class="text-xs font-medium text-primary shrink-0" @click="closeLocalPreview">
          关闭
        </button>
      </header>
      <div class="flex-1 min-h-0 flex items-center justify-center overflow-auto bg-muted/20 p-3">
        <img
          v-if="activePreviewEntry.mimeType.startsWith('image/')"
          :src="activePreviewUrl"
          :alt="activePreviewEntry.name"
          class="max-w-full max-h-full object-contain"
        />
        <video
          v-else-if="activePreviewEntry.mimeType.startsWith('video/')"
          :src="activePreviewUrl"
          controls
          playsinline
          class="max-w-full max-h-full"
        />
        <audio
          v-else-if="activePreviewEntry.mimeType.startsWith('audio/')"
          :src="activePreviewUrl"
          controls
          class="w-full max-w-sm"
        />
        <iframe
          v-else
          :src="activePreviewUrl"
          class="w-full h-full border-0 bg-white rounded-lg"
          title="离线文件预览"
        />
      </div>
    </div>
  </MobileShell>
</template>
