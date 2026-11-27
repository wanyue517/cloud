<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Button as VanButton, showConfirmDialog, showToast } from 'vant'
import { Ban, Check, Folder, Link2, Mail, Trash2, Users, X } from '@lucide/vue'
import { roleLabel } from '@repo/drive-core/categories'
import { normalizeErrorMessage } from '@repo/drive-core/errors'
import { formatDateTime } from '@repo/drive-core/format'
import {
  mobileShareApi,
  subscribeSseWithBackoff,
  type InvitationItem,
  type ResourceShareItem,
  type ShareLinkItem,
} from '@/api/share'
import { resolveShareLinkStatus } from '@/lib/share-helpers'
import { fetchSpaces } from '@/stores/spaces'
import { EmptyState, ErrorState, PullRefresh, SkeletonList } from '@/components/common'

type SharedTab = 'received' | 'sent' | 'links'

const router = useRouter()

const activeTab = ref<SharedTab>('received')
const invitations = ref<InvitationItem[]>([])
const receivedShares = ref<ResourceShareItem[]>([])
const sentShares = ref<ResourceShareItem[]>([])
const shareLinks = ref<ShareLinkItem[]>([])
const loading = ref(true)
const refreshing = ref(false)
const error = ref('')
const busyId = ref('')

let stopSse: (() => void) | null = null

const pendingInvitations = computed(() =>
  invitations.value.filter(i => i.status === 'PENDING' || !i.status),
)

async function loadAll() {
  if (!refreshing.value) loading.value = true
  error.value = ''
  try {
    const [invRes, withMe, byMe, links] = await Promise.all([
      mobileShareApi.receivedInvitations().catch(() => []),
      mobileShareApi.sharedWithMe(),
      mobileShareApi.sharedByMe(),
      mobileShareApi.listMyShareLinks(),
    ])
    invitations.value = invRes
    receivedShares.value = withMe
    sentShares.value = byMe
    shareLinks.value = links
  } catch (err) {
    error.value = normalizeErrorMessage(err, '加载共享中心失败')
  } finally {
    loading.value = false
    refreshing.value = false
  }
}

onMounted(() => {
  void loadAll()
  stopSse = subscribeSseWithBackoff('/api/v1/shares/events', () => {
    void loadAll()
    void fetchSpaces(true)
  })
})

onBeforeUnmount(() => {
  stopSse?.()
  stopSse = null
})

async function onRefresh() {
  refreshing.value = true
  await loadAll()
}

async function handleInvitation(item: InvitationItem, accept: boolean) {
  busyId.value = item.id
  try {
    if (accept) {
      await mobileShareApi.acceptInvitation(item.id)
      showToast('已接受共享邀请')
      await fetchSpaces(true)
    } else {
      await mobileShareApi.rejectInvitation(item.id)
      showToast('已拒绝邀请')
    }
    await loadAll()
  } catch (err) {
    showToast(normalizeErrorMessage(err, '处理邀请失败'))
  } finally {
    busyId.value = ''
  }
}

async function revokeShare(item: ResourceShareItem) {
  try {
    await showConfirmDialog({
      title: '撤销共享',
      message: `确定撤销对「${item.targetName}」的「${item.nodeName}」共享权限吗？`,
      confirmButtonText: '确认撤销',
      confirmButtonColor: 'var(--destructive)',
    })
  } catch {
    return
  }
  busyId.value = item.id
  try {
    await mobileShareApi.revokeResourceShare(item.id)
    showToast('已撤销共享')
    await loadAll()
  } catch (err) {
    showToast(normalizeErrorMessage(err, '撤销失败'))
  } finally {
    busyId.value = ''
  }
}

async function disableLink(item: ShareLinkItem) {
  try {
    await showConfirmDialog({
      title: '停用分享链接',
      message: `停用后「${item.nodeName}」的公开链接将立即失效，确定停用吗？`,
      confirmButtonText: '停用',
      confirmButtonColor: 'var(--destructive)',
    })
  } catch {
    return
  }
  busyId.value = item.id
  try {
    await mobileShareApi.disableShareLink(item.id)
    showToast('已停用该分享链接')
    await loadAll()
  } catch (err) {
    showToast(normalizeErrorMessage(err, '停用失败'))
  } finally {
    busyId.value = ''
  }
}

function linkStatusMeta(item: ShareLinkItem) {
  const st = resolveShareLinkStatus(item)
  switch (st) {
    case 'ACTIVE':
      return { text: '有效', cls: 'bg-emerald-500/10 text-emerald-600 dark:text-emerald-400', active: true }
    case 'DISABLED':
      return { text: '已停用', cls: 'bg-muted text-muted-foreground', active: false }
    case 'EXPIRED':
      return { text: '已过期', cls: 'bg-amber-500/10 text-amber-600 dark:text-amber-400', active: false }
    case 'LIMIT_REACHED':
      return { text: '已达上限', cls: 'bg-amber-500/10 text-amber-600 dark:text-amber-400', active: false }
  }
}
</script>

<template>
  <div class="flex-1 min-h-0 flex flex-col bg-background text-foreground">
    <header class="sticky top-0 z-20 border-b border-border bg-background/95 backdrop-blur px-4 pt-3 pb-2.5 safe-top space-y-2.5">
      <h1 class="text-base font-semibold text-foreground">共享中心</h1>

      <!-- 三段式 Tab -->
      <div class="grid grid-cols-3 gap-1 rounded-xl bg-muted p-1">
        <button
          type="button"
          class="rounded-lg py-1.5 text-xs font-medium transition-colors"
          :class="activeTab === 'received' ? 'bg-background text-foreground shadow-xs' : 'text-muted-foreground'"
          @click="activeTab = 'received'"
        >
          与我共享 ({{ receivedShares.length }})
        </button>
        <button
          type="button"
          class="rounded-lg py-1.5 text-xs font-medium transition-colors"
          :class="activeTab === 'sent' ? 'bg-background text-foreground shadow-xs' : 'text-muted-foreground'"
          @click="activeTab = 'sent'"
        >
          我发起的 ({{ sentShares.length }})
        </button>
        <button
          type="button"
          class="rounded-lg py-1.5 text-xs font-medium transition-colors"
          :class="activeTab === 'links' ? 'bg-background text-foreground shadow-xs' : 'text-muted-foreground'"
          @click="activeTab = 'links'"
        >
          分享链接 ({{ shareLinks.length }})
        </button>
      </div>
    </header>

    <!-- 待处理共享邀请提示条 -->
    <div
      v-if="pendingInvitations.length > 0"
      class="border-b border-primary/20 bg-primary/5 px-3.5 py-2.5 space-y-2"
    >
      <div class="flex items-center gap-1.5 text-xs font-medium text-primary">
        <Mail :size="14" />
        <span>有 {{ pendingInvitations.length }} 条待处理共享邀请</span>
      </div>
      <div
        v-for="inv in pendingInvitations"
        :key="inv.id"
        class="rounded-xl border border-border bg-card p-3 flex items-center justify-between gap-2"
      >
        <div class="min-w-0 flex-1">
          <p class="text-xs font-medium text-foreground truncate">
            {{ inv.inviterName }} 邀请你加入「{{ inv.targetType === 'SPACE' ? inv.spaceName : inv.nodeName }}」
          </p>
          <p class="text-[11px] text-muted-foreground mt-0.5">
            权限：{{ roleLabel(inv.role) }}
          </p>
        </div>
        <div class="flex items-center gap-1.5 shrink-0">
          <VanButton
            size="mini"
            round
            plain
            :disabled="busyId === inv.id"
            @click="handleInvitation(inv, false)"
          >
            <X :size="12" />
          </VanButton>
          <VanButton
            size="mini"
            round
            type="primary"
            :loading="busyId === inv.id"
            @click="handleInvitation(inv, true)"
          >
            <span class="inline-flex items-center gap-0.5">
              <Check :size="12" />
              <span>接受</span>
            </span>
          </VanButton>
        </div>
      </div>
    </div>

    <PullRefresh v-model="refreshing" @refresh="onRefresh">
      <SkeletonList v-if="loading" :count="5" />
      <ErrorState v-else-if="error" :message="error" @retry="loadAll" />

      <!-- Tab 1: 与我共享 -->
      <div v-else-if="activeTab === 'received'" class="px-3.5 py-2 pb-20">
        <EmptyState
          v-if="receivedShares.length === 0"
          title="暂无与我共享的文件"
          description="其他人向你共享的文件夹会显示在这里"
        />
        <div v-else class="divide-y divide-border">
          <button
            v-for="item in receivedShares"
            :key="item.id"
            type="button"
            class="w-full flex items-center gap-3 py-3 text-left active:bg-accent/50"
            @click="router.push(`/shared/received/${item.id}`)"
          >
            <span class="flex h-10 w-10 items-center justify-center rounded-xl bg-primary/10 text-primary shrink-0">
              <Folder :size="20" />
            </span>
            <div class="flex-1 min-w-0">
              <p class="text-sm font-medium truncate text-foreground">{{ item.nodeName }}</p>
              <p class="text-xs text-muted-foreground mt-0.5 truncate">
                来自 {{ item.ownerName }} · {{ roleLabel(item.permission) }}
              </p>
            </div>
          </button>
        </div>
      </div>

      <!-- Tab 2: 我发起的 -->
      <div v-else-if="activeTab === 'sent'" class="px-3.5 py-2 pb-20">
        <EmptyState
          v-if="sentShares.length === 0"
          title="暂未发起协作共享"
          description="可在文件列表的文件夹操作菜单中选择「共享给成员」"
        />
        <div v-else class="divide-y divide-border">
          <div
            v-for="item in sentShares"
            :key="item.id"
            class="flex items-center gap-3 py-3"
          >
            <span class="flex h-10 w-10 items-center justify-center rounded-xl bg-sky-500/10 text-sky-600 dark:text-sky-400 shrink-0">
              <Users :size="19" />
            </span>
            <div class="flex-1 min-w-0">
              <p class="text-sm font-medium truncate text-foreground">{{ item.nodeName }}</p>
              <p class="text-xs text-muted-foreground mt-0.5 truncate">
                共享给 {{ item.targetName }} · {{ roleLabel(item.permission) }} · {{ formatDateTime(item.createdAt) }}
              </p>
            </div>
            <button
              type="button"
              class="inline-flex h-8 w-8 items-center justify-center rounded-full text-destructive active:bg-destructive/10 shrink-0"
              aria-label="撤销共享"
              :disabled="busyId === item.id"
              @click="revokeShare(item)"
            >
              <Trash2 :size="15" />
            </button>
          </div>
        </div>
      </div>

      <!-- Tab 3: 分享链接 -->
      <div v-else class="px-3.5 py-2 pb-20">
        <EmptyState
          v-if="shareLinks.length === 0"
          title="暂无公开分享链接"
          description="在文件列表的更多菜单中点击「分享」即可生成链接与二维码"
        />
        <div v-else class="space-y-2.5 pt-1">
          <div
            v-for="link in shareLinks"
            :key="link.id"
            class="rounded-2xl border border-border bg-card p-3.5 space-y-2"
          >
            <div class="flex items-start justify-between gap-2">
              <div class="flex items-center gap-2.5 min-w-0 flex-1">
                <span class="flex h-9 w-9 items-center justify-center rounded-xl bg-primary/10 text-primary shrink-0">
                  <Link2 :size="17" />
                </span>
                <div class="min-w-0 flex-1">
                  <p class="text-sm font-medium truncate text-foreground">{{ link.nodeName }}</p>
                  <p class="text-[11px] text-muted-foreground mt-0.5">
                    前缀 {{ link.tokenPrefix }}… · {{ link.hasPassword ? '需提取码' : '免提取码' }}
                  </p>
                </div>
              </div>
              <span
                class="rounded-full px-2 py-0.5 text-[10px] font-medium shrink-0"
                :class="linkStatusMeta(link).cls"
              >
                {{ linkStatusMeta(link).text }}
              </span>
            </div>

            <div class="flex items-center justify-between text-[11px] text-muted-foreground pt-1 border-t border-border/60">
              <span>
                浏览 {{ link.viewCount }} · 下载 {{ link.downloadCount }}{{ link.maxDownloads ? `/${link.maxDownloads}` : '' }}
              </span>
              <button
                v-if="linkStatusMeta(link).active"
                type="button"
                class="inline-flex items-center gap-1 rounded-lg px-2 py-1 text-destructive active:bg-destructive/10"
                :disabled="busyId === link.id"
                @click="disableLink(link)"
              >
                <Ban :size="12" />
                <span>停用</span>
              </button>
            </div>
          </div>
        </div>
      </div>
    </PullRefresh>
  </div>
</template>
