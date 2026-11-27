<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { Button as VanButton, showConfirmDialog, showToast } from 'vant'
import { Eye, History, RotateCcw } from '@lucide/vue'
import { canWriteInRole } from '@repo/drive-core/categories'
import { isConflictError, normalizeErrorMessage } from '@repo/drive-core/errors'
import { formatBytes, formatDateTime } from '@repo/drive-core/format'
import { mobileDriveApi, type DriveNode, type FileVersion, type Space } from '@/api/drive'
import { BottomSheet, EmptyState, ErrorState, SkeletonList } from '@/components/common'
import MobilePreviewShell from '@/components/preview/MobilePreviewShell.vue'

const props = defineProps<{
  open: boolean
  node: DriveNode | null
  space: Space | null
}>()

const emit = defineEmits<{
  'update:open': [value: boolean]
  restored: []
}>()

const versions = ref<FileVersion[]>([])
const loading = ref(false)
const error = ref('')
const restoringId = ref('')
const previewVersionId = ref<string | undefined>(undefined)
const previewOpen = ref(false)

const writable = computed(() => canWriteInRole(props.space?.role))

async function loadVersions() {
  if (!props.node) return
  loading.value = true
  error.value = ''
  try {
    versions.value = await mobileDriveApi.versions(props.node.spaceId, props.node.id)
  } catch (err) {
    error.value = normalizeErrorMessage(err, '加载历史版本失败')
  } finally {
    loading.value = false
  }
}

watch(
  () => [props.open, props.node?.id] as const,
  ([opened, nodeId]) => {
    if (opened && nodeId) {
      previewOpen.value = false
      previewVersionId.value = undefined
      void loadVersions()
    }
  },
)

function previewVersion(ver: FileVersion) {
  previewVersionId.value = ver.id
  previewOpen.value = true
}

async function handleRestore(ver: FileVersion) {
  const node = props.node
  if (!node || !writable.value || restoringId.value) return
  try {
    await showConfirmDialog({
      title: '恢复历史版本',
      message: `确定将「${node.name}」恢复为版本 v${ver.number} 吗？系统将生成一个新的当前版本，不会破坏已有历史记录。`,
      confirmButtonText: '确认恢复',
    })
  } catch {
    return
  }

  restoringId.value = ver.id
  try {
    await mobileDriveApi.restoreVersion(node.spaceId, node.id, ver.id, node.revision)
    showToast(`已恢复为 v${ver.number}`)
    emit('restored')
    await loadVersions()
  } catch (err) {
    if (isConflictError(err)) {
      showToast('文件已被其他端修改，已自动刷新')
      emit('restored')
      await loadVersions()
    } else {
      showToast(normalizeErrorMessage(err, '恢复版本失败'))
    }
  } finally {
    restoringId.value = ''
  }
}
</script>

<template>
  <BottomSheet
    :open="props.open"
    title="历史版本"
    :subtitle="props.node?.name"
    max-height="85vh"
    @update:open="emit('update:open', $event)"
  >
    <div class="pb-2">
      <SkeletonList v-if="loading" :count="4" />
      <ErrorState v-else-if="error" :message="error" @retry="loadVersions" />
      <EmptyState v-else-if="versions.length === 0" title="暂无历史版本记录" />
      <div v-else class="space-y-2.5">
        <div
          v-for="ver in versions"
          :key="ver.id"
          class="rounded-2xl border border-border bg-background p-3.5 space-y-2"
        >
          <div class="flex items-start justify-between gap-2">
            <div class="flex items-center gap-2 min-w-0">
              <span class="flex h-8 w-8 items-center justify-center rounded-xl bg-primary/10 text-primary shrink-0">
                <History :size="16" />
              </span>
              <div class="min-w-0">
                <div class="flex items-center gap-1.5">
                  <span class="text-sm font-semibold text-foreground">v{{ ver.number }}</span>
                  <span
                    v-if="props.node?.currentVersionId === ver.id"
                    class="rounded-full bg-primary/10 px-2 py-0.5 text-[10px] font-medium text-primary"
                  >
                    当前版本
                  </span>
                </div>
                <p class="text-[11px] text-muted-foreground mt-0.5">
                  {{ formatBytes(ver.size) }} · {{ formatDateTime(ver.createdAt) }}
                  <template v-if="ver.createdByName"> · {{ ver.createdByName }}</template>
                </p>
              </div>
            </div>
          </div>

          <div class="flex items-center justify-end gap-2 pt-1 border-t border-border/60">
            <VanButton size="mini" round plain type="primary" @click="previewVersion(ver)">
              <span class="inline-flex items-center gap-1">
                <Eye :size="12" />
                <span>预览</span>
              </span>
            </VanButton>
            <VanButton
              v-if="writable && props.node?.currentVersionId !== ver.id"
              size="mini"
              round
              type="primary"
              :loading="restoringId === ver.id"
              @click="handleRestore(ver)"
            >
              <span class="inline-flex items-center gap-1">
                <RotateCcw :size="12" />
                <span>恢复此版本</span>
              </span>
            </VanButton>
          </div>
        </div>
      </div>
    </div>

    <MobilePreviewShell
      :open="previewOpen"
      :node="props.node"
      :version-id="previewVersionId"
      @close="previewOpen = false"
    />
  </BottomSheet>
</template>
