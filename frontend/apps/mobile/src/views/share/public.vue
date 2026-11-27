<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Button as VanButton, showToast } from 'vant'
import {
  ChevronRight,
  CloudDownload,
  Download,
  File,
  Folder,
  FolderPlus,
  Lock,
  Share2,
} from '@lucide/vue'
import { normalizeErrorMessage } from '@repo/drive-core/errors'
import { formatBytes, formatDateTime, formatRelativeTime } from '@repo/drive-core/format'
import { resolvePreviewKind } from '@repo/drive-core/preview-registry'
import { getAccessToken } from '@repo/session/auth'
import { mobileShareApi, type PublicNode, type PublicShareMeta } from '@/api/share'
import { triggerBlobDownload } from '@/lib/file-actions'
import { fetchSpaces } from '@/stores/spaces'
import { BottomSheet, EmptyState, ErrorState, SkeletonList } from '@/components/common'
import FolderPickerSheet from '@/components/drive/FolderPickerSheet.vue'

const route = useRoute()
const router = useRouter()

const token = computed(() => String(route.params.token || ''))
const pwdStorageKey = computed(() => `cloudlane.mobile.sharepwd.${token.value}`)

const meta = ref<PublicShareMeta | null>(null)
const loading = ref(true)
const error = ref('')
const password = ref('')
const unlockedPassword = ref('')
const verifying = ref(false)
const verifyError = ref('')

// Folder browsing state
const folderNodes = ref<PublicNode[]>([])
const folderLoading = ref(false)
const breadcrumbs = ref<{ id: string; name: string }[]>([])

// Inline preview state
const previewOpen = ref(false)
const previewItem = ref<{ id?: string; name: string; size: number; mimeType: string } | null>(null)
const previewLoading = ref(false)
const previewError = ref('')
const previewBlobUrl = ref<string | null>(null)
const previewText = ref<string | null>(null)
const previewKind = computed(() =>
  previewItem.value ? resolvePreviewKind(previewItem.value.name, previewItem.value.mimeType) : 'unsupported',
)

// Save-to-drive state
const pickerOpen = ref(false)
const saving = ref(false)
const saveTargetNodeId = ref<string | undefined>(undefined)

function revokePreviewUrl() {
  if (previewBlobUrl.value) {
    URL.revokeObjectURL(previewBlobUrl.value)
    previewBlobUrl.value = null
  }
  previewText.value = null
}

function readStoredPassword(): string {
  try {
    return sessionStorage.getItem(pwdStorageKey.value) || ''
  } catch {
    return ''
  }
}

function storePassword(pwd: string) {
  try {
    if (pwd) sessionStorage.setItem(pwdStorageKey.value, pwd)
  } catch {
    // ignore storage errors
  }
}

async function loadFolder(parentId?: string) {
  folderLoading.value = true
  try {
    const res = await mobileShareApi.publicShareNodes(token.value, unlockedPassword.value || undefined, parentId)
    folderNodes.value = res.items
  } catch (err) {
    showToast(normalizeErrorMessage(err, '加载分享目录失败'))
  } finally {
    folderLoading.value = false
  }
}

async function loadMeta() {
  loading.value = true
  error.value = ''
  const cachedPwd = readStoredPassword()
  try {
    const res = await mobileShareApi.publicShareMeta(token.value, cachedPwd || undefined)
    meta.value = res
    if (res.unlocked) {
      unlockedPassword.value = cachedPwd
      if (res.nodeType === 'FOLDER') {
        breadcrumbs.value = [{ id: res.nodeId, name: res.nodeName }]
        await loadFolder(res.nodeId)
      }
    }
  } catch (err) {
    error.value = normalizeErrorMessage(err, '分享链接无效、已过期或已停用')
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  void loadMeta()
})

onBeforeUnmount(() => {
  revokePreviewUrl()
})

async function submitPassword() {
  const pwd = password.value.trim()
  if (!pwd) {
    verifyError.value = '请输入提取码'
    return
  }
  verifying.value = true
  verifyError.value = ''
  try {
    await mobileShareApi.verifySharePassword(token.value, pwd)
    unlockedPassword.value = pwd
    storePassword(pwd)
    await loadMeta()
  } catch (err) {
    verifyError.value = normalizeErrorMessage(err, '提取码错误，请重新输入')
  } finally {
    verifying.value = false
  }
}

function enterSubfolder(node: PublicNode) {
  breadcrumbs.value = [...breadcrumbs.value, { id: node.id, name: node.name }]
  void loadFolder(node.id)
}

function jumpBreadcrumb(idx: number) {
  const target = breadcrumbs.value[idx]
  if (!target) return
  breadcrumbs.value = breadcrumbs.value.slice(0, idx + 1)
  void loadFolder(target.id)
}

async function openInlinePreview(item: { id?: string; name: string; size: number; mimeType: string }) {
  revokePreviewUrl()
  previewItem.value = item
  previewError.value = ''
  previewOpen.value = true

  const kind = resolvePreviewKind(item.name, item.mimeType)
  if (!['image', 'pdf', 'video', 'audio', 'markdown', 'code', 'text'].includes(kind)) {
    previewLoading.value = false
    return
  }

  previewLoading.value = true
  try {
    const blob = await mobileShareApi.publicShareContent(token.value, {
      password: unlockedPassword.value || undefined,
      nodeId: item.id,
      download: false,
    })
    if (kind === 'markdown' || kind === 'code' || kind === 'text') {
      previewText.value = await blob.text()
    } else {
      previewBlobUrl.value = URL.createObjectURL(blob)
    }
  } catch (err) {
    previewError.value = normalizeErrorMessage(err, '在线预览加载失败')
  } finally {
    previewLoading.value = false
  }
}

async function downloadItem(item: { id?: string; name: string }) {
  if (!meta.value?.allowDownload) {
    showToast('分享者已禁止下载该资源')
    return
  }
  try {
    showToast('正在准备下载…')
    const blob = await mobileShareApi.publicShareContent(token.value, {
      password: unlockedPassword.value || undefined,
      nodeId: item.id,
      download: true,
    })
    triggerBlobDownload(blob, item.name)
  } catch (err) {
    showToast(normalizeErrorMessage(err, '下载失败'))
  }
}

async function startSaveToDrive(nodeId?: string) {
  if (!getAccessToken()) {
    storePassword(unlockedPassword.value)
    showToast('请先登录后转存到我的网盘')
    await router.push({
      path: '/auth/login',
      query: { redirect: route.fullPath },
    })
    return
  }
  saveTargetNodeId.value = nodeId
  await fetchSpaces()
  pickerOpen.value = true
}

async function handleSaveConfirm(payload: { targetSpaceId: string; targetParentId: string; targetLabel: string }) {
  saving.value = true
  try {
    const saved = await mobileShareApi.savePublicShare(token.value, {
      targetSpaceId: Number(payload.targetSpaceId),
      targetParentId: Number(payload.targetParentId),
      nodeId: saveTargetNodeId.value ? Number(saveTargetNodeId.value) : undefined,
      password: unlockedPassword.value || undefined,
    })
    pickerOpen.value = false
    previewOpen.value = false
    showToast(`已转存「${saved.name}」到 ${payload.targetLabel}`)
  } catch (err) {
    showToast(normalizeErrorMessage(err, '转存失败'))
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <div class="min-h-dvh flex flex-col bg-background text-foreground">
    <!-- 极简公开页顶栏 -->
    <header class="sticky top-0 z-20 border-b border-border bg-background/95 backdrop-blur px-4 py-3 safe-top flex items-center justify-between gap-2">
      <div class="flex items-center gap-2 min-w-0">
        <span class="flex h-8 w-8 items-center justify-center rounded-xl bg-primary/10 text-primary shrink-0">
          <Share2 :size="17" />
        </span>
        <div class="min-w-0">
          <p class="text-sm font-semibold truncate text-foreground">Cloudlane 公开分享</p>
          <p v-if="meta?.ownerName" class="text-[11px] text-muted-foreground truncate">
            来自 {{ meta.ownerName }}
          </p>
        </div>
      </div>

      <button
        type="button"
        class="rounded-full border border-border px-3 py-1 text-xs font-medium text-foreground active:bg-accent shrink-0"
        @click="router.push(getAccessToken() ? '/home' : '/auth/login')"
      >
        {{ getAccessToken() ? '进入网盘' : '登录' }}
      </button>
    </header>

    <main class="flex-1 min-h-0 flex flex-col">
      <SkeletonList v-if="loading" :count="5" />

      <ErrorState v-else-if="error || !meta" :message="error || '分享链接已失效'" @retry="loadMeta" />

      <!-- 提取码解锁卡片 -->
      <div
        v-else-if="meta.requiresPassword && !meta.unlocked"
        class="flex-1 flex items-center justify-center px-5 py-10"
      >
        <form
          class="w-full max-w-sm rounded-3xl border border-border bg-card p-6 shadow-xs space-y-4"
          @submit.prevent="submitPassword"
        >
          <div class="flex flex-col items-center text-center gap-2">
            <span class="flex h-12 w-12 items-center justify-center rounded-2xl bg-primary/10 text-primary">
              <Lock :size="22" />
            </span>
            <h2 class="text-base font-semibold text-foreground break-all">{{ meta.nodeName }}</h2>
            <p class="text-xs text-muted-foreground">
              {{ meta.ownerName ? `${meta.ownerName} 加密分享了此资源` : '此分享受提取码保护' }}
            </p>
          </div>

          <div>
            <input
              v-model="password"
              type="text"
              maxlength="32"
              placeholder="请输入提取码"
              class="w-full rounded-xl border border-input bg-background px-3.5 py-2.5 text-center text-base tracking-widest text-foreground focus:outline-none focus:ring-2 focus:ring-primary/40"
            />
            <p v-if="verifyError" class="text-xs text-destructive text-center mt-1.5">{{ verifyError }}</p>
          </div>

          <VanButton block round type="primary" native-type="submit" :loading="verifying">
            提取文件
          </VanButton>
        </form>
      </div>

      <!-- 已解锁：单文件展示 -->
      <div v-else-if="meta.nodeType !== 'FOLDER'" class="flex-1 flex flex-col items-center justify-center px-5 py-8">
        <div class="w-full max-w-sm rounded-3xl border border-border bg-card p-6 space-y-4 shadow-xs text-center">
          <span class="mx-auto flex h-14 w-14 items-center justify-center rounded-2xl bg-primary/10 text-primary">
            <File :size="28" />
          </span>

          <div class="space-y-1">
            <h2 class="text-base font-semibold text-foreground break-all">{{ meta.nodeName }}</h2>
            <p class="text-xs text-muted-foreground">
              {{ formatBytes(meta.size) }}
              <template v-if="meta.expiresAt"> · 有效期至 {{ formatDateTime(meta.expiresAt) }}</template>
            </p>
          </div>

          <div class="space-y-2 pt-1">
            <VanButton
              block
              round
              type="primary"
              @click="openInlinePreview({ id: meta.nodeId, name: meta.nodeName, size: meta.size, mimeType: meta.mimeType })"
            >
              在线预览
            </VanButton>

            <div class="grid grid-cols-2 gap-2">
              <VanButton
                block
                round
                plain
                type="primary"
                :disabled="!meta.allowDownload"
                @click="downloadItem({ id: meta.nodeId, name: meta.nodeName })"
              >
                <span class="inline-flex items-center gap-1">
                  <Download :size="14" />
                  <span>{{ meta.allowDownload ? '下载' : '禁止下载' }}</span>
                </span>
              </VanButton>
              <VanButton block round plain type="primary" @click="startSaveToDrive()">
                <span class="inline-flex items-center gap-1">
                  <FolderPlus :size="14" />
                  <span>一键转存</span>
                </span>
              </VanButton>
            </div>
          </div>
        </div>
      </div>

      <!-- 已解锁：文件夹层级浏览 -->
      <div v-else class="flex-1 flex flex-col">
        <div class="border-b border-border bg-card/80 px-4 py-3 flex items-center justify-between gap-2">
          <div class="min-w-0">
            <p class="text-sm font-semibold truncate text-foreground">{{ meta.nodeName }}</p>
            <p class="text-[11px] text-muted-foreground">
              {{ meta.expiresAt ? `有效期至 ${formatDateTime(meta.expiresAt)}` : '永久有效' }}
            </p>
          </div>
          <VanButton size="small" round type="primary" @click="startSaveToDrive()">
            <span class="inline-flex items-center gap-1">
              <CloudDownload :size="14" />
              <span>转存全部</span>
            </span>
          </VanButton>
        </div>

        <!-- 面包屑 -->
        <nav class="flex items-center gap-1 overflow-x-auto whitespace-nowrap border-b border-border bg-muted/20 px-3.5 py-2 text-xs text-muted-foreground no-scrollbar">
          <template v-for="(crumb, idx) in breadcrumbs" :key="crumb.id">
            <ChevronRight v-if="idx > 0" :size="12" class="shrink-0 opacity-60" />
            <button
              type="button"
              class="shrink-0 max-w-[140px] truncate rounded px-1.5 py-0.5 active:bg-accent"
              :class="idx === breadcrumbs.length - 1 ? 'font-semibold text-foreground' : ''"
              @click="jumpBreadcrumb(idx)"
            >
              {{ crumb.name }}
            </button>
          </template>
        </nav>

        <SkeletonList v-if="folderLoading" :count="5" />
        <EmptyState v-else-if="folderNodes.length === 0" title="当前共享目录为空" />
        <div v-else class="divide-y divide-border px-3.5 pb-12">
          <div
            v-for="node in folderNodes"
            :key="node.id"
            class="flex items-center gap-3 py-3 active:bg-accent/50"
            @click="node.nodeType === 'FOLDER' ? enterSubfolder(node) : openInlinePreview(node)"
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
            <button
              v-if="node.nodeType === 'FILE' && meta.allowDownload"
              type="button"
              class="inline-flex h-8 w-8 items-center justify-center rounded-full text-muted-foreground active:bg-accent shrink-0"
              aria-label="下载"
              @click.stop="downloadItem(node)"
            >
              <Download :size="16" />
            </button>
          </div>
        </div>
      </div>
    </main>

    <!-- 公开分享内嵌文件预览 BottomSheet -->
    <BottomSheet
      v-model:open="previewOpen"
      :title="previewItem?.name || '预览'"
      max-height="88vh"
      @close="revokePreviewUrl"
    >
      <div class="space-y-3 pb-2">
        <div v-if="previewLoading" class="py-12 text-center text-sm text-muted-foreground">正在加载预览…</div>
        <div v-else-if="previewError" class="py-8 text-center text-xs text-destructive">{{ previewError }}</div>
        <template v-else-if="previewItem">
          <img
            v-if="previewKind === 'image' && previewBlobUrl"
            :src="previewBlobUrl"
            :alt="previewItem.name"
            class="w-full max-h-[60vh] object-contain rounded-xl bg-muted"
          />
          <video
            v-else-if="previewKind === 'video' && previewBlobUrl"
            :src="previewBlobUrl"
            controls
            playsinline
            class="w-full max-h-[60vh] rounded-xl bg-black"
          />
          <audio
            v-else-if="previewKind === 'audio' && previewBlobUrl"
            :src="previewBlobUrl"
            controls
            class="w-full my-4"
          />
          <iframe
            v-else-if="previewKind === 'pdf' && previewBlobUrl"
            :src="previewBlobUrl"
            title="PDF 预览"
            class="w-full h-[60vh] rounded-xl border border-border bg-white"
          />
          <pre
            v-else-if="previewText !== null"
            class="text-xs font-mono whitespace-pre-wrap break-all rounded-xl bg-muted/50 p-3 max-h-[60vh] overflow-auto text-foreground"
          >{{ previewText }}</pre>
          <p v-else class="py-8 text-center text-xs text-muted-foreground">
            该格式暂不支持公开页在线预览，可直接下载或转存至网盘查看。
          </p>

          <div class="grid grid-cols-2 gap-2 pt-1">
            <VanButton
              block
              round
              plain
              type="primary"
              :disabled="!meta?.allowDownload"
              @click="downloadItem(previewItem)"
            >
              {{ meta?.allowDownload ? '下载文件' : '禁止下载' }}
            </VanButton>
            <VanButton block round type="primary" @click="startSaveToDrive(previewItem.id)">
              转存到网盘
            </VanButton>
          </div>
        </template>
      </div>
    </BottomSheet>

    <!-- 一键转存目标目录选择器 -->
    <FolderPickerSheet
      v-model:open="pickerOpen"
      mode="COPY"
      source-space-id=""
      title="选择转存目标位置"
      :submitting="saving"
      @confirm="handleSaveConfirm"
    />
  </div>
</template>
