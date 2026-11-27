<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { Button as VanButton, Checkbox as VanCheckbox, showConfirmDialog, showToast } from 'vant'
import { RotateCcw, Trash2 } from '@lucide/vue'
import { canWriteInRole } from '@repo/drive-core/categories'
import { normalizeErrorMessage } from '@repo/drive-core/errors'
import { formatBytes, formatRelativeTime } from '@repo/drive-core/format'
import { mobileDriveApi, type DriveNode, type Space } from '@/api/drive'
import { fetchSpaces, visibleSpaces } from '@/stores/spaces'
import { EmptyState, ErrorState, MobileShell, PullRefresh, SkeletonList } from '@/components/common'
import NodeThumb from '@/components/drive/NodeThumb.vue'

interface TrashEntry {
  node: DriveNode
  space: Space
}

const activeSpaceId = ref<string>('ALL')
const entries = ref<TrashEntry[]>([])
const loading = ref(true)
const refreshing = ref(false)
const error = ref('')
const busy = ref(false)

// Multi-select mode
const selecting = ref(false)
const selectedIds = ref<Set<string>>(new Set())

let loadSeq = 0

const activeSpace = computed<Space | null>(() =>
  activeSpaceId.value === 'ALL'
    ? null
    : visibleSpaces.value.find(s => s.id === activeSpaceId.value) ?? null,
)

const canEmptyCurrentSpace = computed(
  () => Boolean(activeSpace.value && canWriteInRole(activeSpace.value.role) && entries.value.length > 0),
)

const selectableEntries = computed(() =>
  entries.value.filter(e => canWriteInRole(e.space.role)),
)

const allSelected = computed(
  () =>
    selectableEntries.value.length > 0 &&
    selectableEntries.value.every(e => selectedIds.value.has(`${e.space.id}:${e.node.id}`)),
)

function entryKey(entry: TrashEntry): string {
  return `${entry.space.id}:${entry.node.id}`
}

async function loadTrash() {
  const seq = ++loadSeq
  if (!refreshing.value) loading.value = true
  error.value = ''

  const targetSpaces =
    activeSpaceId.value === 'ALL'
      ? visibleSpaces.value
      : visibleSpaces.value.filter(s => s.id === activeSpaceId.value)

  if (targetSpaces.length === 0) {
    entries.value = []
    loading.value = false
    refreshing.value = false
    return
  }

  try {
    const results = await Promise.all(
      targetSpaces.map(async space => {
        const res = await mobileDriveApi.nodes(space.id, {
          trash: true,
          page: 1,
          pageSize: 100,
          sort: 'updatedAt',
          order: 'desc',
        })
        return res.items.map(node => ({ node, space }))
      }),
    )
    if (seq !== loadSeq) return
    entries.value = results
      .flat()
      .sort((a, b) => new Date(b.node.updatedAt).getTime() - new Date(a.node.updatedAt).getTime())
    selectedIds.value = new Set()
  } catch (err) {
    if (seq !== loadSeq) return
    error.value = normalizeErrorMessage(err, '加载回收站失败')
  } finally {
    if (seq === loadSeq) {
      loading.value = false
      refreshing.value = false
    }
  }
}

onMounted(async () => {
  await fetchSpaces()
  await loadTrash()
})

function selectSpaceFilter(spaceId: string) {
  if (activeSpaceId.value === spaceId) return
  activeSpaceId.value = spaceId
  selecting.value = false
  selectedIds.value = new Set()
  void loadTrash()
}

function toggleSelectMode() {
  selecting.value = !selecting.value
  selectedIds.value = new Set()
}

function toggleSelect(entry: TrashEntry) {
  if (!canWriteInRole(entry.space.role)) return
  const key = entryKey(entry)
  const next = new Set(selectedIds.value)
  if (next.has(key)) next.delete(key)
  else next.add(key)
  selectedIds.value = next
}

function toggleSelectAll() {
  if (allSelected.value) {
    selectedIds.value = new Set()
  } else {
    selectedIds.value = new Set(selectableEntries.value.map(entryKey))
  }
}

async function restoreOne(entry: TrashEntry) {
  if (busy.value) return
  busy.value = true
  try {
    await mobileDriveApi.restoreNode({ id: entry.node.id, spaceId: entry.space.id })
    showToast(`已还原「${entry.node.name}」`)
    await loadTrash()
  } catch (err) {
    showToast(normalizeErrorMessage(err, '还原失败'))
  } finally {
    busy.value = false
  }
}

async function purgeOne(entry: TrashEntry) {
  if (busy.value) return
  try {
    await showConfirmDialog({
      title: '彻底删除',
      message: `确定彻底删除「${entry.node.name}」吗？此操作不可恢复。`,
      confirmButtonText: '彻底删除',
      confirmButtonColor: 'var(--destructive)',
    })
  } catch {
    return
  }

  busy.value = true
  try {
    await mobileDriveApi.purgeTrashNode(entry.space.id, entry.node.id)
    showToast('已彻底删除')
    await loadTrash()
  } catch (err) {
    showToast(normalizeErrorMessage(err, '彻底删除失败'))
  } finally {
    busy.value = false
  }
}

async function batchRestore() {
  const targets = entries.value.filter(e => selectedIds.value.has(entryKey(e)))
  if (targets.length === 0 || busy.value) return
  busy.value = true
  let ok = 0
  let fail = 0
  for (const item of targets) {
    try {
      await mobileDriveApi.restoreNode({ id: item.node.id, spaceId: item.space.id })
      ok += 1
    } catch {
      fail += 1
    }
  }
  busy.value = false
  selecting.value = false
  showToast(fail === 0 ? `已还原 ${ok} 项` : `已还原 ${ok} 项，失败 ${fail} 项`)
  await loadTrash()
}

async function batchPurge() {
  const targets = entries.value.filter(e => selectedIds.value.has(entryKey(e)))
  if (targets.length === 0 || busy.value) return
  try {
    await showConfirmDialog({
      title: '批量彻底删除',
      message: `确定彻底删除选中的 ${targets.length} 项吗？此操作不可恢复。`,
      confirmButtonText: '彻底删除',
      confirmButtonColor: 'var(--destructive)',
    })
  } catch {
    return
  }

  busy.value = true
  let ok = 0
  let fail = 0
  for (const item of targets) {
    try {
      await mobileDriveApi.purgeTrashNode(item.space.id, item.node.id)
      ok += 1
    } catch {
      fail += 1
    }
  }
  busy.value = false
  selecting.value = false
  showToast(fail === 0 ? `已彻底删除 ${ok} 项` : `已删除 ${ok} 项，失败 ${fail} 项`)
  await loadTrash()
}

async function emptyCurrentSpaceTrash() {
  const space = activeSpace.value
  if (!space || busy.value) return
  try {
    await showConfirmDialog({
      title: '清空回收站',
      message: `确定清空「${space.name}」回收站中的全部 ${entries.value.length} 项吗？此操作不可恢复。`,
      confirmButtonText: '确认清空',
      confirmButtonColor: 'var(--destructive)',
    })
  } catch {
    return
  }

  busy.value = true
  try {
    await mobileDriveApi.emptyTrash(space.id)
    showToast(`已清空「${space.name}」回收站`)
    selecting.value = false
    await loadTrash()
  } catch (err) {
    showToast(normalizeErrorMessage(err, '清空回收站失败'))
  } finally {
    busy.value = false
  }
}

async function onRefresh() {
  refreshing.value = true
  await fetchSpaces(true)
  await loadTrash()
}
</script>

<template>
  <MobileShell title="回收站" back-to="/me">
    <template #right>
      <div class="flex items-center gap-1">
        <button
          v-if="selectableEntries.length > 0"
          type="button"
          class="rounded-lg px-2 py-1 text-xs font-medium text-primary active:bg-accent"
          @click="toggleSelectMode"
        >
          {{ selecting ? '取消' : '多选' }}
        </button>
      </div>
    </template>

    <div class="flex-1 min-h-0 flex flex-col">
      <!-- 空间筛选胶囊条 + 清空回收站 -->
      <div class="flex items-center justify-between gap-2 border-b border-border bg-background px-3.5 py-2">
        <div class="flex items-center gap-1.5 overflow-x-auto no-scrollbar min-w-0">
          <button
            type="button"
            class="shrink-0 rounded-full px-3 py-1 text-xs transition-colors"
            :class="
              activeSpaceId === 'ALL'
                ? 'bg-primary text-primary-foreground font-medium'
                : 'bg-muted text-muted-foreground active:bg-accent'
            "
            @click="selectSpaceFilter('ALL')"
          >
            全部空间
          </button>
          <button
            v-for="space in visibleSpaces"
            :key="space.id"
            type="button"
            class="shrink-0 rounded-full px-3 py-1 text-xs transition-colors max-w-[120px] truncate"
            :class="
              activeSpaceId === space.id
                ? 'bg-primary text-primary-foreground font-medium'
                : 'bg-muted text-muted-foreground active:bg-accent'
            "
            @click="selectSpaceFilter(space.id)"
          >
            {{ space.name }}
          </button>
        </div>

        <button
          v-if="canEmptyCurrentSpace && !selecting"
          type="button"
          class="shrink-0 rounded-lg px-2 py-1 text-xs font-medium text-destructive active:bg-destructive/10"
          :disabled="busy"
          @click="emptyCurrentSpaceTrash"
        >
          清空
        </button>
      </div>

      <PullRefresh v-model="refreshing" @refresh="onRefresh">
        <SkeletonList v-if="loading" :count="6" />
        <ErrorState v-else-if="error" :message="error" @retry="loadTrash" />
        <EmptyState
          v-else-if="entries.length === 0"
          title="回收站为空"
          description="删除的文件和文件夹会暂存在这里，支持随时还原"
        />
        <div v-else class="divide-y divide-border px-3.5 pb-24">
          <div
            v-for="entry in entries"
            :key="entryKey(entry)"
            class="flex items-center gap-3 py-3"
            @click="selecting ? toggleSelect(entry) : undefined"
          >
            <VanCheckbox
              v-if="selecting"
              :model-value="selectedIds.has(entryKey(entry))"
              :disabled="!canWriteInRole(entry.space.role)"
              @click.stop
              @update:model-value="toggleSelect(entry)"
            />

            <NodeThumb :node="entry.node" variant="list" />

            <div class="flex-1 min-w-0">
              <p class="text-sm font-medium truncate text-foreground">{{ entry.node.name }}</p>
              <p class="text-xs text-muted-foreground mt-0.5 truncate">
                <span v-if="activeSpaceId === 'ALL'">{{ entry.space.name }} · </span>
                {{ entry.node.nodeType === 'FOLDER' ? '文件夹' : formatBytes(entry.node.size) }} ·
                {{ formatRelativeTime(entry.node.updatedAt) }}
              </p>
            </div>

            <div
              v-if="!selecting && canWriteInRole(entry.space.role)"
              class="flex items-center gap-1 shrink-0"
            >
              <button
                type="button"
                class="inline-flex h-8 w-8 items-center justify-center rounded-full text-primary active:bg-primary/10"
                aria-label="还原"
                :disabled="busy"
                @click="restoreOne(entry)"
              >
                <RotateCcw :size="16" />
              </button>
              <button
                type="button"
                class="inline-flex h-8 w-8 items-center justify-center rounded-full text-destructive active:bg-destructive/10"
                aria-label="彻底删除"
                :disabled="busy"
                @click="purgeOne(entry)"
              >
                <Trash2 :size="16" />
              </button>
            </div>
          </div>
        </div>
      </PullRefresh>

      <!-- 多选底部批量操作栏 -->
      <div
        v-if="selecting"
        class="fixed inset-x-0 bottom-0 z-30 border-t border-border bg-card/95 backdrop-blur px-4 py-2.5 safe-bottom flex items-center justify-between gap-3"
      >
        <button
          type="button"
          class="text-xs font-medium text-foreground active:opacity-70"
          @click="toggleSelectAll"
        >
          {{ allSelected ? '取消全选' : `全选（${selectedIds.size}/${selectableEntries.length}）` }}
        </button>

        <div class="flex items-center gap-2">
          <VanButton
            size="small"
            round
            plain
            type="primary"
            :disabled="selectedIds.size === 0 || busy"
            :loading="busy"
            @click="batchRestore"
          >
            还原 ({{ selectedIds.size }})
          </VanButton>
          <VanButton
            size="small"
            round
            type="danger"
            :disabled="selectedIds.size === 0 || busy"
            :loading="busy"
            @click="batchPurge"
          >
            彻底删除
          </VanButton>
        </div>
      </div>
    </div>
  </MobileShell>
</template>
