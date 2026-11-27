<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { Button as VanButton, Switch as VanSwitch, showToast } from 'vant'
import {
  ChevronRight,
  Eye,
  File,
  Fingerprint,
  Folder,
  FolderPlus,
  Lock,
  Plus,
  ShieldCheck,
  Upload,
} from '@lucide/vue'
import { normalizeErrorMessage } from '@repo/drive-core/errors'
import { formatBytes, formatRelativeTime } from '@repo/drive-core/format'
import {
  clearPrivateSpaceToken,
  hasVaultToken,
  setVaultToken,
} from '@repo/session/request'
import {
  mobileDriveApi,
  type DriveNode,
  type PrivateNodeItem,
  type PrivateSpaceItem,
} from '@/api/drive'
import { handleVaultLockedError } from '@/lib/private-vault'
import {
  authenticateBiometric,
  checkBiometricAvailability,
  loadSecurityLockPrefs,
  mobileCapabilities,
  type BiometricAvailability,
} from '@/native'
import { fetchSpaces } from '@/stores/spaces'
import { enqueueFilesWithGuard, onUploadCompleted } from '@/stores/uploads'
import { EmptyState, MobileShell, SkeletonList } from '@/components/common'
import RenameDialogSheet from '@/components/drive/RenameDialogSheet.vue'
import MobilePreviewShell from '@/components/preview/MobilePreviewShell.vue'

type VaultTab = 'spaces' | 'hidden'

const unlocked = ref(false)
const password = ref('')
const idleMinutes = ref(15)
const unlocking = ref(false)
const unlockError = ref('')
const biometricInfo = ref<BiometricAvailability>({
  available: false,
  biometryType: 'none',
  label: '不支持',
})
const biometricVaultEnabled = ref(loadSecurityLockPrefs().biometricVaultEnabled)

async function handleBiometricVaultCheck() {
  unlockError.value = ''
  const res = await authenticateBiometric('验证身份以解锁私密保险箱')
  if (!res.ok) {
    unlockError.value = res.message || '生物识别未通过，请直接输入账号密码解锁'
    return
  }
  if (hasVaultToken()) {
    await loadOverview()
  } else {
    showToast('生物识别已通过，请输入一次登录密码以签发服务端私密令牌')
  }
}

const activeTab = ref<VaultTab>('spaces')
const loadingOverview = ref(false)
const privateSpaces = ref<PrivateSpaceItem[]>([])
const hiddenItems = ref<PrivateNodeItem[]>([])

// Active private space browsing
const activeSpace = ref<PrivateSpaceItem | null>(null)
const currentFolderId = ref('0')
const folderTrail = ref<{ id: string; name: string }[]>([])
const spaceNodes = ref<DriveNode[]>([])
const loadingNodes = ref(false)

// Create private space / folder / upload / preview
const createSpaceOpen = ref(false)
const newSpaceName = ref('')
const creatingSpace = ref(false)
const createFolderOpen = ref(false)
const creatingFolder = ref(false)
const uploadInputRef = ref<HTMLInputElement | null>(null)
const previewNode = ref<DriveNode | null>(null)

let unsubUpload: (() => void) | null = null

const privateOnlySpaces = computed(() => privateSpaces.value.filter(s => s.privateEnabled))

function wipeAllPrivateMemory() {
  unlocked.value = false
  privateSpaces.value = []
  hiddenItems.value = []
  activeSpace.value = null
  currentFolderId.value = '0'
  folderTrail.value = []
  spaceNodes.value = []
  previewNode.value = null
  password.value = ''
}

function onVault423() {
  wipeAllPrivateMemory()
  showToast('私密空间会话已过期或已锁定，请重新验证')
}

async function loadOverview() {
  if (!hasVaultToken()) {
    wipeAllPrivateMemory()
    return
  }
  loadingOverview.value = true
  try {
    const res = await mobileDriveApi.privateOverview()
    privateSpaces.value = res.spaces
    hiddenItems.value = res.items
    unlocked.value = true
  } catch (err) {
    if (handleVaultLockedError(err, () => clearPrivateSpaceToken(), onVault423)) return
    showToast(normalizeErrorMessage(err, '加载私密空间失败'))
  } finally {
    loadingOverview.value = false
  }
}

async function loadPrivateSpaceNodes(space: PrivateSpaceItem, folderId = '0') {
  activeSpace.value = space
  currentFolderId.value = folderId
  loadingNodes.value = true
  try {
    const res = await mobileDriveApi.nodes(space.id, {
      parentId: folderId,
      page: 1,
      pageSize: 100,
      hidden: true,
    })
    spaceNodes.value = res.items
  } catch (err) {
    if (handleVaultLockedError(err, () => clearPrivateSpaceToken(), onVault423)) return
    showToast(normalizeErrorMessage(err, '加载私密目录失败'))
  } finally {
    loadingNodes.value = false
  }
}

onMounted(() => {
  // 安全红线：仅当本地已有有效 vaultToken 时才加载，绝不提前请求任何私密节点
  if (hasVaultToken()) {
    void loadOverview()
  }
  unsubUpload = onUploadCompleted(evt => {
    if (unlocked.value && activeSpace.value?.id === evt.spaceId && currentFolderId.value === evt.parentId) {
      void loadPrivateSpaceNodes(activeSpace.value, currentFolderId.value)
    }
  })
  if (mobileCapabilities.biometricLock) {
    void checkBiometricAvailability().then(info => {
      biometricInfo.value = info
    })
  }
})

onBeforeUnmount(() => {
  unsubUpload?.()
  unsubUpload = null
  // 离开页面清空组件内存数据，防止回退看到旧缓存
  privateSpaces.value = []
  hiddenItems.value = []
  spaceNodes.value = []
})

async function handleUnlock() {
  if (!password.value) {
    unlockError.value = '请输入登录密码'
    return
  }
  unlocking.value = true
  unlockError.value = ''
  try {
    const res = await mobileDriveApi.unlockVault(password.value, idleMinutes.value)
    setVaultToken(res.privateToken)
    password.value = ''
    await loadOverview()
  } catch (err) {
    unlockError.value = normalizeErrorMessage(err, '密码验证失败')
  } finally {
    unlocking.value = false
  }
}

async function lockNow() {
  try {
    await mobileDriveApi.lockVault()
  } catch {
    // always clear locally even if network fails
  }
  clearPrivateSpaceToken()
  wipeAllPrivateMemory()
  showToast('已立即锁定私密空间')
}

async function toggleSpacePrivate(item: PrivateSpaceItem, enabled: boolean) {
  try {
    await mobileDriveApi.setSpacePrivate(item.id, enabled)
    showToast(enabled ? `已将「${item.name}」设为私密空间` : `已将「${item.name}」移出私密空间`)
    await Promise.all([loadOverview(), fetchSpaces(true)])
  } catch (err) {
    if (handleVaultLockedError(err, () => clearPrivateSpaceToken(), onVault423)) return
    showToast(normalizeErrorMessage(err, '更新空间私密状态失败'))
  }
}

async function createPrivateSpace() {
  const name = newSpaceName.value.trim()
  if (!name) {
    showToast('请输入私密空间名称')
    return
  }
  creatingSpace.value = true
  try {
    await mobileDriveApi.createSpace({ name, kind: 'PRIVATE', color: '#4f46e5' })
    showToast('私密空间已创建')
    newSpaceName.value = ''
    createSpaceOpen.value = false
    await loadOverview()
  } catch (err) {
    if (handleVaultLockedError(err, () => clearPrivateSpaceToken(), onVault423)) return
    showToast(normalizeErrorMessage(err, '创建私密空间失败'))
  } finally {
    creatingSpace.value = false
  }
}

function openSpace(space: PrivateSpaceItem) {
  folderTrail.value = []
  void loadPrivateSpaceNodes(space, '0')
}

function exitSpaceBrowser() {
  activeSpace.value = null
  folderTrail.value = []
  spaceNodes.value = []
}

function openPrivateNode(node: DriveNode) {
  if (node.nodeType === 'FOLDER' && activeSpace.value) {
    folderTrail.value = [...folderTrail.value, { id: node.id, name: node.name }]
    void loadPrivateSpaceNodes(activeSpace.value, node.id)
  } else {
    previewNode.value = node
  }
}

function jumpTrail(index: number) {
  if (!activeSpace.value) return
  if (index < 0) {
    folderTrail.value = []
    void loadPrivateSpaceNodes(activeSpace.value, '0')
  } else {
    const target = folderTrail.value[index]
    if (!target) return
    folderTrail.value = folderTrail.value.slice(0, index + 1)
    void loadPrivateSpaceNodes(activeSpace.value, target.id)
  }
}

async function createPrivateFolder(name: string) {
  if (!activeSpace.value) return
  creatingFolder.value = true
  try {
    await mobileDriveApi.createFolder(activeSpace.value.id, currentFolderId.value, name)
    showToast('文件夹已创建')
    createFolderOpen.value = false
    await loadPrivateSpaceNodes(activeSpace.value, currentFolderId.value)
  } catch (err) {
    if (handleVaultLockedError(err, () => clearPrivateSpaceToken(), onVault423)) return
    showToast(normalizeErrorMessage(err, '创建文件夹失败'))
  } finally {
    creatingFolder.value = false
  }
}

async function handlePrivateUpload(event: Event) {
  const input = event.target as HTMLInputElement
  const files = input.files ? Array.from(input.files) : []
  input.value = ''
  if (!activeSpace.value || files.length === 0) return
  const enqueued = await enqueueFilesWithGuard(activeSpace.value.id, currentFolderId.value, files)
  if (enqueued) showToast(`已加入上传队列（${files.length} 个文件）`)
}

async function unhideItem(item: PrivateNodeItem) {
  try {
    await mobileDriveApi.setHidden(item.spaceId, item.id, false)
    showToast(`已取消隐藏「${item.name}」`)
    await loadOverview()
  } catch (err) {
    if (handleVaultLockedError(err, () => clearPrivateSpaceToken(), onVault423)) return
    showToast(normalizeErrorMessage(err, '取消隐藏失败'))
  }
}

function previewHiddenItem(item: PrivateNodeItem) {
  if (item.nodeType !== 'FILE') {
    showToast('请取消隐藏后在空间中浏览该文件夹')
    return
  }
  previewNode.value = {
    id: item.id,
    spaceId: item.spaceId,
    parentId: item.parentId,
    name: item.name,
    nodeType: item.nodeType,
    currentVersionId: item.currentVersionId,
    revision: item.revision,
    size: item.size,
    mimeType: item.mimeType,
    updatedAt: item.updatedAt,
    hidden: true,
    hasChildren: false,
  }
}
</script>

<template>
  <MobileShell title="私密空间与隐藏项" back-to="/me">
    <template #right>
      <button
        v-if="unlocked"
        type="button"
        class="inline-flex items-center gap-1 rounded-full bg-destructive/10 px-2.5 py-1 text-xs font-medium text-destructive active:bg-destructive/20"
        @click="lockNow"
      >
        <Lock :size="12" />
        <span>立即锁定</span>
      </button>
    </template>

    <!-- 未解锁：独立密码验证锁屏 -->
    <div v-if="!unlocked" class="flex-1 flex items-center justify-center px-5 py-10">
      <form
        class="w-full max-w-sm rounded-3xl border border-border bg-card p-6 shadow-xs space-y-4"
        @submit.prevent="handleUnlock"
      >
        <div class="flex flex-col items-center text-center gap-2">
          <span class="flex h-14 w-14 items-center justify-center rounded-2xl bg-primary/10 text-primary">
            <ShieldCheck :size="28" />
          </span>
          <h2 class="text-base font-semibold text-foreground">解锁私密保险箱</h2>
          <p class="text-xs text-muted-foreground">
            私密空间与隐藏文件独立隔离，未解锁前绝不加载或缓存任何内容。
          </p>
        </div>

        <div>
          <input
            v-model="password"
            type="password"
            autocomplete="current-password"
            placeholder="请输入账号登录密码"
            class="w-full rounded-xl border border-input bg-background px-3.5 py-2.5 text-sm text-foreground focus:outline-none focus:ring-2 focus:ring-primary/40"
          />
          <p v-if="unlockError" class="text-xs text-destructive mt-1.5">{{ unlockError }}</p>
        </div>

        <VanButton
          v-if="biometricInfo.available && biometricVaultEnabled"
          block
          round
          plain
          type="primary"
          size="small"
          @click="handleBiometricVaultCheck"
        >
          <span class="inline-flex items-center gap-1.5">
            <Fingerprint :size="15" />
            <span>使用{{ biometricInfo.label }}快速验证</span>
          </span>
        </VanButton>

        <div class="flex items-center justify-between text-xs text-muted-foreground">
          <span>空闲自动锁定</span>
          <div class="flex gap-1.5">
            <button
              v-for="m in [5, 15, 30]"
              :key="m"
              type="button"
              class="rounded-full px-2.5 py-1 border text-xs"
              :class="
                idleMinutes === m
                  ? 'border-primary bg-primary/10 text-primary font-medium'
                  : 'border-border text-muted-foreground'
              "
              @click="idleMinutes = m"
            >
              {{ m }} 分钟
            </button>
          </div>
        </div>

        <VanButton block round type="primary" native-type="submit" :loading="unlocking">
          验证并进入
        </VanButton>
      </form>
    </div>

    <!-- 已解锁：浏览特定私密空间 -->
    <div v-else-if="activeSpace" class="flex-1 min-h-0 flex flex-col">
      <div class="border-b border-border bg-card/80 px-3.5 py-2.5 flex items-center justify-between gap-2">
        <div class="flex items-center gap-1 overflow-x-auto whitespace-nowrap text-xs text-muted-foreground no-scrollbar min-w-0">
          <button type="button" class="text-primary font-medium shrink-0" @click="exitSpaceBrowser">
            全部私密空间
          </button>
          <ChevronRight :size="12" class="shrink-0 opacity-60" />
          <button
            type="button"
            class="shrink-0"
            :class="folderTrail.length === 0 ? 'font-semibold text-foreground' : ''"
            @click="jumpTrail(-1)"
          >
            {{ activeSpace.name }}
          </button>
          <template v-for="(crumb, idx) in folderTrail" :key="crumb.id">
            <ChevronRight :size="12" class="shrink-0 opacity-60" />
            <button
              type="button"
              class="shrink-0 max-w-[110px] truncate"
              :class="idx === folderTrail.length - 1 ? 'font-semibold text-foreground' : ''"
              @click="jumpTrail(idx)"
            >
              {{ crumb.name }}
            </button>
          </template>
        </div>

        <div class="flex items-center gap-1 shrink-0">
          <button
            type="button"
            class="inline-flex h-8 w-8 items-center justify-center rounded-full text-foreground active:bg-accent"
            aria-label="新建文件夹"
            @click="createFolderOpen = true"
          >
            <FolderPlus :size="17" />
          </button>
          <button
            type="button"
            class="inline-flex h-8 w-8 items-center justify-center rounded-full text-primary active:bg-primary/10"
            aria-label="上传文件"
            @click="uploadInputRef?.click()"
          >
            <Upload :size="17" />
          </button>
        </div>
      </div>

      <input ref="uploadInputRef" type="file" multiple class="hidden" @change="handlePrivateUpload" />

      <div class="flex-1 overflow-y-auto px-3.5 pb-16">
        <SkeletonList v-if="loadingNodes" :count="5" />
        <EmptyState v-else-if="spaceNodes.length === 0" title="当前私密目录为空" />
        <div v-else class="divide-y divide-border">
          <button
            v-for="node in spaceNodes"
            :key="node.id"
            type="button"
            class="w-full flex items-center gap-3 py-3 text-left active:bg-accent/50"
            @click="openPrivateNode(node)"
          >
            <span
              class="flex h-10 w-10 items-center justify-center rounded-xl shrink-0"
              :class="node.nodeType === 'FOLDER' ? 'bg-primary/10 text-primary' : 'bg-muted text-foreground'"
            >
              <Folder v-if="node.nodeType === 'FOLDER'" :size="20" />
              <File v-else :size="20" />
            </span>
            <div class="flex-1 min-w-0">
              <p class="text-sm font-medium truncate text-foreground">{{ node.name }}</p>
              <p class="text-xs text-muted-foreground mt-0.5">
                {{ node.nodeType === 'FOLDER' ? '文件夹' : formatBytes(node.size) }} · {{ formatRelativeTime(node.updatedAt) }}
              </p>
            </div>
          </button>
        </div>
      </div>

      <RenameDialogSheet
        v-model:open="createFolderOpen"
        mode="create-folder"
        :submitting="creatingFolder"
        @submit="createPrivateFolder"
      />
    </div>

    <!-- 已解锁：私密空间与隐藏项总览 -->
    <div v-else class="flex-1 min-h-0 flex flex-col">
      <div class="border-b border-border bg-background px-3.5 py-2">
        <div class="grid grid-cols-2 gap-1 rounded-xl bg-muted p-1">
          <button
            type="button"
            class="rounded-lg py-1.5 text-xs font-medium"
            :class="activeTab === 'spaces' ? 'bg-background text-foreground shadow-xs' : 'text-muted-foreground'"
            @click="activeTab = 'spaces'"
          >
            私密空间 ({{ privateOnlySpaces.length }})
          </button>
          <button
            type="button"
            class="rounded-lg py-1.5 text-xs font-medium"
            :class="activeTab === 'hidden' ? 'bg-background text-foreground shadow-xs' : 'text-muted-foreground'"
            @click="activeTab = 'hidden'"
          >
            隐藏项 ({{ hiddenItems.length }})
          </button>
        </div>
      </div>

      <div class="flex-1 overflow-y-auto px-3.5 py-3 pb-16 space-y-4">
        <SkeletonList v-if="loadingOverview" :count="5" />

        <template v-else-if="activeTab === 'spaces'">
          <!-- 新建私密空间 -->
          <div class="flex items-center justify-between">
            <span class="text-xs font-medium text-muted-foreground">我的私密空间</span>
            <button
              type="button"
              class="inline-flex items-center gap-1 rounded-full bg-primary/10 px-2.5 py-1 text-xs font-medium text-primary"
              @click="createSpaceOpen = !createSpaceOpen"
            >
              <Plus :size="13" />
              <span>新建私密空间</span>
            </button>
          </div>

          <form
            v-if="createSpaceOpen"
            class="flex items-center gap-2 rounded-2xl border border-border bg-card p-2.5"
            @submit.prevent="createPrivateSpace"
          >
            <input
              v-model="newSpaceName"
              type="text"
              maxlength="40"
              placeholder="输入新私密空间名称"
              class="flex-1 min-w-0 rounded-xl border border-input bg-background px-3 py-1.5 text-xs text-foreground focus:outline-none"
            />
            <VanButton size="small" round type="primary" native-type="submit" :loading="creatingSpace">
              创建
            </VanButton>
          </form>

          <EmptyState
            v-if="privateOnlySpaces.length === 0"
            title="还没有私密空间"
            description="可新建私密空间，或将下方普通个人空间切换为私密"
          />
          <div v-else class="space-y-2">
            <button
              v-for="sp in privateOnlySpaces"
              :key="sp.id"
              type="button"
              class="w-full flex items-center justify-between gap-3 rounded-2xl border border-border bg-card p-3.5 text-left active:bg-accent/50"
              @click="openSpace(sp)"
            >
              <div class="flex items-center gap-3 min-w-0">
                <span
                  class="flex h-10 w-10 items-center justify-center rounded-xl text-white shrink-0"
                  :style="{ backgroundColor: sp.color || '#4f46e5' }"
                >
                  <Lock :size="18" />
                </span>
                <div class="min-w-0">
                  <p class="text-sm font-medium truncate text-foreground">{{ sp.name }}</p>
                  <p class="text-[11px] text-muted-foreground">点击进入浏览与上传</p>
                </div>
              </div>
              <ChevronRight :size="16" class="text-muted-foreground shrink-0" />
            </button>
          </div>

          <!-- 个人空间私密属性开关 -->
          <div v-if="privateSpaces.length > 0" class="space-y-2 pt-2">
            <p class="text-xs font-medium text-muted-foreground">空间私密保护设置</p>
            <div class="divide-y divide-border rounded-2xl border border-border bg-card px-3.5">
              <div
                v-for="sp in privateSpaces"
                :key="`toggle-${sp.id}`"
                class="flex items-center justify-between gap-3 py-3"
              >
                <span class="text-sm text-foreground truncate">{{ sp.name }}</span>
                <VanSwitch
                  :model-value="sp.privateEnabled"
                  size="20"
                  @update:model-value="val => toggleSpacePrivate(sp, val)"
                />
              </div>
            </div>
          </div>
        </template>

        <template v-else>
          <EmptyState
            v-if="hiddenItems.length === 0"
            title="暂无隐藏文件或文件夹"
            description="在普通目录更多菜单中可将文件设为隐藏"
          />
          <div v-else class="divide-y divide-border rounded-2xl border border-border bg-card px-3.5">
            <div
              v-for="item in hiddenItems"
              :key="item.id"
              class="flex items-center gap-3 py-3"
              @click="previewHiddenItem(item)"
            >
              <span class="flex h-9 w-9 items-center justify-center rounded-xl bg-muted text-foreground shrink-0">
                <Folder v-if="item.nodeType === 'FOLDER'" :size="18" />
                <File v-else :size="18" />
              </span>
              <div class="flex-1 min-w-0">
                <p class="text-sm font-medium truncate text-foreground">{{ item.name }}</p>
                <p class="text-xs text-muted-foreground truncate">
                  {{ item.spaceName }} · {{ item.nodeType === 'FOLDER' ? '文件夹' : formatBytes(item.size) }}
                </p>
              </div>
              <button
                type="button"
                class="inline-flex items-center gap-1 rounded-full border border-border px-2.5 py-1 text-xs text-foreground active:bg-accent shrink-0"
                @click.stop="unhideItem(item)"
              >
                <Eye :size="12" />
                <span>取消隐藏</span>
              </button>
            </div>
          </div>
        </template>
      </div>
    </div>

    <MobilePreviewShell
      :open="Boolean(previewNode)"
      :node="previewNode"
      :siblings="spaceNodes"
      @close="previewNode = null"
      @select="n => (previewNode = n)"
    />
  </MobileShell>
</template>
