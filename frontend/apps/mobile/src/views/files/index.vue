<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Checkbox as VanCheckbox, showConfirmDialog, showDialog, showToast } from 'vant'
import {
  ArrowUpDown,
  CheckSquare,
  ChevronDown,
  ChevronRight,
  Copy,
  Download,
  Eye,
  EyeOff,
  Folder,
  FolderInput,
  FolderPlus,
  LayoutGrid,
  List,
  MoreVertical,
  Search,
  Settings2,
  Star,
  Trash2,
  Upload,
  Users,
} from '@lucide/vue'
import { canWriteInRole, roleLabel } from '@repo/drive-core/categories'
import { isConflictError, isLockedError, normalizeErrorMessage } from '@repo/drive-core/errors'
import { formatBytes, formatRelativeTime } from '@repo/drive-core/format'
import { clearPrivateSpaceToken, hasPrivateSpaceToken } from '@repo/session/request'
import { accountApi } from '@/api/account'
import {
  mobileDriveApi,
  type DirectoryStats,
  type DriveNode,
  type Space,
} from '@/api/drive'
import {
  buildCategoryPills,
  buildNodeActions,
  formatSortLabel,
  loadFileViewMode,
  loadSortConfig,
  saveFileViewMode,
  saveSortConfig,
  triggerBlobDownload,
  type FileViewMode,
  type SortConfig,
} from '@/lib/file-actions'
import { formatBatchOutcomeMessage } from '@/lib/upload-engine'
import { canEditSpace, resolveActiveSpace } from '@/lib/spaces'
import { setTabBarHidden, showUndoToast } from '@/stores/mobile-ui'
import { fetchSpaces, spacesState, visibleSpaces } from '@/stores/spaces'
import { enqueueFilesWithGuard, onUploadCompleted } from '@/stores/uploads'
import {
  ActionSheet,
  EmptyState,
  ErrorState,
  FloatingActionButton,
  InfiniteList,
  PullRefresh,
  SkeletonList,
  type MobileActionItem,
} from '@/components/common'
import CapturePreviewSheet from '@/components/drive/CapturePreviewSheet.vue'
import CreateActionSheet from '@/components/drive/CreateActionSheet.vue'
import FolderPickerSheet from '@/components/drive/FolderPickerSheet.vue'
import NodeDetailSheet from '@/components/drive/NodeDetailSheet.vue'
import NodeThumb from '@/components/drive/NodeThumb.vue'
import RenameDialogSheet from '@/components/drive/RenameDialogSheet.vue'
import SortSheet from '@/components/drive/SortSheet.vue'
import SpaceFormSheet from '@/components/drive/SpaceFormSheet.vue'
import SpaceSwitcherSheet from '@/components/drive/SpaceSwitcherSheet.vue'
import UnlockHiddenSheet from '@/components/drive/UnlockHiddenSheet.vue'
import VersionsSheet from '@/components/drive/VersionsSheet.vue'
import VoiceRecorderSheet from '@/components/drive/VoiceRecorderSheet.vue'
import MobilePreviewShell from '@/components/preview/MobilePreviewShell.vue'
import FolderShareSheet from '@/components/share/FolderShareSheet.vue'
import ShareLinkSheet from '@/components/share/ShareLinkSheet.vue'
import SpaceMembersSheet from '@/components/share/SpaceMembersSheet.vue'

const PAGE_SIZE = 40

const route = useRoute()
const router = useRouter()

// Space modals
const switcherOpen = ref(false)
const formSheetOpen = ref(false)
const editingSpace = ref<Space | null>(null)

// Directory state
const nodes = ref<DriveNode[]>([])
const ancestors = ref<DriveNode[]>([])
const stats = ref<DirectoryStats | null>(null)
const total = ref(0)
const page = ref(1)
const initialLoading = ref(true)
const loadingMore = ref(false)
const loadMoreError = ref(false)
const finished = ref(false)
const refreshing = ref(false)
const error = ref('')

// View / filter / sort / hidden state
const viewMode = ref<FileViewMode>(loadFileViewMode())
const sortConfig = ref<SortConfig>(loadSortConfig())
const activeCategory = ref<string>('ALL')
const showHidden = ref(false)
const sortSheetOpen = ref(false)
const unlockHiddenOpen = ref(false)
const breadcrumbRef = ref<HTMLElement | null>(null)

// Quick access pinned & favorite nodeIds sets (for accurate star / pin state)
const pinnedNodeIds = ref<Set<string>>(new Set())
const favoriteNodeIds = ref<Set<string>>(new Set())

// Action sheets & dialogs
const createSheetOpen = ref(false)
const actionSheetOpen = ref(false)
const activeNode = ref<DriveNode | null>(null)
const detailSheetOpen = ref(false)
const versionsSheetOpen = ref(false)
const renameSheetOpen = ref(false)
const renameMode = ref<'create-folder' | 'rename'>('create-folder')
const renameSubmitting = ref(false)
const pickerOpen = ref(false)
const pickerMode = ref<'MOVE' | 'COPY'>('MOVE')
const pickerTargetScope = ref<'single' | 'batch'>('single')
const pickerSubmitting = ref(false)

// M017 Multi-select state
const selecting = ref(false)
const selectedNodeIds = ref<Set<string>>(new Set())
const batchBusy = ref(false)
let unsubUploadComplete: (() => void) | null = null

// Hidden file inputs for FAB upload & file replace
const mediaInputRef = ref<HTMLInputElement | null>(null)
const cameraPhotoInputRef = ref<HTMLInputElement | null>(null)
const cameraVideoInputRef = ref<HTMLInputElement | null>(null)
const fileInputRef = ref<HTMLInputElement | null>(null)
const replaceInputRef = ref<HTMLInputElement | null>(null)
const replacingNode = ref<DriveNode | null>(null)
const uploadingHint = ref('')

// M019 Capture preview & Voice recorder sheets
const capturePreviewOpen = ref(false)
const capturePreviewFile = ref<File | null>(null)
const capturePreviewKind = ref<'photo' | 'video'>('photo')
const voiceRecorderOpen = ref(false)

// M027/M028 Share sheets
const shareChoiceOpen = ref(false)
const shareLinkOpen = ref(false)
const folderShareOpen = ref(false)
const spaceMembersOpen = ref(false)
const shareTargetNode = ref<DriveNode | null>(null)

const shareChoiceActions = computed<MobileActionItem[]>(() => {
  const actions: MobileActionItem[] = [
    { key: 'link', name: '创建公开分享链接与二维码', subname: '支持提取码、有效期与微信口令' },
  ]
  if (shareTargetNode.value?.nodeType === 'FOLDER') {
    actions.push({ key: 'member', name: '共享文件夹给指定成员', subname: '授予可编辑或仅查看权限' })
  }
  return actions
})

function openShareForNode(node: DriveNode) {
  if (node.hidden) {
    showToast('隐藏项不可分享，请先取消隐藏')
    return
  }
  shareTargetNode.value = node
  if (node.nodeType === 'FOLDER') {
    shareChoiceOpen.value = true
  } else {
    shareLinkOpen.value = true
  }
}

function handleShareChoice(item: MobileActionItem) {
  if (item.key === 'link') shareLinkOpen.value = true
  else if (item.key === 'member') folderShareOpen.value = true
}

// AbortController & sequence guard for cancelling stale directory fetches
let directoryAbort: AbortController | null = null
let directorySeq = 0
const mountedReady = ref(false)

// Long-press handling
let longPressTimer: ReturnType<typeof setTimeout> | null = null
let longPressTriggered = false

const routeSpaceId = computed(() => (route.params.spaceId ? String(route.params.spaceId) : ''))
const routeFolderId = computed(() => (route.params.folderId ? String(route.params.folderId) : '0'))

const currentSpace = computed<Space | null>(() =>
  resolveActiveSpace(visibleSpaces.value, routeSpaceId.value),
)

const isWritable = computed(() => canWriteInRole(currentSpace.value?.role))

const selectedNodes = computed(() => nodes.value.filter(n => selectedNodeIds.value.has(n.id)))
const allNodesSelected = computed(
  () => nodes.value.length > 0 && nodes.value.every(n => selectedNodeIds.value.has(n.id)),
)
const pickerExcludeIds = computed(() => {
  if (pickerTargetScope.value === 'batch') {
    return selectedNodes.value.filter(n => n.nodeType === 'FOLDER').map(n => n.id)
  }
  return activeNode.value && activeNode.value.nodeType === 'FOLDER' ? [activeNode.value.id] : []
})

// M021 Route-synced preview state (?preview=<fileId>)
const previewNodeId = computed(() => (route.query.preview ? String(route.query.preview) : ''))
const previewNode = computed<DriveNode | null>(() => {
  if (!previewNodeId.value) return null
  return nodes.value.find(n => n.id === previewNodeId.value && n.nodeType === 'FILE') ?? null
})

function closePreview() {
  if (!route.query.preview) return
  const nextQuery = { ...route.query }
  delete nextQuery.preview
  void router.replace({ query: nextQuery })
}

// 深链（首页最近/快捷访问 → ?preview=id）目标可能不在首屏分页内：有界自动续页查找
const PREVIEW_SEEK_MAX_PAGES = 10
let previewSeekPages = 0
watch(
  () => [previewNodeId.value, nodes.value.length, initialLoading.value, loadingMore.value, finished.value] as const,
  async ([targetId, , isInitial, isMore, isFinished]) => {
    if (!targetId) {
      previewSeekPages = 0
      return
    }
    if (isInitial || isMore || previewNode.value) {
      if (previewNode.value) previewSeekPages = 0
      return
    }
    if (!isFinished && previewSeekPages < PREVIEW_SEEK_MAX_PAGES) {
      previewSeekPages += 1
      await onLoadMore()
      return
    }
    previewSeekPages = 0
    showToast('文件不存在、已被删除或不在当前目录')
    closePreview()
  },
)

function selectPreviewNode(target: DriveNode) {
  void router.replace({ query: { ...route.query, preview: target.id } })
}

async function toggleFavoriteDirect(node: DriveNode) {
  const next = !favoriteNodeIds.value.has(node.id)
  try {
    await mobileDriveApi.setFavorite(node.spaceId, node.id, next)
    const nextSet = new Set(favoriteNodeIds.value)
    if (next) nextSet.add(node.id)
    else nextSet.delete(node.id)
    favoriteNodeIds.value = nextSet
    showToast(next ? '已收藏' : '已取消收藏')
  } catch (err) {
    showToast(normalizeErrorMessage(err, '更新收藏失败'))
  }
}

function enterSelectMode(initialNodeId?: string) {
  selecting.value = true
  setTabBarHidden(true)
  const next = new Set(selectedNodeIds.value)
  if (initialNodeId) next.add(initialNodeId)
  selectedNodeIds.value = next
}

function exitSelectMode() {
  selecting.value = false
  selectedNodeIds.value = new Set()
  setTabBarHidden(false)
}

function toggleNodeSelection(nodeId: string) {
  const next = new Set(selectedNodeIds.value)
  if (next.has(nodeId)) next.delete(nodeId)
  else next.add(nodeId)
  selectedNodeIds.value = next
}

function toggleSelectAllNodes() {
  if (allNodesSelected.value) {
    selectedNodeIds.value = new Set()
  } else {
    selectedNodeIds.value = new Set(nodes.value.map(n => n.id))
  }
}

const categoryPills = computed(() => buildCategoryPills(stats.value))

const activeNodeActions = computed<MobileActionItem[]>(() => {
  if (!activeNode.value) return []
  return buildNodeActions(
    {
      id: activeNode.value.id,
      name: activeNode.value.name,
      nodeType: activeNode.value.nodeType,
      favorite: favoriteNodeIds.value.has(activeNode.value.id),
      hidden: activeNode.value.hidden,
      pinned: pinnedNodeIds.value.has(activeNode.value.id),
    },
    currentSpace.value?.role,
  )
})

function abortPendingDirectoryRequest() {
  if (directoryAbort) {
    directoryAbort.abort()
    directoryAbort = null
  }
}

function isAbortError(err: unknown): boolean {
  if (!err || typeof err !== 'object') return false
  const e = err as { name?: string; code?: string }
  return e.name === 'CanceledError' || e.name === 'AbortError' || e.code === 'ERR_CANCELED'
}

async function scrollBreadcrumbsToEnd() {
  await nextTick()
  if (breadcrumbRef.value) {
    breadcrumbRef.value.scrollLeft = breadcrumbRef.value.scrollWidth
  }
}

async function loadQuickAccessPins() {
  try {
    const list = await mobileDriveApi.listQuickAccess()
    pinnedNodeIds.value = new Set(list.map(i => i.nodeId))
  } catch {
    // non-critical
  }
}

async function loadSpaceFavorites(spaceId: string) {
  try {
    const res = await mobileDriveApi.nodes(spaceId, { favorite: true, page: 1, pageSize: 200 })
    favoriteNodeIds.value = new Set(res.items.map(i => i.id))
  } catch {
    // non-critical
  }
}

async function loadDirectory(reset = true) {
  const space = currentSpace.value
  if (!space) {
    nodes.value = []
    ancestors.value = []
    stats.value = null
    initialLoading.value = false
    finished.value = true
    return
  }

  if (reset) {
    void loadSpaceFavorites(space.id)
    abortPendingDirectoryRequest()
    directoryAbort = new AbortController()
    directorySeq += 1
    page.value = 1
    finished.value = false
    loadMoreError.value = false
    if (!refreshing.value) initialLoading.value = true
    error.value = ''
  }

  const seq = directorySeq
  const controller = directoryAbort ?? new AbortController()
  directoryAbort = controller
  const signal = controller.signal

  const folderId = routeFolderId.value
  const params: Record<string, unknown> = {
    parentId: folderId,
    page: page.value,
    pageSize: PAGE_SIZE,
    sort: sortConfig.value.field,
    order: sortConfig.value.order,
    hidden: showHidden.value,
  }
  if (activeCategory.value && activeCategory.value !== 'ALL') {
    params.category = activeCategory.value
  }

  try {
    const nodesPromise = mobileDriveApi.nodes(space.id, params, signal)
    const statsPromise = reset
      ? mobileDriveApi.stats(space.id, folderId, showHidden.value, signal).catch(() => null)
      : Promise.resolve(stats.value)
    const ancestorsPromise =
      reset && folderId !== '0'
        ? mobileDriveApi.ancestors(space.id, folderId, signal).catch(() => [])
        : reset
          ? Promise.resolve<DriveNode[]>([])
          : Promise.resolve(ancestors.value)

    const [pageRes, statsRes, ancestorsRes] = await Promise.all([
      nodesPromise,
      statsPromise,
      ancestorsPromise,
    ])
    if (signal.aborted || seq !== directorySeq) return

    if (reset) {
      nodes.value = pageRes.items
      stats.value = statsRes
      ancestors.value = ancestorsRes
      await scrollBreadcrumbsToEnd()
    } else {
      const existing = new Set(nodes.value.map(n => n.id))
      const appended = pageRes.items.filter(n => !existing.has(n.id))
      nodes.value = [...nodes.value, ...appended]
    }

    total.value = pageRes.total
    finished.value = nodes.value.length >= pageRes.total || pageRes.items.length < PAGE_SIZE
    page.value += 1
  } catch (err) {
    if (signal.aborted || seq !== directorySeq || isAbortError(err)) return
    if (isLockedError(err)) {
      clearPrivateSpaceToken(space.id)
      showHidden.value = false
      showToast('隐藏项解锁已过期，请重新验证')
      void loadDirectory(true)
      return
    }
    const status = (err as { httpStatus?: number; code?: number } | null)?.httpStatus
      ?? (err as { code?: number } | null)?.code
    if (reset && status === 404 && folderId !== '0') {
      const parentAncestor = ancestors.value.length >= 2 ? ancestors.value[ancestors.value.length - 2] : null
      showToast('当前目录不存在或已删除，已返回上级目录')
      if (parentAncestor) {
        await router.replace(`/files/${space.id}/folders/${parentAncestor.id}`)
      } else {
        await router.replace(`/files/${space.id}`)
      }
      return
    }
    const msg = normalizeErrorMessage(err, '加载目录失败')
    if (reset) {
      error.value = msg
    } else {
      loadMoreError.value = true
      showToast(msg)
    }
  } finally {
    if (!signal.aborted && seq === directorySeq) {
      initialLoading.value = false
      loadingMore.value = false
      refreshing.value = false
    }
  }
}

async function onLoadMore() {
  if (finished.value || initialLoading.value || !currentSpace.value) {
    loadingMore.value = false
    return
  }
  loadingMore.value = true
  loadMoreError.value = false
  await loadDirectory(false)
}

onMounted(async () => {
  unsubUploadComplete = onUploadCompleted(evt => {
    if (evt.spaceId === currentSpace.value?.id && evt.parentId === routeFolderId.value) {
      void loadDirectory(true)
    }
  })
  await Promise.all([fetchSpaces(), loadQuickAccessPins()])
  if (!routeSpaceId.value && currentSpace.value) {
    await router.replace(`/files/${currentSpace.value.id}`)
  }
  mountedReady.value = true
})

onBeforeUnmount(() => {
  unsubUploadComplete?.()
  unsubUploadComplete = null
  exitSelectMode()
  abortPendingDirectoryRequest()
  clearLongPress()
})

watch(
  () => [mountedReady.value, currentSpace.value?.id, routeFolderId.value] as const,
  ([ready, spaceId], [, prevSpaceId]) => {
    if (!ready) return
    exitSelectMode()
    activeCategory.value = 'ALL'
    if (spaceId !== prevSpaceId) {
      showHidden.value = false
    }
    if (spaceId) {
      void loadDirectory(true)
    } else {
      nodes.value = []
      ancestors.value = []
      stats.value = null
      initialLoading.value = false
      finished.value = true
    }
  },
)

function selectSpace(space: Space) {
  void router.push(`/files/${space.id}`)
}

function openCreateSpace() {
  editingSpace.value = null
  formSheetOpen.value = true
}

function openManageSpace(space: Space) {
  editingSpace.value = space
  formSheetOpen.value = true
}

async function onSpaceSaved(spaceId?: string) {
  await fetchSpaces(true)
  if (spaceId) {
    await router.push(`/files/${spaceId}`)
  }
}

async function onSpaceDeleted(deletedId: string) {
  await fetchSpaces(true)
  if (currentSpace.value?.id === deletedId || !currentSpace.value) {
    const fallback = visibleSpaces.value[0]
    if (fallback) {
      await router.replace(`/files/${fallback.id}`)
    } else {
      await router.replace('/files')
    }
  }
}

function goBreadcrumb(folderId?: string) {
  if (!currentSpace.value) return
  if (!folderId) {
    void router.push(`/files/${currentSpace.value.id}`)
  } else {
    void router.push(`/files/${currentSpace.value.id}/folders/${folderId}`)
  }
}

function selectCategory(key: string) {
  if (activeCategory.value === key) return
  activeCategory.value = key
  void loadDirectory(true)
}

function toggleViewMode() {
  viewMode.value = viewMode.value === 'list' ? 'grid' : 'list'
  saveFileViewMode(viewMode.value)
  void accountApi.updatePreferences({ fileView: viewMode.value }).catch(() => undefined)
}

function onSortChange(next: SortConfig) {
  sortConfig.value = next
  saveSortConfig(next)
  void accountApi.updatePreferences({ fileSort: next.field }).catch(() => undefined)
  void loadDirectory(true)
}

function toggleHidden() {
  const space = currentSpace.value
  if (!space) return
  if (showHidden.value) {
    showHidden.value = false
    void loadDirectory(true)
    return
  }
  if (hasPrivateSpaceToken(space.id)) {
    showHidden.value = true
    void loadDirectory(true)
  } else {
    unlockHiddenOpen.value = true
  }
}

function onHiddenUnlocked() {
  showHidden.value = true
  void loadDirectory(true)
}

function openNode(node: DriveNode) {
  if (longPressTriggered) {
    longPressTriggered = false
    return
  }
  if (selecting.value) {
    toggleNodeSelection(node.id)
    return
  }
  if (node.nodeType === 'FOLDER') {
    void router.push(`/files/${node.spaceId}/folders/${node.id}`)
  } else {
    void router.push({ query: { ...route.query, preview: node.id } })
  }
}

function openNodeMenu(node: DriveNode) {
  activeNode.value = node
  actionSheetOpen.value = true
}

function onItemTouchStart(node: DriveNode) {
  if (selecting.value) return
  clearLongPress()
  longPressTriggered = false
  longPressTimer = setTimeout(() => {
    longPressTriggered = true
    enterSelectMode(node.id)
  }, 500)
}

function clearLongPress() {
  if (longPressTimer) {
    clearTimeout(longPressTimer)
    longPressTimer = null
  }
}

async function onRefresh() {
  refreshing.value = true
  await Promise.all([fetchSpaces(true), loadQuickAccessPins()])
  await loadDirectory(true)
}

// Create folder & rename
function openCreateFolderDialog() {
  renameMode.value = 'create-folder'
  renameSheetOpen.value = true
}

function openRenameDialog(node: DriveNode) {
  activeNode.value = node
  renameMode.value = 'rename'
  renameSheetOpen.value = true
}

async function handleRenameOrCreateSubmit(name: string) {
  const space = currentSpace.value
  if (!space) return
  renameSubmitting.value = true
  try {
    if (renameMode.value === 'create-folder') {
      await mobileDriveApi.createFolder(space.id, routeFolderId.value, name)
      showToast('文件夹已创建')
    } else if (activeNode.value) {
      await mobileDriveApi.updateNode(activeNode.value, name)
      showToast('已重命名')
    }
    renameSheetOpen.value = false
    await loadDirectory(true)
  } catch (err) {
    if (isConflictError(err)) {
      showToast('文件已被其他端修改，已自动刷新')
      renameSheetOpen.value = false
      await loadDirectory(true)
    } else {
      showToast(normalizeErrorMessage(err, '操作失败'))
    }
  } finally {
    renameSubmitting.value = false
  }
}

// FAB upload handlers
let mediaPickWaitTimer: ReturnType<typeof setTimeout> | null = null
let mediaPickFocusCleanup: (() => void) | null = null

function clearMediaPickWait() {
  if (mediaPickWaitTimer) {
    clearTimeout(mediaPickWaitTimer)
    mediaPickWaitTimer = null
  }
  mediaPickFocusCleanup?.()
  mediaPickFocusCleanup = null
  if (uploadingHint.value.startsWith('系统正在导出所选照片/视频')) {
    uploadingHint.value = ''
  }
}

function triggerMediaUpload() {
  clearMediaPickWait()
  const onReturnFocus = () => {
    mediaPickFocusCleanup?.()
    mediaPickFocusCleanup = null
    // 手机系统相册返回后，若系统正在导出/转码 HEIC 或大视频，change 事件会延迟数秒到数十秒，先给出友好等待提示
    mediaPickWaitTimer = setTimeout(() => {
      uploadingHint.value = '系统正在导出所选照片/视频，请稍候…（大文件可在「上传文件」选原件跳过转码）'
      mediaPickWaitTimer = setTimeout(() => {
        clearMediaPickWait()
      }, 45_000)
    }, 350)
  }
  window.addEventListener('focus', onReturnFocus, { once: true })
  mediaPickFocusCleanup = () => window.removeEventListener('focus', onReturnFocus)
  mediaInputRef.value?.click()
}

function triggerPhotoCapture() {
  cameraPhotoInputRef.value?.click()
}

function triggerVideoCapture() {
  cameraVideoInputRef.value?.click()
}

function openVoiceRecorder() {
  voiceRecorderOpen.value = true
}

function triggerFileUpload() {
  fileInputRef.value?.click()
}

function handleCapturedFile(kind: 'photo' | 'video', event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0] ?? null
  input.value = ''
  if (!file) return
  capturePreviewKind.value = kind
  capturePreviewFile.value = file
  capturePreviewOpen.value = true
}

async function handleConfirmedMediaUpload(file: File) {
  const space = currentSpace.value
  if (!space) return
  const enqueued = await enqueueFilesWithGuard(space.id, routeFolderId.value, [file])
  if (enqueued) {
    showToast(`已加入上传队列：${file.name}`)
  }
}

async function handleSelectedFiles(event: Event) {
  clearMediaPickWait()
  const input = event.target as HTMLInputElement
  const files = input.files ? Array.from(input.files) : []
  input.value = ''
  const space = currentSpace.value
  if (!space || files.length === 0) return

  const enqueued = await enqueueFilesWithGuard(space.id, routeFolderId.value, files)
  if (enqueued) {
    showToast(`已加入上传队列（${files.length} 个文件）`)
  }
}

function handlePickerCancel() {
  clearMediaPickWait()
}

async function handleReplaceFileChange(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  const target = replacingNode.value
  replacingNode.value = null
  if (!file || !target) return

  uploadingHint.value = `正在替换 ${target.name}…`
  try {
    await mobileDriveApi.replaceFile(target, file)
    showToast('已替换并生成新版本')
    await loadDirectory(true)
  } catch (err) {
    if (isConflictError(err)) {
      showToast('文件已被其他端修改，已自动刷新')
      await loadDirectory(true)
    } else {
      showToast(normalizeErrorMessage(err, '替换文件失败'))
    }
  } finally {
    uploadingHint.value = ''
  }
}

// Node ActionSheet handler
async function handleNodeAction(item: MobileActionItem) {
  const node = activeNode.value
  const space = currentSpace.value
  if (!node || !space) return

  switch (item.key) {
    case 'open':
      openNode(node)
      return

    case 'download':
      try {
        showToast('正在准备下载…')
        const blob = await mobileDriveApi.downloadContent(
          node.spaceId,
          node.id,
          node.currentVersionId ?? undefined,
        )
        triggerBlobDownload(blob, node.name)
      } catch (err) {
        showToast(normalizeErrorMessage(err, '下载失败'))
      }
      return

    case 'share':
      openShareForNode(node)
      return

    case 'favorite': {
      const next = !favoriteNodeIds.value.has(node.id)
      try {
        await mobileDriveApi.setFavorite(node.spaceId, node.id, next)
        const nextSet = new Set(favoriteNodeIds.value)
        if (next) nextSet.add(node.id)
        else nextSet.delete(node.id)
        favoriteNodeIds.value = nextSet
        showToast(next ? '已收藏' : '已取消收藏')
      } catch (err) {
        showToast(normalizeErrorMessage(err, '更新收藏失败'))
      }
      return
    }

    case 'pin': {
      if (node.hidden) {
        showToast('隐藏项不可固定到快捷访问')
        return
      }
      const next = !pinnedNodeIds.value.has(node.id)
      try {
        await mobileDriveApi.setPin(node.spaceId, node.id, next)
        if (next) pinnedNodeIds.value.add(node.id)
        else pinnedNodeIds.value.delete(node.id)
        showToast(next ? '已固定到快捷访问' : '已取消快捷访问')
      } catch (err) {
        showToast(normalizeErrorMessage(err, '更新快捷访问失败'))
      }
      return
    }

    case 'rename':
      openRenameDialog(node)
      return

    case 'move':
      pickerTargetScope.value = 'single'
      pickerMode.value = 'MOVE'
      pickerOpen.value = true
      return

    case 'copy':
      pickerTargetScope.value = 'single'
      pickerMode.value = 'COPY'
      pickerOpen.value = true
      return

    case 'versions':
      versionsSheetOpen.value = true
      return

    case 'replace':
      replacingNode.value = node
      replaceInputRef.value?.click()
      return

    case 'hidden': {
      const next = !node.hidden
      try {
        await mobileDriveApi.setHidden(node.spaceId, node.id, next)
        showToast(next ? '已设为隐藏' : '已取消隐藏')
        await loadDirectory(true)
      } catch (err) {
        showToast(normalizeErrorMessage(err, '更新隐藏状态失败'))
      }
      return
    }

    case 'detail':
      detailSheetOpen.value = true
      return

    case 'delete':
      await confirmTrashNode(node)
      return
  }
}

async function handleTransferConfirm(payload: {
  targetSpaceId: string
  targetParentId: string
  targetLabel: string
}) {
  if (pickerTargetScope.value === 'batch') {
    await executeBatchTransfer(payload)
    return
  }
  const node = activeNode.value
  if (!node) return
  pickerSubmitting.value = true
  try {
    if (pickerMode.value === 'MOVE' && payload.targetSpaceId === node.spaceId) {
      const oldParentId = node.parentId
      await mobileDriveApi.updateNode(node, node.name, payload.targetParentId)
      pickerOpen.value = false
      await loadDirectory(true)
      const receipt = await mobileDriveApi
        .issueUndo({
          spaceId: node.spaceId,
          actionType: 'MOVE',
          summary: `已移动「${node.name}」`,
          nodeId: node.id,
          oldParentId,
        })
        .catch(() => null)
      if (receipt?.tokenId) {
        showUndoToast({
          tokenId: receipt.tokenId,
          summary: receipt.summary || `已移动「${node.name}」`,
          onUndo: async tokenId => {
            await mobileDriveApi.executeUndo(tokenId)
            showToast('已撤销移动')
            await loadDirectory(true)
          },
        })
      } else {
        showToast(`已移动到 ${payload.targetLabel}`)
      }
    } else {
      await mobileDriveApi.transferNode(
        node,
        payload.targetSpaceId,
        payload.targetParentId,
        pickerMode.value,
      )
      pickerOpen.value = false
      showToast(
        pickerMode.value === 'MOVE'
          ? `已移动到 ${payload.targetLabel}`
          : `已复制到 ${payload.targetLabel}`,
      )
      await loadDirectory(true)
    }
  } catch (err) {
    if (isConflictError(err)) {
      showToast('文件已被其他端修改，已自动刷新')
      pickerOpen.value = false
      await loadDirectory(true)
    } else {
      showToast(normalizeErrorMessage(err, pickerMode.value === 'MOVE' ? '移动失败' : '复制失败'))
    }
  } finally {
    pickerSubmitting.value = false
  }
}

// M017 Batch operations
function openBatchPicker(mode: 'MOVE' | 'COPY') {
  if (selectedNodeIds.value.size === 0) return
  pickerTargetScope.value = 'batch'
  pickerMode.value = mode
  pickerOpen.value = true
}

async function executeBatchTransfer(payload: {
  targetSpaceId: string
  targetParentId: string
  targetLabel: string
}) {
  const space = currentSpace.value
  const ids = Array.from(selectedNodeIds.value)
  if (!space || ids.length === 0) return
  pickerSubmitting.value = true
  try {
    const res = await mobileDriveApi.executeBatch(space.id, {
      action: pickerMode.value,
      nodeIds: ids,
      targetSpaceId: Number(payload.targetSpaceId),
      targetParentId: Number(payload.targetParentId),
    })
    pickerOpen.value = false
    const summary = formatBatchOutcomeMessage(pickerMode.value === 'MOVE' ? '批量移动' : '批量复制', res)
    if (summary.ok) {
      showToast(summary.title)
    } else {
      await showDialog({ title: summary.title, message: summary.detail, confirmButtonText: '知道了' })
    }
    exitSelectMode()
    await loadDirectory(true)
  } catch (err) {
    showToast(normalizeErrorMessage(err, '批量操作失败'))
  } finally {
    pickerSubmitting.value = false
  }
}

async function executeBatchDelete() {
  const space = currentSpace.value
  const ids = Array.from(selectedNodeIds.value)
  if (!space || ids.length === 0 || batchBusy.value) return
  try {
    await showConfirmDialog({
      title: '批量移入回收站',
      message: `确定将选中的 ${ids.length} 项移入回收站吗？`,
      confirmButtonText: '移入回收站',
      confirmButtonColor: 'var(--destructive)',
    })
  } catch {
    return
  }

  batchBusy.value = true
  try {
    const res = await mobileDriveApi.executeBatch(space.id, {
      action: 'DELETE',
      nodeIds: ids,
    })
    const summary = formatBatchOutcomeMessage('批量删除', res)
    if (summary.ok) {
      showToast(summary.title)
    } else {
      await showDialog({ title: summary.title, message: summary.detail, confirmButtonText: '知道了' })
    }
    exitSelectMode()
    await loadDirectory(true)
  } catch (err) {
    showToast(normalizeErrorMessage(err, '批量删除失败'))
  } finally {
    batchBusy.value = false
  }
}

async function executeBatchZipDownload() {
  const space = currentSpace.value
  const ids = Array.from(selectedNodeIds.value)
  if (!space || ids.length === 0 || batchBusy.value) return
  batchBusy.value = true
  uploadingHint.value = `正在打包 ${ids.length} 项为 ZIP…`
  try {
    const job = await mobileDriveApi.createZipBatch(space.id, ids)
    const blob = await mobileDriveApi.downloadZipBatch(job.id)
    triggerBlobDownload(blob, `${space.name}-${ids.length}项.zip`)
    showToast('打包下载已开始')
    exitSelectMode()
  } catch (err) {
    showToast(normalizeErrorMessage(err, '打包下载失败'))
  } finally {
    batchBusy.value = false
    uploadingHint.value = ''
  }
}

async function confirmTrashNode(node: DriveNode) {
  try {
    await showConfirmDialog({
      title: '移入回收站',
      message: `确定将「${node.name}」移入回收站吗？10 秒内可撤销，或在回收站中恢复。`,
      confirmButtonText: '移入回收站',
      confirmButtonColor: 'var(--destructive)',
    })
  } catch {
    return
  }

  try {
    await mobileDriveApi.trashNode(node)
    await loadDirectory(true)
    const receipt = await mobileDriveApi
      .issueUndo({
        spaceId: node.spaceId,
        actionType: 'TRASH',
        summary: `已删除「${node.name}」`,
        nodeId: node.id,
        oldParentId: node.parentId,
      })
      .catch(() => null)

    if (receipt?.tokenId) {
      showUndoToast({
        tokenId: receipt.tokenId,
        summary: receipt.summary || `已删除「${node.name}」`,
        onUndo: async tokenId => {
          await mobileDriveApi.executeUndo(tokenId)
          showToast('已撤销删除')
          await loadDirectory(true)
        },
      })
    } else {
      showToast('已移入回收站')
    }
  } catch (err) {
    if (isConflictError(err)) {
      showToast('文件已被其他端修改，已自动刷新')
      await loadDirectory(true)
    } else {
      showToast(normalizeErrorMessage(err, '删除失败'))
    }
  }
}
</script>

<template>
  <div class="flex-1 min-h-0 flex flex-col bg-background text-foreground">
    <!-- 顶部空间切换栏 / 多选顶栏 -->
    <header class="sticky top-0 z-20 border-b border-border bg-background/95 backdrop-blur safe-top">
      <div v-if="selecting" class="flex items-center justify-between gap-2 px-3.5 py-3">
        <button
          type="button"
          class="rounded-lg px-2 py-1 text-sm text-muted-foreground active:bg-accent"
          @click="exitSelectMode"
        >
          取消
        </button>
        <span class="text-sm font-semibold text-foreground">
          已选 {{ selectedNodeIds.size }} 项
        </span>
        <button
          type="button"
          class="rounded-lg px-2 py-1 text-sm font-medium text-primary active:bg-accent"
          @click="toggleSelectAllNodes"
        >
          {{ allNodesSelected ? '取消全选' : '全选' }}
        </button>
      </div>

      <div v-else class="flex items-center justify-between gap-2 px-3.5 py-2.5">
        <button
          type="button"
          class="flex items-center gap-2 min-w-0 rounded-xl px-2 py-1 -ml-2 active:bg-accent"
          @click="switcherOpen = true"
        >
          <span
            class="flex h-7 w-7 items-center justify-center rounded-lg text-white shrink-0"
            :style="{ backgroundColor: currentSpace?.color || '#2563eb' }"
          >
            <Users v-if="currentSpace?.kind === 'SHARED'" :size="15" />
            <Folder v-else :size="15" />
          </span>
          <div class="min-w-0 text-left">
            <div class="flex items-center gap-1">
              <span class="text-sm font-semibold truncate max-w-[160px]">
                {{ currentSpace?.name || (spacesState.loading ? '加载中…' : '选择空间') }}
              </span>
              <ChevronDown :size="15" class="text-muted-foreground shrink-0" />
            </div>
            <p v-if="currentSpace?.kind === 'SHARED'" class="text-[10px] text-primary leading-tight">
              共享 · {{ roleLabel(currentSpace.role) }}
            </p>
          </div>
        </button>

        <div class="flex items-center gap-1 shrink-0">
          <button
            v-if="nodes.length > 0"
            type="button"
            class="inline-flex h-9 w-9 items-center justify-center rounded-full text-foreground active:bg-accent"
            aria-label="多选"
            @click="enterSelectMode()"
          >
            <CheckSquare :size="18" />
          </button>
          <button
            v-if="currentSpace?.kind === 'SHARED'"
            type="button"
            class="inline-flex h-9 w-9 items-center justify-center rounded-full text-foreground active:bg-accent"
            aria-label="空间成员"
            @click="spaceMembersOpen = true"
          >
            <Users :size="18" />
          </button>
          <button
            v-if="canEditSpace(currentSpace)"
            type="button"
            class="inline-flex h-9 w-9 items-center justify-center rounded-full text-foreground active:bg-accent"
            aria-label="空间设置"
            @click="currentSpace && openManageSpace(currentSpace)"
          >
            <Settings2 :size="18" />
          </button>
          <button
            type="button"
            class="inline-flex h-9 w-9 items-center justify-center rounded-full text-foreground active:bg-accent"
            aria-label="搜索"
            @click="router.push('/search')"
          >
            <Search :size="18" />
          </button>
        </div>
      </div>

      <!-- 面包屑条 -->
      <nav
        v-if="currentSpace && (ancestors.length > 0 || routeFolderId !== '0')"
        ref="breadcrumbRef"
        aria-label="目录路径"
        class="flex items-center gap-1 overflow-x-auto whitespace-nowrap px-3.5 pb-2 text-xs text-muted-foreground no-scrollbar"
      >
        <button
          type="button"
          class="shrink-0 rounded-md px-1.5 py-0.5 active:bg-accent"
          :class="ancestors.length === 0 ? 'font-semibold text-foreground' : 'text-muted-foreground'"
          @click="goBreadcrumb()"
        >
          {{ currentSpace.name }}
        </button>
        <template v-for="(anc, idx) in ancestors" :key="anc.id">
          <ChevronRight :size="13" class="shrink-0 opacity-60" />
          <button
            type="button"
            class="shrink-0 max-w-[140px] truncate rounded-md px-1.5 py-0.5 active:bg-accent"
            :class="idx === ancestors.length - 1 ? 'font-semibold text-foreground' : 'text-muted-foreground'"
            @click="goBreadcrumb(anc.id)"
          >
            {{ anc.name }}
          </button>
        </template>
      </nav>

      <!-- 目录分类统计胶囊条 -->
      <div
        v-if="currentSpace && categoryPills.length > 1"
        class="flex items-center gap-1.5 overflow-x-auto px-3.5 pb-2 no-scrollbar"
      >
        <button
          v-for="pill in categoryPills"
          :key="pill.key"
          type="button"
          class="shrink-0 rounded-full px-2.5 py-1 text-xs transition-colors"
          :class="
            activeCategory === pill.key
              ? 'bg-primary text-primary-foreground font-medium'
              : 'bg-muted text-muted-foreground active:bg-accent'
          "
          @click="selectCategory(pill.key)"
        >
          {{ pill.label }}
          <span class="ml-0.5 opacity-80">{{ pill.count }}</span>
        </button>
        <span
          v-if="stats && stats.folders > 0"
          class="shrink-0 rounded-full border border-border px-2.5 py-1 text-xs text-muted-foreground"
        >
          文件夹 {{ stats.folders }}
        </span>
      </div>

      <!-- 工具条：排序 / 隐藏项 / 视图切换 -->
      <div
        v-if="currentSpace"
        class="flex items-center justify-between gap-2 px-3.5 py-1.5 border-t border-border/60 bg-muted/20 text-xs"
      >
        <div class="flex items-center gap-2 min-w-0">
          <button
            type="button"
            class="inline-flex items-center gap-1 rounded-lg px-2 py-1 text-muted-foreground active:bg-accent"
            @click="sortSheetOpen = true"
          >
            <ArrowUpDown :size="13" />
            <span>{{ formatSortLabel(sortConfig) }}</span>
          </button>

          <button
            type="button"
            class="inline-flex items-center gap-1 rounded-lg px-2 py-1 transition-colors"
            :class="showHidden ? 'bg-primary/10 text-primary font-medium' : 'text-muted-foreground active:bg-accent'"
            @click="toggleHidden"
          >
            <Eye v-if="showHidden" :size="13" />
            <EyeOff v-else :size="13" />
            <span>{{ showHidden ? '含隐藏项' : '隐藏项' }}</span>
          </button>
        </div>

        <button
          type="button"
          class="inline-flex h-7 w-7 items-center justify-center rounded-lg text-muted-foreground active:bg-accent"
          :aria-label="viewMode === 'list' ? '切换网格视图' : '切换列表视图'"
          @click="toggleViewMode"
        >
          <LayoutGrid v-if="viewMode === 'list'" :size="16" />
          <List v-else :size="16" />
        </button>
      </div>
    </header>

    <!-- 上传进度轻提示条 -->
    <div
      v-if="uploadingHint"
      class="bg-primary/10 text-primary text-xs px-4 py-2 flex items-center justify-between border-b border-primary/20"
    >
      <span class="truncate">{{ uploadingHint }}</span>
    </div>

    <PullRefresh v-model="refreshing" @refresh="onRefresh">
      <SkeletonList v-if="initialLoading" :count="7" />
      <ErrorState v-else-if="error" :message="error" @retry="loadDirectory(true)" />
      <EmptyState
        v-else-if="!currentSpace"
        title="还没有可用空间"
        description="创建一个个人空间或共享空间开始管理文件"
        action-text="新建空间"
        @action="openCreateSpace"
      />
      <div v-else-if="nodes.length === 0" class="px-6 py-12 text-center">
        <EmptyState
          :title="activeCategory === 'ALL' ? '当前目录为空' : '该分类下暂无文件'"
          :description="
            activeCategory === 'ALL'
              ? isWritable
                ? '点击下方按钮快速新建文件夹或上传文件'
                : '当前空间为只读权限，暂无可查看内容'
              : '可切换其他分类筛选或查看全部'
          "
        />
        <div v-if="activeCategory === 'ALL' && isWritable" class="mt-2 flex items-center justify-center gap-3">
          <button
            type="button"
            class="inline-flex items-center gap-1.5 rounded-full border border-border bg-card px-4 py-2 text-xs font-medium text-foreground active:bg-accent"
            @click="openCreateFolderDialog"
          >
            <FolderPlus :size="15" />
            <span>新建文件夹</span>
          </button>
          <button
            type="button"
            class="inline-flex items-center gap-1.5 rounded-full bg-primary px-4 py-2 text-xs font-medium text-primary-foreground active:opacity-90"
            @click="triggerFileUpload"
          >
            <Upload :size="15" />
            <span>上传文件</span>
          </button>
        </div>
      </div>

      <InfiniteList
        v-else
        v-model:loading="loadingMore"
        v-model:error="loadMoreError"
        :finished="finished"
        :finished-text="total > PAGE_SIZE ? `已全部加载（共 ${total} 项）` : ''"
        @load="onLoadMore"
      >
        <!-- 列表视图 -->
        <div v-if="viewMode === 'list'" class="divide-y divide-border px-3.5 pb-24">
          <div
            v-for="node in nodes"
            :key="node.id"
            class="flex items-center gap-3 py-2.5 select-none active:bg-accent/50 rounded-lg"
            @click="openNode(node)"
            @touchstart.passive="onItemTouchStart(node)"
            @touchmove.passive="clearLongPress"
            @touchend.passive="clearLongPress"
            @touchcancel.passive="clearLongPress"
          >
            <VanCheckbox
              v-if="selecting"
              :model-value="selectedNodeIds.has(node.id)"
              @click.stop
              @update:model-value="toggleNodeSelection(node.id)"
            />

            <NodeThumb :node="node" variant="list" />

            <div class="flex-1 min-w-0">
              <div class="flex items-center gap-1.5">
                <p class="text-sm font-medium truncate text-foreground">{{ node.name }}</p>
                <Star
                  v-if="favoriteNodeIds.has(node.id)"
                  :size="12"
                  class="shrink-0 text-amber-500 fill-amber-500"
                />
                <EyeOff
                  v-if="node.hidden"
                  :size="12"
                  class="shrink-0 text-muted-foreground"
                />
              </div>
              <p class="text-xs text-muted-foreground mt-0.5 truncate">
                {{ node.nodeType === 'FOLDER' ? '文件夹' : formatBytes(node.size) }} · {{ formatRelativeTime(node.updatedAt) }}
              </p>
            </div>

            <button
              v-if="!selecting"
              type="button"
              class="inline-flex h-9 w-9 items-center justify-center rounded-full text-muted-foreground active:bg-accent shrink-0"
              aria-label="更多操作"
              @click.stop="openNodeMenu(node)"
            >
              <MoreVertical :size="17" />
            </button>
          </div>
        </div>

        <!-- 3 列网格视图 -->
        <div v-else class="grid grid-cols-3 gap-2.5 px-3.5 pt-3 pb-24">
          <div
            v-for="node in nodes"
            :key="node.id"
            class="flex flex-col rounded-xl bg-card border border-border/60 p-2 select-none active:bg-accent/40"
            @click="openNode(node)"
            @touchstart.passive="onItemTouchStart(node)"
            @touchmove.passive="clearLongPress"
            @touchend.passive="clearLongPress"
            @touchcancel.passive="clearLongPress"
          >
            <div class="relative">
              <NodeThumb :node="node" variant="grid" />
              <div
                v-if="selecting"
                class="absolute top-1.5 left-1.5 rounded-full bg-background/90 p-0.5"
                @click.stop
              >
                <VanCheckbox
                  :model-value="selectedNodeIds.has(node.id)"
                  icon-size="16"
                  @update:model-value="toggleNodeSelection(node.id)"
                />
              </div>
              <Star
                v-if="favoriteNodeIds.has(node.id)"
                :size="13"
                class="absolute top-1.5 right-1.5 text-amber-500 fill-amber-500 drop-shadow"
              />
              <button
                v-if="!selecting"
                type="button"
                class="absolute bottom-1 right-1 inline-flex h-7 w-7 items-center justify-center rounded-full bg-background/85 text-foreground shadow-xs"
                aria-label="更多操作"
                @click.stop="openNodeMenu(node)"
              >
                <MoreVertical :size="14" />
              </button>
            </div>
            <p class="mt-1.5 text-xs font-medium text-foreground line-clamp-2 break-all leading-snug">
              {{ node.name }}
            </p>
            <p class="mt-0.5 text-[10px] text-muted-foreground truncate">
              {{ node.nodeType === 'FOLDER' ? '文件夹' : formatBytes(node.size) }}
            </p>
          </div>
        </div>
      </InfiniteList>
    </PullRefresh>

    <!-- 右下角新建/上传 FAB（VIEWER 或多选模式隐藏） -->
    <FloatingActionButton
      v-if="currentSpace && isWritable && !selecting"
      @click="createSheetOpen = true"
    />

    <!-- M017 底部批量操作栏（多选模式替代底部 Tab） -->
    <div
      v-if="selecting"
      class="fixed inset-x-0 bottom-0 z-30 border-t border-border bg-card/95 backdrop-blur safe-bottom px-2 py-2"
    >
      <div class="grid gap-1" :class="isWritable ? 'grid-cols-4' : 'grid-cols-2'">
        <button
          v-if="isWritable"
          type="button"
          class="flex flex-col items-center gap-1 rounded-xl py-1.5 text-foreground active:bg-accent disabled:opacity-40"
          :disabled="selectedNodeIds.size === 0 || batchBusy"
          @click="openBatchPicker('MOVE')"
        >
          <FolderInput :size="18" />
          <span class="text-[11px]">移动</span>
        </button>

        <button
          type="button"
          class="flex flex-col items-center gap-1 rounded-xl py-1.5 text-foreground active:bg-accent disabled:opacity-40"
          :disabled="selectedNodeIds.size === 0 || batchBusy"
          @click="openBatchPicker('COPY')"
        >
          <Copy :size="18" />
          <span class="text-[11px]">复制</span>
        </button>

        <button
          type="button"
          class="flex flex-col items-center gap-1 rounded-xl py-1.5 text-foreground active:bg-accent disabled:opacity-40"
          :disabled="selectedNodeIds.size === 0 || batchBusy"
          @click="executeBatchZipDownload"
        >
          <Download :size="18" />
          <span class="text-[11px]">打包下载</span>
        </button>

        <button
          v-if="isWritable"
          type="button"
          class="flex flex-col items-center gap-1 rounded-xl py-1.5 text-destructive active:bg-destructive/10 disabled:opacity-40"
          :disabled="selectedNodeIds.size === 0 || batchBusy"
          @click="executeBatchDelete"
        >
          <Trash2 :size="18" />
          <span class="text-[11px]">删除</span>
        </button>
      </div>
    </div>

    <!-- 隐藏文件选择器 -->
    <input
      ref="mediaInputRef"
      type="file"
      accept="image/*,video/*"
      multiple
      class="hidden"
      @change="handleSelectedFiles"
      @cancel="handlePickerCancel"
    />
    <input
      ref="cameraPhotoInputRef"
      type="file"
      accept="image/*"
      capture="environment"
      class="hidden"
      @change="handleCapturedFile('photo', $event)"
    />
    <input
      ref="cameraVideoInputRef"
      type="file"
      accept="video/*"
      capture="environment"
      class="hidden"
      @change="handleCapturedFile('video', $event)"
    />
    <input
      ref="fileInputRef"
      type="file"
      multiple
      class="hidden"
      @change="handleSelectedFiles"
    />
    <input
      ref="replaceInputRef"
      type="file"
      class="hidden"
      @change="handleReplaceFileChange"
    />

    <!-- 各类 BottomSheet 与 ActionSheet -->
    <SpaceSwitcherSheet
      v-model:open="switcherOpen"
      :spaces="visibleSpaces"
      :current-space-id="currentSpace?.id || ''"
      @select="selectSpace"
      @create="openCreateSpace"
      @manage="openManageSpace"
    />

    <SpaceFormSheet
      v-model:open="formSheetOpen"
      :space="editingSpace"
      @saved="onSpaceSaved"
      @deleted="onSpaceDeleted"
    />

    <SortSheet
      v-model:open="sortSheetOpen"
      :model-value="sortConfig"
      @update:model-value="onSortChange"
    />

    <UnlockHiddenSheet
      v-model:open="unlockHiddenOpen"
      :space-id="currentSpace?.id || ''"
      :space-name="currentSpace?.name"
      @unlocked="onHiddenUnlocked"
    />

    <CreateActionSheet
      v-model:open="createSheetOpen"
      @create-folder="openCreateFolderDialog"
      @upload-media="triggerMediaUpload"
      @capture-photo="triggerPhotoCapture"
      @capture-video="triggerVideoCapture"
      @record-voice="openVoiceRecorder"
      @upload-files="triggerFileUpload"
    />

    <CapturePreviewSheet
      v-model:open="capturePreviewOpen"
      :file="capturePreviewFile"
      :kind="capturePreviewKind"
      @confirm="handleConfirmedMediaUpload"
    />

    <VoiceRecorderSheet
      v-model:open="voiceRecorderOpen"
      @recorded="handleConfirmedMediaUpload"
    />

    <MobilePreviewShell
      :open="Boolean(previewNode)"
      :node="previewNode"
      :siblings="nodes"
      :favorite="previewNode ? favoriteNodeIds.has(previewNode.id) : false"
      @close="closePreview"
      @select="selectPreviewNode"
      @toggle-favorite="toggleFavoriteDirect"
      @detail="node => { activeNode = node; detailSheetOpen = true }"
      @more="node => openNodeMenu(node)"
      @share="openShareForNode"
    />

    <ActionSheet
      v-model:open="shareChoiceOpen"
      title="选择分享方式"
      :actions="shareChoiceActions"
      @select="handleShareChoice"
    />

    <ShareLinkSheet
      v-model:open="shareLinkOpen"
      :node="shareTargetNode"
      :space="currentSpace"
    />

    <FolderShareSheet
      v-model:open="folderShareOpen"
      :node="shareTargetNode"
      :space="currentSpace"
    />

    <SpaceMembersSheet
      v-model:open="spaceMembersOpen"
      :space-id="currentSpace?.id || ''"
      :space-name="currentSpace?.name"
    />

    <ActionSheet
      v-model:open="actionSheetOpen"
      :title="activeNode?.name || ''"
      :actions="activeNodeActions"
      @select="handleNodeAction"
    />

    <RenameDialogSheet
      v-model:open="renameSheetOpen"
      :mode="renameMode"
      :initial-name="activeNode?.name || ''"
      :is-folder="renameMode === 'create-folder' || activeNode?.nodeType === 'FOLDER'"
      :submitting="renameSubmitting"
      @submit="handleRenameOrCreateSubmit"
    />

    <NodeDetailSheet
      v-model:open="detailSheetOpen"
      :node="activeNode"
      :space="currentSpace"
      :ancestors="ancestors"
      :favorite="activeNode ? favoriteNodeIds.has(activeNode.id) : false"
    />

    <VersionsSheet
      v-model:open="versionsSheetOpen"
      :node="activeNode"
      :space="currentSpace"
      @restored="loadDirectory(true)"
    />

    <FolderPickerSheet
      v-model:open="pickerOpen"
      :mode="pickerMode"
      :source-space-id="pickerTargetScope === 'batch' ? currentSpace?.id || '' : activeNode?.spaceId || currentSpace?.id || ''"
      :source-parent-id="pickerTargetScope === 'batch' ? routeFolderId : activeNode?.parentId || routeFolderId"
      :exclude-node-ids="pickerExcludeIds"
      :title="
        pickerTargetScope === 'batch'
          ? `${pickerMode === 'MOVE' ? '批量移动' : '批量复制'}（已选 ${selectedNodeIds.size} 项）`
          : activeNode
            ? `${pickerMode === 'MOVE' ? '移动' : '复制'}「${activeNode.name}」`
            : undefined
      "
      :submitting="pickerSubmitting"
      @confirm="handleTransferConfirm"
    />
  </div>
</template>
