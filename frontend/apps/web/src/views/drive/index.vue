<script setup lang="ts">
import { Button } from '@repo/ui/button'
import { Checkbox } from '@repo/ui/checkbox'
import { ColorPicker } from '@repo/ui/color-picker'
import { IconPicker, LucideIconByName } from '@repo/ui/icon-picker'
import { Input } from '@repo/ui/input'
import { Select } from '@repo/ui/select'
import { toast } from '@repo/ui/toast'
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogDescription, DialogFooter } from '@repo/ui/dialog'
import { ContextMenu, ContextMenuTrigger, ContextMenuContent, ContextMenuItem, ContextMenuSeparator } from '@repo/ui/context-menu'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@repo/ui/dropdown-menu'
import MoveDialog from '@/components/drive/MoveDialog.vue'
import FolderTree from '@/components/drive/FolderTree.vue'
import PreviewDialog from '@/components/drive/PreviewDialog.vue'
import TaskCenterPanel from '@/components/drive/TaskCenterPanel.vue'
import SharedCenterPanel from '@/components/drive/SharedCenterPanel.vue'
import SpaceMembersDialog from '@/components/drive/SpaceMembersDialog.vue'
import FolderShareDialog from '@/components/drive/FolderShareDialog.vue'
import ShareLinkDialog from '@/components/drive/ShareLinkDialog.vue'
import BatchRenameDialog from '@/components/drive/BatchRenameDialog.vue'
import SearchPanel from '@/components/drive/SearchPanel.vue'
import RecentPanel from '@/components/drive/RecentPanel.vue'
import MessagePopover from '@/components/drive/MessagePopover.vue'
import UserCenterPanel, { type SettingsSection, type UserProfile } from '@/components/drive/UserCenterPanel.vue'
import { computed, nextTick, reactive, ref, watch, onMounted, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  ArrowLeft,
  ChevronRight,
  ChevronsUpDown,
  Clock,
  Cloud,
  Download,
  Eye,
  File,
  FileArchive,
  Folder,
  FolderPlus,
  FolderUp,
  Grid2X2,
  History,
  Info,
  Link2,
  List,
  ListTree,
  LockKeyhole,
  LogOut,
  MoreHorizontal,
  Palette,
  PanelLeft,
  Pin,
  Plus,
  RefreshCw,
  Search,
  Settings,
  Share2,
  Shield,
  ShieldCheck,
  SlidersHorizontal,
  Star,
  Trash2,
  Undo2,
  Upload,
  User,
  Users,
  Wand2,
  X,
} from '@lucide/vue'
import { drive, uploadFile, type DirectoryStats, type DriveNode, type Space, type Version } from '@/api/drive'
import { roleLabel, type ResourceShareItem } from '@/api/share'
import { opsApi, type QuickAccessItem, type RecentAccessItem, type UndoTokenReceipt } from '@/api/ops'
import type { SearchHit } from '@/api/search'
import { mainFilenameRange, partitionBreadcrumbs } from '@/lib/tree'
import { getOrFetchThumbnail } from '@/lib/thumb-cache'
import { createUploadQueue, statusLabel, type UploadQueueItem } from '@/lib/upload-queue'
import {
  DEFAULT_TREE_WIDTH,
  MAX_TREE_WIDTH,
  MIN_TREE_WIDTH,
  clampTreeWidth,
  loadLayoutPreferences,
  resolveViewportTier,
  saveLayoutPreferences,
  type LayoutPreferences,
} from '@/lib/layout'
import { logout as endSession } from '@repo/session/session'
import { clearPrivateSpaceToken, hasPrivateSpaceToken, request, RequestError, saveBlob, setPrivateSpaceToken } from '@/utils/request'
import '@/styles/drive.css'

const route = useRoute(), router = useRouter()
const layout = reactive<LayoutPreferences>(loadLayoutPreferences())
const viewportWidth = ref(typeof window !== 'undefined' ? window.innerWidth : 1440)
const viewportTier = computed(() => resolveViewportTier(viewportWidth.value))
const mobileNavOpen = ref(false), drawerTreeOpen = ref(false), resizingTree = ref(false)
const mode = computed<'files' | 'favorites' | 'settings' | 'tasks' | 'shared' | 'recent' | 'search'>(() => {
  if (route.name === 'settings') return 'settings'
  if (route.name === 'search') return 'search'
  if (route.name === 'tasks') return 'tasks'
  if (route.name === 'shared') return 'shared'
  if (route.name === 'recent') return 'recent'
  if (route.name === 'favorites') return 'favorites'
  return 'files'
})
const hasSecondaryPane = computed(() => mode.value === 'settings' || mode.value === 'files')
const sidebarVisible = computed(() => viewportTier.value === 'mobile' ? mobileNavOpen.value : !layout.sidebarCollapsed)
const treeVisible = computed(() => hasSecondaryPane.value && (viewportTier.value === 'desktop' ? (mode.value === 'settings' || !layout.treeCollapsed) : drawerTreeOpen.value))
const hasDrawerOpen = computed(() => (viewportTier.value === 'mobile' && mobileNavOpen.value) || (viewportTier.value !== 'desktop' && hasSecondaryPane.value && drawerTreeOpen.value))
let stopResize: (() => void) | undefined

function closeDrawers() { mobileNavOpen.value = false; drawerTreeOpen.value = false }
function toggleSidebar() {
  if (viewportTier.value === 'mobile') { mobileNavOpen.value = !mobileNavOpen.value; if (mobileNavOpen.value) drawerTreeOpen.value = false }
  else { layout.sidebarCollapsed = !layout.sidebarCollapsed }
}
function toggleTree() {
  if (viewportTier.value === 'desktop') { layout.treeCollapsed = !layout.treeCollapsed }
  else { drawerTreeOpen.value = !drawerTreeOpen.value; if (drawerTreeOpen.value) mobileNavOpen.value = false }
}
function nudgeTreeWidth(delta: number) { layout.treeWidth = clampTreeWidth(layout.treeWidth + delta) }
function startTreeResize(event: PointerEvent) {
  if (viewportTier.value !== 'desktop') return
  event.preventDefault()
  stopResize?.()
  const startX = event.clientX, startWidth = layout.treeWidth
  resizingTree.value = true
  const move = (e: PointerEvent) => { layout.treeWidth = clampTreeWidth(startWidth + (e.clientX - startX)) }
  const end = () => {
    resizingTree.value = false
    window.removeEventListener('pointermove', move)
    window.removeEventListener('pointerup', end)
    window.removeEventListener('pointercancel', end)
    stopResize = undefined
  }
  stopResize = end
  window.addEventListener('pointermove', move)
  window.addEventListener('pointerup', end)
  window.addEventListener('pointercancel', end)
}
function handleResize() {
  viewportWidth.value = window.innerWidth
  if (resolveViewportTier(viewportWidth.value) === 'desktop') closeDrawers()
}

const spaces = ref<Space[]>([]), nodes = ref<DriveNode[]>([]), ancestors = ref<DriveNode[]>([])
const visibleSpaces = computed(() => spaces.value.filter(s => s.kind !== 'PRIVATE'))
const selected = ref(new Set<string>()), page = ref(1), total = ref(0)
const sort = ref(localStorage.getItem('cloudlane.sort') || 'name')
const query = ref('')
const searchInitialQuery = ref('')
const searchInitialScope = ref<'space' | 'global'>('space')
const view = ref(localStorage.getItem('cloudlane.view') || 'list')
const category = ref(''), dirStats = ref<DirectoryStats>({ folders: 0, files: 0, categories: {} })
const crumbMenuOpen = ref(false), draggingId = ref(''), dropTargetId = ref('')
const inlineCreateActive = ref(false), inlineCreateName = ref('新建文件夹'), inlineCreateBusy = ref(false), inlineCreateInput = ref<HTMLInputElement>()
const inlineRenameId = ref(''), inlineRenameName = ref(''), inlineRenameBusy = ref(false)

const CATEGORY_LABELS: Record<string, string> = {
  IMAGE: '图片', PDF: 'PDF', VIDEO: '视频', AUDIO: '音频', WORD: 'Word',
  EXCEL: '表格', PPT: '演示', CODE: '代码/文本', ARCHIVE: '压缩包', OTHER: '其他',
}
const SETTINGS_NAV: { key: SettingsSection; label: string; description: string }[] = [
  { key: 'profile', label: '个人资料', description: '昵称、头像与基础信息' },
  { key: 'security', label: '账户与安全', description: '活跃登录设备与会话管理' },
  { key: 'appearance', label: '显示与外观', description: '主题模式、强调色与密度' },
  { key: 'files', label: '文件偏好', description: '默认视图、排序与版本策略' },
  { key: 'private', label: '私密设置', description: '需密码进入，管理私密空间与文件' },
  { key: 'trash', label: '回收站', description: '恢复或彻底清理已删除资源' },
]

const settingsSection = computed<SettingsSection>(() => {
  const sec = String(route.params.section || 'profile')
  return (['profile', 'security', 'appearance', 'files', 'private', 'trash'].includes(sec) ? sec : 'profile') as SettingsSection
})

const lastSpaceId = ref('')
const spaceId = computed(() => String(route.params.spaceId || lastSpaceId.value || visibleSpaces.value[0]?.id || spaces.value[0]?.id || ''))
const parentId = computed(() => String(route.params.folderId || '0'))
const currentSpace = computed(() => spaces.value.find(s => s.id === spaceId.value))
const currentSelection = computed(() => nodes.value.filter(n => selected.value.has(n.id)))
const activeCategories = computed(() => Object.entries(dirStats.value.categories || {}).filter(([, count]) => count > 0))
const breadcrumbParts = computed(() => partitionBreadcrumbs(ancestors.value, 2))

const loading = ref(false), error = ref(''), notice = ref(''), treeRevision = ref(0)
const preview = ref<DriveNode>(), previewVersion = ref<Version>(), versions = ref<Version[]>([]), versionNode = ref<DriveNode>()
const fileInput = ref<HTMLInputElement>(), folderInput = ref<HTMLInputElement>(), replacement = ref<HTMLInputElement>()
const replacing = ref<DriveNode>(), uploadTasks = ref<UploadQueueItem[]>([])
const uploadPanelVisible = ref(false)
const activeUploadCount = computed(() => uploadTasks.value.filter(t => t.status === 'uploading' || t.status === 'queued').length)
const movingNodes = ref<DriveNode[]>([])
const spaceSettingsTab = ref<'general' | 'members'>('general')
const sharingNode = ref<DriveNode>()
const shareLinkNode = ref<DriveNode>()
const detailNode = ref<DriveNode>()
const batchRenameOpen = ref(false)

function openSearch(scope: 'space' | 'global' = 'space', initial = '') {
  searchInitialScope.value = scope
  searchInitialQuery.value = initial
  crumbMenuOpen.value = false
  cancelInlineEdit()
  closeDrawers()
  if (route.name !== 'search') void router.push({ name: 'search' })
}

function locateSearchHit(hit: SearchHit) {
  navigate(hit.nodeType === 'FOLDER' ? hit.id : hit.parentId, hit.spaceId)
}

function previewSearchHit(hit: SearchHit) {
  previewVersion.value = undefined
  preview.value = {
    id: hit.id,
    spaceId: hit.spaceId,
    parentId: hit.parentId,
    name: hit.name,
    nodeType: hit.nodeType,
    currentVersionId: null,
    revision: 0,
    size: hit.size,
    mimeType: hit.mimeType,
    hidden: false,
    updatedAt: hit.updatedAt,
    hasChildren: false,
  }
}
const quickAccess = ref<QuickAccessItem[]>([])
const undoReceipt = ref<UndoTokenReceipt | null>(null)
const undoing = ref(false)
let undoTimer: ReturnType<typeof setTimeout> | undefined

const allSelected = computed(() => nodes.value.length > 0 && selected.value.size === nodes.value.length)
const someSelected = computed(() => selected.value.size > 0 && selected.value.size < nodes.value.length)

function toggleNodeSelected(node: DriveNode, checked: boolean | 'indeterminate') {
  const next = new Set(selected.value)
  if (checked === true) next.add(node.id)
  else next.delete(node.id)
  selected.value = next
}
function previewDetailNode() {
  const target = detailNode.value
  detailNode.value = undefined
  if (target) open(target)
}

function toggleSelectAll() {
  if (allSelected.value) {
    selected.value = new Set()
  } else {
    selected.value = new Set(nodes.value.map(n => n.id))
  }
}

function showUndo(receipt: UndoTokenReceipt | null) {
  if (undoTimer) clearTimeout(undoTimer)
  undoReceipt.value = receipt
  if (receipt) {
    undoTimer = setTimeout(() => { undoReceipt.value = null }, 10_000)
  }
}

async function performUndo() {
  if (!undoReceipt.value || undoing.value) return
  undoing.value = true
  try {
    await opsApi.executeUndo(undoReceipt.value.tokenId)
    showUndo(null)
    notice.value = '已撤销上次操作'
    await refresh()
    treeRevision.value++
  } catch (e) {
    fail(e)
  } finally {
    undoing.value = false
  }
}

async function loadQuickAccess() {
  try { quickAccess.value = await opsApi.listQuickAccess() } catch { /* ignore */ }
}

async function pinNode(node: DriveNode, enabled: boolean) {
  try {
    await opsApi.setPin(node.spaceId, node.id, enabled)
    notice.value = enabled ? `已将「${node.name}」固定到快捷访问` : `已取消快捷访问`
    await loadQuickAccess()
  } catch (e) { fail(e) }
}

function openQuickAccessItem(item: QuickAccessItem) {
  closeDrawers()
  if (item.nodeType === 'FOLDER') {
    router.push({ name: 'folders', params: { spaceId: item.spaceId, folderId: item.nodeId } })
  } else {
    previewVersion.value = undefined
    preview.value = {
      id: item.nodeId,
      spaceId: item.spaceId,
      parentId: '0',
      name: item.name,
      nodeType: 'FILE',
      currentVersionId: null,
      revision: 0,
      size: item.size,
      mimeType: '',
      hidden: false,
      updatedAt: item.updatedAt,
      hasChildren: false,
    }
  }
}

function openShared() { closeDrawers(); router.push({ name: 'shared' }) }
function openRecent() { closeDrawers(); router.push({ name: 'recent' }) }
async function markNodePrivate(node: DriveNode) {
  try {
    await opsApi.setHidden(node.spaceId, node.id, true)
    notice.value = `已将「${node.name}」设为私密（可在个人中心 → 私密设置中查看）`
    await refresh()
    await loadQuickAccess()
  } catch (e) { fail(e) }
}
async function downloadZipSelection() {
  if (!spaceId.value || !currentSelection.value.length) return
  try {
    const job = await opsApi.createZipBatch(spaceId.value, currentSelection.value.map(n => n.id))
    if (job.successCount > 0) {
      const blob = await opsApi.downloadZipBatch(job.id)
      saveBlob(blob, `cloudlane-batch-${job.id}.zip`)
      notice.value = `已打包下载 ${job.successCount} 个文件`
    } else {
      error.value = job.errorMessage || '所选条目中没有可打包的文件'
    }
  } catch (e) { fail(e) }
}
function openRecentItem(item: RecentAccessItem, modeAction: 'locate' | 'preview') {
  if (modeAction === 'locate') {
    navigate(item.parentId, item.spaceId)
    return
  }
  previewVersion.value = undefined
  preview.value = {
    id: item.id, spaceId: item.spaceId, parentId: item.parentId, name: item.name,
    nodeType: item.nodeType, currentVersionId: null, revision: 0, size: item.size,
    mimeType: item.mimeType, hidden: false, updatedAt: item.accessedAt, hasChildren: false,
  }
}
function openSharedFolder(item: ResourceShareItem) {
  closeDrawers()
  router.push({ name: 'folders', params: { spaceId: item.spaceId, folderId: item.nodeId } })
}
async function reloadSpaces() {
  spaces.value = await drive.spaces()
}
const profile = ref<UserProfile>({ id: '', displayName: '', avatarUrl: null, admin: false })
const admin = computed(() => profile.value.admin)
const uploading = computed(() => activeUploadCount.value > 0)
const folderCache = new Map<string, string>()
const uploadQueue = createUploadQueue(
  async (item, onProgress, signal) => {
    let parent = item.parentId
    let pathKey = `${item.spaceId}:${item.parentId}`
    for (const seg of item.name.split('/').slice(0, -1)) {
      const nextKey = `${pathKey}/${seg}`
      if (!folderCache.has(nextKey)) {
        try {
          folderCache.set(nextKey, (await drive.folder(item.spaceId, parent, seg)).id)
        } catch (e) {
          const existing = await drive.nodes(item.spaceId, { parentId: parent, folders: true, pageSize: 200 })
          const folder = existing.items.find(n => n.name === seg)
          if (!folder) throw e
          folderCache.set(nextKey, folder.id)
        }
      }
      parent = folderCache.get(nextKey)!
      pathKey = nextKey
    }
    await uploadFile(item.spaceId, parent, item.file, onProgress, signal)
    treeRevision.value++
    await refresh()
  },
  items => { uploadTasks.value = items },
)
let generation = 0, searchTimer: ReturnType<typeof setTimeout> | undefined

const dialog = ref<{ title: string; label: string; value: string; submit: (value: string) => Promise<unknown> }>()
const dialogBusy = ref(false)
const spaceDialogOpen = ref(false), spaceSaving = ref(false), spaceName = ref(''), spaceDescription = ref(''), spaceColor = ref('#6366f1'), spaceIcon = ref('folder'), spaceSortOrder = ref(0), spaceDeleteConfirm = ref('')

async function submitDialog() {
  if (!dialog.value || dialogBusy.value) return
  dialogBusy.value = true; error.value = ''
  try { await dialog.value.submit(dialog.value.value); dialog.value = undefined; await refresh(); treeRevision.value++ }
  catch (e) { fail(e) } finally { dialogBusy.value = false }
}
function openSpaceSettings(tab: 'general' | 'members' = 'general') {
  if (!currentSpace.value) return
  spaceName.value = currentSpace.value.name
  spaceDescription.value = currentSpace.value.description || ''
  spaceColor.value = currentSpace.value.color || '#6366f1'
  spaceIcon.value = currentSpace.value.icon || 'folder'
  spaceSortOrder.value = currentSpace.value.sortOrder || 0
  spaceDeleteConfirm.value = ''
  error.value = ''
  spaceSettingsTab.value = tab
  spaceDialogOpen.value = true
}
async function saveSpaceSettings() {
  if (spaceSaving.value || !spaceId.value) return
  spaceSaving.value = true; error.value = ''
  try {
    await drive.updateSpace(spaceId.value, spaceName.value, Number(spaceSortOrder.value) || 0, {
      description: spaceDescription.value,
      color: spaceColor.value,
      icon: spaceIcon.value,
    })
    await loadSpaces()
    spaceDialogOpen.value = false
    notice.value = '空间设置已保存'
  } catch (e) { fail(e) } finally { spaceSaving.value = false }
}
async function confirmDeleteSpace() {
  if (spaceSaving.value || !spaceId.value || !currentSpace.value) return
  spaceSaving.value = true; error.value = ''
  try {
    await drive.deleteSpace(spaceId.value, spaceDeleteConfirm.value)
    await loadSpaces()
    spaceDialogOpen.value = false
    if (spaces.value[0]) navigate('0', spaces.value[0].id)
    notice.value = '空间已删除'
  } catch (e) { fail(e) } finally { spaceSaving.value = false }
}

function fail(e: unknown) {
  const msg = e instanceof Error ? e.message : '操作失败，请重试'
  error.value = msg
  toast.error(msg)
}
function notify(msg: string) {
  toast.success(msg)
}

const includeSubdirs = ref(false)
const privateUnlockOpen = ref(false)
const privateUnlockPassword = ref('')
const privateUnlockIdle = ref(15)
const privateUnlockBusy = ref(false)
const privateUnlockError = ref('')

async function submitPrivateUnlock() {
  if (privateUnlockBusy.value || !spaceId.value) return
  if (!privateUnlockPassword.value) {
    privateUnlockError.value = '请输入账号密码'
    return
  }
  privateUnlockBusy.value = true
  privateUnlockError.value = ''
  try {
    const res = await opsApi.unlockPrivate(spaceId.value, privateUnlockPassword.value, Number(privateUnlockIdle.value) || 15)
    setPrivateSpaceToken(spaceId.value, res.privateToken)
    privateUnlockOpen.value = false
    privateUnlockPassword.value = ''
    notice.value = `私密空间已解锁（空闲 ${res.idleMinutes} 分钟后自动锁定）`
    await refresh()
  } catch (e) {
    privateUnlockError.value = e instanceof Error ? e.message : '密码验证失败'
  } finally {
    privateUnlockBusy.value = false
  }
}

async function lockCurrentPrivateSpace() {
  if (!spaceId.value) return
  try {
    await opsApi.lockPrivate(spaceId.value)
  } catch { /* ignore */ }
  clearPrivateSpaceToken(spaceId.value)
  nodes.value = []
  total.value = 0
  notice.value = '私密空间已锁定'
  const fallback = visibleSpaces.value[0]
  if (fallback) navigate('0', fallback.id)
  else openSettings('private')
}

async function loadSpaces() { spaces.value = await drive.spaces() }
async function refresh() {
  if (mode.value === 'settings' || mode.value === 'tasks' || mode.value === 'search' || !spaceId.value) return
  const current = ++generation; loading.value = true; error.value = ''; selected.value = new Set()
  try {
    const [result, path, statsResult] = await Promise.all([
      drive.nodes(spaceId.value, {
        parentId: parentId.value, page: page.value, pageSize: 100, sort: sort.value,
        query: query.value, hidden: false, trash: false,
        favorites: mode.value === 'favorites', category: mode.value === 'files' && category.value ? category.value : undefined,
      }),
      parentId.value === '0' || mode.value !== 'files' ? Promise.resolve([]) : drive.ancestors(spaceId.value, parentId.value),
      mode.value === 'files' ? drive.stats(spaceId.value, parentId.value, false, includeSubdirs.value) : Promise.resolve({ folders: 0, files: 0, categories: {} }),
    ])
    if (current !== generation) return
    nodes.value = result.items; total.value = result.total; ancestors.value = path; dirStats.value = statsResult
  } catch (e) {
    if (current !== generation) return
    if (e instanceof RequestError && e.httpStatus === 423) {
      clearPrivateSpaceToken(spaceId.value)
      nodes.value = []; total.value = 0
      privateUnlockError.value = ''
      privateUnlockPassword.value = ''
      privateUnlockOpen.value = true
      return
    }
    fail(e)
  } finally { if (current === generation) loading.value = false }
}

function navigate(id: string, space = spaceId.value) {
  if (space) lastSpaceId.value = space
  query.value = ''; category.value = ''; page.value = 1; crumbMenuOpen.value = false; cancelInlineEdit(); closeDrawers()
  const target = id === '0' ? `/app/spaces/${space}` : `/app/spaces/${space}/folders/${id}`
  if (route.path === target) void refresh(); else void router.push(target)
}
function openFavorites() {
  query.value = ''; category.value = ''; page.value = 1; crumbMenuOpen.value = false; cancelInlineEdit(); closeDrawers()
  if (route.path === '/app/favorites') void refresh(); else void router.push('/app/favorites')
}
function openSettings(section: SettingsSection = 'profile') {
  crumbMenuOpen.value = false; cancelInlineEdit(); closeDrawers()
  void router.push(`/app/settings/${section}`)
}
function openTasks() {
  crumbMenuOpen.value = false; cancelInlineEdit(); closeDrawers()
  void router.push('/app/tasks')
}
function backToFiles() {
  const targetSpace = (currentSpace.value && currentSpace.value.kind !== 'PRIVATE' ? currentSpace.value.id : '') || visibleSpaces.value[0]?.id || spaces.value[0]?.id
  if (targetSpace) navigate('0', targetSpace)
}

function selectCategory(cat: string) {
  category.value = category.value === cat ? '' : cat
  page.value = 1
  void refresh()
}
function cancelInlineEdit() {
  inlineCreateActive.value = false
  inlineRenameId.value = ''
}
async function newFolder() {
  if (mode.value !== 'files' || !spaceId.value) return
  inlineRenameId.value = ''
  inlineCreateName.value = '新建文件夹'
  inlineCreateActive.value = true
  error.value = ''
  await nextTick()
  inlineCreateInput.value?.focus()
  inlineCreateInput.value?.select()
}
async function commitInlineCreate() {
  if (!inlineCreateActive.value || inlineCreateBusy.value) return
  inlineCreateBusy.value = true; error.value = ''
  try {
    const created = await drive.folder(spaceId.value, parentId.value, inlineCreateName.value)
    inlineCreateActive.value = false
    await refresh()
    selected.value = new Set([created.id])
    treeRevision.value++
  } catch (e) { fail(e) } finally { inlineCreateBusy.value = false }
}
async function rename(node: DriveNode) {
  inlineCreateActive.value = false
  inlineRenameId.value = node.id
  inlineRenameName.value = node.name
  error.value = ''
  await nextTick()
  const input = document.querySelector<HTMLInputElement>(`[data-inline-rename="${node.id}"]`)
  if (input) {
    input.focus()
    const [start, end] = mainFilenameRange(node.name, node.nodeType === 'FOLDER')
    input.setSelectionRange(start, end)
  }
}
async function commitInlineRename(node: DriveNode) {
  if (inlineRenameId.value !== node.id || inlineRenameBusy.value) return
  if (inlineRenameName.value.trim() === node.name) { inlineRenameId.value = ''; return }
  inlineRenameBusy.value = true; error.value = ''
  try {
    await drive.change(node, inlineRenameName.value)
    inlineRenameId.value = ''
    await refresh()
    treeRevision.value++
    try {
      const receipt = await opsApi.issueUndo({
        spaceId: node.spaceId,
        actionType: 'RENAME',
        summary: `已重命名为「${inlineRenameName.value.trim()}」`,
        nodeId: node.id,
        oldName: node.name,
        oldParentId: node.parentId,
      })
      showUndo(receipt)
    } catch { /* non-blocking */ }
  } catch (e) { fail(e) } finally { inlineRenameBusy.value = false }
}

function open(node: DriveNode) {
  if (node.nodeType === 'FOLDER') navigate(node.id)
  else {
    previewVersion.value = undefined
    preview.value = node
    void opsApi.recordAccess(node.spaceId, node.id).catch(() => {})
  }
}
const createSpaceOpen = ref(false)
const createSpaceName = ref('')
const createSpaceKind = ref<'PERSONAL' | 'SHARED'>('PERSONAL')
const createSpaceDesc = ref('')
const createSpaceIcon = ref('folder')
const createSpaceColor = ref('#6366f1')
const createSpaceBusy = ref(false)
const createSpaceError = ref('')

function newSpace() {
  createSpaceName.value = ''
  createSpaceKind.value = 'PERSONAL'
  createSpaceDesc.value = ''
  createSpaceIcon.value = 'folder'
  createSpaceColor.value = '#6366f1'
  createSpaceError.value = ''
  createSpaceOpen.value = true
}

async function submitCreateSpace() {
  if (createSpaceBusy.value) return
  if (!createSpaceName.value.trim()) {
    createSpaceError.value = '请输入空间名称'
    return
  }
  createSpaceBusy.value = true
  createSpaceError.value = ''
  try {
    const id = await drive.createSpace(createSpaceName.value.trim(), {
      kind: createSpaceKind.value,
      description: createSpaceDesc.value.trim(),
      icon: createSpaceIcon.value,
      color: createSpaceColor.value,
    })
    createSpaceOpen.value = false
    await loadSpaces()
    navigate('0', id)
  } catch (e) {
    createSpaceError.value = e instanceof Error ? e.message : '创建空间失败'
  } finally {
    createSpaceBusy.value = false
  }
}

const thumbUrls = reactive<Record<string, string>>({})
const thumbLoading = new Set<string>()
const THUMB_EXTS = new Set([
  // 图片
  'png', 'jpg', 'jpeg', 'gif', 'webp', 'bmp',
  // 视频
  'mp4', 'webm', 'mov', 'mkv', 'avi', 'm4v', 'flv', 'ts', 'wmv', 'ogv',
  // 音频（内嵌专辑封面）
  'mp3', 'flac', 'm4a', 'ogg', 'opus', 'aac', 'wav',
  // 文档与表格
  'pdf', 'docx', 'pptx', 'xlsx', 'xls', 'csv', 'zip',
  // 文本与代码
  'md', 'markdown', 'mdx', 'txt', 'log', 'env',
  'js', 'mjs', 'cjs', 'jsx', 'ts', 'mts', 'cts', 'tsx', 'vue',
  'html', 'htm', 'css', 'scss', 'less', 'json', 'xml', 'yaml', 'yml',
  'java', 'kt', 'py', 'go', 'rs', 'c', 'cpp', 'h', 'hpp', 'cs',
  'php', 'rb', 'sql', 'sh', 'bash', 'ps1', 'toml', 'ini', 'conf', 'properties',
])

function canShowThumb(node: DriveNode): boolean {
  if (node.nodeType !== 'FILE') return false
  const ext = node.name.includes('.') ? node.name.split('.').pop()!.toLowerCase() : ''
  return THUMB_EXTS.has(ext)
}

async function ensureThumbnails() {
  const pending = nodes.value.filter(node => {
    if (!canShowThumb(node)) return false
    const key = `${node.id}:${node.currentVersionId || ''}`
    return !thumbUrls[key] && !thumbLoading.has(key)
  })
  if (!pending.length) return

  // 限制并发数，避免大目录同时触发过多后端缩略图生成任务
  const concurrency = 4
  let cursor = 0
  async function worker() {
    while (cursor < pending.length) {
      const node = pending[cursor++]!
      const key = `${node.id}:${node.currentVersionId || ''}`
      if (thumbUrls[key] || thumbLoading.has(key)) continue
      thumbLoading.add(key)
      try {
        const blob = await getOrFetchThumbnail(`${key}:320`, () => drive.thumbnail(node, 320, node.currentVersionId || undefined))
        if (blob && blob.size > 0) thumbUrls[key] = URL.createObjectURL(blob)
      } catch {
        // 404（如无专辑封面的音频）静默回退默认文件图标
      } finally {
        thumbLoading.delete(key)
      }
    }
  }
  await Promise.all(Array.from({ length: Math.min(concurrency, pending.length) }, () => worker()))
}

watch([() => view.value, () => nodes.value], () => { void ensureThumbnails() })
async function move(source: string, target: string) {
  const node = nodes.value.find(n => n.id === source)
  if (!node) return
  try {
    await drive.change(node, node.name, target)
    await refresh()
    treeRevision.value++
    try {
      const receipt = await opsApi.issueUndo({
        spaceId: node.spaceId,
        actionType: 'MOVE',
        summary: `已移动「${node.name}」`,
        nodeId: node.id,
        oldName: node.name,
        oldParentId: node.parentId,
      })
      showUndo(receipt)
    } catch { /* non-blocking */ }
  } catch (e) { fail(e) }
}
function moveSelection() { movingNodes.value = [...currentSelection.value] }
async function batch(action: (node: DriveNode) => Promise<unknown>) {
  let success = 0; const failures: string[] = []
  for (const node of currentSelection.value) { try { await action(node); success++ } catch (e) { failures.push(`${node.name}：${e instanceof Error ? e.message : '失败'}`) } }
  await refresh(); treeRevision.value++
  if (failures.length) toast.warning(`成功 ${success} 项；失败：${failures.join('；')}`)
  else if (success > 0) toast.success(`已完成 ${success} 项操作`)
}
async function deleteSelection() {
  const targets = [...currentSelection.value]
  if (!targets.length) return
  let success = 0; const failures: string[] = []
  let lastTrashed: DriveNode | undefined
  for (const node of targets) {
    try { await drive.trash(node); success++; lastTrashed = node }
    catch (e) { failures.push(`${node.name}：${e instanceof Error ? e.message : '失败'}`) }
  }
  await refresh(); treeRevision.value++
  if (failures.length) {
    toast.warning(`已移入回收站 ${success} 项；失败：${failures.join('；')}`)
  } else if (success > 0) {
    notify(success === 1 ? `已将「${lastTrashed?.name}」移入回收站` : `已将 ${success} 项移入回收站`)
    if (success === 1 && lastTrashed) {
      try {
        const receipt = await opsApi.issueUndo({
          spaceId: lastTrashed.spaceId,
          actionType: 'TRASH',
          summary: `已移入回收站「${lastTrashed.name}」`,
          nodeId: lastTrashed.id,
          oldName: lastTrashed.name,
          oldParentId: lastTrashed.parentId,
        })
        showUndo(receipt)
      } catch { /* non-blocking */ }
    }
  }
}
async function download(node: DriveNode) { try { saveBlob(await drive.content(node), node.name) } catch (e) { fail(e) } }
async function showVersions(node: DriveNode) { try { versions.value = await drive.versions(node); versionNode.value = node } catch (e) { fail(e) } }
async function restoreVersion(version: Version) {
  if (!versionNode.value) return
  try { await drive.restoreVersion(versionNode.value, version.id); versionNode.value = undefined; await refresh(); notice.value = '已创建新的当前版本，历史版本保留' } catch (e) { fail(e) }
}
async function replace(event: Event) {
  const file = (event.target as HTMLInputElement).files?.[0]
  if (!file || !replacing.value) return
  try { await drive.replace(replacing.value, file); notice.value = '已保存新版本'; await refresh() } catch (e) { fail(e) }
  ;(event.target as HTMLInputElement).value = ''
}
async function upload(files: File[], targetParentId = parentId.value) {
  if (!spaceId.value || !files.length) return
  folderCache.clear()
  uploadPanelVisible.value = true
  uploadQueue.enqueue(files.map(file => ({
    file,
    name: file.webkitRelativePath || file.name,
    spaceId: spaceId.value,
    parentId: targetParentId,
  })))
}
function selectFiles(event: Event) { const target = event.target as HTMLInputElement; void upload(Array.from(target.files || [])); target.value = '' }

const externalDragOver = ref(false)
let externalDragDepth = 0

const currentDropTargetName = computed(() => {
  if (ancestors.value.length) return ancestors.value[ancestors.value.length - 1]!.name
  return currentSpace.value?.name ? `${currentSpace.value.name}（根目录）` : '当前目录'
})

function isExternalFileDrag(event: DragEvent): boolean {
  if (draggingId.value) return false
  const types = event.dataTransfer?.types
  return Boolean(types && Array.from(types).includes('Files'))
}

function resetExternalDrag() {
  externalDragDepth = 0
  externalDragOver.value = false
  dropTargetId.value = ''
}

function onMainDragEnter(event: DragEvent) {
  if (mode.value !== 'files' || !isExternalFileDrag(event)) return
  event.preventDefault()
  externalDragDepth++
  externalDragOver.value = true
}

function onMainDragOver(event: DragEvent) {
  if (mode.value !== 'files') return
  if (isExternalFileDrag(event)) {
    event.preventDefault()
    if (event.dataTransfer) event.dataTransfer.dropEffect = 'copy'
    externalDragOver.value = true
  } else {
    event.preventDefault()
  }
}

function onMainDragLeave() {
  if (mode.value !== 'files' || !externalDragOver.value) return
  externalDragDepth = Math.max(0, externalDragDepth - 1)
  if (externalDragDepth === 0) {
    externalDragOver.value = false
    dropTargetId.value = ''
  }
}

function dropFiles(event: DragEvent) {
  const files = event.dataTransfer?.files ? Array.from(event.dataTransfer.files) : []
  resetExternalDrag()
  if (mode.value === 'files' && files.length) {
    void upload(files, parentId.value)
  }
}

function onNodeDragOver(event: DragEvent, node: DriveNode) {
  event.preventDefault()
  if (isExternalFileDrag(event)) {
    if (event.dataTransfer) event.dataTransfer.dropEffect = 'copy'
    externalDragOver.value = true
    dropTargetId.value = node.nodeType === 'FOLDER' ? node.id : ''
    return
  }
  if (node.nodeType === 'FOLDER' && draggingId.value && draggingId.value !== node.id) {
    dropTargetId.value = node.id
  }
}

function onNodeDragLeave(node: DriveNode) {
  if (dropTargetId.value === node.id) dropTargetId.value = ''
}

function onNodeDrop(event: DragEvent, node: DriveNode) {
  const externalFiles = isExternalFileDrag(event) && event.dataTransfer?.files?.length
    ? Array.from(event.dataTransfer.files)
    : []
  const draggedNodeId = event.dataTransfer?.getData('application/cloudlane-node') || ''
  resetExternalDrag()
  draggingId.value = ''
  if (externalFiles.length) {
    if (mode.value !== 'files') return
    if (node.nodeType === 'FOLDER') {
      void upload(externalFiles, node.id)
      toast.success(`已开始上传 ${externalFiles.length} 个文件到「${node.name}」`)
    } else {
      void upload(externalFiles, parentId.value)
    }
    return
  }
  if (node.nodeType === 'FOLDER' && draggedNodeId && draggedNodeId !== node.id) {
    void move(draggedNodeId, node.id)
  }
}
function size(bytes: number) { if (!bytes) return '0 B'; const units = ['B', 'KB', 'MB', 'GB']; const index = Math.min(3, Math.floor(Math.log(bytes) / Math.log(1024))); return `${(bytes / 1024 ** index).toFixed(index ? 1 : 0)} ${units[index]}` }
async function logout() { try { await endSession() } catch (e) { fail(e) } finally { void router.replace('/auth/login') } }

function key(event: KeyboardEvent) {
  if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'k') {
    event.preventDefault()
    openSearch('global')
    return
  }
  if (event.key === 'Escape' && crumbMenuOpen.value) { crumbMenuOpen.value = false; return }
  if (event.key === 'Escape' && hasDrawerOpen.value) { closeDrawers(); return }
  if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'f' && mode.value !== 'settings') {
    if (!(event.target as HTMLElement)?.closest('input,textarea,select,[contenteditable]')) {
      event.preventDefault()
      openSearch('space')
      return
    }
  }
  if ((event.target as HTMLElement)?.closest('input,textarea,select,[contenteditable]') || inlineCreateActive.value || inlineRenameId.value || dialog.value || preview.value || versionNode.value || spaceDialogOpen.value || mode.value === 'settings' || mode.value === 'search') return
  if ((event.ctrlKey || event.metaKey) && event.key === 'a') { event.preventDefault(); selected.value = new Set(nodes.value.map(n => n.id)) }
  if (event.key === 'Escape') selected.value = new Set()
  if (event.key === 'F2' && currentSelection.value[0]) { event.preventDefault(); rename(currentSelection.value[0]) }
  if (event.key === 'Delete' && selected.value.size) deleteSelection()
  if (event.key === 'Enter' && currentSelection.value[0] && !(event.target as HTMLElement)?.closest('button,a,[role="menuitem"]')) open(currentSelection.value[0])
}

const topbarTitle = computed(() => {
  if (mode.value === 'settings') {
    const found = SETTINGS_NAV.find(i => i.key === settingsSection.value)
    return `个人中心 / ${found?.label || '个人资料'}`
  }
  if (mode.value === 'search') return '搜索与检索'
  if (mode.value === 'tasks') return '传输与任务中心'
  if (mode.value === 'shared') return '共享中心'
  if (mode.value === 'recent') return '最近使用'
  if (mode.value === 'favorites') return '我的收藏'
  return ''
})

watch(() => route.fullPath, () => {
  if (route.params.spaceId) lastSpaceId.value = String(route.params.spaceId)
  page.value = 1
  if (mode.value !== 'settings' && mode.value !== 'search') void refresh()
})
watch([sort, page], () => { if (mode.value !== 'settings' && mode.value !== 'search') void refresh() })
watch(query, () => { clearTimeout(searchTimer); searchTimer = setTimeout(() => { page.value = 1; if (mode.value !== 'settings' && mode.value !== 'search') void refresh() }, 300) })
watch(view, value => localStorage.setItem('cloudlane.view', value))
watch(sort, value => localStorage.setItem('cloudlane.sort', value))
watch(layout, value => { saveLayoutPreferences(value) }, { deep: true })

onMounted(async () => {
  document.addEventListener('keydown', key)
  window.addEventListener('resize', handleResize)
  try {
    await loadSpaces()
    void loadQuickAccess()
    const me = await request.get<UserProfile>('/v1/me')
    profile.value = me
    const firstSpace = visibleSpaces.value[0] || spaces.value[0]
    if (route.params.spaceId) lastSpaceId.value = String(route.params.spaceId)
    else if (firstSpace) lastSpaceId.value = firstSpace.id
    if (route.name === 'app' && firstSpace) navigate('0', firstSpace.id)
    else if (mode.value !== 'settings' && mode.value !== 'search') await refresh()
  } catch (e) { fail(e) }
})
onBeforeUnmount(() => { generation++; clearTimeout(searchTimer); uploadQueue.abortAll(); stopResize?.(); document.removeEventListener('keydown', key); window.removeEventListener('resize', handleResize) })
</script>

<template>
  <div
    class="drive-app"
    :class="{
      'is-sidebar-collapsed': viewportTier !== 'mobile' && layout.sidebarCollapsed,
      'is-tree-collapsed': viewportTier === 'desktop' && mode === 'files' && layout.treeCollapsed,
      'is-resizing': resizingTree,
    }"
    :style="{ '--drive-tree-width': `${layout.treeWidth}px` }"
  >
    <div v-if="hasDrawerOpen" class="drive-drawer-backdrop" aria-hidden="true" @click="closeDrawers" />

    <!-- 第一栏：左侧全局导航（折叠时保留图标栏，不含个人中心/回收站，底部账户菜单精简） -->
    <aside class="drive-sidebar" :class="{ 'is-open': mobileNavOpen }" aria-label="全局导航">
      <div class="drive-brand-row">
        <a class="drive-brand" href="/app" title="Cloudlane">
          <span><Cloud :size="24" /></span>
          <div class="drive-brand-text">
            <strong>Cloudlane</strong>
            <small>个人云端工作台</small>
          </div>
        </a>
        <Button v-if="viewportTier === 'mobile'" variant="ghost" size="icon-xs" class="pane-close-btn" aria-label="关闭导航栏" @click="toggleSidebar">
          <X :size="16" />
        </Button>
      </div>

      <div class="sidebar-caption has-action">
        <span class="sidebar-caption-label">空间</span>
        <Button variant="ghost" size="icon-xs" aria-label="新建空间" title="新建空间" @click="newSpace"><Plus :size="16" /></Button>
      </div>
      <div class="space-list">
        <Button
          v-for="space in visibleSpaces"
          :key="space.id"
          variant="ghost"
          class="space-button"
          :class="{ active: spaceId === space.id && mode === 'files' }"
          :title="space.name"
          @click="navigate('0', space.id)"
        >
          <LucideIconByName :name="space.icon" :size="17" class="shrink-0" :style="{ color: space.color || '#6366f1' }" />
          <span class="sidebar-item-label flex-1 text-left">{{ space.name }}</span>
          <small class="space-kind-badge">{{ space.kind === 'SHARED' ? `共享 · ${roleLabel(space.role || 'OWNER')}` : '个人' }}</small>
        </Button>
      </div>

      <div v-if="quickAccess.length" class="sidebar-caption"><span class="sidebar-caption-label">快捷访问</span></div>
      <div v-if="quickAccess.length" class="space-list">
        <div v-for="qa in quickAccess" :key="qa.id" class="flex items-center gap-1">
          <Button variant="ghost" class="nav-button flex-1 min-w-0" :title="qa.name" @click="openQuickAccessItem(qa)">
            <Pin :size="15" class="shrink-0" />
            <span class="sidebar-item-label truncate text-left">{{ qa.name }}</span>
          </Button>
          <Button variant="ghost" size="icon-xs" class="qa-unpin-btn" title="取消固定" aria-label="取消固定" @click="opsApi.setPin(qa.spaceId, qa.nodeId, false).then(loadQuickAccess)">
            <X :size="13" />
          </Button>
        </div>
      </div>

      <div class="sidebar-caption"><span class="sidebar-caption-label">常用与协作</span></div>
      <Button variant="ghost" class="nav-button" :class="{ active: mode === 'recent' }" title="最近使用" @click="openRecent">
        <Clock :size="17" class="shrink-0" /> <span class="sidebar-item-label">最近使用</span>
      </Button>
      <Button variant="ghost" class="nav-button" :class="{ active: mode === 'shared' }" title="共享中心" @click="openShared">
        <Share2 :size="17" class="shrink-0" /> <span class="sidebar-item-label">共享中心</span>
      </Button>
      <Button variant="ghost" class="nav-button" :class="{ active: mode === 'favorites' }" title="我的收藏" @click="openFavorites">
        <Star :size="17" class="shrink-0" /> <span class="sidebar-item-label">我的收藏</span>
      </Button>
      <Button variant="ghost" class="nav-button" :class="{ active: mode === 'tasks' }" title="传输任务" @click="openTasks">
        <Upload :size="17" class="shrink-0" />
        <span class="sidebar-item-label flex-1 text-left">传输任务</span>
        <small v-if="activeUploadCount" class="topbar-badge">{{ activeUploadCount }}</small>
      </Button>

      <div class="sidebar-bottom">
        <DropdownMenu>
          <DropdownMenuTrigger as-child>
            <button type="button" class="sidebar-user-card" aria-label="打开账户菜单" :title="profile.displayName || '我的账号'">
              <img v-if="profile.avatarUrl" :src="profile.avatarUrl" alt="" class="user-avatar-sm" />
              <span v-else class="user-avatar-fallback-sm">{{ (profile.displayName || 'U').slice(0, 1).toUpperCase() }}</span>
              <span class="sidebar-user-meta">
                <strong>{{ profile.displayName || '我的账号' }}</strong>
                <small>{{ admin ? '超级管理员' : '普通成员' }}</small>
              </span>
              <ChevronsUpDown :size="15" class="sidebar-user-chevron text-muted-foreground shrink-0" />
            </button>
          </DropdownMenuTrigger>
          <DropdownMenuContent side="top" align="start" class="w-48">
            <DropdownMenuLabel>{{ profile.displayName || '账户管理' }}</DropdownMenuLabel>
            <DropdownMenuSeparator />
            <DropdownMenuItem @select="openSettings('profile')"><User :size="15" /> 个人中心</DropdownMenuItem>
            <template v-if="admin">
              <DropdownMenuSeparator />
              <DropdownMenuItem as-child>
                <a href="/admin/"><ShieldCheck :size="15" /> 进入管理后台</a>
              </DropdownMenuItem>
            </template>
            <DropdownMenuSeparator />
            <DropdownMenuItem class="text-destructive" @select="logout"><LogOut :size="15" /> 退出登录</DropdownMenuItem>
          </DropdownMenuContent>
        </DropdownMenu>
      </div>
    </aside>

    <!-- 第二栏：仅在文件目录树或个人中心分组导航时展示，其余页面不占用空间 -->
    <aside v-if="hasSecondaryPane" class="drive-tree-pane" :class="{ 'is-open': drawerTreeOpen }" :aria-label="mode === 'settings' ? '个人中心导航' : '目录树'">
      <header class="tree-pane-header">
        <div class="tree-pane-title">
          <Settings v-if="mode === 'settings'" :size="16" />
          <LucideIconByName v-else :name="currentSpace?.icon" :size="16" class="shrink-0" :style="currentSpace?.color ? { color: currentSpace.color } : undefined" />
          <span>{{ mode === 'settings' ? '个人中心' : currentSpace?.name || '目录树' }}</span>
        </div>
        <Button variant="ghost" size="icon-xs" :aria-label="viewportTier === 'desktop' ? '折叠目录树' : '关闭目录树'" @click="toggleTree"><X :size="15" /></Button>
      </header>
      <div class="tree-pane-body">
        <template v-if="mode === 'settings'">
          <nav class="settings-nav-list" aria-label="个人设置分组">
            <Button
              v-for="item in SETTINGS_NAV"
              :key="item.key"
              variant="ghost"
              class="settings-nav-item"
              :class="{ active: settingsSection === item.key }"
              @click="openSettings(item.key)"
            >
              <User v-if="item.key === 'profile'" :size="16" />
              <Shield v-else-if="item.key === 'security'" :size="16" />
              <Palette v-else-if="item.key === 'appearance'" :size="16" />
              <SlidersHorizontal v-else-if="item.key === 'files'" :size="16" />
              <LockKeyhole v-else-if="item.key === 'private'" :size="16" />
              <Trash2 v-else :size="16" />
              <span class="settings-nav-text">
                <strong>{{ item.label }}</strong>
                <small>{{ item.description }}</small>
              </span>
            </Button>
          </nav>
          <div class="mt-auto pt-4">
            <Button variant="outline" size="sm" class="w-full" @click="backToFiles">
              <ArrowLeft :size="14" /> 返回文件工作台
            </Button>
          </div>
        </template>
        <template v-else-if="spaceId">
          <Button variant="ghost" size="sm" class="tree-root-button" :class="{ active: parentId === '0' }" @click="navigate('0')">
            <Folder :size="15" /><span>全部文件</span>
          </Button>
          <FolderTree :space="spaceId" parent="0" :active="parentId" :revision="treeRevision" :ancestor-ids="ancestors.map(a => a.id)" @navigate="navigate" @move="move" />
        </template>
      </div>
      <div
        v-if="viewportTier === 'desktop' && !layout.treeCollapsed"
        class="drive-tree-resizer"
        role="separator"
        aria-orientation="vertical"
        aria-label="调整目录树宽度"
        :aria-valuemin="MIN_TREE_WIDTH"
        :aria-valuemax="MAX_TREE_WIDTH"
        :aria-valuenow="layout.treeWidth"
        tabindex="0"
        @pointerdown="startTreeResize"
        @dblclick="layout.treeWidth = DEFAULT_TREE_WIDTH"
        @keydown.left.prevent="nudgeTreeWidth(-16)"
        @keydown.right.prevent="nudgeTreeWidth(16)"
      />
    </aside>

    <!-- 第三栏：主内容区 -->
    <main
      class="drive-main"
      @dragenter="onMainDragEnter"
      @dragover="onMainDragOver"
      @dragleave="onMainDragLeave"
      @drop.prevent="dropFiles"
    >
      <div v-if="mode === 'files' && externalDragOver && !dropTargetId" class="drive-drop-overlay" aria-hidden="true">
        <div class="drive-drop-overlay-card">
          <div class="flex size-12 items-center justify-center rounded-2xl bg-primary/12 text-primary">
            <Upload :size="24" />
          </div>
          <p class="text-base font-semibold text-foreground">松开鼠标，上传到「{{ currentDropTargetName }}」</p>
          <p class="text-xs text-muted-foreground">拖放到子文件夹行上可直接上传至该子文件夹</p>
        </div>
      </div>
      <header class="drive-topbar">
        <div class="topbar-leading">
          <Button variant="ghost" size="icon-sm" class="layout-toggle-btn" :aria-label="sidebarVisible ? '收起导航栏' : '展开导航栏'" :aria-pressed="sidebarVisible" @click="toggleSidebar"><PanelLeft :size="17" /></Button>
          <Button
            v-if="mode === 'files'"
            variant="ghost"
            :size="treeVisible ? 'icon-sm' : 'sm'"
            class="layout-toggle-btn"
            :aria-label="treeVisible ? '收起目录树' : '展开目录树'"
            :aria-pressed="treeVisible"
            :title="treeVisible ? '收起目录树' : '展开目录树'"
            @click="toggleTree"
          >
            <ListTree :size="17" />
            <span v-if="!treeVisible" class="text-xs">目录树</span>
          </Button>
          <span class="topbar-crumb">{{ topbarTitle }}</span>
        </div>

        <div class="topbar-trailing">
          <button
            v-if="mode !== 'settings' && mode !== 'search'"
            type="button"
            class="drive-search cursor-pointer text-left"
            aria-label="搜索当前空间或全局检索"
            @click="openSearch('space', query)"
          >
            <Search :size="16" class="shrink-0" />
            <span class="truncate text-sm">{{ query || '搜索当前空间 / 全局检索… (Ctrl+F)' }}</span>
          </button>

          <MessagePopover />

          <Button
            variant="ghost"
            size="sm"
            class="topbar-upload-btn"
            aria-label="传输任务中心"
            title="传输任务中心"
            @click="openTasks"
          >
            <Upload :size="16" />
            <span v-if="activeUploadCount || uploadTasks.length" class="topbar-badge">{{ activeUploadCount || uploadTasks.length }}</span>
          </Button>

          <DropdownMenu>
            <DropdownMenuTrigger as-child>
              <Button variant="ghost" size="icon-sm" class="rounded-full p-0" aria-label="用户快捷菜单">
                <img v-if="profile.avatarUrl" :src="profile.avatarUrl" alt="" class="user-avatar-sm" />
                <span v-else class="user-avatar-fallback-sm">{{ (profile.displayName || 'U').slice(0, 1).toUpperCase() }}</span>
              </Button>
            </DropdownMenuTrigger>
            <DropdownMenuContent align="end" class="w-48">
              <DropdownMenuLabel>
                <div class="truncate">{{ profile.displayName || '我的账号' }}</div>
                <div class="text-xs font-normal text-muted-foreground">{{ admin ? '超级管理员' : '普通成员' }}</div>
              </DropdownMenuLabel>
              <DropdownMenuSeparator />
              <DropdownMenuItem @select="openSettings('profile')"><User :size="15" /> 个人中心</DropdownMenuItem>
              <template v-if="admin">
                <DropdownMenuSeparator />
                <DropdownMenuItem as-child>
                  <a href="/admin/"><ShieldCheck :size="15" /> 管理后台</a>
                </DropdownMenuItem>
              </template>
              <DropdownMenuSeparator />
              <DropdownMenuItem class="text-destructive" @select="logout"><LogOut :size="15" /> 退出登录</DropdownMenuItem>
            </DropdownMenuContent>
          </DropdownMenu>
        </div>
      </header>

      <!-- 个人中心内容区 -->
      <section v-if="mode === 'settings'" class="drive-content">
        <UserCenterPanel
          :section="settingsSection"
          :profile="profile"
          :view="view"
          :sort="sort"
          :spaces="spaces"
          :current-space-id="spaceId"
          @profile-updated="next => { profile = next }"
          @update:view="v => { view = v }"
          @update:sort="v => { sort = v }"
          @spaces-changed="reloadSpaces"
          @open-space="id => navigate('0', id)"
          @open-folder="(sid, fid) => navigate(fid, sid)"
          @preview-node="n => { previewVersion = undefined; preview = n }"
          @logout="logout"
        />
      </section>

      <!-- 统一搜索与全局检索页 -->
      <section v-else-if="mode === 'search'" class="drive-content">
        <SearchPanel
          :spaces="visibleSpaces"
          :current-space-id="spaceId"
          :initial-query="searchInitialQuery"
          :initial-scope="searchInitialScope"
          @locate="locateSearchHit"
          @preview="previewSearchHit"
        />
      </section>

      <!-- 传输与任务中心 -->
      <section v-else-if="mode === 'tasks'" class="drive-content">
        <TaskCenterPanel
          :tasks="uploadTasks"
          :uploading="uploading"
          @pause="id => uploadQueue.pause(id)"
          @resume="id => uploadQueue.resume(id)"
          @cancel="id => uploadQueue.cancel(id)"
          @pause-all="uploadQueue.abortAll()"
          @clear-finished="uploadQueue.clearFinished()"
        />
      </section>

      <!-- 共享中心 -->
      <section v-else-if="mode === 'shared'" class="drive-content">
        <SharedCenterPanel
          @open-shared-folder="openSharedFolder"
          @spaces-changed="reloadSpaces"
        />
      </section>

      <!-- 最近使用 -->
      <section v-else-if="mode === 'recent'" class="drive-content">
        <RecentPanel
          @locate="item => openRecentItem(item, 'locate')"
          @preview="item => openRecentItem(item, 'preview')"
        />
      </section>

      <!-- 文件工作台内容区 -->
      <section v-else class="drive-content">
        <div class="drive-heading">
          <nav v-if="mode === 'files'" class="breadcrumbs" aria-label="目录路径">
            <Button variant="ghost" size="sm" class="crumb-root px-2" @click="navigate('0')">{{ currentSpace?.name || '我的文件' }}</Button>
            <template v-if="breadcrumbParts.collapsed.length">
              <ChevronRight :size="13" class="text-muted-foreground" />
              <div class="crumb-collapse-wrap">
                <Button variant="ghost" size="icon-xs" aria-label="展开折叠路径" :aria-expanded="crumbMenuOpen" @click="crumbMenuOpen = !crumbMenuOpen"><MoreHorizontal :size="15" /></Button>
                <div v-if="crumbMenuOpen" class="crumb-collapse-menu" role="menu">
                  <Button v-for="c in breadcrumbParts.collapsed" :key="c.id" variant="ghost" size="sm" class="w-full justify-start" role="menuitem" @click="navigate(c.id)">{{ c.name }}</Button>
                </div>
              </div>
            </template>
            <template v-for="node in breadcrumbParts.tail" :key="node.id">
              <ChevronRight :size="13" class="text-muted-foreground" />
              <Button variant="ghost" size="sm" class="px-2" @click="navigate(node.id)">{{ node.name }}</Button>
            </template>
          </nav>
          <h1 v-else>我的收藏</h1>

          <div v-if="mode === 'files'" class="heading-actions">
            <Button
              v-if="currentSpace?.kind === 'PRIVATE' && hasPrivateSpaceToken(spaceId)"
              variant="outline"
              size="sm"
              @click="lockCurrentPrivateSpace"
            >
              锁定私密空间
            </Button>
            <Button v-if="currentSpace" variant="ghost" size="icon-sm" aria-label="空间设置与成员" title="空间设置与成员" @click="openSpaceSettings('general')">
              <Settings :size="16" />
            </Button>
            <Button variant="outline" size="sm" @click="newFolder"><FolderPlus :size="15" /> 新建文件夹</Button>
            <Button variant="outline" size="sm" :disabled="!spaceId" @click="folderInput?.click()">
              <FolderUp :size="15" /> 上传文件夹
            </Button>
            <Button variant="default" size="sm" :disabled="!spaceId" @click="fileInput?.click()">
              <Upload :size="15" /> 上传文件
            </Button>
          </div>
        </div>

        <div v-if="mode === 'files' && (dirStats.files > 0 || dirStats.folders > 0)" class="category-bar" role="tablist" aria-label="文件类型筛选">
          <Button variant="ghost" size="sm" role="tab" :aria-selected="!category" :class="{ 'is-active-tab': !category }" @click="selectCategory('')">全部 ({{ dirStats.files }})</Button>
          <Button v-for="[cat, count] in activeCategories" :key="cat" variant="ghost" size="sm" role="tab" :aria-selected="category === cat" :class="{ 'is-active-tab': category === cat }" @click="selectCategory(cat)">{{ CATEGORY_LABELS[cat] || cat }} ({{ count }})</Button>
          <span v-if="dirStats.folders > 0" class="category-folder-meta">{{ dirStats.folders }} 个文件夹</span>
          <Button variant="ghost" size="sm" class="ml-auto text-xs" :class="{ 'text-primary': includeSubdirs }" @click="includeSubdirs = !includeSubdirs; void refresh()">
            {{ includeSubdirs ? '已包含子目录统计' : '包含子目录' }}
          </Button>
        </div>

        <div class="drive-toolbar">
          <div class="toolbar-leading">
            <template v-if="selected.size">
              <span>已选 {{ selected.size }} 项</span>
              <Button variant="ghost" size="sm" class="h-7 px-2 text-xs" @click="selected = new Set()">取消选择</Button>
            </template>
          </div>

          <div v-if="selected.size" class="toolbar-actions" role="toolbar" aria-label="已选文件操作">
            <Button v-if="selected.size === 1" variant="outline" size="sm" @click="open(currentSelection[0]!)">
              <Eye v-if="currentSelection[0]?.nodeType === 'FILE'" :size="14" />
              <Folder v-else :size="14" />
              {{ currentSelection[0]?.nodeType === 'FILE' ? '预览' : '打开' }}
            </Button>
            <Button v-if="selected.size === 1 && currentSelection[0]?.nodeType === 'FILE'" variant="outline" size="sm" @click="download(currentSelection[0]!)">
              <Download :size="14" /> 下载
            </Button>
            <Button v-if="selected.size === 1" variant="outline" size="sm" @click="detailNode = currentSelection[0]">
              <Info :size="14" /> 详情
            </Button>
            <Button v-if="selected.size === 1 && currentSelection[0]?.nodeType === 'FILE'" variant="outline" size="sm" @click="showVersions(currentSelection[0]!)">
              <History :size="14" /> 历史版本
            </Button>
            <Button v-if="selected.size === 1 && currentSelection[0]?.nodeType === 'FILE'" variant="outline" size="sm" @click="replacing = currentSelection[0]; replacement?.click()">
              替换文件
            </Button>
            <Button v-if="selected.size === 1 && currentSelection[0]?.nodeType === 'FOLDER'" variant="outline" size="sm" @click="sharingNode = currentSelection[0]">
              <Share2 :size="14" /> 共享
            </Button>
            <Button v-if="selected.size === 1" variant="outline" size="sm" @click="shareLinkNode = currentSelection[0]">
              <Link2 :size="14" /> 链接
            </Button>
            <Button v-if="selected.size === 1" variant="outline" size="sm" @click="rename(currentSelection[0]!)">重命名</Button>
            <Button v-if="selected.size > 1 && mode === 'files'" variant="outline" size="sm" @click="batchRenameOpen = true">
              <Wand2 :size="14" /> 批量重命名
            </Button>
            <Button v-if="selected.size > 1 && mode === 'files'" variant="outline" size="sm" @click="downloadZipSelection">
              <FileArchive :size="14" /> 打包下载
            </Button>
            <Button variant="outline" size="sm" @click="moveSelection">移动</Button>
            <Button variant="outline" size="sm" @click="batch(node => drive.favorite(node, mode !== 'favorites'))">
              {{ mode === 'favorites' ? '取消收藏' : '收藏' }}
            </Button>
            <Button variant="outline" size="sm" class="danger" @click="deleteSelection">移到回收站</Button>
          </div>

          <div v-else class="toolbar-actions">
            <Select
              :model-value="sort"
              class="h-8 w-28 text-xs"
              aria-label="文件排序"
              :options="[
                { value: 'name', label: '名称排序' },
                { value: 'updated', label: '最近修改' },
                { value: 'size', label: '文件大小' },
              ]"
              @update:model-value="v => { sort = v as typeof sort }"
            />
            <Button variant="outline" size="sm" class="h-8 px-2.5" aria-label="列表视图" :aria-pressed="view === 'list'" @click="view = 'list'"><List :size="16" /></Button>
            <Button variant="outline" size="sm" class="h-8 px-2.5" aria-label="网格视图" :aria-pressed="view === 'grid'" @click="view = 'grid'"><Grid2X2 :size="16" /></Button>
            <Button variant="outline" size="sm" class="h-8 px-2.5" aria-label="刷新文件" @click="refresh"><RefreshCw :size="15" /></Button>
          </div>
        </div>

        <p v-if="error" class="drive-error" role="alert">{{ error }}<Button variant="ghost" size="icon-xs" @click="error = ''" aria-label="关闭错误"><X :size="15" /></Button></p>
        <p v-if="notice" class="drive-notice" role="status">{{ notice }}<Button variant="ghost" size="icon-xs" @click="notice = ''" aria-label="关闭提示"><X :size="15" /></Button></p>

        <div v-if="loading" class="drive-empty" role="status">正在加载文件…</div>
        <div v-else-if="!nodes.length && !inlineCreateActive" class="drive-empty">
          <Folder :size="48" />
          <h2>{{ query || category ? '没有找到相关文件' : mode === 'favorites' ? '还没有收藏' : '为想法留一个位置' }}</h2>
          <p>{{ mode === 'files' ? '拖拽文件到此处，或点击右上角「上传」开始整理你的空间' : '文件会在这里集中显示' }}</p>
          <Button variant="outline" v-if="mode === 'files' && category" @click="selectCategory('')">清除类型筛选</Button>
          <Button variant="outline" v-else-if="mode === 'files'" @click="fileInput?.click()">选择文件上传</Button>
        </div>
        <div v-else class="file-collection" :class="view" role="list" aria-label="文件列表">
          <div v-if="view === 'list'" class="file-columns">
            <span class="flex items-center gap-3">
              <Checkbox
                :model-value="allSelected ? true : someSelected ? 'indeterminate' : false"
                aria-label="表头全选"
                @update:model-value="toggleSelectAll"
              />
              <span>名称</span>
            </span>
            <span class="col-size">大小</span><span class="col-time">修改时间</span><span class="col-type">类型</span><span class="col-actions text-right">操作</span>
          </div>
          <div v-if="inlineCreateActive" class="file-item selected" role="listitem">
            <template v-if="view === 'grid'">
              <div class="file-thumb-stage">
                <span class="file-icon folder"><Folder :size="52" /></span>
              </div>
              <div class="file-card-body">
                <input ref="inlineCreateInput" v-model="inlineCreateName" class="inline-edit-input" aria-label="新建文件夹名称" :disabled="inlineCreateBusy" @keydown.enter.prevent="commitInlineCreate" @keydown.esc.prevent="cancelInlineEdit" @blur="commitInlineCreate" />
                <div class="file-card-meta"><span>新建中…</span><span>文件夹</span></div>
              </div>
            </template>
            <template v-else>
              <span class="file-name">
                <span class="file-icon folder"><Folder :size="20" /></span>
                <input ref="inlineCreateInput" v-model="inlineCreateName" class="inline-edit-input" aria-label="新建文件夹名称" :disabled="inlineCreateBusy" @keydown.enter.prevent="commitInlineCreate" @keydown.esc.prevent="cancelInlineEdit" @blur="commitInlineCreate" />
              </span>
              <span>—</span><span>新建中…</span><small>文件夹</small><span />
            </template>
          </div>
          <ContextMenu v-for="node in nodes" :key="node.id">
            <ContextMenuTrigger as-child>
              <div
                class="file-item"
                :class="{ selected: selected.has(node.id), 'is-dragging': draggingId === node.id, 'is-drop-target': dropTargetId === node.id && node.nodeType === 'FOLDER' }"
                role="listitem"
                tabindex="0"
                draggable="true"
                @dragstart="draggingId = node.id; $event.dataTransfer?.setData('application/cloudlane-node', node.id)"
                @dragend="draggingId = ''; dropTargetId = ''"
                @dragover="onNodeDragOver($event, node)"
                @dragleave="onNodeDragLeave(node)"
                @drop.prevent.stop="onNodeDrop($event, node)"
                @dblclick="inlineRenameId !== node.id && open(node)"
                @keydown.enter.stop="inlineRenameId !== node.id && open(node)"
              >
                <!-- 卡片视图（缩略图/图标占大部分区域） -->
                <template v-if="view === 'grid'">
                  <div class="file-thumb-stage">
                    <span class="file-card-check" @click.stop @dblclick.stop>
                      <Checkbox
                        :model-value="selected.has(node.id)"
                        :aria-label="`选择 ${node.name}`"
                        @update:model-value="v => toggleNodeSelected(node, v)"
                      />
                    </span>
                    <span class="file-icon" :class="{ folder: node.nodeType === 'FOLDER' }">
                      <img
                        v-if="thumbUrls[`${node.id}:${node.currentVersionId || ''}`]"
                        :src="thumbUrls[`${node.id}:${node.currentVersionId || ''}`]"
                        :alt="node.name"
                        class="file-thumb-img"
                      />
                      <Folder v-else-if="node.nodeType === 'FOLDER'" :size="56" />
                      <File v-else :size="48" />
                    </span>
                    <span class="item-more-wrap" @click.stop @dblclick.stop>
                      <DropdownMenu>
                        <DropdownMenuTrigger as-child>
                          <Button variant="ghost" size="icon-xs" :aria-label="`更多操作：${node.name}`">
                            <MoreHorizontal :size="16" />
                          </Button>
                        </DropdownMenuTrigger>
                        <DropdownMenuContent align="end" class="w-48">
                          <DropdownMenuItem @select="open(node)">
                            <Eye v-if="node.nodeType === 'FILE'" :size="14" />
                            <Folder v-else :size="14" />
                            {{ node.nodeType === 'FOLDER' ? '打开文件夹' : '预览' }}
                          </DropdownMenuItem>
                          <DropdownMenuItem v-if="node.nodeType === 'FILE'" @select="download(node)"><Download :size="14" /> 下载</DropdownMenuItem>
                          <DropdownMenuItem @select="detailNode = node"><Info :size="14" /> 查看详情</DropdownMenuItem>
                          <DropdownMenuItem v-if="node.nodeType === 'FILE'" @select="showVersions(node)"><History :size="14" /> 历史版本</DropdownMenuItem>
                          <DropdownMenuItem v-if="node.nodeType === 'FILE'" @select="replacing = node; replacement?.click()">替换文件</DropdownMenuItem>
                          <DropdownMenuSeparator />
                          <DropdownMenuItem v-if="node.nodeType === 'FOLDER'" @select="sharingNode = node"><Share2 :size="14" /> 共享给他人…</DropdownMenuItem>
                          <DropdownMenuItem @select="shareLinkNode = node"><Link2 :size="14" /> 创建公开分享链接…</DropdownMenuItem>
                          <DropdownMenuSeparator />
                          <DropdownMenuItem @select="rename(node)">重命名</DropdownMenuItem>
                          <DropdownMenuItem @select="movingNodes = [node]">移动到…</DropdownMenuItem>
                          <DropdownMenuItem @select="markNodePrivate(node)"><LockKeyhole :size="14" /> 设为私密</DropdownMenuItem>
                          <DropdownMenuItem @select="selected = new Set([node.id]); batch(n => drive.favorite(n, mode !== 'favorites'))">
                            {{ mode === 'favorites' ? '取消收藏' : '收藏' }}
                          </DropdownMenuItem>
                          <DropdownMenuItem @select="pinNode(node, true)"><Pin :size="14" /> 固定到快捷访问</DropdownMenuItem>
                          <DropdownMenuSeparator />
                          <DropdownMenuItem class="text-destructive" @select="selected = new Set([node.id]); deleteSelection()">移到回收站</DropdownMenuItem>
                        </DropdownMenuContent>
                      </DropdownMenu>
                    </span>
                  </div>
                  <div class="file-card-body">
                    <input
                      v-if="inlineRenameId === node.id"
                      :data-inline-rename="node.id"
                      v-model="inlineRenameName"
                      class="inline-edit-input"
                      :aria-label="`重命名 ${node.name}`"
                      :disabled="inlineRenameBusy"
                      @click.stop
                      @dblclick.stop
                      @keydown.enter.prevent.stop="commitInlineRename(node)"
                      @keydown.esc.prevent.stop="cancelInlineEdit"
                      @blur="commitInlineRename(node)"
                    />
                    <span v-else class="filename" :title="node.name">{{ node.name }}</span>
                    <div class="file-card-meta">
                      <span>{{ node.nodeType === 'FOLDER' ? '文件夹' : size(node.size) }}</span>
                      <span>{{ node.nodeType === 'FOLDER' ? '' : node.name.split('.').pop()?.toUpperCase() || '文件' }}</span>
                    </div>
                  </div>
                </template>

                <!-- 列表视图（左侧显示对应缩略图 + 组件库 Checkbox） -->
                <template v-else>
                  <span class="file-name">
                    <span class="inline-flex shrink-0" @click.stop @dblclick.stop>
                      <Checkbox
                        :model-value="selected.has(node.id)"
                        :aria-label="`选择 ${node.name}`"
                        @update:model-value="v => toggleNodeSelected(node, v)"
                      />
                    </span>
                    <span class="file-icon" :class="{ folder: node.nodeType === 'FOLDER' }">
                      <img
                        v-if="thumbUrls[`${node.id}:${node.currentVersionId || ''}`]"
                        :src="thumbUrls[`${node.id}:${node.currentVersionId || ''}`]"
                        :alt="node.name"
                        class="file-thumb-img"
                      />
                      <Folder v-else-if="node.nodeType === 'FOLDER'" :size="20" />
                      <File v-else :size="19" />
                    </span>
                    <input
                      v-if="inlineRenameId === node.id"
                      :data-inline-rename="node.id"
                      v-model="inlineRenameName"
                      class="inline-edit-input"
                      :aria-label="`重命名 ${node.name}`"
                      :disabled="inlineRenameBusy"
                      @click.stop
                      @dblclick.stop
                      @keydown.enter.prevent.stop="commitInlineRename(node)"
                      @keydown.esc.prevent.stop="cancelInlineEdit"
                      @blur="commitInlineRename(node)"
                    />
                    <span v-else class="filename" :title="node.name">{{ node.name }}</span>
                  </span>
                  <span class="col-size">{{ node.nodeType === 'FOLDER' ? '—' : size(node.size) }}</span>
                  <time class="col-time" :datetime="node.updatedAt">{{ new Date(node.updatedAt).toLocaleString('zh-CN') }}</time>
                  <small class="col-type">{{ node.nodeType === 'FOLDER' ? '文件夹' : node.name.split('.').pop()?.toUpperCase() || '文件' }}</small>
                  <span class="col-actions item-more-wrap flex justify-end" @click.stop @dblclick.stop>
                    <DropdownMenu>
                      <DropdownMenuTrigger as-child>
                        <Button variant="ghost" size="icon-xs" :aria-label="`更多操作：${node.name}`">
                          <MoreHorizontal :size="16" />
                        </Button>
                      </DropdownMenuTrigger>
                      <DropdownMenuContent align="end" class="w-48">
                        <DropdownMenuItem @select="open(node)">
                          <Eye v-if="node.nodeType === 'FILE'" :size="14" />
                          <Folder v-else :size="14" />
                          {{ node.nodeType === 'FOLDER' ? '打开文件夹' : '预览' }}
                        </DropdownMenuItem>
                        <DropdownMenuItem v-if="node.nodeType === 'FILE'" @select="download(node)"><Download :size="14" /> 下载</DropdownMenuItem>
                        <DropdownMenuItem @select="detailNode = node"><Info :size="14" /> 查看详情</DropdownMenuItem>
                        <DropdownMenuItem v-if="node.nodeType === 'FILE'" @select="showVersions(node)"><History :size="14" /> 历史版本</DropdownMenuItem>
                        <DropdownMenuItem v-if="node.nodeType === 'FILE'" @select="replacing = node; replacement?.click()">替换文件</DropdownMenuItem>
                        <DropdownMenuSeparator />
                        <DropdownMenuItem v-if="node.nodeType === 'FOLDER'" @select="sharingNode = node"><Share2 :size="14" /> 共享给他人…</DropdownMenuItem>
                        <DropdownMenuItem @select="shareLinkNode = node"><Link2 :size="14" /> 创建公开分享链接…</DropdownMenuItem>
                        <DropdownMenuSeparator />
                        <DropdownMenuItem @select="rename(node)">重命名</DropdownMenuItem>
                        <DropdownMenuItem @select="movingNodes = [node]">移动到…</DropdownMenuItem>
                        <DropdownMenuItem @select="markNodePrivate(node)"><LockKeyhole :size="14" /> 设为私密</DropdownMenuItem>
                        <DropdownMenuItem @select="selected = new Set([node.id]); batch(n => drive.favorite(n, mode !== 'favorites'))">
                          {{ mode === 'favorites' ? '取消收藏' : '收藏' }}
                        </DropdownMenuItem>
                        <DropdownMenuItem @select="pinNode(node, true)"><Pin :size="14" /> 固定到快捷访问</DropdownMenuItem>
                        <DropdownMenuSeparator />
                        <DropdownMenuItem class="text-destructive" @select="selected = new Set([node.id]); deleteSelection()">移到回收站</DropdownMenuItem>
                      </DropdownMenuContent>
                    </DropdownMenu>
                  </span>
                </template>
              </div>
            </ContextMenuTrigger>
            <ContextMenuContent class="w-48">
              <ContextMenuItem class="gap-2" @select="open(node)">
                <Eye v-if="node.nodeType === 'FILE'" :size="14" />
                <Folder v-else :size="14" />
                {{ node.nodeType === 'FOLDER' ? '打开文件夹' : '预览' }}
              </ContextMenuItem>
              <ContextMenuItem v-if="node.nodeType === 'FILE'" class="gap-2" @select="download(node)"><Download :size="14" /> 下载</ContextMenuItem>
              <ContextMenuItem class="gap-2" @select="detailNode = node"><Info :size="14" /> 查看详情</ContextMenuItem>
              <ContextMenuItem v-if="node.nodeType === 'FILE'" class="gap-2" @select="showVersions(node)"><History :size="14" /> 历史版本</ContextMenuItem>
              <ContextMenuItem v-if="node.nodeType === 'FILE'" class="gap-2" @select="replacing = node; replacement?.click()">替换文件</ContextMenuItem>
              <ContextMenuSeparator class="my-1 h-px bg-border" />
              <ContextMenuItem v-if="node.nodeType === 'FOLDER'" class="gap-2" @select="sharingNode = node"><Share2 :size="14" /> 共享给他人…</ContextMenuItem>
              <ContextMenuItem class="gap-2" @select="shareLinkNode = node"><Link2 :size="14" /> 创建公开分享链接…</ContextMenuItem>
              <ContextMenuSeparator class="my-1 h-px bg-border" />
              <ContextMenuItem class="gap-2" @select="rename(node)">重命名</ContextMenuItem>
              <ContextMenuItem class="gap-2" @select="movingNodes = [node]">移动到…</ContextMenuItem>
              <ContextMenuItem class="gap-2" @select="markNodePrivate(node)"><LockKeyhole :size="14" /> 设为私密</ContextMenuItem>
              <ContextMenuItem class="gap-2" @select="selected = new Set([node.id]); batch(n => drive.favorite(n, mode !== 'favorites'))">
                {{ mode === 'favorites' ? '取消收藏' : '收藏' }}
              </ContextMenuItem>
              <ContextMenuItem class="gap-2" @select="pinNode(node, true)"><Pin :size="14" /> 固定到快捷访问</ContextMenuItem>
              <ContextMenuSeparator class="my-1 h-px bg-border" />
              <ContextMenuItem class="gap-2 text-destructive" @select="selected = new Set([node.id]); deleteSelection()">移到回收站</ContextMenuItem>
            </ContextMenuContent>
          </ContextMenu>
        </div>
      </section>

      <footer v-if="mode === 'files' || mode === 'favorites'" class="drive-pagination">
        <span class="mr-auto text-xs text-muted-foreground tabular-nums">共 {{ total }} 项</span>
        <Button variant="outline" size="sm" :disabled="page <= 1" @click="page--">上一页</Button>
        <span class="tabular-nums">第 {{ page }} / {{ Math.max(1, Math.ceil(total / 100)) }} 页</span>
        <Button variant="outline" size="sm" :disabled="page * 100 >= total" @click="page++">下一页</Button>
      </footer>
    </main>

    <input ref="fileInput" type="file" multiple hidden @change="selectFiles" />
    <input ref="folderInput" type="file" multiple webkitdirectory hidden @change="selectFiles" />
    <input ref="replacement" type="file" hidden @change="replace" />

    <Dialog :open="!!detailNode" @update:open="v => { if (!v) detailNode = undefined }">
      <DialogContent>
        <template v-if="detailNode">
          <DialogHeader>
            <DialogTitle class="flex items-center gap-2 break-all">
              <Folder v-if="detailNode.nodeType === 'FOLDER'" :size="20" class="shrink-0 text-file-folder" />
              <File v-else :size="20" class="shrink-0 text-primary" />
              <span>{{ detailNode.name }}</span>
            </DialogTitle>
            <DialogDescription>文件与文件夹元信息详情</DialogDescription>
          </DialogHeader>
          <dl class="grid grid-cols-[88px_1fr] gap-y-2.5 rounded-lg bg-muted/40 p-4 text-sm">
            <dt class="text-muted-foreground">类型</dt>
            <dd>{{ detailNode.nodeType === 'FOLDER' ? '文件夹' : detailNode.mimeType || '文件' }}</dd>
            <dt class="text-muted-foreground">大小</dt>
            <dd>{{ detailNode.nodeType === 'FOLDER' ? '—' : size(detailNode.size) }}</dd>
            <dt class="text-muted-foreground">修订版本</dt>
            <dd>r{{ detailNode.revision }}</dd>
            <dt class="text-muted-foreground">更新时间</dt>
            <dd>{{ new Date(detailNode.updatedAt).toLocaleString('zh-CN') }}</dd>
            <dt class="text-muted-foreground">节点 ID</dt>
            <dd class="font-mono text-xs text-muted-foreground">{{ detailNode.id }}</dd>
          </dl>
          <DialogFooter>
            <Button v-if="detailNode.nodeType === 'FILE'" variant="outline" @click="previewDetailNode">
              <Eye :size="15" /> 预览
            </Button>
            <Button v-if="detailNode.nodeType === 'FILE'" variant="outline" @click="download(detailNode)">
              <Download :size="15" /> 下载
            </Button>
            <Button @click="detailNode = undefined">关闭</Button>
          </DialogFooter>
        </template>
      </DialogContent>
    </Dialog>

    <MoveDialog v-if="movingNodes.length" :nodes="movingNodes" @close="movingNodes = []" @moved="selected = new Set(); refresh(); treeRevision++" />
    <FolderShareDialog v-if="sharingNode" :node="sharingNode" @close="sharingNode = undefined" @shared="notice = '文件夹共享授权已生效'" />
    <ShareLinkDialog v-if="shareLinkNode" :node="shareLinkNode" @close="shareLinkNode = undefined" />
    <BatchRenameDialog
      v-if="batchRenameOpen && spaceId"
      :space-id="spaceId"
      :nodes="currentSelection"
      @close="batchRenameOpen = false"
      @applied="undo => { selected = new Set(); refresh(); treeRevision++; showUndo(undo) }"
    />

    <div v-if="undoReceipt" class="undo-toast" role="status">
      <span>{{ undoReceipt.summary }}</span>
      <Button size="sm" variant="outline" :disabled="undoing" @click="performUndo">
        <Undo2 :size="14" /> {{ undoing ? '撤销中…' : '撤销 (10s)' }}
      </Button>
      <Button size="icon-xs" variant="ghost" aria-label="关闭撤销提示" @click="showUndo(null)"><X :size="14" /></Button>
    </div>

    <aside v-if="mode !== 'tasks' && uploadTasks.length && uploadPanelVisible" class="upload-panel" aria-label="上传任务">
      <header>
        <strong>上传任务 ({{ uploadTasks.length }})</strong>
        <Button variant="ghost" size="sm" @click="openTasks">全部任务</Button>
        <Button v-if="uploading" variant="outline" size="sm" @click="uploadQueue.abortAll()">全部暂停</Button>
        <Button v-else variant="outline" size="sm" @click="uploadQueue.clearFinished(); if (!uploadTasks.length) uploadPanelVisible = false">清理已完成</Button>
        <Button variant="ghost" size="icon-xs" aria-label="收起上传面板" @click="uploadPanelVisible = false"><X :size="16" /></Button>
      </header>
      <div v-for="task in uploadTasks" :key="task.id">
        <div class="flex items-center justify-between gap-2">
          <span class="truncate text-sm">{{ task.name }}</span>
          <div class="flex items-center gap-1 shrink-0">
            <Button v-if="task.status === 'uploading' || task.status === 'queued'" variant="ghost" size="sm" @click="uploadQueue.pause(task.id)">暂停</Button>
            <Button v-if="task.status === 'paused' || task.status === 'error'" variant="ghost" size="sm" @click="uploadQueue.resume(task.id)">{{ task.status === 'error' ? '重试' : '继续' }}</Button>
            <Button v-if="task.status !== 'completed' && task.status !== 'cancelled'" variant="ghost" size="sm" class="text-destructive" @click="uploadQueue.cancel(task.id)">取消</Button>
          </div>
        </div>
        <small>{{ statusLabel(task.status, task.message) }} · {{ task.percent }}%</small>
        <div
          role="progressbar"
          :aria-valuenow="task.percent"
          aria-valuemin="0"
          aria-valuemax="100"
          class="h-1.5 w-full overflow-hidden rounded-full bg-muted"
        >
          <div class="h-full rounded-full bg-primary transition-[width] duration-200" :style="{ width: `${Math.min(100, Math.max(0, task.percent))}%` }" />
        </div>
      </div>
    </aside>

    <PreviewDialog v-if="preview" :node="preview" :version="previewVersion" :siblings="nodes" @select="next => { preview = next; previewVersion = undefined }" @close="preview = undefined" />

    <Dialog :open="createSpaceOpen" @update:open="value => { if (!value && !createSpaceBusy) createSpaceOpen = false }">
      <DialogContent>
        <DialogHeader>
          <DialogTitle>新建空间</DialogTitle>
          <DialogDescription>选择空间类型（个人 / 共享）并配置名称与说明；如需设为私密可在个人中心「私密设置」中开启。</DialogDescription>
        </DialogHeader>
        <form class="space-y-4 mt-2" @submit.prevent="submitCreateSpace">
          <div class="grid grid-cols-2 gap-2">
            <Button
              v-for="opt in ([
                { id: 'PERSONAL', label: '个人空间' },
                { id: 'SHARED', label: '共享空间' },
              ] as const)"
              :key="opt.id"
              type="button"
              size="sm"
              :variant="createSpaceKind === opt.id ? 'default' : 'outline'"
              @click="createSpaceKind = opt.id"
            >
              {{ opt.label }}
            </Button>
          </div>
          <label class="block space-y-1.5 text-sm">
            <span>空间名称</span>
            <Input v-model="createSpaceName" placeholder="例如：工作资料 / 家庭相册" :disabled="createSpaceBusy" />
          </label>
          <label class="block space-y-1.5 text-sm">
            <span>空间简介（可选）</span>
            <Input v-model="createSpaceDesc" placeholder="简短描述此空间用途" :disabled="createSpaceBusy" />
          </label>
          <div class="grid grid-cols-2 gap-3">
            <div class="space-y-1.5 text-sm">
              <span>空间图标</span>
              <IconPicker
                v-model="createSpaceIcon"
                :preview-color="createSpaceColor"
                :disabled="createSpaceBusy"
                aria-label="选择空间图标"
              />
            </div>
            <div class="space-y-1.5 text-sm">
              <span>标记颜色</span>
              <ColorPicker
                v-model="createSpaceColor"
                :disabled="createSpaceBusy"
                aria-label="选择空间标记颜色"
              />
            </div>
          </div>
          <p v-if="createSpaceError" role="alert" class="text-destructive text-sm">{{ createSpaceError }}</p>
          <DialogFooter class="pt-2">
            <Button variant="outline" type="button" :disabled="createSpaceBusy" @click="createSpaceOpen = false">取消</Button>
            <Button type="submit" :disabled="createSpaceBusy">{{ createSpaceBusy ? '正在创建…' : '创建空间' }}</Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>

    <Dialog :open="privateUnlockOpen" @update:open="value => { if (!value && !privateUnlockBusy) privateUnlockOpen = false }">
      <DialogContent>
        <DialogHeader>
          <DialogTitle>解锁私密空间</DialogTitle>
          <DialogDescription>私密空间受二次密码验证保护，请输入当前账号登录密码解锁短期会话。</DialogDescription>
        </DialogHeader>
        <form class="space-y-4 mt-2" @submit.prevent="submitPrivateUnlock">
          <label class="block space-y-1.5 text-sm">
            <span>账号密码</span>
            <Input v-model="privateUnlockPassword" type="password" autocomplete="current-password" placeholder="输入登录密码" :disabled="privateUnlockBusy" />
          </label>
          <div class="block space-y-1.5 text-sm">
            <span>空闲自动锁定时间（分钟）</span>
            <Select
              :model-value="privateUnlockIdle"
              :disabled="privateUnlockBusy"
              aria-label="空闲自动锁定时间"
              :options="[
                { value: '15', label: '15 分钟' },
                { value: '30', label: '30 分钟' },
                { value: '60', label: '60 分钟' },
              ]"
              @update:model-value="v => { privateUnlockIdle = Number(v) || 30 }"
            />
          </div>
          <p v-if="privateUnlockError" role="alert" class="text-destructive text-sm">{{ privateUnlockError }}</p>
          <DialogFooter>
            <Button variant="outline" type="button" :disabled="privateUnlockBusy" @click="privateUnlockOpen = false">取消</Button>
            <Button type="submit" :disabled="privateUnlockBusy">{{ privateUnlockBusy ? '正在验证…' : '确认解锁' }}</Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>

    <Dialog :open="!!dialog" @update:open="value => { if (!value && !dialogBusy) dialog = undefined }">
      <DialogContent>
        <template v-if="dialog">
          <DialogHeader><DialogTitle>{{ dialog.title }}</DialogTitle><DialogDescription>{{ dialog.label }}</DialogDescription></DialogHeader>
          <form @submit.prevent="submitDialog">
            <Input v-model="dialog.value" :aria-label="dialog.label" :disabled="dialogBusy" />
            <p v-if="error" role="alert" class="text-destructive text-sm mt-3">{{ error }}</p>
            <DialogFooter class="mt-5">
              <Button variant="outline" type="button" :disabled="dialogBusy" @click="dialog = undefined">取消</Button>
              <Button type="submit" :disabled="dialogBusy">{{ dialogBusy ? '正在保存…' : '确认' }}</Button>
            </DialogFooter>
          </form>
        </template>
      </DialogContent>
    </Dialog>

    <Dialog :open="!!versionNode" @update:open="value => { if (!value) versionNode = undefined }">
      <DialogContent>
        <template v-if="versionNode">
          <DialogHeader>
            <DialogTitle>{{ versionNode.name }} · 历史版本</DialogTitle>
            <DialogDescription>恢复历史内容会创建新版本，原有记录保留。</DialogDescription>
          </DialogHeader>
          <div class="max-h-80 overflow-y-auto divide-y divide-border">
            <div v-for="version in versions" :key="version.id" class="flex items-center gap-2 py-2.5">
              <span class="min-w-0 flex-1 text-sm">
                版本 {{ version.number }} · {{ size(version.size) }}
                <small class="block text-muted-foreground">
                  {{ new Date(version.createdAt).toLocaleString('zh-CN') }}<template v-if="version.createdByName"> · 修改人：{{ version.createdByName }}</template>
                </small>
              </span>
              <Button variant="outline" size="sm" @click="previewVersion = version; preview = versionNode">预览</Button>
              <Button variant="outline" size="sm" @click="drive.content(versionNode, version.id).then(b => saveBlob(b, versionNode!.name)).catch(fail)">下载</Button>
              <Button variant="outline" size="sm" :disabled="version.id === versionNode.currentVersionId" @click="restoreVersion(version)">恢复</Button>
            </div>
          </div>
        </template>
      </DialogContent>
    </Dialog>

    <Dialog :open="spaceDialogOpen" @update:open="value => { if (!value && !spaceSaving) spaceDialogOpen = false }">
      <DialogContent class="!max-w-2xl">
        <DialogHeader>
          <DialogTitle class="flex items-center gap-2">
            <LucideIconByName :name="currentSpace?.icon" :size="18" :style="currentSpace?.color ? { color: currentSpace.color } : undefined" />
            <span>{{ currentSpace?.name || '空间' }} · 空间设置</span>
          </DialogTitle>
          <DialogDescription>统一管理空间的基本信息、图标颜色、共享协作与成员权限。</DialogDescription>
        </DialogHeader>

        <div class="inline-flex w-fit rounded-lg border border-border bg-muted/40 p-1" role="tablist" aria-label="空间设置分页">
          <Button
            type="button"
            size="sm"
            class="h-8 px-3 text-xs"
            :variant="spaceSettingsTab === 'general' ? 'default' : 'ghost'"
            role="tab"
            :aria-selected="spaceSettingsTab === 'general'"
            @click="spaceSettingsTab = 'general'"
          >
            <Settings :size="14" /> 基本设置
          </Button>
          <Button
            type="button"
            size="sm"
            class="h-8 px-3 text-xs"
            :variant="spaceSettingsTab === 'members' ? 'default' : 'ghost'"
            role="tab"
            :aria-selected="spaceSettingsTab === 'members'"
            @click="spaceSettingsTab = 'members'"
          >
            <Users :size="14" /> 成员与共享
          </Button>
        </div>

        <template v-if="spaceSettingsTab === 'general'">
          <form class="space-y-4 pt-1" @submit.prevent="saveSpaceSettings">
            <label class="block space-y-1.5 text-sm"><span>空间名称</span><Input v-model="spaceName" aria-label="空间名称" :disabled="spaceSaving" /></label>
            <label class="block space-y-1.5 text-sm"><span>空间描述</span><Input v-model="spaceDescription" aria-label="空间描述" placeholder="简短描述空间用途" :disabled="spaceSaving" /></label>
            <div class="grid grid-cols-2 gap-3">
              <div class="space-y-1.5 text-sm">
                <span>空间图标</span>
                <IconPicker
                  v-model="spaceIcon"
                  :preview-color="spaceColor"
                  :disabled="spaceSaving"
                  aria-label="空间图标"
                />
              </div>
              <div class="space-y-1.5 text-sm">
                <span>标记颜色</span>
                <ColorPicker
                  v-model="spaceColor"
                  :disabled="spaceSaving"
                  aria-label="空间标记颜色"
                />
              </div>
            </div>
            <label class="block space-y-1.5 text-sm"><span>排序权重</span><Input v-model="spaceSortOrder" type="number" aria-label="排序权重" :disabled="spaceSaving" /></label>
            <p v-if="error" role="alert" class="text-destructive text-sm">{{ error }}</p>
            <DialogFooter class="pt-2"><Button variant="outline" type="button" :disabled="spaceSaving" @click="spaceDialogOpen = false">取消</Button><Button type="submit" :disabled="spaceSaving">{{ spaceSaving ? '正在保存…' : '保存设置' }}</Button></DialogFooter>
          </form>
          <div v-if="spaces.length > 1" class="mt-4 pt-4 border-t border-border space-y-2">
            <p class="text-xs text-destructive">危险操作：输入空间名称「{{ currentSpace?.name }}」以确认删除该空间。</p>
            <div class="flex items-center gap-2">
              <Input v-model="spaceDeleteConfirm" aria-label="确认删除空间名称" :placeholder="currentSpace?.name" :disabled="spaceSaving" />
              <Button variant="outline" class="text-destructive shrink-0" :disabled="spaceSaving || spaceDeleteConfirm !== currentSpace?.name" @click="confirmDeleteSpace">删除空间</Button>
            </div>
          </div>
        </template>

        <div v-else-if="currentSpace" class="pt-1">
          <SpaceMembersDialog embedded :space="currentSpace" @updated="reloadSpaces" />
        </div>
      </DialogContent>
    </Dialog>
  </div>
</template>
