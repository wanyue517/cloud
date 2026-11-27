<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { Button as VanButton, showToast } from 'vant'
import { ChevronRight, Folder, FolderPlus, Users } from '@lucide/vue'
import { normalizeErrorMessage } from '@repo/drive-core/errors'
import { mobileDriveApi, type DriveNode, type Space } from '@/api/drive'
import { filterWritableSpaces, isFolderPickBlocked } from '@/lib/spaces'
import { visibleSpaces } from '@/stores/spaces'
import { BottomSheet, EmptyState, SkeletonList } from '@/components/common'

const props = withDefaults(
  defineProps<{
    open: boolean
    mode: 'MOVE' | 'COPY'
    sourceSpaceId: string
    sourceParentId?: string
    excludeNodeIds?: string[]
    title?: string
    submitting?: boolean
  }>(),
  {
    sourceParentId: '0',
    excludeNodeIds: () => [],
    title: '',
    submitting: false,
  },
)

const emit = defineEmits<{
  'update:open': [value: boolean]
  confirm: [payload: { targetSpaceId: string; targetParentId: string; targetLabel: string }]
}>()

const targetSpaceId = ref('')
const currentFolderId = ref('0')
const breadcrumbs = ref<{ id: string; name: string }[]>([])
const folders = ref<DriveNode[]>([])
const loading = ref(false)
const errorText = ref('')

// Inline create folder inside picker
const creatingFolder = ref(false)
const newFolderName = ref('')
const creatingBusy = ref(false)

let fetchSeq = 0

const writableSpaces = computed<Space[]>(() => filterWritableSpaces(visibleSpaces.value))
const excludedSet = computed(() => new Set(props.excludeNodeIds))

const currentTargetSpace = computed<Space | null>(
  () => writableSpaces.value.find(s => s.id === targetSpaceId.value) ?? writableSpaces.value[0] ?? null,
)

const isSameLocationMove = computed(
  () =>
    props.mode === 'MOVE' &&
    targetSpaceId.value === props.sourceSpaceId &&
    currentFolderId.value === props.sourceParentId,
)

const isCurrentBlocked = computed(() =>
  isFolderPickBlocked(currentFolderId.value, targetSpaceId.value, props.sourceSpaceId, excludedSet.value),
)

const targetPathLabel = computed(() => {
  const spaceName = currentTargetSpace.value?.name || '目标空间'
  if (breadcrumbs.value.length === 0) return spaceName
  return `${spaceName} / ${breadcrumbs.value.map(b => b.name).join(' / ')}`
})

async function loadFolders() {
  if (!targetSpaceId.value) {
    folders.value = []
    return
  }
  const seq = ++fetchSeq
  loading.value = true
  errorText.value = ''
  try {
    const res = await mobileDriveApi.nodes(targetSpaceId.value, {
      parentId: currentFolderId.value,
      page: 1,
      pageSize: 200,
      sort: 'name',
      order: 'asc',
    })
    if (seq !== fetchSeq) return
    folders.value = res.items.filter(n => n.nodeType === 'FOLDER')
  } catch (err) {
    if (seq !== fetchSeq) return
    errorText.value = normalizeErrorMessage(err, '加载目录失败')
  } finally {
    if (seq === fetchSeq) loading.value = false
  }
}

watch(
  () => props.open,
  opened => {
    if (!opened) return
    creatingFolder.value = false
    newFolderName.value = ''
    const hasSource = writableSpaces.value.some(s => s.id === props.sourceSpaceId)
    targetSpaceId.value = hasSource ? props.sourceSpaceId : writableSpaces.value[0]?.id || ''
    currentFolderId.value = '0'
    breadcrumbs.value = []
    void loadFolders()
  },
)

function selectSpace(space: Space) {
  if (targetSpaceId.value === space.id) return
  targetSpaceId.value = space.id
  currentFolderId.value = '0'
  breadcrumbs.value = []
  creatingFolder.value = false
  void loadFolders()
}

function enterFolder(folder: DriveNode) {
  if (isFolderPickBlocked(folder.id, targetSpaceId.value, props.sourceSpaceId, excludedSet.value)) {
    showToast('不能选择自身或其子目录')
    return
  }
  currentFolderId.value = folder.id
  breadcrumbs.value = [...breadcrumbs.value, { id: folder.id, name: folder.name }]
  creatingFolder.value = false
  void loadFolders()
}

function jumpBreadcrumb(index: number) {
  if (index < 0) {
    currentFolderId.value = '0'
    breadcrumbs.value = []
  } else {
    const target = breadcrumbs.value[index]
    if (!target) return
    currentFolderId.value = target.id
    breadcrumbs.value = breadcrumbs.value.slice(0, index + 1)
  }
  creatingFolder.value = false
  void loadFolders()
}

async function submitNewFolder() {
  const name = newFolderName.value.trim()
  if (!name) {
    showToast('请输入文件夹名称')
    return
  }
  if (/[/\\]/.test(name)) {
    showToast('名称不能包含 / 或 \\')
    return
  }
  if (!targetSpaceId.value) return
  creatingBusy.value = true
  try {
    await mobileDriveApi.createFolder(targetSpaceId.value, currentFolderId.value, name)
    showToast('文件夹已创建')
    newFolderName.value = ''
    creatingFolder.value = false
    await loadFolders()
  } catch (err) {
    showToast(normalizeErrorMessage(err, '创建文件夹失败'))
  } finally {
    creatingBusy.value = false
  }
}

function handleConfirm() {
  if (!targetSpaceId.value || isCurrentBlocked.value) return
  if (isSameLocationMove.value) {
    showToast('已在当前目录，请选择其他位置')
    return
  }
  emit('confirm', {
    targetSpaceId: targetSpaceId.value,
    targetParentId: currentFolderId.value,
    targetLabel: targetPathLabel.value,
  })
}
</script>

<template>
  <BottomSheet
    :open="props.open"
    :title="props.title || (props.mode === 'MOVE' ? '移动到' : '复制到')"
    :subtitle="`目标：${targetPathLabel}`"
    max-height="86vh"
    @update:open="emit('update:open', $event)"
  >
    <div class="space-y-3">
      <!-- 可写目标空间切换条 -->
      <div class="flex items-center gap-1.5 overflow-x-auto pb-1 no-scrollbar">
        <button
          v-for="space in writableSpaces"
          :key="space.id"
          type="button"
          class="shrink-0 inline-flex items-center gap-1.5 rounded-full px-3 py-1.5 text-xs border transition-colors"
          :class="
            targetSpaceId === space.id
              ? 'border-primary bg-primary/10 text-primary font-medium'
              : 'border-border bg-background text-muted-foreground active:bg-accent'
          "
          @click="selectSpace(space)"
        >
          <Users v-if="space.kind === 'SHARED'" :size="12" />
          <Folder v-else :size="12" />
          <span class="max-w-[110px] truncate">{{ space.name }}</span>
        </button>
      </div>

      <!-- 面包屑 + 新建文件夹按钮 -->
      <div class="flex items-center justify-between gap-2 border-b border-border pb-2">
        <div class="flex items-center gap-1 overflow-x-auto whitespace-nowrap text-xs text-muted-foreground no-scrollbar min-w-0">
          <button
            type="button"
            class="shrink-0 rounded px-1.5 py-0.5 active:bg-accent"
            :class="breadcrumbs.length === 0 ? 'font-semibold text-foreground' : ''"
            @click="jumpBreadcrumb(-1)"
          >
            {{ currentTargetSpace?.name || '根目录' }}
          </button>
          <template v-for="(crumb, idx) in breadcrumbs" :key="crumb.id">
            <ChevronRight :size="12" class="shrink-0 opacity-60" />
            <button
              type="button"
              class="shrink-0 max-w-[120px] truncate rounded px-1.5 py-0.5 active:bg-accent"
              :class="idx === breadcrumbs.length - 1 ? 'font-semibold text-foreground' : ''"
              @click="jumpBreadcrumb(idx)"
            >
              {{ crumb.name }}
            </button>
          </template>
        </div>

        <button
          type="button"
          class="shrink-0 inline-flex items-center gap-1 rounded-lg border border-border px-2 py-1 text-xs text-foreground active:bg-accent"
          @click="creatingFolder = !creatingFolder"
        >
          <FolderPlus :size="13" />
          <span>新建</span>
        </button>
      </div>

      <!-- 内联新建文件夹输入行 -->
      <form
        v-if="creatingFolder"
        class="flex items-center gap-2 rounded-xl bg-muted/50 p-2"
        @submit.prevent="submitNewFolder"
      >
        <input
          v-model="newFolderName"
          type="text"
          maxlength="255"
          placeholder="输入新文件夹名称"
          class="flex-1 min-w-0 rounded-lg border border-input bg-background px-2.5 py-1.5 text-xs text-foreground focus:outline-none focus:ring-2 focus:ring-primary/40"
        />
        <VanButton size="small" type="primary" native-type="submit" :loading="creatingBusy">
          创建
        </VanButton>
      </form>

      <!-- 目录列表 -->
      <div class="min-h-[220px]">
        <SkeletonList v-if="loading" :count="4" />
        <div v-else-if="errorText" class="py-8 text-center text-xs text-destructive space-y-2">
          <p>{{ errorText }}</p>
          <button type="button" class="text-primary underline" @click="loadFolders">重试</button>
        </div>
        <EmptyState
          v-else-if="writableSpaces.length === 0"
          title="没有可写入的目标空间"
          description="当前所有空间均为只读权限"
        />
        <EmptyState
          v-else-if="folders.length === 0"
          title="当前目录下没有子文件夹"
          description="可直接点击下方按钮确认放入此目录"
        />
        <div v-else class="divide-y divide-border">
          <button
            v-for="folder in folders"
            :key="folder.id"
            type="button"
            class="w-full flex items-center gap-3 py-2.5 text-left transition-colors"
            :class="
              isFolderPickBlocked(folder.id, targetSpaceId, props.sourceSpaceId, excludedSet)
                ? 'opacity-45 cursor-not-allowed'
                : 'active:bg-accent/50'
            "
            @click="enterFolder(folder)"
          >
            <span class="flex h-9 w-9 items-center justify-center rounded-xl bg-primary/10 text-primary shrink-0">
              <Folder :size="18" />
            </span>
            <div class="flex-1 min-w-0">
              <p class="text-sm font-medium truncate text-foreground">{{ folder.name }}</p>
              <p
                v-if="isFolderPickBlocked(folder.id, targetSpaceId, props.sourceSpaceId, excludedSet)"
                class="text-[11px] text-muted-foreground"
              >
                不可选入自身目录
              </p>
            </div>
            <ChevronRight :size="16" class="text-muted-foreground shrink-0" />
          </button>
        </div>
      </div>
    </div>

    <template #footer>
      <div class="flex gap-2">
        <VanButton
          block
          round
          plain
          :disabled="props.submitting"
          @click="emit('update:open', false)"
        >
          取消
        </VanButton>
        <VanButton
          block
          round
          type="primary"
          :loading="props.submitting"
          :disabled="!targetSpaceId || isCurrentBlocked || isSameLocationMove"
          @click="handleConfirm"
        >
          {{ props.mode === 'MOVE' ? '移动到此处' : '复制到此处' }}
        </VanButton>
      </div>
    </template>
  </BottomSheet>
</template>
