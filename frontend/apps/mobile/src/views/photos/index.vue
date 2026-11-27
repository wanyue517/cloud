<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { showConfirmDialog, showToast } from 'vant'
import { Check, CheckSquare, Download, Image as ImageIcon, Play, Trash2, X } from '@lucide/vue'
import { normalizeErrorMessage } from '@repo/drive-core/errors'
import { mobileDriveApi, type DriveNode } from '@/api/drive'
import { formatMediaTime } from '@/lib/archive-tree'
import { groupTimelineByDate, type TimelineMediaItem } from '@/lib/timeline'
import { EmptyState, ErrorState, MobileShell, SkeletonList } from '@/components/common'
import NodeThumb from '@/components/drive/NodeThumb.vue'
import MobilePreviewShell from '@/components/preview/MobilePreviewShell.vue'

type FilterKind = 'ALL' | 'IMAGE' | 'VIDEO'

const filter = ref<FilterKind>('ALL')
const items = ref<TimelineMediaItem[]>([])
const total = ref(0)
const page = ref(1)
const hasMore = ref(false)
const loading = ref(true)
const loadingMore = ref(false)
const errorText = ref('')

// Preview & multi-select
const previewNode = ref<DriveNode | null>(null)
const selectMode = ref(false)
const selectedIds = ref<Set<string>>(new Set())
const busyAction = ref(false)

const dateGroups = computed(() => groupTimelineByDate(items.value))

const allPreviewNodes = computed<DriveNode[]>(() => items.value.map(toDriveNode))

function toDriveNode(item: TimelineMediaItem): DriveNode {
  return {
    id: item.id,
    spaceId: item.spaceId,
    parentId: item.parentId,
    name: item.name,
    nodeType: 'FILE',
    currentVersionId: item.currentVersionId ?? null,
    revision: item.revision,
    size: item.size,
    mimeType: item.mimeType,
    hidden: false,
    updatedAt: item.takenAt,
    hasChildren: false,
  }
}

async function loadTimeline(reset = true) {
  if (reset) {
    loading.value = true
    page.value = 1
    errorText.value = ''
  } else {
    if (!hasMore.value || loadingMore.value) return
    loadingMore.value = true
  }
  try {
    const res = await mobileDriveApi.mediaTimeline({
      mediaType: filter.value,
      page: reset ? 1 : page.value + 1,
      pageSize: 60,
    })
    if (reset) {
      items.value = res.items
      page.value = 1
    } else {
      items.value = [...items.value, ...res.items]
      page.value += 1
    }
    total.value = res.total
    hasMore.value = res.hasMore
  } catch (err) {
    if (reset) errorText.value = normalizeErrorMessage(err, '加载云端相册失败')
    else showToast(normalizeErrorMessage(err, '加载更多照片失败'))
  } finally {
    loading.value = false
    loadingMore.value = false
  }
}

function setFilter(next: FilterKind) {
  if (filter.value === next) return
  filter.value = next
  exitSelectMode()
  void loadTimeline(true)
}

function exitSelectMode() {
  selectMode.value = false
  selectedIds.value = new Set()
}

function toggleSelect(id: string) {
  const next = new Set(selectedIds.value)
  if (next.has(id)) next.delete(id)
  else next.add(id)
  selectedIds.value = next
}

function handleItemClick(item: TimelineMediaItem) {
  if (selectMode.value) {
    toggleSelect(item.id)
    return
  }
  previewNode.value = toDriveNode(item)
}

function scrollToDateGroup(date: string) {
  const el = document.getElementById(`timeline-group-${date}`)
  el?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

async function handleBatchDownload() {
  if (selectedIds.value.size === 0 || busyAction.value) return
  const chosen = items.value.filter(i => selectedIds.value.has(i.id))
  const bySpace = new Map<string, string[]>()
  for (const it of chosen) {
    const list = bySpace.get(it.spaceId) ?? []
    list.push(it.id)
    bySpace.set(it.spaceId, list)
  }
  busyAction.value = true
  try {
    for (const [spaceId, ids] of bySpace.entries()) {
      const job = await mobileDriveApi.createZipBatch(spaceId, ids)
      const blob = await mobileDriveApi.downloadZipBatch(job.id)
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = `cloudlane-photos-${Date.now()}.zip`
      a.click()
      setTimeout(() => URL.revokeObjectURL(url), 5000)
    }
    showToast(`已开始下载 ${chosen.length} 项媒体`)
    exitSelectMode()
  } catch (err) {
    showToast(normalizeErrorMessage(err, '批量下载失败'))
  } finally {
    busyAction.value = false
  }
}

async function handleBatchDelete() {
  if (selectedIds.value.size === 0 || busyAction.value) return
  const chosen = items.value.filter(i => selectedIds.value.has(i.id))
  try {
    await showConfirmDialog({
      title: '移入回收站',
      message: `确定将选中的 ${chosen.length} 项照片/视频移入回收站吗？`,
      confirmButtonText: '确认删除',
      confirmButtonColor: 'var(--destructive)',
    })
  } catch {
    return
  }
  busyAction.value = true
  try {
    const bySpace = new Map<string, string[]>()
    for (const it of chosen) {
      const list = bySpace.get(it.spaceId) ?? []
      list.push(it.id)
      bySpace.set(it.spaceId, list)
    }
    for (const [spaceId, ids] of bySpace.entries()) {
      await mobileDriveApi.executeBatch(spaceId, { action: 'DELETE', nodeIds: ids })
    }
    showToast(`已将 ${chosen.length} 项移入回收站`)
    exitSelectMode()
    await loadTimeline(true)
  } catch (err) {
    showToast(normalizeErrorMessage(err, '批量删除失败'))
  } finally {
    busyAction.value = false
  }
}

onMounted(() => {
  void loadTimeline(true)
})
</script>

<template>
  <MobileShell title="云端相册" fallback-path="/home">
    <template #right>
      <button
        v-if="!selectMode && items.length > 0"
        type="button"
        class="inline-flex items-center gap-1 rounded-full px-2.5 py-1 text-xs font-medium text-foreground active:bg-accent"
        @click="selectMode = true"
      >
        <CheckSquare :size="14" />
        <span>选择</span>
      </button>
      <button
        v-else-if="selectMode"
        type="button"
        class="inline-flex items-center gap-1 rounded-full px-2.5 py-1 text-xs font-medium text-muted-foreground active:bg-accent"
        @click="exitSelectMode"
      >
        <X :size="14" />
        <span>取消</span>
      </button>
    </template>

    <!-- 筛选胶囊 -->
    <div class="sticky top-0 z-10 flex items-center justify-between gap-2 border-b border-border bg-background/95 backdrop-blur px-4 py-2">
      <div class="flex items-center gap-1.5">
        <button
          v-for="tab in ([
            { id: 'ALL', label: '全部' },
            { id: 'IMAGE', label: '照片' },
            { id: 'VIDEO', label: '视频' },
          ] as const)"
          :key="tab.id"
          type="button"
          class="rounded-full px-3 py-1 text-xs font-medium transition-colors"
          :class="
            filter === tab.id
              ? 'bg-primary text-primary-foreground'
              : 'bg-muted text-muted-foreground active:bg-accent'
          "
          @click="setFilter(tab.id)"
        >
          {{ tab.label }}
        </button>
      </div>
      <span class="text-xs text-muted-foreground tabular-nums">共 {{ total }} 项</span>
    </div>

    <div class="relative flex-1 min-h-0 px-2 py-3 pb-24">
      <SkeletonList v-if="loading" :rows="6" />
      <ErrorState v-else-if="errorText" :message="errorText" @retry="loadTimeline(true)" />
      <EmptyState
        v-else-if="items.length === 0"
        :icon="ImageIcon"
        title="暂无照片与视频"
        description="上传的照片与视频会按拍摄时间自动归档在这里（私密空间与隐藏项不会出现）"
      />

      <div v-else class="space-y-5 pr-4">
        <section
          v-for="group in dateGroups"
          :id="`timeline-group-${group.date}`"
          :key="group.date"
          class="space-y-2"
        >
          <div class="flex items-center justify-between px-1.5">
            <h2 class="text-xs font-semibold text-foreground">{{ group.label }}</h2>
            <span class="text-[11px] text-muted-foreground tabular-nums">{{ group.items.length }} 项</span>
          </div>

          <div class="grid grid-cols-3 gap-1">
            <button
              v-for="item in group.items"
              :key="item.id"
              type="button"
              class="relative aspect-square overflow-hidden rounded-lg bg-muted active:opacity-85"
              @click="handleItemClick(item)"
            >
              <NodeThumb :node="toDriveNode(item)" variant="grid" class="!w-full !h-full" />

              <!-- 视频标识与时长 -->
              <span
                v-if="item.mediaType === 'VIDEO'"
                class="pointer-events-none absolute bottom-1 right-1 inline-flex items-center gap-0.5 rounded bg-black/65 px-1.5 py-0.5 text-[10px] text-white tabular-nums"
              >
                <Play :size="9" fill="currentColor" />
                <span v-if="item.durationSeconds">{{ formatMediaTime(item.durationSeconds) }}</span>
              </span>

              <!-- 多选复选圆点 -->
              <span
                v-if="selectMode"
                class="absolute top-1.5 right-1.5 flex h-5 w-5 items-center justify-center rounded-full border-2 transition-colors"
                :class="
                  selectedIds.has(item.id)
                    ? 'border-primary bg-primary text-primary-foreground'
                    : 'border-white bg-black/35 text-transparent'
                "
              >
                <Check :size="12" />
              </span>
            </button>
          </div>
        </section>

        <div v-if="hasMore" class="pt-2 text-center">
          <button
            type="button"
            class="rounded-full border border-border bg-card px-5 py-2 text-xs font-medium text-foreground active:bg-accent"
            :disabled="loadingMore"
            @click="loadTimeline(false)"
          >
            {{ loadingMore ? '加载中…' : '加载更早的照片' }}
          </button>
        </div>
      </div>

      <!-- 右侧时间轴快速定位条 -->
      <aside
        v-if="dateGroups.length > 2"
        class="fixed right-1 top-1/2 -translate-y-1/2 z-20 flex flex-col items-center gap-1 rounded-full bg-card/85 px-1 py-2 shadow-sm border border-border backdrop-blur max-h-[55vh] overflow-y-auto no-scrollbar"
        aria-label="时间轴快速定位"
      >
        <button
          v-for="g in dateGroups.slice(0, 14)"
          :key="g.date"
          type="button"
          class="rounded-full px-1 py-0.5 text-[9px] font-medium text-muted-foreground active:text-primary active:bg-primary/10 tabular-nums"
          @click="scrollToDateGroup(g.date)"
        >
          {{ g.date.slice(5, 10) }}
        </button>
      </aside>
    </div>

    <!-- 多选底部批量操作栏 -->
    <div
      v-if="selectMode"
      class="fixed inset-x-0 bottom-0 z-30 flex items-center justify-between border-t border-border bg-card/95 px-4 py-3 pb-[calc(0.75rem+env(safe-area-inset-bottom))] backdrop-blur"
    >
      <span class="text-xs font-medium text-foreground">已选 {{ selectedIds.size }} 项</span>
      <div class="flex items-center gap-2">
        <button
          type="button"
          class="inline-flex items-center gap-1 rounded-full bg-primary/10 px-3.5 py-1.5 text-xs font-medium text-primary disabled:opacity-40"
          :disabled="selectedIds.size === 0 || busyAction"
          @click="handleBatchDownload"
        >
          <Download :size="14" />
          <span>打包下载</span>
        </button>
        <button
          type="button"
          class="inline-flex items-center gap-1 rounded-full bg-destructive/10 px-3.5 py-1.5 text-xs font-medium text-destructive disabled:opacity-40"
          :disabled="selectedIds.size === 0 || busyAction"
          @click="handleBatchDelete"
        >
          <Trash2 :size="14" />
          <span>删除</span>
        </button>
      </div>
    </div>

    <!-- 沉浸式全屏媒体预览 -->
    <MobilePreviewShell
      v-if="previewNode"
      :open="Boolean(previewNode)"
      :node="previewNode"
      :siblings="allPreviewNodes"
      @close="previewNode = null"
      @update:node="(n: DriveNode) => (previewNode = n)"
    />
  </MobileShell>
</template>
