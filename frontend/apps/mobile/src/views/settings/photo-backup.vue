<script setup lang="ts">
import { computed, ref } from 'vue'
import { Button as VanButton, Switch as VanSwitch, showConfirmDialog, showToast } from 'vant'
import { CloudUpload, FolderOpen, Image as ImageIcon, RefreshCw, Trash2, Wifi } from '@lucide/vue'
import { formatDateTime } from '@repo/drive-core/format'
import { MobileShell } from '@/components/common'
import FolderPickerSheet from '@/components/drive/FolderPickerSheet.vue'
import { networkStatus } from '@/lib/network'
import {
  buildMediaFingerprint,
  clearBackedUpLedger,
  filterUnbackedMedia,
  hasNativeBackgroundTransferPlugin,
  loadBackedUpLedger,
  loadPhotoBackupConfig,
  markBackedUpFingerprints,
  savePhotoBackupConfig,
  shouldRunAutoBackup,
} from '@/native/photo-backup'
import { enqueueFilesWithGuard } from '@/stores/uploads'

const ALBUM_OPTIONS = ['Camera', 'Screenshots', 'WeChat', 'Download'] as const

const config = ref(loadPhotoBackupConfig())
const ledgerCount = ref(loadBackedUpLedger().size)
const folderPickerOpen = ref(false)
const fileInputRef = ref<HTMLInputElement | null>(null)
const backingUp = ref(false)
const nativeBgReady = hasNativeBackgroundTransferPlugin()

const statusCheck = computed(() =>
  shouldRunAutoBackup(config.value, {
    online: networkStatus.online,
    isWifi: networkStatus.kind === 'wifi',
    isCharging: true,
  }),
)

function updateConfig(patch: Partial<typeof config.value>) {
  config.value = savePhotoBackupConfig(patch)
}

function toggleAlbum(album: string) {
  const cur = new Set(config.value.selectedAlbums)
  if (cur.has(album)) cur.delete(album)
  else cur.add(album)
  updateConfig({ selectedAlbums: Array.from(cur) })
}

function handleFolderPicked(payload: { targetSpaceId: string; targetParentId: string; targetLabel: string }) {
  updateConfig({
    targetSpaceId: payload.targetSpaceId,
    targetParentId: payload.targetParentId,
    targetLabel: payload.targetLabel,
  })
  showToast('已更新相册备份目标目录')
}

function triggerManualBackupPick() {
  if (!config.value.targetSpaceId) {
    showToast('请先选择备份目标目录')
    folderPickerOpen.value = true
    return
  }
  fileInputRef.value?.click()
}

async function handleBackupFilesSelected(event: Event) {
  const input = event.target as HTMLInputElement
  const rawFiles = input.files ? Array.from(input.files) : []
  input.value = ''
  if (!rawFiles.length || !config.value.targetSpaceId) return

  const ledger = loadBackedUpLedger()
  const freshFiles = filterUnbackedMedia(rawFiles, ledger)
  const skipped = rawFiles.length - freshFiles.length
  if (freshFiles.length === 0) {
    showToast(`所选 ${rawFiles.length} 个媒体均已备份，无需重复上传`)
    return
  }

  backingUp.value = true
  try {
    const enqueued = await enqueueFilesWithGuard(
      config.value.targetSpaceId,
      config.value.targetParentId || '0',
      freshFiles,
    )
    if (enqueued) {
      ledgerCount.value = markBackedUpFingerprints(freshFiles.map(buildMediaFingerprint))
      updateConfig({ lastRunAt: new Date().toISOString() })
      showToast(
        skipped > 0
          ? `已增量入队 ${freshFiles.length} 项，跳过已备份 ${skipped} 项`
          : `已增量入队 ${freshFiles.length} 项媒体备份`,
      )
    }
  } finally {
    backingUp.value = false
  }
}

async function handleResetLedger() {
  try {
    await showConfirmDialog({
      title: '重置增量备份记录',
      message: '清空本地已备份指纹记录后，下次扫描将重新比对（云端相同文件仍可通过 SHA-256 秒传去重）。',
      confirmButtonText: '确认重置',
    })
  } catch {
    return
  }
  clearBackedUpLedger()
  ledgerCount.value = 0
  showToast('已重置本地增量记录')
}
</script>

<template>
  <MobileShell title="相册自动增量备份" fallback-path="/me" padded>
    <div class="space-y-4 pb-6">
      <section class="rounded-2xl border border-border bg-card p-4 space-y-3.5">
        <div class="flex items-center justify-between gap-3">
          <div class="flex items-center gap-2.5 min-w-0">
            <span class="flex h-10 w-10 items-center justify-center rounded-xl bg-primary/10 text-primary shrink-0">
              <CloudUpload :size="20" />
            </span>
            <div class="min-w-0">
              <p class="text-sm font-semibold text-foreground">相册自动增量备份</p>
              <p class="text-xs text-muted-foreground">
                {{ nativeBgReady ? '原生后台保活传输引擎已就绪' : '结合分片断点续传与 SHA-256 秒传去重' }}
              </p>
            </div>
          </div>
          <VanSwitch
            :model-value="config.enabled"
            size="22px"
            @update:model-value="v => updateConfig({ enabled: Boolean(v) })"
          />
        </div>

        <div class="rounded-xl bg-muted/60 px-3 py-2 text-xs text-muted-foreground flex items-center justify-between gap-2">
          <span>{{ statusCheck.allowed ? '当前满足自动备份条件' : statusCheck.reason }}</span>
          <span class="shrink-0 tabular-nums font-medium text-foreground">已记录 {{ ledgerCount }} 项</span>
        </div>
      </section>

      <!-- 备份目标与条件 -->
      <section class="rounded-2xl border border-border bg-card p-4 space-y-3.5">
        <button
          type="button"
          class="w-full flex items-center justify-between gap-3 text-left"
          @click="folderPickerOpen = true"
        >
          <div class="flex items-center gap-2.5 min-w-0">
            <FolderOpen :size="18" class="text-primary shrink-0" />
            <div class="min-w-0">
              <p class="text-sm font-medium text-foreground">备份目标目录</p>
              <p class="text-xs text-muted-foreground truncate">{{ config.targetLabel }}</p>
            </div>
          </div>
          <span class="text-xs font-medium text-primary shrink-0">更改</span>
        </button>

        <div class="flex items-center justify-between gap-3 border-t border-border pt-3">
          <div class="flex items-center gap-2 min-w-0">
            <Wifi :size="16" class="text-muted-foreground shrink-0" />
            <div>
              <p class="text-sm font-medium text-foreground">仅 Wi-Fi 下备份</p>
              <p class="text-xs text-muted-foreground">使用蜂窝流量时自动暂停相册备份</p>
            </div>
          </div>
          <VanSwitch
            :model-value="config.wifiOnly"
            size="20px"
            @update:model-value="v => updateConfig({ wifiOnly: Boolean(v) })"
          />
        </div>

        <div class="flex items-center justify-between gap-3 border-t border-border pt-3">
          <div>
            <p class="text-sm font-medium text-foreground">仅充电时备份</p>
            <p class="text-xs text-muted-foreground">保护电池续航，仅在接入电源时后台传输</p>
          </div>
          <VanSwitch
            :model-value="config.chargingOnly"
            size="20px"
            @update:model-value="v => updateConfig({ chargingOnly: Boolean(v) })"
          />
        </div>
      </section>

      <!-- 本地相册范围 -->
      <section class="rounded-2xl border border-border bg-card p-4 space-y-3">
        <div class="flex items-center gap-2">
          <ImageIcon :size="16" class="text-primary" />
          <h3 class="text-sm font-semibold text-foreground">需要备份的相册</h3>
        </div>
        <div class="flex flex-wrap gap-2">
          <button
            v-for="album in ALBUM_OPTIONS"
            :key="album"
            type="button"
            class="rounded-full border px-3 py-1.5 text-xs font-medium transition-colors"
            :class="
              config.selectedAlbums.includes(album)
                ? 'border-primary bg-primary text-primary-foreground'
                : 'border-border bg-background text-muted-foreground'
            "
            @click="toggleAlbum(album)"
          >
            {{ album }}
          </button>
        </div>

        <p v-if="config.lastRunAt" class="text-xs text-muted-foreground">
          上次备份时间：{{ formatDateTime(config.lastRunAt) }}
        </p>
      </section>

      <!-- 立即增量备份操作 -->
      <div class="space-y-2.5">
        <input
          ref="fileInputRef"
          type="file"
          accept="image/*,video/*"
          multiple
          class="hidden"
          @change="handleBackupFilesSelected"
        />
        <VanButton
          block
          round
          type="primary"
          :loading="backingUp"
          class="!h-11"
          @click="triggerManualBackupPick"
        >
          <span class="inline-flex items-center gap-1.5">
            <RefreshCw :size="15" />
            <span>选择相册媒体立即增量备份</span>
          </span>
        </VanButton>

        <VanButton block round plain type="default" class="!h-10" @click="handleResetLedger">
          <span class="inline-flex items-center gap-1.5 text-xs">
            <Trash2 :size="14" />
            <span>重置本地增量指纹记录</span>
          </span>
        </VanButton>
      </div>
    </div>

    <FolderPickerSheet
      v-model:open="folderPickerOpen"
      mode="COPY"
      :source-space-id="config.targetSpaceId || '0'"
      title="选择相册备份目录"
      @confirm="handleFolderPicked"
    />
  </MobileShell>
</template>
