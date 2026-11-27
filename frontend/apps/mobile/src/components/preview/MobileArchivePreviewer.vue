<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { Button as VanButton, showToast } from 'vant'
import { ChevronRight, Download, File, Folder } from '@lucide/vue'
import { normalizeErrorMessage } from '@repo/drive-core/errors'
import { formatBytes } from '@repo/drive-core/format'
import { resolvePreviewKind } from '@repo/drive-core/preview-registry'
import { mobileDriveApi, type ArchiveEntry, type DriveNode } from '@/api/drive'
import {
  archiveBreadcrumbs,
  listArchiveDirectory,
  type ArchiveBrowserItem,
} from '@/lib/archive-tree'
import { triggerBlobDownload } from '@/lib/file-actions'
import { BottomSheet, EmptyState, ErrorState } from '@/components/common'

const props = defineProps<{
  node: DriveNode
  versionId?: string
}>()

const emit = defineEmits<{
  'toggle-chrome': []
}>()

const loading = ref(true)
const errorText = ref('')
const formatName = ref('')
const totalEntries = ref(0)
const truncated = ref(false)
const allEntries = ref<ArchiveEntry[]>([])
const currentDir = ref('')

// Extracted file quick viewer sheet
const extractingPath = ref('')
const previewOpen = ref(false)
const previewTitle = ref('')
const previewBlob = ref<Blob | null>(null)
const previewImageUrl = ref<string | null>(null)
const previewText = ref<string | null>(null)

let loadSeq = 0

const crumbs = computed(() => archiveBreadcrumbs(currentDir.value))
const items = computed(() => listArchiveDirectory(allEntries.value, currentDir.value))

function clearExtractPreview() {
  if (previewImageUrl.value) {
    URL.revokeObjectURL(previewImageUrl.value)
    previewImageUrl.value = null
  }
  previewText.value = null
  previewBlob.value = null
  previewTitle.value = ''
}

async function loadEntries() {
  const seq = ++loadSeq
  loading.value = true
  errorText.value = ''
  currentDir.value = ''
  try {
    const listing = await mobileDriveApi.archiveEntries(
      props.node.spaceId,
      props.node.id,
      props.versionId ?? props.node.currentVersionId ?? undefined,
    )
    if (seq !== loadSeq) return
    formatName.value = listing.format
    totalEntries.value = listing.totalEntries
    truncated.value = listing.truncated
    allEntries.value = listing.entries
  } catch (err) {
    if (seq !== loadSeq) return
    errorText.value = normalizeErrorMessage(err, '读取压缩包目录失败')
  } finally {
    if (seq === loadSeq) loading.value = false
  }
}

watch(
  () => [props.node.spaceId, props.node.id, props.versionId ?? props.node.currentVersionId] as const,
  () => {
    clearExtractPreview()
    previewOpen.value = false
    void loadEntries()
  },
  { immediate: true },
)

onBeforeUnmount(() => {
  loadSeq += 1
  clearExtractPreview()
})

async function openItem(item: ArchiveBrowserItem) {
  if (item.directory) {
    currentDir.value = item.fullPath
    return
  }
  if (extractingPath.value) return
  extractingPath.value = item.fullPath
  try {
    const blob = await mobileDriveApi.archiveExtract(
      props.node.spaceId,
      props.node.id,
      item.fullPath,
      props.versionId ?? props.node.currentVersionId ?? undefined,
    )
    const kind = resolvePreviewKind(item.name, blob.type)
    clearExtractPreview()
    previewTitle.value = item.name
    previewBlob.value = blob

    if (kind === 'image') {
      previewImageUrl.value = URL.createObjectURL(blob)
      previewOpen.value = true
    } else if ((kind === 'text' || kind === 'code' || kind === 'markdown') && blob.size <= 512_000) {
      previewText.value = await blob.text()
      previewOpen.value = true
    } else {
      triggerBlobDownload(blob, item.name)
      showToast(`已提取并下载 ${item.name}`)
    }
  } catch (err) {
    showToast(normalizeErrorMessage(err, '提取压缩包文件失败'))
  } finally {
    extractingPath.value = ''
  }
}

function downloadExtracted() {
  if (!previewBlob.value || !previewTitle.value) return
  triggerBlobDownload(previewBlob.value, previewTitle.value)
}
</script>

<template>
  <div class="flex-1 min-h-0 flex flex-col bg-background text-foreground">
    <!-- 归档信息与面包屑 -->
    <div class="border-b border-border bg-card/80 px-3.5 py-2 shrink-0 space-y-1.5">
      <div class="flex items-center justify-between text-[11px] text-muted-foreground">
        <span>{{ formatName || 'ARCHIVE' }} · 共 {{ totalEntries }} 项</span>
        <span v-if="truncated" class="text-amber-600 dark:text-amber-400">条目过多已截断展示</span>
      </div>

      <div class="flex items-center gap-1 overflow-x-auto whitespace-nowrap text-xs text-muted-foreground no-scrollbar">
        <button
          type="button"
          class="shrink-0 rounded px-1.5 py-0.5 active:bg-accent"
          :class="crumbs.length === 0 ? 'font-semibold text-foreground' : ''"
          @click="currentDir = ''"
        >
          根目录
        </button>
        <template v-for="(crumb, idx) in crumbs" :key="crumb.path">
          <ChevronRight :size="12" class="shrink-0 opacity-60" />
          <button
            type="button"
            class="shrink-0 max-w-[130px] truncate rounded px-1.5 py-0.5 active:bg-accent"
            :class="idx === crumbs.length - 1 ? 'font-semibold text-foreground' : ''"
            @click="currentDir = crumb.path"
          >
            {{ crumb.label }}
          </button>
        </template>
      </div>
    </div>

    <div class="flex-1 min-h-0 overflow-y-auto px-3.5 pb-24" @click="emit('toggle-chrome')">
      <div v-if="loading" class="py-16 text-center text-sm text-muted-foreground">正在解析压缩包…</div>
      <ErrorState v-else-if="errorText" :message="errorText" @retry="loadEntries" />
      <EmptyState v-else-if="items.length === 0" title="当前归档目录为空" />
      <div v-else class="divide-y divide-border" @click.stop>
        <button
          v-for="item in items"
          :key="item.fullPath"
          type="button"
          class="w-full flex items-center gap-3 py-3 text-left active:bg-accent/50"
          :disabled="extractingPath === item.fullPath"
          @click="openItem(item)"
        >
          <span
            class="flex h-9 w-9 items-center justify-center rounded-xl shrink-0"
            :class="item.directory ? 'bg-primary/10 text-primary' : 'bg-muted text-foreground'"
          >
            <Folder v-if="item.directory" :size="18" />
            <File v-else :size="18" />
          </span>
          <div class="flex-1 min-w-0">
            <p class="text-sm font-medium truncate text-foreground">{{ item.name }}</p>
            <p class="text-xs text-muted-foreground mt-0.5">
              {{
                extractingPath === item.fullPath
                  ? '正在提取…'
                  : item.directory
                    ? '文件夹'
                    : formatBytes(item.size)
              }}
            </p>
          </div>
          <ChevronRight v-if="item.directory" :size="16" class="text-muted-foreground shrink-0" />
          <Download v-else :size="15" class="text-muted-foreground shrink-0" />
        </button>
      </div>
    </div>

    <!-- 提取文件快速预览 Sheet -->
    <BottomSheet
      v-model:open="previewOpen"
      :title="previewTitle"
      max-height="85vh"
      @close="clearExtractPreview"
    >
      <div class="space-y-3 pb-2">
        <img
          v-if="previewImageUrl"
          :src="previewImageUrl"
          :alt="previewTitle"
          class="w-full max-h-[60vh] object-contain rounded-xl bg-muted"
        />
        <pre
          v-else-if="previewText !== null"
          class="text-xs font-mono whitespace-pre-wrap break-all rounded-xl bg-muted/50 p-3 max-h-[60vh] overflow-auto text-foreground"
        >{{ previewText }}</pre>

        <VanButton block round type="primary" @click="downloadExtracted">
          下载此文件
        </VanButton>
      </div>
    </BottomSheet>
  </div>
</template>
