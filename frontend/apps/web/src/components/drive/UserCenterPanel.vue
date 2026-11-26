<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import {
  Eye, File, Folder, FolderOpen, FolderPlus, Laptop, Lock, LockKeyhole, LogOut,
  Palette, Plus, RefreshCw, RotateCcw, Shield, SlidersHorizontal, Trash2, Unlock, Upload, User,
} from '@lucide/vue'
import { Button } from '@repo/ui/button'
import { Checkbox } from '@repo/ui/checkbox'
import { confirm } from '@repo/ui/confirm-dialog'
import { Input } from '@repo/ui/input'
import { Select } from '@repo/ui/select'
import { Separator } from '@repo/ui/separator'
import { Switch } from '@repo/ui/switch'
import { toast } from '@repo/ui/toast'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@repo/ui/dialog'
import { accentColors, theme, type ColorMode } from '@repo/ui/theme'
import { clearPrivateSpaceToken, hasVaultToken, request, RequestError, setVaultToken } from '@/utils/request'
import { drive, uploadFile, type DriveNode, type Space } from '@/api/drive'
import { opsApi, type PrivateNodeItem, type PrivateSpaceItem } from '@/api/ops'

export type SettingsSection = 'profile' | 'security' | 'appearance' | 'files' | 'private' | 'trash'

export interface UserProfile {
  id: string
  displayName: string
  avatarUrl: string | null
  admin: boolean
  usedBytes?: number
  quotaBytes?: number
}

interface DeviceSessionItem {
  id: string
  deviceName: string
  clientType: string
  ipAddress: string
  createdAt: string
  lastUsedAt: string
  expiresAt: string
  current: boolean
}

const props = defineProps<{
  section: SettingsSection
  profile: UserProfile
  view: string
  sort: string
  spaces: Space[]
  currentSpaceId: string
}>()

const emit = defineEmits<{
  'profile-updated': [profile: UserProfile]
  'update:view': [value: string]
  'update:sort': [value: string]
  'spaces-changed': []
  'open-space': [spaceId: string]
  'open-folder': [spaceId: string, folderId: string]
  'preview-node': [node: DriveNode]
  logout: []
}>()

const accentOptions: { key: keyof typeof accentColors; name: string; color: string }[] = [
  { key: 'blue', name: '海蓝', color: accentColors.blue },
  { key: 'violet', name: '紫罗兰', color: accentColors.violet },
  { key: 'green', name: '森林', color: accentColors.green },
  { key: 'rose', name: '玫瑰', color: accentColors.rose },
  { key: 'orange', name: '暖橙', color: accentColors.orange },
]
const colorModes: { id: ColorMode; name: string }[] = [
  { id: 'system', name: '跟随系统' },
  { id: 'light', name: '浅色' },
  { id: 'dark', name: '深色' },
]
const radiusOptions = [0, 0.5, 0.75, 1] as const

const currentMode = computed(() => theme.mode)
const currentAccent = computed(() => theme.accent)
const currentRadius = computed(() => theme.radius)
const isCompact = computed(() => theme.compact)

function setColorMode(mode: ColorMode) { theme.mode = mode }
function setAccent(accent: keyof typeof accentColors) { theme.accent = accent }
function setRadius(radius: number) { theme.radius = radius }
function setCompact(checked: boolean) { theme.compact = checked }

const displayName = ref(props.profile.displayName)
const avatarUrl = ref(props.profile.avatarUrl || '')
const avatarInput = ref<HTMLInputElement | null>(null)
const avatarUploading = ref(false)
const profileSaving = ref(false)
const feedback = ref('')
const error = ref('')

async function onAvatarSelected(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return
  avatarUploading.value = true
  error.value = ''
  feedback.value = ''
  try {
    const uploaded = await request.upload<{ id: string; url: string }>('/v1/assets/images', file, {
      extraData: { category: 'avatar' },
    })
    avatarUrl.value = uploaded.url
    await request.put('/v1/me', { displayName: displayName.value || props.profile.displayName, avatarUrl: uploaded.url })
    const me = await request.get<UserProfile>('/v1/me')
    emit('profile-updated', me)
    feedback.value = '头像已上传至当前存储方案并保存'
  } catch (e) {
    error.value = e instanceof Error ? e.message : '上传头像失败'
  } finally {
    avatarUploading.value = false
  }
}

async function clearAvatar() {
  if (avatarUploading.value || profileSaving.value) return
  avatarUrl.value = ''
  await saveProfile()
}

const sessions = ref<DeviceSessionItem[]>([])
const sessionsLoading = ref(false)
const revokingId = ref('')

const currentPassword = ref('')
const newPassword = ref('')
const confirmPassword = ref('')
const passwordSaving = ref(false)

const userMaxVersions = ref(10)
const systemMaxVersions = ref(20)
const versionPolicySaving = ref(false)

async function loadVersionPolicy() {
  try {
    const res = await opsApi.getVersionPolicy()
    userMaxVersions.value = res.maxVersions
    systemMaxVersions.value = res.systemMaxVersions
  } catch { /* ignore */ }
}

async function saveVersionPolicy() {
  if (versionPolicySaving.value) return
  versionPolicySaving.value = true
  error.value = ''
  feedback.value = ''
  try {
    const res = await opsApi.updateVersionPolicy(Number(userMaxVersions.value) || 10)
    userMaxVersions.value = res.maxVersions
    systemMaxVersions.value = res.systemMaxVersions
    feedback.value = `个人历史版本保留上限已更新为 ${res.maxVersions} 个`
  } catch (e) {
    error.value = e instanceof Error ? e.message : '保存版本策略失败'
  } finally {
    versionPolicySaving.value = false
  }
}

const quotaPercent = computed(() => {
  const quota = Number(props.profile.quotaBytes || 0)
  const used = Number(props.profile.usedBytes || 0)
  if (!quota) return 0
  return Math.min(100, Math.round((used / quota) * 1000) / 10)
})

function formatBytes(bytes: number) {
  if (!bytes) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB', 'TB']
  const i = Math.min(units.length - 1, Math.floor(Math.log(bytes) / Math.log(1024)))
  return `${(bytes / 1024 ** i).toFixed(i ? 1 : 0)} ${units[i]}`
}

async function changePassword() {
  if (passwordSaving.value) return
  error.value = ''
  feedback.value = ''
  if (!currentPassword.value || newPassword.value.length < 8) {
    error.value = '请输入当前密码与 8–32 位新密码'
    return
  }
  if (newPassword.value !== confirmPassword.value) {
    error.value = '两次输入的新密码不一致'
    return
  }
  passwordSaving.value = true
  try {
    await request.put('/v1/me/password', {
      currentPassword: currentPassword.value,
      newPassword: newPassword.value,
    })
    currentPassword.value = ''
    newPassword.value = ''
    confirmPassword.value = ''
    feedback.value = '登录密码已修改，其他设备会话已自动失效。'
  } catch (e) {
    error.value = e instanceof Error ? e.message : '修改密码失败'
  } finally {
    passwordSaving.value = false
  }
}

watch(
  () => props.profile,
  next => {
    displayName.value = next.displayName
    avatarUrl.value = next.avatarUrl || ''
  },
  { deep: true },
)

async function saveProfile() {
  if (profileSaving.value) return
  profileSaving.value = true
  error.value = ''
  feedback.value = ''
  try {
    await request.put('/v1/me', { displayName: displayName.value, avatarUrl: avatarUrl.value })
    const me = await request.get<UserProfile>('/v1/me')
    emit('profile-updated', me)
    feedback.value = '个人资料已保存'
  } catch (e) {
    error.value = e instanceof Error ? e.message : '保存资料失败'
  } finally {
    profileSaving.value = false
  }
}

async function loadSessions() {
  sessionsLoading.value = true
  error.value = ''
  try {
    sessions.value = await request.get<DeviceSessionItem[]>('/v1/auth/sessions')
  } catch (e) {
    error.value = e instanceof Error ? e.message : '加载登录设备失败'
  } finally {
    sessionsLoading.value = false
  }
}

async function revokeSession(id: string) {
  const ok = await confirm({
    title: '下线登录设备',
    description: '确定强制下线该设备会话吗？该设备需要重新登录才能访问网盘。',
    confirmText: '确认下线',
    variant: 'destructive',
  })
  if (!ok) return
  revokingId.value = id
  error.value = ''
  feedback.value = ''
  try {
    await request.delete(`/v1/auth/sessions/${id}`)
    sessions.value = sessions.value.filter(item => item.id !== id)
    toast.success('已下线指定设备会话')
  } catch (e) {
    const msg = e instanceof Error ? e.message : '下线设备失败'
    error.value = msg
    toast.error(msg)
  } finally {
    revokingId.value = ''
  }
}

// ============ 私密设置（需密码进入） ============
const vaultUnlocked = ref(hasVaultToken())
const vaultPassword = ref('')
const vaultIdleMinutes = ref(15)
const vaultUnlockBusy = ref(false)
const vaultLoading = ref(false)
const privateSpaces = ref<PrivateSpaceItem[]>([])
const privateItems = ref<PrivateNodeItem[]>([])
const togglingSpaceId = ref('')
const unhideBusyId = ref('')

// 从现有空间挑选文件/文件夹设为私密
const pickerOpen = ref(false)
const pickerSpaceId = ref('')
const pickerParentId = ref('0')
const pickerStack = ref<{ id: string; name: string }[]>([])
const pickerNodes = ref<DriveNode[]>([])
const pickerLoading = ref(false)
const pickerBusyId = ref('')

// 直接新建私密文件夹 / 上传私密文件
const createPrivateSpaceId = ref('')
const newPrivateFolderOpen = ref(false)
const newPrivateFolderName = ref('')
const newPrivateFolderBusy = ref(false)
const privateFileInput = ref<HTMLInputElement | null>(null)
const privateUploadBusy = ref(false)

async function loadPrivateOverview() {
  vaultLoading.value = true
  error.value = ''
  try {
    const res = await opsApi.privateOverview()
    privateSpaces.value = res.spaces
    privateItems.value = res.items
    vaultUnlocked.value = true
    if (!createPrivateSpaceId.value && res.spaces.length) {
      createPrivateSpaceId.value = res.spaces[0]!.id
    }
  } catch (e) {
    if (e instanceof RequestError && e.httpStatus === 423) {
      setVaultToken(null)
      vaultUnlocked.value = false
      privateSpaces.value = []
      privateItems.value = []
      return
    }
    error.value = e instanceof Error ? e.message : '加载私密设置失败'
  } finally {
    vaultLoading.value = false
  }
}

async function submitVaultUnlock() {
  if (!vaultPassword.value || vaultUnlockBusy.value) return
  vaultUnlockBusy.value = true
  error.value = ''
  feedback.value = ''
  try {
    const res = await opsApi.unlockVault(vaultPassword.value, Number(vaultIdleMinutes.value) || 15)
    setVaultToken(res.privateToken)
    vaultPassword.value = ''
    vaultUnlocked.value = true
    feedback.value = `私密设置已解锁（空闲 ${res.idleMinutes} 分钟后自动锁定）`
    await loadPrivateOverview()
  } catch (e) {
    error.value = e instanceof Error ? e.message : '密码验证失败'
  } finally {
    vaultUnlockBusy.value = false
  }
}

async function lockVaultNow() {
  try { await opsApi.lockVault() } catch { /* ignore */ }
  clearPrivateSpaceToken()
  vaultUnlocked.value = false
  privateSpaces.value = []
  privateItems.value = []
  feedback.value = '私密设置与所有私密空间已锁定'
}

async function toggleSpacePrivate(space: PrivateSpaceItem) {
  if (togglingSpaceId.value) return
  togglingSpaceId.value = space.id
  error.value = ''
  feedback.value = ''
  try {
    const next = !space.privateEnabled
    await opsApi.setSpacePrivate(space.id, next)
    feedback.value = next
      ? `已将「${space.name}」设为私密空间（主页面已隐藏，仅可在此访问）`
      : `已取消「${space.name}」的私密保护（已恢复显示到主页面）`
    await loadPrivateOverview()
    emit('spaces-changed')
  } catch (e) {
    error.value = e instanceof Error ? e.message : '更新空间私密状态失败'
  } finally {
    togglingSpaceId.value = ''
  }
}

async function removePrivateItem(item: PrivateNodeItem) {
  if (unhideBusyId.value) return
  unhideBusyId.value = item.id
  error.value = ''
  feedback.value = ''
  try {
    await opsApi.setHidden(item.spaceId, item.id, false)
    feedback.value = `已取消「${item.name}」的私密状态`
    await loadPrivateOverview()
  } catch (e) {
    error.value = e instanceof Error ? e.message : '取消私密失败'
  } finally {
    unhideBusyId.value = ''
  }
}

function previewPrivateItem(item: PrivateNodeItem) {
  emit('preview-node', {
    id: item.id,
    spaceId: item.spaceId,
    parentId: item.parentId,
    name: item.name,
    nodeType: item.nodeType,
    currentVersionId: item.currentVersionId,
    revision: item.revision,
    size: item.size,
    mimeType: item.mimeType,
    hidden: true,
    updatedAt: item.updatedAt,
    hasChildren: false,
  })
}

async function openPicker() {
  const defaultSpace = props.spaces.find(s => s.kind !== 'PRIVATE')?.id || privateSpaces.value[0]?.id || ''
  pickerSpaceId.value = defaultSpace
  pickerParentId.value = '0'
  pickerStack.value = []
  pickerOpen.value = true
  if (defaultSpace) await loadPickerNodes()
}

async function loadPickerNodes() {
  if (!pickerSpaceId.value) { pickerNodes.value = []; return }
  pickerLoading.value = true
  try {
    const res = await drive.nodes(pickerSpaceId.value, {
      parentId: pickerParentId.value,
      page: 1,
      pageSize: 100,
      hidden: false,
    })
    pickerNodes.value = res.items
  } catch (e) {
    error.value = e instanceof Error ? e.message : '加载目录失败'
    pickerNodes.value = []
  } finally {
    pickerLoading.value = false
  }
}

function enterPickerFolder(node: DriveNode) {
  pickerStack.value.push({ id: node.id, name: node.name })
  pickerParentId.value = node.id
  void loadPickerNodes()
}

function jumpPickerBreadcrumb(index: number) {
  if (index < 0) {
    pickerStack.value = []
    pickerParentId.value = '0'
  } else {
    pickerStack.value = pickerStack.value.slice(0, index + 1)
    pickerParentId.value = pickerStack.value[index]!.id
  }
  void loadPickerNodes()
}

async function markPickerNodePrivate(node: DriveNode) {
  if (pickerBusyId.value) return
  pickerBusyId.value = node.id
  error.value = ''
  feedback.value = ''
  try {
    await opsApi.setHidden(node.spaceId, node.id, true)
    feedback.value = `已将「${node.name}」设为私密（主页面已隐藏）`
    await Promise.all([loadPickerNodes(), loadPrivateOverview()])
  } catch (e) {
    error.value = e instanceof Error ? e.message : '设为私密失败'
  } finally {
    pickerBusyId.value = ''
  }
}

async function submitCreatePrivateFolder() {
  const targetSpace = createPrivateSpaceId.value || privateSpaces.value[0]?.id || props.spaces[0]?.id
  if (!targetSpace || !newPrivateFolderName.value.trim() || newPrivateFolderBusy.value) return
  newPrivateFolderBusy.value = true
  error.value = ''
  feedback.value = ''
  try {
    const created = await drive.folder(targetSpace, '0', newPrivateFolderName.value.trim())
    await opsApi.setHidden(targetSpace, created.id, true)
    newPrivateFolderOpen.value = false
    newPrivateFolderName.value = ''
    feedback.value = `已新建私密文件夹「${created.name}」`
    await loadPrivateOverview()
  } catch (e) {
    error.value = e instanceof Error ? e.message : '新建私密文件夹失败'
  } finally {
    newPrivateFolderBusy.value = false
  }
}

async function onPrivateFileSelected(event: Event) {
  const input = event.target as HTMLInputElement
  const files = Array.from(input.files || [])
  input.value = ''
  const targetSpace = createPrivateSpaceId.value || privateSpaces.value[0]?.id || props.spaces[0]?.id
  if (!targetSpace || !files.length) return
  privateUploadBusy.value = true
  error.value = ''
  feedback.value = ''
  try {
    let count = 0
    for (const file of files) {
      const fileId = await uploadFile(targetSpace, '0', file, () => {})
      if (fileId) {
        await opsApi.setHidden(targetSpace, fileId, true)
        count++
      }
    }
    toast.success(`已上传并设为私密 ${count} 个文件`)
    await loadPrivateOverview()
  } catch (e) {
    const msg = e instanceof Error ? e.message : '上传私密文件失败'
    error.value = msg
    toast.error(msg)
  } finally {
    privateUploadBusy.value = false
  }
}

// ============ 回收站（个人中心内） ============
const trashSpaces = computed(() => props.spaces.filter(s => s.kind !== 'PRIVATE' || vaultUnlocked.value))
const trashSpaceId = ref('')
const trashNodes = ref<DriveNode[]>([])
const trashPage = ref(1)
const trashTotal = ref(0)
const TRASH_PAGE_SIZE = 50
const trashLoading = ref(false)
const trashSelected = ref(new Set<string>())
const trashBusy = ref(false)
const trashAllSelected = computed(() => trashNodes.value.length > 0 && trashSelected.value.size === trashNodes.value.length)

async function loadTrash(targetPage = trashPage.value) {
  if (!trashSpaceId.value) {
    const preferred = props.currentSpaceId && trashSpaces.value.some(s => s.id === props.currentSpaceId)
      ? props.currentSpaceId
      : trashSpaces.value[0]?.id || ''
    trashSpaceId.value = preferred
  }
  if (!trashSpaceId.value) { trashNodes.value = []; trashTotal.value = 0; return }
  trashLoading.value = true
  error.value = ''
  trashSelected.value = new Set()
  try {
    const res = await drive.nodes(trashSpaceId.value, { parentId: '0', page: targetPage, pageSize: TRASH_PAGE_SIZE, trash: true })
    trashNodes.value = res.items
    trashTotal.value = res.total
    trashPage.value = targetPage
  } catch (e) {
    const msg = e instanceof Error ? e.message : '加载回收站失败'
    error.value = msg
    toast.error(msg)
  } finally {
    trashLoading.value = false
  }
}

function toggleTrashSelect(id: string) {
  const next = new Set(trashSelected.value)
  if (next.has(id)) next.delete(id)
  else next.add(id)
  trashSelected.value = next
}

function toggleTrashSelectAll() {
  if (trashAllSelected.value) trashSelected.value = new Set()
  else trashSelected.value = new Set(trashNodes.value.map(n => n.id))
}

async function restoreTrashItems(targets: DriveNode[]) {
  if (!targets.length || trashBusy.value) return
  trashBusy.value = true
  error.value = ''
  feedback.value = ''
  try {
    for (const node of targets) await drive.restore(node)
    toast.success(`已恢复 ${targets.length} 项`)
    await loadTrash(trashPage.value)
  } catch (e) {
    const msg = e instanceof Error ? e.message : '恢复失败'
    error.value = msg
    toast.error(msg)
  } finally {
    trashBusy.value = false
  }
}

async function purgeTrashItems(targets: DriveNode[]) {
  if (!targets.length || trashBusy.value) return
  const ok = await confirm({
    title: '彻底删除确认',
    description: `确定永久销毁所选 ${targets.length} 项吗？此操作不可恢复。`,
    confirmText: '彻底删除',
    variant: 'destructive',
  })
  if (!ok) return
  trashBusy.value = true
  error.value = ''
  feedback.value = ''
  try {
    for (const node of targets) await drive.purgeTrashNode(node)
    toast.success(`已彻底删除 ${targets.length} 项`)
    await loadTrash(trashPage.value)
  } catch (e) {
    const msg = e instanceof Error ? e.message : '彻底删除失败'
    error.value = msg
    toast.error(msg)
  } finally {
    trashBusy.value = false
  }
}

async function emptyTrashSpace() {
  if (!trashSpaceId.value || !trashTotal.value || trashBusy.value) return
  const ok = await confirm({
    title: '清空回收站',
    description: `确定清空当前空间回收站中的全部 ${trashTotal.value} 项吗？所有项目将被永久销毁且不可恢复。`,
    confirmText: '确认清空',
    variant: 'destructive',
  })
  if (!ok) return
  trashBusy.value = true
  error.value = ''
  feedback.value = ''
  try {
    const removed = await drive.emptyTrash(trashSpaceId.value)
    toast.success(`已清空回收站（共彻底删除 ${removed} 项）`)
    await loadTrash(1)
  } catch (e) {
    const msg = e instanceof Error ? e.message : '清空回收站失败'
    error.value = msg
    toast.error(msg)
  } finally {
    trashBusy.value = false
  }
}

watch(
  () => props.section,
  next => {
    error.value = ''
    feedback.value = ''
    if (next === 'security') void loadSessions()
    if (next === 'files') void loadVersionPolicy()
    if (next === 'private') {
      vaultUnlocked.value = hasVaultToken()
      if (vaultUnlocked.value) void loadPrivateOverview()
    }
    if (next === 'trash') void loadTrash()
  },
)

onMounted(() => {
  if (props.section === 'security') void loadSessions()
  if (props.section === 'files') void loadVersionPolicy()
  if (props.section === 'private' && hasVaultToken()) void loadPrivateOverview()
  if (props.section === 'trash') void loadTrash()
})
</script>

<template>
  <div class="user-center">
    <p v-if="error" class="drive-error" role="alert">{{ error }}</p>
    <p v-if="feedback" class="drive-notice" role="status">{{ feedback }}</p>

    <!-- 个人资料 -->
    <section v-if="section === 'profile'" class="settings-card">
      <header class="settings-card-header">
        <div class="settings-card-icon"><User :size="18" /></div>
        <div>
          <h2>个人资料</h2>
          <p>管理你的公开显示昵称、头像以及账户基础信息。</p>
        </div>
      </header>

      <Separator />

      <form class="settings-form" @submit.prevent="saveProfile">
        <div class="profile-avatar-row">
          <img
            v-if="avatarUrl"
            :src="avatarUrl"
            alt="头像预览"
            class="profile-avatar-img"
          />
          <div v-else class="profile-avatar-fallback">
            {{ (displayName || profile.displayName || 'U').slice(0, 1).toUpperCase() }}
          </div>
          <div class="profile-meta flex-1">
            <strong>{{ profile.displayName || '未设置昵称' }}</strong>
            <span>账号 ID：{{ profile.id || '—' }} · 角色：{{ profile.admin ? '超级管理员' : '普通成员' }}</span>
          </div>
          <div class="flex items-center gap-2 shrink-0">
            <input ref="avatarInput" type="file" accept="image/*" class="hidden" @change="onAvatarSelected" />
            <Button type="button" variant="outline" size="sm" :disabled="avatarUploading || profileSaving" @click="avatarInput?.click()">
              <Upload :size="14" /> {{ avatarUploading ? '上传中…' : '上传头像' }}
            </Button>
            <Button v-if="avatarUrl" type="button" variant="ghost" size="sm" :disabled="avatarUploading || profileSaving" @click="clearAvatar">
              <Trash2 :size="14" /> 恢复默认
            </Button>
          </div>
        </div>

        <label class="settings-field">
          <span>显示昵称</span>
          <Input v-model="displayName" aria-label="显示昵称" :disabled="profileSaving" placeholder="输入显示昵称" />
        </label>

        <div class="rounded-lg border border-border bg-muted/30 p-3.5 space-y-1.5 text-xs">
          <div class="flex items-center justify-between">
            <span class="text-muted-foreground">存储空间用量</span>
            <span class="font-medium tabular-nums">
              {{ formatBytes(Number(profile.usedBytes || 0)) }} /
              {{ Number(profile.quotaBytes || 0) > 0 ? formatBytes(Number(profile.quotaBytes)) : '不限' }}
            </span>
          </div>
          <div v-if="Number(profile.quotaBytes || 0) > 0" class="h-1.5 w-full rounded-full bg-muted overflow-hidden">
            <div class="h-full rounded-full bg-primary transition-all" :style="{ width: `${quotaPercent}%` }" />
          </div>
        </div>

        <div class="settings-actions">
          <Button type="submit" :disabled="profileSaving">
            {{ profileSaving ? '正在保存…' : '保存资料' }}
          </Button>
        </div>
      </form>
    </section>

    <!-- 账户与安全 -->
    <section v-else-if="section === 'security'" class="settings-card">
      <header class="settings-card-header">
        <div class="settings-card-icon"><Shield :size="18" /></div>
        <div class="flex-1">
          <h2>账户与安全</h2>
          <p>查看当前账号的所有活跃登录设备与刷新会话，发现异常可立即下线。</p>
        </div>
        <Button variant="outline" size="sm" :disabled="sessionsLoading" @click="loadSessions">
          <RefreshCw :size="14" :class="{ 'animate-spin': sessionsLoading }" /> 刷新
        </Button>
      </header>

      <Separator />

      <div v-if="sessionsLoading" class="settings-empty">正在加载活跃设备列表…</div>
      <div v-else-if="!sessions.length" class="settings-empty">当前没有记录到其他活跃会话。</div>
      <div v-else class="session-list">
        <div v-for="item in sessions" :key="item.id" class="session-row">
          <div class="session-icon"><Laptop :size="18" /></div>
          <div class="session-info">
            <div class="session-title">
              <strong>{{ item.deviceName }}</strong>
              <span v-if="item.current" class="session-badge">当前设备</span>
            </div>
            <p>
              {{ item.clientType }}{{ item.ipAddress ? ` · ${item.ipAddress}` : '' }} · 最近活跃 {{ new Date(item.lastUsedAt || item.createdAt).toLocaleString('zh-CN') }}
            </p>
          </div>
          <Button
            variant="outline"
            size="sm"
            :disabled="item.current || revokingId === item.id"
            @click="revokeSession(item.id)"
          >
            {{ item.current ? '当前' : revokingId === item.id ? '处理中…' : '下线' }}
          </Button>
        </div>
      </div>

      <Separator />

      <form class="settings-form" @submit.prevent="changePassword">
        <div class="text-sm font-medium">修改登录密码</div>
        <div class="grid grid-cols-1 sm:grid-cols-3 gap-3">
          <Input v-model="currentPassword" type="password" autocomplete="current-password" placeholder="当前密码" :disabled="passwordSaving" />
          <Input v-model="newPassword" type="password" autocomplete="new-password" maxlength="32" placeholder="新密码（8–32位）" :disabled="passwordSaving" />
          <Input v-model="confirmPassword" type="password" autocomplete="new-password" maxlength="32" placeholder="确认新密码" :disabled="passwordSaving" />
        </div>
        <div class="settings-actions">
          <Button type="submit" size="sm" variant="outline" :disabled="passwordSaving">
            {{ passwordSaving ? '正在修改…' : '更新登录密码' }}
          </Button>
        </div>
      </form>

      <Separator />

      <div class="settings-danger-row">
        <div>
          <strong>退出当前账号</strong>
          <p>将立即撤销当前浏览器的登录凭证并返回登录页。</p>
        </div>
        <Button variant="outline" class="text-destructive" @click="emit('logout')">
          <LogOut :size="15" /> 退出登录
        </Button>
      </div>
    </section>

    <!-- 显示与外观 -->
    <section v-else-if="section === 'appearance'" class="settings-card">
      <header class="settings-card-header">
        <div class="settings-card-icon"><Palette :size="18" /></div>
        <div>
          <h2>显示与外观</h2>
          <p>自定义颜色模式、主题强调色、圆角与列表密度，设置自动保存在当前浏览器。</p>
        </div>
      </header>

      <Separator />

      <div class="settings-form">
        <fieldset class="settings-fieldset">
          <legend>颜色模式</legend>
          <div class="settings-option-row">
            <Button
              v-for="option in colorModes"
              :key="option.id"
              :variant="currentMode === option.id ? 'default' : 'outline'"
              :aria-pressed="currentMode === option.id"
              @click="setColorMode(option.id)"
            >
              {{ option.name }}
            </Button>
          </div>
        </fieldset>

        <fieldset class="settings-fieldset">
          <legend>主题强调色</legend>
          <div class="settings-option-row">
            <Button
              v-for="item in accentOptions"
              :key="item.key"
              variant="outline"
              :aria-pressed="currentAccent === item.key"
              :class="currentAccent === item.key ? 'ring-2 ring-ring' : ''"
              @click="setAccent(item.key)"
            >
              <span class="size-3 rounded-full" :style="{ background: item.color }" />
              {{ item.name }}
            </Button>
          </div>
        </fieldset>

        <fieldset class="settings-fieldset">
          <legend>界面圆角</legend>
          <div class="settings-option-row">
            <Button
              v-for="radius in radiusOptions"
              :key="radius"
              :variant="currentRadius === radius ? 'default' : 'outline'"
              :aria-pressed="currentRadius === radius"
              @click="setRadius(radius)"
            >
              {{ radius === 0 ? '直角' : `${radius * 16}px` }}
            </Button>
          </div>
        </fieldset>

        <div class="settings-checkbox justify-between">
          <div>
            <strong>紧凑文件列表</strong>
            <span>减小文件行高（36px），在单屏内浏览更多文件。</span>
          </div>
          <Switch :model-value="isCompact" @update:model-value="v => setCompact(v)" />
        </div>
      </div>
    </section>

    <!-- 文件偏好 -->
    <section v-else-if="section === 'files'" class="settings-card">
      <header class="settings-card-header">
        <div class="settings-card-icon"><SlidersHorizontal :size="18" /></div>
        <div>
          <h2>文件工作台偏好</h2>
          <p>调整文件列表的默认展示方式、排序规则与历史版本保留策略。</p>
        </div>
      </header>

      <Separator />

      <div class="settings-form">
        <fieldset class="settings-fieldset">
          <legend>默认视图模式</legend>
          <div class="settings-option-row">
            <Button :variant="view === 'list' ? 'default' : 'outline'" @click="emit('update:view', 'list')">列表视图</Button>
            <Button :variant="view === 'grid' ? 'default' : 'outline'" @click="emit('update:view', 'grid')">网格视图</Button>
          </div>
        </fieldset>

        <fieldset class="settings-fieldset">
          <legend>默认排序方式</legend>
          <div class="settings-option-row">
            <Button :variant="sort === 'name' ? 'default' : 'outline'" @click="emit('update:sort', 'name')">名称排序</Button>
            <Button :variant="sort === 'updated' ? 'default' : 'outline'" @click="emit('update:sort', 'updated')">最近修改</Button>
            <Button :variant="sort === 'size' ? 'default' : 'outline'" @click="emit('update:sort', 'size')">文件大小</Button>
          </div>
        </fieldset>

        <Separator />

        <form class="space-y-2" @submit.prevent="saveVersionPolicy">
          <label class="settings-field">
            <span>单文件历史版本保留上限（系统最大允许 {{ systemMaxVersions }} 个）</span>
            <div class="flex items-center gap-2">
              <Input v-model.number="userMaxVersions" type="number" min="1" :max="systemMaxVersions" class="w-32" :disabled="versionPolicySaving" />
              <Button type="submit" size="sm" variant="outline" :disabled="versionPolicySaving">
                {{ versionPolicySaving ? '保存中…' : '保存版本上限' }}
              </Button>
            </div>
            <small>替换文件产生新版本时，超出此上限的最旧版本将自动清理。</small>
          </label>
        </form>
      </div>
    </section>

    <!-- 私密设置（需密码进入） -->
    <section v-else-if="section === 'private'" class="settings-card">
      <header class="settings-card-header">
        <div class="settings-card-icon"><LockKeyhole :size="18" /></div>
        <div class="flex-1">
          <h2>私密设置</h2>
          <p>单独设置空间的私密保护，或管理私密文件与文件夹。设为私密的内容不会在主页面和搜索中显示。</p>
        </div>
        <Button v-if="vaultUnlocked" variant="outline" size="sm" @click="lockVaultNow">
          <Lock :size="14" /> 立即锁定
        </Button>
      </header>

      <Separator />

      <!-- 未解锁：要求输入账号密码 -->
      <form v-if="!vaultUnlocked" class="mx-auto max-w-md space-y-4 py-6" @submit.prevent="submitVaultUnlock">
        <div class="flex items-center gap-3 rounded-lg border border-border bg-muted/30 p-3.5 text-sm">
          <LockKeyhole :size="20" class="shrink-0 text-primary" />
          <div>
            <div class="font-medium">需要验证账号密码</div>
            <div class="text-xs text-muted-foreground">进入私密设置查看或管理私密空间与私密文件前，请先输入当前账号登录密码。</div>
          </div>
        </div>
        <label class="settings-field">
          <span>当前账号密码</span>
          <Input
            v-model="vaultPassword"
            type="password"
            autocomplete="current-password"
            placeholder="请输入登录密码"
            :disabled="vaultUnlockBusy"
            autofocus
          />
        </label>
        <div class="settings-field">
          <span>空闲自动锁定时间</span>
          <Select
            :model-value="vaultIdleMinutes"
            :disabled="vaultUnlockBusy"
            aria-label="空闲自动锁定时间"
            :options="[
              { value: '15', label: '15 分钟' },
              { value: '30', label: '30 分钟' },
              { value: '60', label: '60 分钟' },
            ]"
            @update:model-value="v => { vaultIdleMinutes = Number(v) || 30 }"
          />
        </div>
        <Button type="submit" class="w-full" :disabled="vaultUnlockBusy || !vaultPassword">
          <Unlock :size="14" /> {{ vaultUnlockBusy ? '正在验证…' : '验证密码并进入' }}
        </Button>
      </form>

      <!-- 已解锁：空间私密开关 + 私密文件/文件夹管理 -->
      <div v-else class="space-y-6">
        <div v-if="vaultLoading" class="settings-empty">正在加载私密内容…</div>
        <template v-else>
          <!-- 1. 空间私密开关 -->
          <div class="space-y-3">
            <div class="flex items-center justify-between">
              <div>
                <h3 class="text-sm font-semibold">空间私密保护</h3>
                <p class="text-xs text-muted-foreground">开启后该空间将从主页面左侧栏隐藏，并隔离全局搜索与外部分享。</p>
              </div>
              <Button variant="ghost" size="sm" @click="loadPrivateOverview"><RefreshCw :size="14" /> 刷新</Button>
            </div>
            <div v-if="!privateSpaces.length" class="rounded-lg border border-border p-4 text-center text-xs text-muted-foreground">
              暂无可设置的个人空间
            </div>
            <div v-else class="divide-y divide-border rounded-lg border border-border">
              <div v-for="s in privateSpaces" :key="s.id" class="flex items-center justify-between gap-3 px-4 py-3">
                <div class="flex min-w-0 items-center gap-2.5">
                  <Folder :size="16" class="shrink-0" :style="{ color: s.color || '#6366f1' }" />
                  <span class="truncate text-sm font-medium">{{ s.name }}</span>
                  <span
                    class="shrink-0 whitespace-nowrap rounded px-1.5 py-0.5 text-[11px]"
                    :class="s.privateEnabled ? 'bg-amber-500/15 text-amber-600 dark:text-amber-400' : 'bg-muted text-muted-foreground'"
                  >
                    {{ s.privateEnabled ? '已私密' : '常规空间' }}
                  </span>
                </div>
                <div class="flex shrink-0 items-center gap-2">
                  <Button v-if="s.privateEnabled" variant="outline" size="sm" @click="emit('open-space', s.id)">
                    <FolderOpen :size="13" /> 浏览文件
                  </Button>
                  <Button
                    :variant="s.privateEnabled ? 'outline' : 'default'"
                    size="sm"
                    :disabled="togglingSpaceId === s.id"
                    @click="toggleSpacePrivate(s)"
                  >
                    {{ togglingSpaceId === s.id ? '处理中…' : s.privateEnabled ? '取消私密' : '开启私密' }}
                  </Button>
                </div>
              </div>
            </div>
          </div>

          <Separator />

          <!-- 2. 私密文件与文件夹 -->
          <div class="space-y-3">
            <div class="flex flex-wrap items-center justify-between gap-2">
              <div>
                <h3 class="text-sm font-semibold">私密文件与文件夹 ({{ privateItems.length }})</h3>
                <p class="text-xs text-muted-foreground">可从现有空间添加私密项，或直接上传私密文件、新建私密文件夹。</p>
              </div>
              <div class="flex flex-wrap items-center gap-2">
                <Select
                  v-if="privateSpaces.length"
                  v-model="createPrivateSpaceId"
                  class="h-8 w-36 text-xs"
                  aria-label="新增私密项所属空间"
                  :options="privateSpaces.map(s => ({ value: s.id, label: s.name }))"
                />
                <Button variant="outline" size="sm" @click="openPicker">
                  <Plus :size="13" /> 从空间添加
                </Button>
                <Button variant="outline" size="sm" @click="newPrivateFolderName = ''; newPrivateFolderOpen = true">
                  <FolderPlus :size="13" /> 新建私密文件夹
                </Button>
                <input ref="privateFileInput" type="file" multiple class="hidden" @change="onPrivateFileSelected" />
                <Button size="sm" :disabled="privateUploadBusy || !privateSpaces.length" @click="privateFileInput?.click()">
                  <Upload :size="13" /> {{ privateUploadBusy ? '上传中…' : '上传私密文件' }}
                </Button>
              </div>
            </div>

            <div v-if="!privateItems.length" class="rounded-lg border border-border p-8 text-center text-xs text-muted-foreground">
              暂无单独设为私密的文件或文件夹
            </div>
            <div v-else class="divide-y divide-border rounded-lg border border-border">
              <div v-for="item in privateItems" :key="item.id" class="flex items-center justify-between gap-3 px-4 py-2.5">
                <div class="flex min-w-0 flex-1 items-center gap-2.5">
                  <Folder v-if="item.nodeType === 'FOLDER'" :size="16" class="shrink-0 text-amber-500" />
                  <File v-else :size="16" class="shrink-0 text-primary" />
                  <div class="min-w-0 flex-1">
                    <div class="flex items-center gap-2">
                      <span class="truncate text-sm font-medium">{{ item.name }}</span>
                      <span class="shrink-0 whitespace-nowrap rounded bg-muted px-1.5 py-0.5 text-[10px] text-muted-foreground">{{ item.spaceName }}</span>
                    </div>
                    <div class="text-[11px] text-muted-foreground">
                      {{ item.nodeType === 'FOLDER' ? '文件夹' : formatBytes(item.size) }} · {{ new Date(item.updatedAt).toLocaleString('zh-CN') }}
                    </div>
                  </div>
                </div>
                <div class="flex shrink-0 items-center gap-1.5">
                  <Button v-if="item.nodeType === 'FILE'" variant="outline" size="sm" @click="previewPrivateItem(item)">
                    <Eye :size="13" /> 预览
                  </Button>
                  <Button v-else variant="outline" size="sm" @click="emit('open-folder', item.spaceId, item.id)">
                    <FolderOpen :size="13" /> 打开
                  </Button>
                  <Button variant="ghost" size="sm" :disabled="unhideBusyId === item.id" @click="removePrivateItem(item)">
                    取消私密
                  </Button>
                </div>
              </div>
            </div>
          </div>
        </template>
      </div>
    </section>

    <!-- 回收站（个人中心内） -->
    <section v-else class="settings-card">
      <header class="settings-card-header">
        <div class="settings-card-icon"><Trash2 :size="18" /></div>
        <div class="flex-1">
          <h2>回收站</h2>
          <p>查看各空间中已移入回收站的文件与文件夹，支持恢复或彻底销毁。</p>
        </div>
        <div class="flex items-center gap-2">
          <Select
            :model-value="trashSpaceId"
            class="h-8 w-36 text-xs"
            aria-label="选择回收站空间"
            :options="trashSpaces.map(s => ({ value: s.id, label: s.name }))"
            @update:model-value="v => { trashSpaceId = v; void loadTrash(1) }"
          />
          <Button variant="outline" size="sm" :disabled="trashLoading" @click="loadTrash(trashPage)">
            <RefreshCw :size="13" :class="{ 'animate-spin': trashLoading }" /> 刷新
          </Button>
          <Button variant="outline" size="sm" class="text-destructive" :disabled="!trashTotal || trashBusy" @click="emptyTrashSpace">
            <Trash2 :size="13" /> 清空回收站
          </Button>
        </div>
      </header>

      <Separator />

      <div v-if="trashSelected.size" class="flex items-center justify-between rounded-lg bg-muted/40 px-3 py-2 text-xs">
        <span>已选 {{ trashSelected.size }} 项</span>
        <div class="flex items-center gap-2">
          <Button
            variant="outline"
            size="sm"
            :disabled="trashBusy"
            @click="restoreTrashItems(trashNodes.filter(n => trashSelected.has(n.id)))"
          >
            <RotateCcw :size="13" /> 恢复选中
          </Button>
          <Button
            variant="outline"
            size="sm"
            class="text-destructive"
            :disabled="trashBusy"
            @click="purgeTrashItems(trashNodes.filter(n => trashSelected.has(n.id)))"
          >
            <Trash2 :size="13" /> 彻底删除
          </Button>
        </div>
      </div>

      <div v-if="trashLoading" class="settings-empty">正在加载回收站…</div>
      <div v-else-if="!trashNodes.length" class="settings-empty">当前空间回收站是空的。</div>
      <div v-else class="divide-y divide-border rounded-lg border border-border">
        <div class="flex items-center gap-3 bg-muted/40 px-3.5 py-2 text-xs text-muted-foreground">
          <Checkbox :model-value="trashAllSelected ? true : trashSelected.size > 0 ? 'indeterminate' : false" aria-label="全选回收站" @update:model-value="toggleTrashSelectAll" />
          <span class="flex-1">名称</span>
          <span class="w-20 text-right">大小</span>
          <span class="w-36 text-right">删除/更新时间</span>
          <span class="w-32 text-right">操作</span>
        </div>
        <div v-for="node in trashNodes" :key="node.id" class="flex items-center gap-3 px-3.5 py-2.5 text-sm">
          <Checkbox
            :model-value="trashSelected.has(node.id)"
            :aria-label="`选择 ${node.name}`"
            @update:model-value="toggleTrashSelect(node.id)"
          />
          <div class="flex min-w-0 flex-1 items-center gap-2">
            <Folder v-if="node.nodeType === 'FOLDER'" :size="16" class="shrink-0 text-amber-500" />
            <File v-else :size="16" class="shrink-0 text-primary" />
            <span class="truncate">{{ node.name }}</span>
          </div>
          <span class="w-20 shrink-0 text-right text-xs text-muted-foreground">{{ node.nodeType === 'FOLDER' ? '—' : formatBytes(node.size) }}</span>
          <span class="w-36 shrink-0 text-right text-xs text-muted-foreground">{{ new Date(node.updatedAt).toLocaleString('zh-CN') }}</span>
          <div class="flex w-32 shrink-0 justify-end gap-1">
            <Button variant="outline" size="sm" class="h-7 px-2 text-xs" :disabled="trashBusy" @click="restoreTrashItems([node])">恢复</Button>
            <Button variant="ghost" size="sm" class="h-7 px-2 text-xs text-destructive" :disabled="trashBusy" @click="purgeTrashItems([node])">彻底删除</Button>
          </div>
        </div>
        <div v-if="trashTotal > TRASH_PAGE_SIZE" class="flex items-center justify-between bg-muted/20 px-3.5 py-2 text-xs text-muted-foreground">
          <Button variant="outline" size="sm" :disabled="trashPage <= 1 || trashLoading" @click="loadTrash(trashPage - 1)">上一页</Button>
          <span>第 {{ trashPage }} / {{ Math.ceil(trashTotal / TRASH_PAGE_SIZE) }} 页（共 {{ trashTotal }} 项）</span>
          <Button variant="outline" size="sm" :disabled="trashPage * TRASH_PAGE_SIZE >= trashTotal || trashLoading" @click="loadTrash(trashPage + 1)">下一页</Button>
        </div>
      </div>
    </section>

    <!-- 从空间挑选设为私密的弹窗 -->
    <Dialog :open="pickerOpen" @update:open="v => { if (!v) pickerOpen = false }">
      <DialogContent class="!max-w-xl">
        <DialogHeader>
          <DialogTitle>从空间添加私密文件或文件夹</DialogTitle>
          <DialogDescription>选择任意文件或文件夹设为私密后，将立即从主页面隐藏，仅在私密设置中可见。</DialogDescription>
        </DialogHeader>
        <div class="space-y-3">
          <div class="flex items-center gap-2">
            <Select
              :model-value="pickerSpaceId"
              class="flex-1"
              aria-label="选择添加私密项的空间"
              :options="spaces.map(s => ({ value: s.id, label: s.name }))"
              @update:model-value="v => { pickerSpaceId = v; pickerParentId = '0'; pickerStack = []; void loadPickerNodes() }"
            />
          </div>
          <div class="flex flex-wrap items-center gap-1 text-xs text-muted-foreground">
            <button type="button" class="hover:text-foreground underline-offset-2 hover:underline" @click="jumpPickerBreadcrumb(-1)">根目录</button>
            <template v-for="(crumb, idx) in pickerStack" :key="crumb.id">
              <span>/</span>
              <button type="button" class="hover:text-foreground underline-offset-2 hover:underline" @click="jumpPickerBreadcrumb(idx)">{{ crumb.name }}</button>
            </template>
          </div>
          <div class="max-h-72 overflow-y-auto divide-y divide-border rounded-lg border border-border">
            <div v-if="pickerLoading" class="p-6 text-center text-xs text-muted-foreground">加载中…</div>
            <div v-else-if="!pickerNodes.length" class="p-6 text-center text-xs text-muted-foreground">当前目录为空</div>
            <div v-for="node in pickerNodes" :key="node.id" class="flex items-center justify-between gap-2 px-3 py-2 text-sm">
              <div class="flex min-w-0 flex-1 items-center gap-2">
                <Folder v-if="node.nodeType === 'FOLDER'" :size="15" class="shrink-0 text-amber-500" />
                <File v-else :size="15" class="shrink-0 text-primary" />
                <button
                  v-if="node.nodeType === 'FOLDER'"
                  type="button"
                  class="truncate text-left hover:text-primary hover:underline"
                  @click="enterPickerFolder(node)"
                >
                  {{ node.name }}
                </button>
                <span v-else class="truncate">{{ node.name }}</span>
              </div>
              <Button size="sm" variant="outline" class="h-7 shrink-0 px-2 text-xs" :disabled="pickerBusyId === node.id" @click="markPickerNodePrivate(node)">
                {{ pickerBusyId === node.id ? '处理中…' : '设为私密' }}
              </Button>
            </div>
          </div>
        </div>
        <DialogFooter>
          <Button variant="outline" @click="pickerOpen = false">完成</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>

    <!-- 新建私密文件夹弹窗 -->
    <Dialog :open="newPrivateFolderOpen" @update:open="v => { if (!v && !newPrivateFolderBusy) newPrivateFolderOpen = false }">
      <DialogContent class="!max-w-sm">
        <DialogHeader>
          <DialogTitle>新建私密文件夹</DialogTitle>
          <DialogDescription>创建后自动标记为私密，不会在主页面显示。</DialogDescription>
        </DialogHeader>
        <form class="space-y-3" @submit.prevent="submitCreatePrivateFolder">
          <Input v-model="newPrivateFolderName" placeholder="输入文件夹名称" :disabled="newPrivateFolderBusy" autofocus />
          <DialogFooter>
            <Button type="button" variant="outline" :disabled="newPrivateFolderBusy" @click="newPrivateFolderOpen = false">取消</Button>
            <Button type="submit" :disabled="newPrivateFolderBusy || !newPrivateFolderName.trim()">
              {{ newPrivateFolderBusy ? '创建中…' : '创建' }}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  </div>
</template>
